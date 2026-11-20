# Working in this repository

The product is the guides: `guides/src/content/docs/guides/*.mdx`. Everything else exists to keep them right.
[CONTRIBUTING.md](CONTRIBUTING.md) has the full rules; this page is the short version.

## Map

| Path | What |
| ---- | ---- |
| `guides/` | The site (Astro, Starlight) and the guide pages |
| `data/` | The baseline (`versions.yaml`), tested claims (`evidence/`), checklist, official docs (`references.yaml`) |
| `fixtures/single-hexagonal/` | The service whose tests prove the guides |
| `acceptance/` | Go tests run against the fixture image |
| `services/start/`, `frontends/start-web/`, `templates/` | The project generator, its page and what it renders |
| `cmd/guidekit/`, `internal/` | Go tooling that checks the data and writes the generated files |

## Rules that hold everywhere

1. **A claim is tested only when a test proves it.** A record in `data/evidence/<guide>.yaml` names a test that is
   in a fixture's `requiredTests`; the fixture workflow fails when that test fails. No record means the step rests
   on the official documentation the guide lists. Test what protects readers most (roles, migrations, auth,
   startup, failure handling); cite the documentation for library defaults.
2. **Generated files are never edited by hand.** Run `go run ./cmd/guidekit generate`. They are the reference page,
   `guides/src/generated/`, `fixtures/*/docs/references.md`, `templates/checklist.json` and `skills/`.
3. **One baseline.** `data/versions.yaml` holds the versions the guides, the fixture and the generator use. Change
   a pin there and in the fixture together; the fixture's CI fails when they differ.
4. **Guides, steps and checklist items are public URLs.** Rename or remove one only with a record in
   `data/retired.yaml`; a retired step keeps its anchor.
5. **The generator is frozen** at its 16 combinations while teams try the recommended path. See
   [ADR 0002](docs/adr/0002-a-smaller-scope.md).
6. No em or en dashes in guides or data.

## Before you push

| You changed | Run |
| ----------- | --- |
| guides, data, Go tooling | `go test ./cmd/... ./internal/...`, `go run ./cmd/guidekit check`, `go run ./cmd/guidekit generate --check`, then `cd guides && npm ci && npm test && npm run check && npm run build` and `go run ./cmd/guidekit site` |
| the fixture or `acceptance/` | the fixture commands in CONTRIBUTING.md, "Commands" (Docker) |
| the generator or templates | `./gradlew :start:build` |

Commits go straight to `main` and are pushed; a red pipeline is fixed with a new commit.
