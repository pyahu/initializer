# 4. The generator gets a monorepo option, proven by a fixture it reproduces

Date: 2026-11-27, amended 2026-11-28 (the layout of a vertical). Status: accepted. Owner: Pyahu (Terson Rigaud).

## Context

ADR 0002 froze the generator at its 16 combinations until teams showed they needed more. Pyahu's own platform
is that evidence: it keeps several Spring Boot services and their Next.js apps in one repository, and the pains
it carries (build logic in `buildSrc`, a catalog nothing reads, every consumer compiling the others' protos, path
lists copied into each workflow, a layering rule broken in 49 files with no check) are the ones the monorepo
guide warns about. A team that starts a platform today has no generator that gets these right on day one.

The monorepo guide (rewritten in `f91a943`) rests only on documentation. Its code is written inline, not quoted
from a build that runs.

## Decision

- **The generator gets a second shape, a monorepo of verticals.** A vertical is one business capability that one
  team owns end to end: Otto's term for the parts it cut its shop into, and what micro-frontends call a vertical
  split. With its own web app it can grow into a self-contained system, which also asks for its own UI and for
  working when the others are down. Not to be confused with Vertical Slice Architecture, which arranges the code
  inside one service. Each vertical is one folder, `verticals/<name>/`, with its service in `service/` and its
  contract project in `api/`. Each vertical picks its own architecture style and its database. Group and the
  identity provider are shared: one OIDC issuer for the whole platform.
- **Planned, not generated yet: the web apps.** A vertical's Next.js app goes in its `web/` folder. When any
  vertical has one, a central portal (the application shell of micro-frontends) owns the home page, login, refresh
  and logout and finds the vertical apps by their folders; they are Next.js zones on the same origin. Until the
  fixture shows it, every vertical is generated without a frontend.
- **A vertical is a folder.** `settings.gradle.kts` and CI discover `verticals/*`, so adding a vertical later means
  adding a folder; start.pyahu.io can generate one vertical for an existing monorepo.
- **The build enforces the boundaries twice.** The service convention plugin fails the build on a project
  dependency to another service, and each vertical's `ArchitectureTest` fails on a class that reaches into
  another vertical other than through its `api` package.
- **`fixtures/monorepo` is what the generator produces** for a fixed set of options, checked file by file like
  the Initializer's own service (`services/start/drift.yaml`). The guide quotes it and its tests become the
  guide's evidence. The single-service shape and its 16 combinations stay as they are.
- **The rest of ADR 0002 holds**: tested claims only, one baseline, online checks never block.

## Consequences

- The Start workflow builds a monorepo combination next to the 16 single-service ones, and the option schema,
  the API and start-web grow a list of verticals.
- The monorepo checklist items apply only to monorepo projects.
- The quality report, written for one project, has to cover every project of a build.
- Calls between verticals need a decision on tokens (relay with a shared audience, or token exchange) before the
  fixture shows one.
