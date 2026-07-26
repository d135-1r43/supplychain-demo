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

The whole pipeline is wired up and has something to report: the build generates a CycloneDX
SBOM, CI publishes it to Dependency-Track and pushes a container image to GHCR, a
[second SBOM scans that image](#the-image-sbom) so the base layer is covered too, and
`pom.xml` carries two [deliberately vulnerable dependencies](#the-deliberately-vulnerable-dependencies)
so the findings page is not empty.

## Prerequisites

- JDK 25 (the build targets `maven.compiler.release=25`)
- A container runtime (Docker or Podman) — Quarkus Dev Services starts Postgres for you in
  dev and test mode, so there is no database to install
- A reachable Dependency-Track instance plus an API key whose team has `BOM_UPLOAD` and
  `PROJECT_CREATION_UPLOAD`, for the publishing step

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

SBOM generation is a Quarkus extension, not a separate plugin invocation. It is wired up in
`pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-cyclonedx</artifactId>
</dependency>
```

and configured in `src/main/resources/application.properties`:

```properties
quarkus.cyclonedx.format=json
quarkus.cyclonedx.pretty-print=true
```

Every `./mvnw package` writes a CycloneDX 1.6 document into the build output directory,
named after the runnable JAR:

```shell
./mvnw package
jq '.components | length' target/quarkus-run-cyclonedx.json
```

Several hundred components, from the handful of dependencies declared in `pom.xml`. Run it
live rather than quoting a number — it drifts with every platform bump, and differs between
your laptop and the CI runner because the resolved native artifacts are not the same.

Why the extension rather than the more widely known `cyclonedx-maven-plugin`: the plugin
reports the *Maven dependency tree*, while the Quarkus extension reports the *distribution* —
what the augmentation step actually assembled, after Quarkus has dropped build-time-only
artifacts and resolved the runtime classpath. Those two lists are not the same, and the
difference is worth a slide.

The complete set of settings the extension offers in Quarkus 3.37 — there are only nine:

| Property | Default | Effect |
| --- | --- | --- |
| `quarkus.cyclonedx.enabled` | `true` | Generate the SBOM at all |
| `quarkus.cyclonedx.format` | `json` | `json`, `xml` or both |
| `quarkus.cyclonedx.schema-version` | latest | Pin the CycloneDX schema version |
| `quarkus.cyclonedx.pretty-print` | `false` | Readable output |
| `quarkus.cyclonedx.include-license-text` | `false` | Embed full license texts |
| `quarkus.cyclonedx.embedded.enabled` | `false` | Ship the SBOM inside the app |
| `quarkus.cyclonedx.embedded.resource-name` | `META-INF/sbom/dependency.cdx.json` | Where it lands on the classpath |
| `quarkus.cyclonedx.embedded.compress` | `true` | Compress the embedded copy |

`embedded.enabled` is the interesting one: the application carries its own bill of materials
as a classpath resource, so an auditor can ask a *running* instance what it is made of
instead of trusting a build artifact from months ago.

Note what is **not** on that list: there is no setting for the name the SBOM gives itself.
See below.

## Publishing to Dependency-Track

### Getting the name and version right

The SBOM names its own root component after the runnable JAR, not after the project:

```json
"metadata": { "component": { "name": "quarkus-run.jar", "version": "1.0.1-SNAPSHOT" } }
```

The version is right, the name is not, and the extension has no setting to change it. Left
alone, `autoCreate=true` would happily create a Dependency-Track project called
*quarkus-run.jar*. So the upload passes `projectName` and `projectVersion` explicitly — form
fields take precedence over the document's own metadata:

| Trigger | `projectVersion` in Dependency-Track |
| --- | --- |
| push to `main` | `1.0.1-SNAPSHOT` — overwritten on every build |
| tag `v1.0.0` | `1.0.0` — frozen, one project version per release |
| pull request | not uploaded |

Both come from `./mvnw help:evaluate`, the same values that tag the container image, so the
image and the Dependency-Track project version always describe the same build. One
`projectVersion` per release, not per commit — otherwise you will be scrolling through
several thousand project versions in the UI.

### Uploading

Dependency-Track accepts a BOM on `POST /api/v1/bom` as a multipart upload:

```shell
export DTRACK_URL=https://dependency-track.example
export DTRACK_API_KEY=...

curl -sS --fail-with-body -X POST "$DTRACK_URL/api/v1/bom" \
  -H "X-Api-Key: $DTRACK_API_KEY" \
  -F "autoCreate=true" \
  -F "projectName=supplychain-demo" \
  -F "projectVersion=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)" \
  -F "bom=@$(ls target/*-cyclonedx.json)"
```

The response contains a token. Ingestion is asynchronous, so a `200` only means the document
was accepted for processing, not that it was processed — poll
`GET /api/v1/bom/token/{token}` until `processing` is `false` before believing anything. The
CI step does exactly this; without it a green build says nothing about whether
Dependency-Track actually took the SBOM.

Once ingested, Dependency-Track keeps re-evaluating it as new advisories land. That is the
actual argument of the talk: an SBOM is not a build artifact you produce once and file away,
it is a subscription to bad news about code you already shipped.

### In CI

`.github/workflows/ci.yml` uploads on every push to `main` and every `v*` tag. It needs two
repository secrets:

```shell
gh secret set DTRACK_URL --body "https://dependency-track.example"   # no trailing slash
gh secret set DTRACK_API_KEY                                        # prompts, keeps it off the shell history
```

The SBOM is also attached to every run as a build artifact, so it can be downloaded and
shown even without a reachable Dependency-Track.

The instance has to be reachable from the GitHub runner. A Dependency-Track running on your
laptop is not — for that, upload from the workstation with the `curl` above.

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

## The deliberately vulnerable dependencies

> ⚠ `pom.xml` pins two known-bad artifacts **on purpose**. This repository is a demo. Do not
> copy those two `<dependency>` blocks anywhere near a real project.

| Artifact | Vulnerability | |
| --- | --- | --- |
| `org.apache.logging.log4j:log4j-core:2.14.1` | CVE-2021-44228 "Log4Shell" | CVSS 10.0 |
| `commons-collections:commons-collections:3.2.1` | CVE-2015-6420, unsafe deserialization | |

No code in this application imports either of them, which is the point worth making on
stage: a vulnerable component is part of what you ship whether or not you ever call it, and
the SBOM lists it either way. "We don't use that class" is not a remediation.

A second detail worth a slide: the Quarkus platform manages `log4j-api` at a current version
while `log4j-core` sits at the pinned 2.14.1. A split like that — half the library updated,
half not — is exactly what real dependency trees look like after a few years.

To make the demo boring again, delete both dependencies and the next build reports a clean
project.

## What is not wired up yet

- nothing gates on the findings; CI uploads the SBOM and moves on, so the build stays green
  no matter what Dependency-Track says. Reading the findings back and failing on severity or
  on policy violations is the obvious next step, and deliberately left out of the proof of
  concept

## Releasing

```shell
./mvnw release:prepare      # asks for the release and next development version
```

That sets the version, commits, tags and pushes. The tag format is configured as
`v@{project.version}`, so releases are tagged `v1.0.0` rather than the plugin's default
`supplychain-demo-1.0.0`. There is no `distributionManagement`, so `release:perform` is
configured to run `verify` instead of `deploy` — the published artifact is the container
image, not a JAR in a Maven repository.

Use the release version as the `projectVersion` when publishing the SBOM — that is the
number the findings in Dependency-Track end up attached to.

## Container image

Every build of `main` and every `v*` tag publishes to
[GHCR](https://github.com/d135-1r43/supplychain-demo/pkgs/container/supplychain-demo),
tagged with the Maven version of that build:

| Trigger | Version in `pom.xml` | Image tags |
| --- | --- | --- |
| push to `main` | `1.0.1-SNAPSHOT` | `1.0.1-SNAPSHOT`, `latest` |
| tag `v1.0.0` from `mvn release:prepare` | `1.0.0` | `1.0.0` |
| pull request | — | built but not pushed |

Note that `latest` follows the snapshot from `main`, not the newest release. Pulling it
gives you the current state of the demo; releases are only reachable under their exact
version, which is what you want when a finding has to be traced back to a specific build.

```shell
docker run --rm -p 8080:8080 \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://host.docker.internal:5432/demo \
  -e QUARKUS_DATASOURCE_USERNAME=demo -e QUARKUS_DATASOURCE_PASSWORD=demo \
  ghcr.io/d135-1r43/supplychain-demo:latest
```

The image is built from `src/main/docker/Dockerfile.jvm`, which the Quarkus scaffold
provides: a UBI 9 JDK 25 runtime running as non-root user 185. It expects `target/quarkus-app/`
to exist, so the workflow packages before it builds.

The first push creates the package as **private**. Make it public under the package's
settings if the audience should be able to pull it.

### The image SBOM

The Maven SBOM describes the *Java distribution* — the jars that ship in
`target/quarkus-app/`. The image ships more than that: the UBI 9 base OS packages and the
JDK sit underneath those jars, and none of them appear in the build's CycloneDX document.
So CI generates a second SBOM, scanning the image itself with
[Syft](https://github.com/anchore/syft) straight from the digest it just pushed:

```shell
syft ghcr.io/d135-1r43/supplychain-demo:latest -o cyclonedx-json
```

That document goes to Dependency-Track as a **separate project**, `supplychain-demo-image`,
sharing the same `projectVersion` as the app project for the same build. Two projects, not
one, because the interesting thing on stage is the comparison: the app SBOM lists a few
hundred jars, the image SBOM lists those *plus* everything the base layer drags in — which
is where the OS-level advisories a Maven-only view never sees come from.

Pull requests build the image too — loaded into the runner's Docker daemon rather than
pushed — so the image SBOM is generated and attached as a build artifact on every run,
alongside the app SBOM. Only the Dependency-Track upload is push-only: a PR shows you the
image's bill of materials without publishing anything.

| SBOM | Source | Dependency-Track project | Covers |
| --- | --- | --- | --- |
| app | `quarkus-cyclonedx`, at build time | `supplychain-demo` | the runtime jars |
| image | Syft, scanning the pushed image | `supplychain-demo-image` | jars **+** UBI 9 base OS packages + JDK |

## References

- [Quarkus CycloneDX guide](https://quarkus.io/guides/cyclonedx)
- [CycloneDX specification](https://cyclonedx.org/specification/overview/)
- [Dependency-Track REST API](https://docs.dependencytrack.org/integrations/rest-api/)
- [JUG München](https://jugm.de/)
