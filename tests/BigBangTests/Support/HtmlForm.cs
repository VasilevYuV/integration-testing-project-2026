using System.Globalization;
using System.Text.RegularExpressions;

namespace BigBangTests.Support;

// Only the fixed upstream input/form markup is parsed, not arbitrary HTML.
internal static class HtmlForm
{
    public static Dictionary<string, string> Inputs(string html)
    {
        var fields = new Dictionary<string, string>();
        foreach (Match input in Regex.Matches(html, "<input\\b[^>]*>", RegexOptions.IgnoreCase))
        {
            var attributes = Regex.Matches(input.Value, "([\\w-]+)=\"([^\"]*)\"")
                .ToDictionary(m => m.Groups[1].Value, m => WebUtility.HtmlDecode(m.Groups[2].Value));
            if (attributes.TryGetValue("name", out var name))
                fields[name] = attributes.GetValueOrDefault("value", "");
        }
        return fields;
    }

    public static string Token(string html) => Inputs(html)["__RequestVerificationToken"];

    public static int[] ProductIds(string html) => Regex.Matches(html, "<form\\b.*?</form>", RegexOptions.Singleline)
        .Select(m => Inputs(m.Value))
        .Where(fields => fields.ContainsKey("id"))
        .Select(fields => int.Parse(fields["id"], CultureInfo.InvariantCulture)).ToArray();

    public static int[] OrderIds(string html) => Regex.Matches(html, "href=\"/order/detail/(\\d+)\"", RegexOptions.IgnoreCase)
        .Select(m => int.Parse(m.Groups[1].Value, CultureInfo.InvariantCulture)).ToArray();

    public static int Quantity(string html) => int.Parse(Inputs(html)["Items[0].Quantity"], CultureInfo.InvariantCulture);
    public static string Money(decimal amount) => amount.ToString("N2", CultureInfo.InvariantCulture);
    public static string Text(string html) => WebUtility.HtmlDecode(Regex.Replace(html, "<[^>]+>", " "));
}
