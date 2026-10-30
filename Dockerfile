ARG SDK_IMAGE
ARG RUNTIME_IMAGE

FROM ${SDK_IMAGE} AS build
WORKDIR /source
COPY .upstream/ ./
COPY environment/global.json ./global.json
COPY environment/appsettings.Docker.json ./src/BlazorAdmin/wwwroot/appsettings.Docker.json
RUN dotnet publish src/Web/Web.csproj -c Release -o /publish/web
RUN dotnet publish src/PublicApi/PublicApi.csproj -c Release -o /publish/api

FROM ${RUNTIME_IMAGE} AS web
WORKDIR /app
COPY --from=build /publish/web/ ./
COPY --from=build /source/LICENSE ./UPSTREAM-LICENSE
ENTRYPOINT ["dotnet", "Web.dll"]

FROM ${RUNTIME_IMAGE} AS api
WORKDIR /app
COPY --from=build /publish/api/ ./
COPY --from=build /source/LICENSE ./UPSTREAM-LICENSE
ENTRYPOINT ["dotnet", "PublicApi.dll"]
