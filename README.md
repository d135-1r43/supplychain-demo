# supplychain-demo

Demo application for a talk at [JUG München](https://jugm.de/) on software supply chain
security in the JVM world.

The app itself is deliberately boring: a small [Quarkus](https://quarkus.io/) REST service
managing a JUG session schedule — speakers, talks, rooms — on top of Postgres. Nothing about
the domain matters. What matters is its *dependency graph* —
the build emits a [CycloneDX](https://cyclonedx.org/) SBOM describing everything that ends
up in the distribution, and that SBOM is published to
[Dependency-Track](https://dependencytrack.org/), which continuously correlates it against
vulnerability sources (OSV, NVD, GitHub Advisories).

```
./mvnw package  ──►  target/*-cyclonedx.json  ──►  Dependency-Track  ──►  findings / policy violations
     (Quarkus)          (CycloneDX SBOM)            (POST /api/v1/bom)
```

The point of the demo is that nobody audits transitive dependencies by hand. A REST service
with a handful of declared dependencies pulls in well over a hundred artifacts, and the interesting
question is not "what did I write in my `pom.xml`" but "what actually ships, and what do we
know about it today."

## Status

The application, its Postgres entities and the CI build are wired up. The CycloneDX and
Dependency-Track steps described below are the subject of the talk and are added live —
see [What is not wired up yet](#what-is-not-wired-up-yet).

## Prerequisites

- JDK 25 (the build targets `maven.compiler.release=25`)
- A container runtime (Docker or Podman) — Quarkus Dev Services starts Postgres for you in
  dev and test mode, so there is no database to install
- A reachable Dependency-Track instance plus an API key with the `BOM_UPLOAD` permission,
  for the publishing step

## Running the application

Dev mode, with live reload and an automatically provisioned Postgres:

```shell
./mvnw quarkus:dev
```

- <http://localhost:8080/q/swagger-ui/> — OpenAPI / Swagger UI
- <http://localhost:8080/q/dev/> — the Dev UI (dev mode only)

The database is seeded from `src/main/resources/import.sql` on every start, so there is
data to look at immediately:

| Method | Path | |
| --- | --- | --- |
| `GET` | `/api/talks` | all talks by schedule, `?room=` to filter |
| `GET` | `/api/talks/{id}` | a single talk |
| `POST` | `/api/talks` | create; references a speaker by `speakerId` |
| `PUT` | `/api/talks/{id}` | update |
| `DELETE` | `/api/talks/{id}` | delete |
| `GET` | `/api/speakers` | all speakers, `?company=` to filter |
| `GET` | `/api/speakers/{id}` | a single speaker |
| `GET` | `/api/speakers/{id}/talks` | that speaker's talks |
| `POST` | `/api/speakers` | create |
| `DELETE` | `/api/speakers/{id}` | delete |

```shell
curl -s localhost:8080/api/talks | jq '.[].title'
curl -s localhost:8080/api/talks -H 'Content-Type: application/json' \
  -d '{"title":"Reproducible Builds","summary":"Same input, same output.",
       "durationMinutes":45,"scheduledAt":"2026-11-19T19:00:00",
       "room":"Hoersaal 1","speakerId":1}'
```

Packaging:

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Note this is not an über-jar — dependencies are copied into `target/quarkus-app/lib/`. Which
is convenient for this demo, because you can look at exactly what shipped.

## Generating the SBOM

SBOM generation is a Quarkus extension, not a separate plugin invocation. Add it as a
dependency:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-cyclonedx</artifactId>
</dependency>
```

and configure it in `src/main/resources/application.properties`:

```properties
quarkus.cyclonedx.format=json
quarkus.cyclonedx.pretty-print=true
```

Every `./mvnw package` now writes a CycloneDX document into the build output directory,
named after the executable with a `-cyclonedx.<format>` suffix:

```shell
./mvnw package
ls target/*-cyclonedx.json
```

Why the extension rather than the more widely known `cyclonedx-maven-plugin`: the plugin
reports the *Maven dependency tree*, while the Quarkus extension reports the *distribution* —
what the augmentation step actually assembled, after Quarkus has dropped build-time-only
artifacts and resolved the runtime classpath. Those two lists are not the same, and the
difference is worth a slide.

Useful variations:

| Property | Effect |
| --- | --- |
| `quarkus.cyclonedx.format=all` | Emit both JSON and XML |
| `quarkus.cyclonedx.libraries-only=true` | Only library components, no application metadata |
| `quarkus.cyclonedx.schema-version` | Pin the CycloneDX schema version |
| `quarkus.cyclonedx.include-license-text=true` | Embed full license texts |
| `quarkus.cyclonedx.embedded.enabled=true` | Ship the SBOM inside the app as a classpath resource |
| `quarkus.cyclonedx.endpoint.enabled=true` | Serve the embedded SBOM over HTTP |

The last two are the fun ones: the application can carry and expose its own bill of
materials at runtime, so an auditor can ask a *running* instance what it is made of instead
of trusting a build artifact from months ago.

## Publishing to Dependency-Track

Dependency-Track accepts a BOM on `POST /api/v1/bom` as a multipart upload. With
`autoCreate=true` it creates the project on first upload, so there is no manual setup:

```shell
export DTRACK_URL=https://dependency-track.example
export DTRACK_API_KEY=...

curl -sS -X POST "$DTRACK_URL/api/v1/bom" \
  -H "X-Api-Key: $DTRACK_API_KEY" \
  -F "autoCreate=true" \
  -F "projectName=supplychain-demo" \
  -F "projectVersion=1.0.0-SNAPSHOT" \
  -F "bom=@$(ls target/*-cyclonedx.json)"
```

The response contains a token you can poll on `GET /api/v1/bom/token/{token}` to find out
when Dependency-Track has finished processing — worth doing in CI before you gate a build on
the findings.

Once ingested, Dependency-Track keeps re-evaluating the SBOM as new advisories land. That is
the actual argument of the talk: an SBOM is not a build artifact you produce once and file
away, it is a subscription to bad news about code you already shipped.

### In CI

`.github/workflows/ci.yml` currently only builds and tests. The publishing step slots in
after the build:

```yaml
      - name: Publish SBOM to Dependency-Track
        run: |
          curl -sS -X POST "${{ secrets.DTRACK_URL }}/api/v1/bom" \
            -H "X-Api-Key: ${{ secrets.DTRACK_API_KEY }}" \
            -F "autoCreate=true" \
            -F "projectName=supplychain-demo" \
            -F "projectVersion=${{ github.ref_name }}" \
            -F "bom=@$(ls target/*-cyclonedx.json)"
```

Use one `projectVersion` per release, not per commit, unless you enjoy scrolling through
several thousand projects in the Dependency-Track UI.

## Running Dependency-Track locally

For the demo, the bundled distribution is enough:

```shell
curl -sSL -o docker-compose.yml https://dependencytrack.org/docker-compose.yml
docker compose up -d
```

The UI comes up on <http://localhost:8080> — which collides with Quarkus, so either remap it
or run the app elsewhere with `-Dquarkus.http.port=8081`. Default credentials are
`admin` / `admin`; you are asked to change the password on first login. Create the API key
under *Administration → Access Management → Teams*.

## Testing

```shell
./mvnw test                     # Dev Services provides Postgres
./mvnw verify -DskipITs=false   # including the integration tests
```

## What is not wired up yet

Deliberately, so the talk can build it up on stage:

- `quarkus-cyclonedx` is not in `pom.xml`, and `application.properties` has no
  `quarkus.cyclonedx.*` settings
- CI builds and tests but does not publish an SBOM
- no known-vulnerable dependency is pinned yet, so Dependency-Track has nothing dramatic to
  report until one is added

## Releasing

```shell
./mvnw release:prepare      # asks for the release and next development version
```

That sets the version, commits, tags and pushes. The tag format is configured as
`v@{project.version}`, so releases are tagged `v1.0.0` rather than the plugin's default
`supplychain-demo-1.0.0`. There is no `distributionManagement`, so `release:perform` is
configured to run `verify` instead of `deploy`.

Use the release version as the `projectVersion` when publishing the SBOM — that is the
number the findings in Dependency-Track end up attached to.

## References

- [Quarkus CycloneDX guide](https://quarkus.io/guides/cyclonedx)
- [CycloneDX specification](https://cyclonedx.org/specification/overview/)
- [Dependency-Track REST API](https://docs.dependencytrack.org/integrations/rest-api/)
- [JUG München](https://jugm.de/)
