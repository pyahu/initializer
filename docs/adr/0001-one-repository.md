# 1. The Initializer service lives in the guides repository

Date: 2026-10-18. Status: accepted. Owner: Pyahu (Terson Rigaud).

## Context

start.pyahu.io generates projects that follow the guides. Its templates must stay in step with the guides,
the fixtures that prove them and the baseline in `data/versions.yaml`.

## Decision

Keep the service (`services/start`), its frontend (`frontends/start-web`, from S5) and the templates in this
repository, as a monorepo: an included `build-logic`, one version catalog in `gradle/libs.versions.toml`, one
flat Gradle project per service and one image per service built from the repository root. Fixtures stay
separate Gradle builds, because each one must look exactly like a project someone generated.

## Consequences

- A change to the baseline, a guide and the templates lands in one commit and one CI run.
- The Go tooling (`guidekit`), the site (`guides/`, Node) and the Gradle build share the repository; each keeps
  its own toolchain and its own workflow.
- Exceptions to the monorepo guide are recorded here.
