"""Publish Maven XML results without treating missing reports as a successful run."""

import argparse
import json
import os
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET


REPORT_DIRECTORIES = (
    Path("order-service/target/surefire-reports"),
    Path("integration-tests/target/failsafe-reports"),
)
COUNTERS = ("tests", "failures", "errors", "skipped")


def collect_results():
    suites = []
    problems = []
    for directory in REPORT_DIRECTORIES:
        reports = sorted(directory.glob("TEST-*.xml"))
        if not reports:
            problems.append(f"No test reports in {directory.as_posix()}")
        for report in reports:
            try:
                suite = ET.parse(report).getroot()
                suites.append({
                    "name": suite.attrib["name"],
                    "report": report.as_posix(),
                    **{key: int(suite.attrib[key]) for key in COUNTERS},
                    "seconds": float(suite.attrib["time"]),
                })
            except (ET.ParseError, KeyError, ValueError) as error:
                problems.append(f"Invalid report {report.as_posix()}: {error}")
    totals = {key: sum(suite[key] for suite in suites) for key in COUNTERS}
    totals["passed"] = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    return suites, totals, problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    suites, totals, problems = collect_results()
    maven_outcome = os.environ.get("MAVEN_OUTCOME", "unknown")
    successful = (maven_outcome == "success" and not problems and totals["tests"] > 0
                  and all(totals[key] == 0 for key in ("failures", "errors", "skipped")))
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
    result = {
        "successful": successful,
        "mavenOutcome": maven_outcome,
        "commit": os.environ.get("GITHUB_SHA", ""),
        "event": os.environ.get("GITHUB_EVENT_NAME", "local"),
        "ref": os.environ.get("GITHUB_REF", ""),
        "runId": run_id,
        "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT", ""),
        "runUrl": f"{server}/{repository}/actions/runs/{run_id}" if run_id and repository else "",
        "totals": totals,
        "problems": problems,
        "suites": suites,
    }
    lines = ["## Maven integration tests", "",
             f"Maven step: **{maven_outcome}**. Complete test run: **{successful}**.", "",
             f"Tests: {totals['tests']}; passed: {totals['passed']}; failures: {totals['failures']}; "
             f"errors: {totals['errors']}; skipped: {totals['skipped']}.", "",
             "| Suite | Tests | Failures | Errors | Skipped | Seconds |",
             "| --- | ---: | ---: | ---: | ---: | ---: |"]
    for suite in suites:
        name = suite["name"].rsplit(".", 1)[-1].replace("|", "\\|")
        values = " | ".join(str(suite[key]) for key in COUNTERS)
        lines.append(f"| {name} | {values} | {suite['seconds']:.3f} |")
    if problems:
        lines += ["", "Report problems:", ""] + [f"- {problem}" for problem in problems]
    markdown = "\n".join(lines) + "\n"
    args.output.mkdir(parents=True, exist_ok=True)
    for directory in (*REPORT_DIRECTORIES, Path("integration-tests/target/service-logs")):
        if directory.is_dir():
            shutil.copytree(directory, args.output / directory.name, dirs_exist_ok=True)
    timings = Path("integration-tests/target/response-times.csv")
    if timings.is_file():
        shutil.copy2(timings, args.output / timings.name)
    (args.output / "test-summary.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    (args.output / "test-summary.md").write_text(markdown, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
            summary.write(markdown)
    print(markdown)
    return 0 if successful else 1


if __name__ == "__main__":
    raise SystemExit(main())
