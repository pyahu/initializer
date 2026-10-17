# Contributing

The guides are the product. Every decision is a card with the failure it prevents and every
claim carries the test that proves it. `guidekit` enforces the rules below; CI runs it on every
change.

Writing rules and the page shape are on the site: [style guide](https://initializer.pyahu.io/contributing/style/)
and [diagram kit](https://initializer.pyahu.io/contributing/diagrams/).

## Toolchain

| Tool | Version | Where |
| ---- | ------- | ----- |
| Go | 1.27.2 | `go.mod` (`toolchain go1.27.2`); all tooling |
| Node.js | 24.22.0, with npm 11.19.1 | `guides/.node-version`; only inside `guides/` |
| Docker | with Buildx | fixture builds and acceptance tests |

Site packages are pinned exactly in `guides/package.json`, lock file committed.

## Commands

```sh
go test ./cmd/... ./internal/...                 # guidekit's own tests
go run ./cmd/guidekit check --base origin/main   # data, guides, cross-references, history
go run ./cmd/guidekit generate                   # rewrite every generated file
go run ./cmd/guidekit generate --check           # fail if a generated file is stale
cd guides && npm ci && npm run check && npm run build
go run ./cmd/guidekit site --dist guides/dist    # built HTML and llms exports
go run ./cmd/guidekit links --dist guides/dist   # external links (online)
go run ./cmd/guidekit boms                       # BOM-managed pins against Maven Central
```

Fixture and acceptance tests:

```sh
docker run --rm -v "$PWD/fixtures/single-hexagonal:/w" -w /w \
  eclipse-temurin@sha256:a75cd2f1bd3e1e45da898bf658f1f1039ab7392b34844862514d230fd6a516d4 ./gradlew --no-daemon build
docker buildx build --load -t single-hexagonal:ci fixtures/single-hexagonal
go test -tags acceptance -count=1 -p 1 ./acceptance/...
```

## Layout

| Path | What |
| ---- | ---- |
| `guides/` | Astro and Starlight site: pages, components, diagrams |
| `data/versions.yaml` | Baseline revisions: every pinned version and its qualification |
| `data/references.yaml` | Official documentation per component and baseline |
| `data/checklist.yaml` | Checklist items of every guide |
| `data/evidence/<guide>.yaml` | Evidence records, one claim each |
| `data/invalidations.yaml`, `retired.yaml` | Open invalidations; retired stable IDs |
| `data/link-exceptions.yaml`, `audit-exceptions.yaml` | Expiring exceptions for the link check and `npm audit` |
| `data/schemas/` | JSON Schema 2020-12 for every data file, the guide frontmatter and fixtures |
| `fixtures/<name>/` | Services written by hand that prove the guides |
| `acceptance/` | Go behavioural tests against the fixture images (build tag `acceptance`) |
| `cmd/guidekit/`, `internal/` | The Go tooling |

## Data files

- YAML is strict: no duplicate keys, no anchors or aliases, no non-string keys. Quote dates and
  versions (`"2026-10-17"`, `"1.10"`). Every object rejects unknown fields.
- Stable IDs never change and are never reused:

  | ID | Format | Example |
  | -- | ------ | ------- |
  | guide | kebab-case | `jvm-in-containers` |
  | guide prefix | 2 to 5 capitals | `JVM` |
  | question | `<prefix>-Q<n>` | `JVM-Q1` |
  | card | `<prefix lowercase>-<kebab>` | `jvm-heap-sizing` |
  | checklist item | `<prefix>-<two digits>` | `JVM-03` |
  | evidence record | `ev-<card>-<n>` | `ev-jvm-heap-sizing-1` |
  | reference entry, diagram | kebab-case | `jvm-container-memory` |
  | baseline revision | `<release>-b<n>` | `v0.1-b1` |

- Removing or renaming an ID fails unless `data/retired.yaml` gains a record in the same change
  (`id`, `kind`, `retired`, `reason` and `replacedBy` for a guide or card). A retired card keeps
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
4. `## Put it all together`: per recipe a `###` heading, one `<Recipe />`, a paragraph starting
   `**Expected:**` and a list starting `**If it fails:**`.
5. `## Checklist` with `<Checklist guide="..." />` and `## References` with
   `<GuideReferences guide="..." />`.

Write for someone meeting the topic for the first time: simple words, short sentences, the
problem before the jargon. Detail that does not change what the reader does goes into the
evidence or out.

`<Recipe id path start end lang />` shows a fixture file from the first line starting with
`start` through the first later line starting with `end` (one line without `end`). `start`
must match exactly one line. The complete file is published at `/fixtures/<path>/`.

## Evidence

One record states one claim, in `data/evidence/<guide>.yaml`:

```yaml
- id: ev-jvm-heap-sizing-1
  card: jvm-heap-sizing
  claim: "With -XX:MaxRAMPercentage=75 and a 1 GiB memory limit, MaxHeapSize is 805306368 bytes."
  badge: reproduced
  capabilities: [runtime]
  components: [temurin-jre-image]
  baseline: v0.1-b1
  fixture: single-hexagonal
  test: acceptance/jvm::TestHeapIsSeventyFivePercent
  commands: ["go test -tags acceptance -count=1 -run '^TestHeapIsSeventyFivePercent$' ./acceptance/jvm"]
  environment: "GitHub-hosted ubuntu-24.04 x64, Docker"
  run: https://github.com/pyahu/initializer/actions/runs/<id>/attempts/<n>
  commit: <40-hex sha>
  date: "2026-10-17"
  result: "MaxHeapSize = 805306368"
  limitations: "Flags only."
```

| Badge | May claim |
| ----- | --------- |
| `compiled` | `compile`, `test` |
| `reproduced` | `test`, `image`, `runtime` |
| `measured` | `runtime`; needs `measurement` |
| `cluster-run` | `cluster`; needs `cluster` |
| `not-exercised` | nothing; needs `reason` and has no run fields |

`result` is the decisive output, inline, because run logs expire. Every step has at least one
record. Records are not shown on the site; the landing page explains the method. Readers
who want the detail read these files. A record counts toward qualification only when its test is in the fixture's
`requiredTests` and passed in the latest completed fixture run on `main`.

## Baselines

`data/versions.yaml` holds revisions, oldest first; `active` is the one the site and new
fixtures use. A revision is frozen once it is on `main`: a bump is a new revision appended to
the list. Only each component's `qualification` block changes and only through
`guidekit qualify --report <evidence-report.json>`, which sets `qualified` when every claim has
an eligible record, `partial` when some do, `provisional` when none do.

A pin with `managedBy` must equal what the managing BOM gives its `coordinates`; `guidekit boms`
checks this weekly.

A guide shows "Re-verify" when a later revision changed one of its components or an open
invalidation names it and "Stale" when its baseline is more than two revisions behind
`active`. Clear it by moving `baseline` forward after a passing run and recording
`verification`.

## References

Each entry in `data/references.yaml` names a `component`, the `docVersion` it covers and how
that must `match` the pin (`exact`, `minor`, `major` or `none` with a `pinNote` stating the
skew), for every revision in `baselines`. A new revision that breaks the match gets a new entry;
the old one keeps its list. `baselines: []` marks documents for later releases.

## Checklist

Items in `data/checklist.yaml` have a `type` (`generated-config`, `verified-behaviour`,
`deployment-prerequisite`, `operator-task`) and a `claim`: `documented` or `enforced` with the
required tests that fail when the behaviour breaks. `appliesWhen` filters by project options:
keys combine with AND, values with OR, `{}` means always.

## Generated files

`guidekit generate` writes these deterministically; commit them, never edit them:

- `guides/src/content/docs/reference/docs.md`
- `guides/src/generated/guides.json` and `guides/src/generated/fixture-files.json`
- `fixtures/<name>/docs/references.md`

## Fixtures

`fixtures/<name>/fixture.yaml` sets the baseline, the project options, the components the
fixture `exercises` and its `requiredTests`. The fixture workflow builds it inside the pinned
JDK image, builds the image, runs the acceptance tests and runs `guidekit evidence verify`,
which fails when a required test is missing, skipped or failed or when a resolved version,
image digest or `java -version` differs from the baseline. It uploads `evidence-report` even
when it fails.

To add a claim: write the test, add it to `requiredTests`, push and record the run in the
evidence file from the report.

## Exceptions

`data/link-exceptions.yaml` and `data/audit-exceptions.yaml` need a reason and expire at most
30 days after `added`. `added` is the date of the commit that introduced the record and never
changes; a renewal is a new record.

## CI and publication

| Workflow | Does |
| -------- | ---- |
| `guides.yml` | `go test`, `check`, `generate --check`, `qualify --check`, `npm audit`, site build, `site` |
| `links.yml` | external links and BOM pins, also weekly |
| `fixture-single-hexagonal.yml` | fixture build, image, acceptance tests, evidence report |

The site deploys from `main` through Cloudflare Workers Builds (root directory `guides`) to
https://initializer.pyahu.io. Commits go straight to `main`; a red pipeline is fixed with a new
commit.
