# Working in this repository

The product is the guides: `guides/src/content/docs/guides/*.mdx`. Everything else exists to keep them right.
[CONTRIBUTING.md](CONTRIBUTING.md) has the full rules; this page is the short version.

## Map

| Path | What |
| ---- | ---- |
| `guides/` | The site (Astro, Starlight) and the guide pages |
| `data/` | The baseline (`versions.yaml`), tested claims (`evidence/`), checklist, official docs (`references.yaml`) |
| `fixtures/` | The projects whose tests prove the guides: `single-hexagonal` and `monorepo` |
| `acceptance/` | Go tests run against the fixture image |
| `services/start/`, `frontends/start-web/`, `templates/` | The project generator, its page and what it renders |
| `cmd/guidekit/`, `internal/` | Go tooling that checks the data and writes the generated files |

## Rules that hold everywhere

1. **A claim is tested only when a test proves it.** A record in `data/evidence/<guide>.yaml` names a test that is
   in a fixture's `requiredTests`; the fixture workflow fails when that test fails. No record means the step rests
   on the official documentation the guide lists. Test what protects readers most (roles, migrations, auth,
   startup, failure handling); cite the documentation for library defaults.
2. **Generated files are never edited by hand.** Run `go run ./cmd/guidekit generate`. They are the reference page,
   `guides/src/generated/`, `fixtures/*/docs/references.md`, `templates/checklist.json` and
   `skills/pyahu-production-guides/`. `skills/README.md` is edited by hand.
3. **One baseline.** `data/versions.yaml` holds the versions the guides, the fixture and the generator use. Change
   a pin there and in the fixture together; the fixture's CI fails when they differ.
4. **Guides, steps and checklist items are public URLs.** Rename or remove one only with a record in
   `data/retired.yaml`; a retired step keeps its anchor.
5. **The generator is frozen** at its 16 combinations while teams try the recommended path, plus the monorepo of
   verticals ([ADR 0004](docs/adr/0004-monorepo-option.md)). See [ADR 0002](docs/adr/0002-a-smaller-scope.md).
6. No em or en dashes in guides or data.

## What else to update when you change something

Nothing here may go stale. Find the row for what you changed and go through the whole row before you commit. In
each row, **Updates itself** is what `go run ./cmd/guidekit generate` writes (commit the result) or what the site
build produces, such as the llms exports. **Update by hand** is on you, and **Caught by** is the check that fails if
you forget.

| You changed | Updates itself | Update by hand | Caught by |
| ----------- | -------------- | -------------- | --------- |
| **A guide's text** (`guides/src/content/docs/guides/*.mdx`) | Agent Skill (`skills/`), `guides.json`, llms exports | Nothing, unless the text now disagrees with a template or the fixture: fix whichever is wrong | `guidekit check` (format, recipes), `generate --check` |
| **A guide's steps**: add, remove, reorder or rename | The same, plus the step numbers in `templates/checklist.json` | `data/retired.yaml` for a removed step; `data/evidence/` and `data/checklist.yaml` records of that step; "(X guide, step N)" comments in `templates/`; "step N" links in other guides; the intro's count ("nine decisions"); step anchors linked from https://pyahu.io/guides (`app/guides.ts` in `pyahu/landing`); redeploy the starter, whose `CHECKLIST.md` carries step numbers | `guidekit check` (IDs, retired, template step numbers); the intro count is not checked |
| **A guide**: add, remove, rename or move to another part | Skill index, guide counts on the landing page | Its line in `guides/src/content/docs/guides.mdx` (the source of the order); the sidebar in `guides/astro.config.mjs`; `modules` in `guides/src/pages/index.astro`; `PathGuides.astro` if it is on the recommended path; `data/retired.yaml` if removed; "24 guides" in `README.md` | `guidekit check` (sidebar and landing must match `guides.mdx`); README is not checked |
| **A checklist item** (`data/checklist.yaml`) | `templates/checklist.json`, the skill's `checklist.md`, the guide's checklist on the site | Redeploy the starter (`start` image), which puts the items in every generated `CHECKLIST.md`; a line in the unreleased entry of `data/releases.yaml` (generated `CHECKLIST.md` files change) | `guidekit check`, `generate --check` |
| **A tested claim** (`data/evidence/`) or a fixture test | Draft labels (page, menu, landing, skill), coverage lines on Start here | `requiredTests` in `fixtures/*/fixture.yaml` in the same commit | `guidekit check` (test must be required); fixture workflow (test must pass) |
| **A version** (`data/versions.yaml`) | Reference page, versions in guides.json and skill; the generator reads the baseline at run time; the baseline snapshot in `data/baselines/` | Each fixture (`libs.versions.toml`, Dockerfile digests, wrapper); the Initializer's own build (`gradle/libs.versions.toml`, Dockerfiles); `JDK_IMAGE` digests in `.github/workflows/`; version numbers written in guide text; `data/references.yaml` entries whose `docVersion` no longer matches; redeploy the starter; the release notes in `data/releases.yaml` (upgrade steps, `components`), then tag the release once CI passed | Fixture workflow (pins must match); `guidekit check` (references); guide text is not checked; `guidekit check` (the active baseline needs released notes) |
| **A template** (`templates/`) | `templates/checklist.json` if the checklist changed | The guide text that describes that file; `services/start/drift.yaml` when a new file appears; after a change to the sample contract (`api.yaml`), `templates/nextjs/frontends/web/src/api/schema.d.ts` for each variant; redeploy the starter (`start` image); a line in the unreleased entry of `data/releases.yaml` (an upgrade step when a team must act) | `./gradlew :start:build` (DriftTest, MonorepoFixtureTest, generation tests: run `./gradlew :start:regenerateFixture` when a template a monorepo uses changed); Start workflow (all 16 combinations and a monorepo build and start); `guidekit check --base` (a change to the templates needs release notes) |
| **A generator option or the API** (`services/start/`, `start.yaml`) | Nothing | After a change to `start.yaml`, regenerate `frontends/start-web/src/api/schema.d.ts` (`npm run api` there); the matrix in `.github/workflows/start.yml`; `data/schemas/options.schema.json` and `appliesWhen` filters; "Start here" and the landing card; the "16 combinations" in README, AGENTS, CONTRIBUTING, maintenance page; ADR 0002 (the generator is frozen); redeploy both images; the release notes when generated projects change | Start workflow (`api:check`, matrix); the counts are not checked |
| **The generator page** (`frontends/start-web/`) | Nothing | Redeploy the `start-web` image | Start workflow (browser tests) |
| **A site page** (landing, Start here, why, maintenance) | Nothing | `README.md` and `skills/README.md` if they say the same thing | `npm run check`, `guidekit site` |
| **How the project works** (evidence model, CI, publication, rules) | Nothing | `AGENTS.md`, `CONTRIBUTING.md`, the maintenance and why pages, `skills/README.md`, an ADR in `docs/adr/` for a decision | Nothing: read them |

Releases: a baseline is released with notes in `data/releases.yaml` and a git tag of the same name, pushed by a
person on the commit whose fixture, Start and Guides runs passed; the Releases workflow writes the GitHub release
from `releases/<id>.md`. See CONTRIBUTING.md, "Release notes".

Deploys: the site publishes itself on every push to `main` once the Guides and fixture workflows pass. The starter
at https://start.pyahu.io does not: after a change to `templates/`, `services/start/`, `frontends/start-web/` or
`data/versions.yaml`, rebuild and redeploy **both** images, `start` and `start-web`, on the Pyahu platform. Check
with `curl https://start.pyahu.io/metadata`: the baseline must be the one in `data/versions.yaml`.

## Before you push

| You changed | Run |
| ----------- | --- |
| guides, data, Go tooling | `go test ./cmd/... ./internal/...`, `go run ./cmd/guidekit check`, `go run ./cmd/guidekit generate --check`, then `(cd guides && npm ci && npm test && npm run check && npm run build)` and `go run ./cmd/guidekit site`, all from the repository root |
| the fixture or `acceptance/` | the fixture commands in CONTRIBUTING.md, "Commands" (Docker) |
| the generator or templates | `./gradlew :start:build` |

Commits go straight to `main` and are pushed; a red pipeline is fixed with a new commit.
