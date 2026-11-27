# Contributing

The guides are the product, published as a public preview while their test coverage grows. Each step answers
one question with the failure it prevents. A claim tested here has an evidence record naming its test; the rest
rests on the official documentation each guide lists. `guidekit` enforces the rules below; CI runs it on every change.

Before you commit, read "What else to update when you change something" in [AGENTS.md](AGENTS.md): one row per kind
of change, with what regenerates itself, what you update by hand and which check catches a miss.

## Toolchain

| Tool | Version | Where |
| ---- | ------- | ----- |
| Go | 1.27.2 | `go.mod` (`toolchain go1.27.2`); all tooling |
| Node.js | 24.22.3, with npm 11.19.1 | `guides/.node-version`; only inside `guides/` |
| Docker | with Buildx | fixture builds and acceptance tests |

Site packages are pinned exactly in `guides/package.json`, lock file committed.

## Commands

```sh
go test ./cmd/... ./internal/...                 # guidekit's own tests
go run ./cmd/guidekit check --base origin/main   # data, guides, cross-references, retired URLs, navigation
go run ./cmd/guidekit generate                   # rewrite every generated file
go run ./cmd/guidekit generate --check           # fail if a generated file is stale
(cd guides && npm ci && npm run check && npm run build)   # in a subshell, so you stay at the root
go run ./cmd/guidekit site --dist guides/dist    # built HTML and llms exports
go run ./cmd/guidekit links --dist guides/dist   # external links (online)
go run ./cmd/guidekit boms                       # BOM-managed pins against Maven Central
```

Fixture and acceptance tests. The fixture build reads `templates/postgres/` and starts PostgreSQL with
Testcontainers, so it mounts the whole repository and the Docker socket, as its workflow does:

```sh
docker run --rm -v "$PWD:/repo" -w /repo/fixtures/single-hexagonal \
  -v /var/run/docker.sock:/var/run/docker.sock \
  eclipse-temurin@sha256:c7eef3dfeb81468533ea48c67bddaa8c7ca916454259e4d0d734d12bcff8e125 ./gradlew --no-daemon build
docker buildx build --load -t single-hexagonal:ci fixtures/single-hexagonal
go test -tags acceptance -count=1 -p 1 ./acceptance/...
```

The Initializer service and a project it generates. `bootRun` keeps running, so start it in its own terminal
and run the rest from a second one at the repository root:

```sh
./gradlew :start:build                           # engine tests, templates included
./gradlew :start:bootRun                         # first terminal
curl localhost:8080/starter.zip -d name=orders -d database=postgres -o orders.zip && unzip orders.zip
go run ./cmd/runworkflow -dir orders             # the generated project's own CI, on this machine
```

## Layout

| Path | What |
| ---- | ---- |
| `guides/` | Astro and Starlight site: pages, components, diagrams |
| `data/versions.yaml` | The baseline: every pinned version |
| `data/references.yaml` | Official documentation per component and baseline |
| `data/checklist.yaml` | Checklist items of every guide |
| `data/evidence/<guide>.yaml` | Evidence records, one claim each |
| `data/retired.yaml` | Retired guides, steps and checklist items, so their links keep working |
| `data/link-exceptions.yaml`, `audit-exceptions.yaml` | Expiring exceptions for the link check and `npm audit` |
| `data/schemas/` | JSON Schema 2020-12 for every data file, the guide frontmatter and fixtures |
| `fixtures/<name>/` | Services written by hand that prove the guides |
| `acceptance/` | Go behavioural tests against the fixture images (build tag `acceptance`) |
| `services/start/` | The Initializer service: the engine and its REST API (start.pyahu.io) |
| `templates/` | The files the engine renders into a generated project |
| `build-logic/` | Convention plugins of the Initializer's own Gradle build |
| `cmd/guidekit/`, `cmd/runworkflow/`, `internal/` | The Go tooling |

## Data files

- YAML is strict: no duplicate keys, no anchors or aliases, no non-string keys. Quote dates and
  versions (`"2026-10-17"`, `"1.10"`). Every object rejects unknown fields.
- IDs:

  | ID | Format | Example |
  | -- | ------ | ------- |
  | guide | kebab-case | `jvm-in-containers` |
  | guide prefix | 2 to 5 capitals | `JVM` |
  | question | `<prefix>-Q<n>` | `JVM-Q1` |
  | card (a step) | `<prefix lowercase>-<kebab>` | `jvm-heap-sizing` |
  | checklist item | `<prefix>-<two digits>` | `JVM-03` |
  | evidence record | `ev-<card>-<n>` | `ev-jvm-heap-sizing-1` |
  | reference entry, diagram | kebab-case | `jvm-container-memory` |
  | baseline | `<release>-b<n>` | `v0.1-b3` |

- Guides, cards and checklist items are in URLs and anchors readers save. Removing or renaming one fails unless
  `data/retired.yaml` gains a record in the same change (`id`, `kind`, `retired`, `reason` and `replacedBy` for a
  guide or card). The other IDs may change freely. A retired card keeps
  `<a id="<old>"></a>` before its replacement's anchor, so deep links keep working.
- No em or en dashes anywhere in guides or data.

## Guides

A guide is `guides/src/content/docs/guides/<guide-id>.mdx`. Its frontmatter `guide` block has
`id`, `prefix`, `state` (`draft` or `explanatory`), `baseline`, `components`, `verification`
(`null` until a complete run is recorded), `questions` (`id`, `text`, `answeredIn`) and
`sources` for ported text. `tableOfContents` is `{ maxHeadingLevel: 2 }`.

The MDX is restricted so `guidekit` can read its structure:

- Frontmatter, then `import` lines from `../../../components/`, then content.
- Only the guide components and `<a id="..."></a>` lines. No `{...}` expressions outside
  props, no MDX comments, no `export`, no Mermaid.
- Props are string literals, except `items` on `<DiagramLegend>`.

Body, in this order:

1. A short opening, prose only: a scene the reader recognises, then what the guide fixes. Two
   short paragraphs.
2. `<GuideStatus guide="..." />`. It renders "What you'll learn" (the questions, in order, each
   linking to its step) and one line on versions and how the guides are proven.
3. Steps, one per question: an anchor line, a blank line and `## <n>. <question text>`, so the
   heading is the question, numbered. Each `questions` entry answers exactly one step. Then:
   - a paragraph starting `**In short:**`: the answer in one sentence, optionally followed by
     the one line of config;
   - `### What's going on`: the mechanism in plain words, with an analogy when it helps and
     the step's diagram;
   - `### Do this`: numbered actions, with the code;
   - `### When this is not for you`: two or three bullets.

   The heading text may change with the question; the anchor may not.
4. `## Put it all together`: one sentence on where the steps land, then one `###` per example:
   a sentence or two on what it does and which steps it applies, one or more `<Recipe />`, and
   a paragraph starting `**Check it:**` with one way to see it work. An example may be a short
   list instead of code, such as what goes in the Deployment.
5. `## Checklist` with `<Checklist guide="..." />` and `## References` with
   `<GuideReferences guide="..." />`.

Write for someone meeting the topic for the first time: simple words, short sentences, the
problem before the jargon. Detail that does not change what the reader does goes into the
evidence or out.

`<Recipe id path start end lang />` shows a fixture file from the first line starting with
`start` through the first later line starting with `end` (one line without `end`). `start`
must match exactly one line. The complete file is published at `/fixtures/<path>/`.

## Evidence

Only tested claims get a record, in `data/evidence/<guide>.yaml`:

```yaml
- id: ev-jvm-heap-sizing-1
  card: jvm-heap-sizing
  claim: "With -XX:MaxRAMPercentage=75 and a 1 GiB memory limit, MaxHeapSize is 805306368 bytes."
  badge: reproduced
  test: acceptance/jvm::TestHeapIsSeventyFivePercent
  result: "--memory=1g -XX:MaxRAMPercentage=75: MaxHeapSize = 805306368"
  limitations: "Flags only. Native memory under load is not measured here."
```

| Badge | Means |
| ----- | ----- |
| `compiled` | the code the claim is about builds and loads |
| `reproduced` | a test observes the behaviour |
| `measured` | a recorded measurement; also needs `measurement`, `result`, `run` and `date`, because the numbers belong to that run |

`test` must be in a fixture's `requiredTests`. The fixture workflow runs every required test on every change and
fails when one does not pass, so a record is true for as long as `main` is green; there is nothing to record by
hand after a run. `result` says what the test observes, `limitations` what it does not prove.

A step without a record is explained from the official documentation the guide lists and from our practice. A
guide with no record at all is a draft: it carries a Draft label on its page, in the menu, on the landing page and
in the Agent Skill. The label goes away with its first tested claim.

Test what protects a reader most: database permissions, migrations, authentication, startup and failure handling.
For a library default, cite its official documentation instead of reproducing it.

## Baselines

The process, step by step, is public at https://initializer.pyahu.io/maintenance/. Renovate
(`renovate.json`) opens pull requests for tooling only; baseline and toolchain updates wait on the
Dependency Dashboard.

`data/versions.yaml` holds the one baseline the guides, the fixture and the generator use. Update the pins in
place and give it a new `id` (`v0.1-b4` after `v0.1-b3`); git keeps the earlier lists. The fixture's CI fails
when a resolved version, image digest or `java -version` differs from it.

A pin with `managedBy` must equal what the managing BOM gives its `coordinates`; `guidekit boms`
checks this weekly.

## References

Each entry in `data/references.yaml` names a `component`, the `docVersion` it covers and how that must `match`
the pin (`exact`, `minor`, `major` or `none`). `baselines: [<active id>]` puts it on the reference page and in the
fixture's references; `baselines: []` marks a document for a later release, shown only on the guides that list it
in `guides`. An entry that is on neither has no reader: delete it. `pinNote` is optional and says only what the
fields cannot, such as a version skew. `expect` is text the link check must find on the page; for a release-line
document, expect the release line (`content="2.1.`), not a patch, which upstream replaces.

`guides`, `appliesWhen`, `jep` and `llms` are left out when empty.

## Checklist

Items in `data/checklist.yaml` have a `type` (`generated-config`, `verified-behaviour`,
`deployment-prerequisite`, `operator-task`). `appliesWhen` filters by project options:
keys combine with AND, values with OR, `{}` means always.

## Generated files

`guidekit generate` writes these deterministically; commit them, never edit them:

- `guides/src/content/docs/reference/docs.md`
- `guides/src/generated/guides.json` and `guides/src/generated/fixture-files.json`
- `fixtures/<name>/docs/references.md`
- `templates/checklist.json` (the checklist the project generator filters by the project's options)
- `skills/pyahu-production-guides/` (the Agent Skill: `SKILL.md`, `checklist.md` and one file per guide)

## Fixtures

`fixtures/<name>/fixture.yaml` sets the project options, the components the
fixture `exercises` and its `requiredTests`. The fixture workflow builds it inside the pinned
JDK image, builds the image, runs the acceptance tests and runs `guidekit evidence verify`,
which fails when a required test is missing, skipped or failed or when a resolved version,
image digest or `java -version` differs from the baseline. It uploads `evidence-report` even
when it fails.

To add a tested claim: write the test, add it to `requiredTests` and add the record in the same change.

The single-hexagonal fixture's `DatabaseRolesTest` runs PostgreSQL as a test container, using the role script and
changelog from `templates/postgres/`. Its database dependencies are test-only: the service image used by JVM and
telemetry acceptance tests still has no data source. The fixture workflow watches those template inputs too.

Spring Boot database integration runs in the separate `databaseTest` suite, also required by `build`. This keeps
JDBC auto-configuration out of the ordinary fixture tests. Both suites' JUnit reports feed the evidence report.

## Templates

`templates/base/` goes into every project, `templates/<architecture>/` adds the files of one architecture and each
option adds its own sets: `postgres/` with `<architecture>-postgres/`, `oidc/`, `nextjs/` with `nextjs-oidc/` or
`nextjs-plain/` (the frontend's package and lock files differ by the login library). A path segment `_package_`
becomes the package directory; `gitignore`, `gitattributes` and `dockerignore` get their dot when generated. Two
sets writing the same file is an error, never a merge.

Every file is a JMustache template with `[[ ]]` as delimiters, so `${{ }}` in workflows passes through. The
wrapper scripts and the wrapper jar are copied from this repository at build time and never rendered. `ProjectRecipe`
decides what a project contains: dependencies, catalog entries, `application.yaml` keys and checklist items. The
templates only write those decisions. Where one file differs by option, a template uses a section per option flag
(`[[#postgres]]`, `[[#oidc]]`, `[[#nextjs]]`) and holds no other logic. Imports of rendered Java files are sorted after
rendering, so `spotlessCheck` passes for any package. `frontends/web/src/api/schema.d.ts` must be what
`openapi-typescript` writes for each variant of the contract; the generated workflow checks it.

Core Java platform versions come from the active baseline when a project is generated. Frontend packages and
auxiliary build tools have exact pins in their lockfiles, Dockerfiles and workflows; update their related copies
together and run the generator matrix. The Java fixture does not test these pins. The
exceptions are tooling that Renovate updates: the generated workflow's action SHAs, the frontend's npm packages
and lock files, its Node image (listed only, like a toolchain) and the mock identity provider of `compose.yml` and
the smoke test. Tests keep the kustomize image on the baseline's kustomize version and each image pinned in
several files the same in all of them. The matrix in `start.yml` generates all 16 combinations through the API and
runs each generated workflow with `cmd/runworkflow`.

## The Initializer's own files

The Initializer is a project its own engine would produce: ddd-layered, with a Next.js frontend.
`services/start/drift.yaml` says what each generated file is here: the same bytes, the generated lines with more
between them, adapted for a reason it gives or left out for one. `DriftTest` fails when a file drifts or a new
template file has no rule. After changing a template, `./gradlew :start:regenerate` writes the files marked same
again; review the diff like any other change.

`frontends/start-web` is the generator page. It loads the form from `/metadata`; on every change a Server Action
fetches the files from `/preview`. Its own `/starter.zip` passes download requests on to the service, so a terminal
and the browser use the same address.

Its Playwright tests run in CI against both images. To run them locally, start the service in one terminal; in a
second, run `npm ci` and `npm run dev` in `frontends/start-web/`; in a third, in the same directory, run
`npx playwright install chromium` once, then `npm run test:e2e`.

## Exceptions

`data/link-exceptions.yaml` and `data/audit-exceptions.yaml` need a reason and expire at most
30 days after `added`. `added` is the date of the commit that introduced the record and never
changes; a renewal is a new record.

## CI and publication

Each workflow runs only for the paths that can change its result, and a new push cancels the older run on the
same ref.

| Workflow | Does | Blocks publication |
| -------- | ---- | ------------------ |
| `guides.yml` | clean checkout, `go test`, `check`, `generate --check`, site build, `site` | yes |
| `fixture-single-hexagonal.yml` | fixture build, image, acceptance tests, `evidence verify` | yes |
| `start.yml` | the Initializer: build, image, smoke, then all 16 generated combinations through their own CI | no |
| `links.yml` | online: external links, BOM pins, `npm audit` of the site; also every Monday | no |

The site is published at https://initializer.pyahu.io by Cloudflare Workers Builds (root directory `guides`,
branch `main`, `NODE_VERSION=24.22.3`). Its deploy command, `npm run deploy`, first runs `publication:check`,
which waits up to 30 minutes for successful Guides and fixture runs whose inputs match the commit and refuses a
dirty checkout or a commit superseded on `main`. `guides/wrangler.jsonc` declares the Worker and its domain; the
repository connection and build variables live in the Worker's Settings > Builds. For a local packaging check,
run `cd guides && npx wrangler deploy --dry-run` after a build.

Things that have bitten us:

- Gradle downloads fail now and then. `.github/scripts/gradle-ci.sh` retries dependency acquisition up to three
  times, also inside the Docker build stages; test, compile and formatting failures are final.
- CI passes the tested jar to the Dockerfile as a named context (`--build-context build=...`), so the image holds
  the artifact that passed the tests. Without it, the Dockerfile compiles from source.
- Windows wrappers are LF in Git and CRLF on checkout (`.gitattributes`). An unnormalized blob makes a fresh clone
  dirty and blocks every Workers build; normalize with `git add --renormalize`. The Guides workflow checks it.

Commits go straight to `main`; a red pipeline is fixed with a new commit.
