# 3. Release notes tell a team how to upgrade a generated project

Date: 2026-11-27. Status: accepted. Owner: Pyahu (Terson Rigaud).

## Context

A generated project belongs to its team from the first day and is never generated again. Its `README.md` and
`AGENTS.md` name the baseline it was generated on, but nothing said what to change to reach a newer one. Moving
from v0.1-b2 to v0.1-b3 (Spring Boot 4.1.2 to 4.2.0) needed new pins and digests in six files, a Gradle wrapper
update, a renamed property and a patched frontend image; only a diff of two generated projects showed that.

A baseline also did not identify a project. Between v0.1-b2 and v0.1-b3 the templates gained the quality gates,
the quality report and `AGENTS.md` without a new baseline id, so two projects that both say v0.1-b2 can differ by
dozens of files. The image of the generator is built without `.git`, so it cannot record its commit either.

## Decision

- **A release is a baseline revision with notes**, and a git tag and a GitHub release of the same name.
  `data/releases.yaml` holds the notes; `guidekit generate` writes the site page, the Agent Skill's
  `releases.md` and the GitHub bodies from it.
- **Upgrade steps are checks, not history.** Each step has a command that tells whether a project still needs it,
  so a team applies the right steps whatever version of the templates it started from. Each step also names its
  files, its verification and the guide step behind it.
- **The version table comes from snapshots.** `data/baselines/<id>.yaml` keeps the pins of each released baseline,
  a copy of `data/versions.yaml` when it was released. This is the one exception to "git keeps the history" of
  ADR 0002: a table computed from git at build time depends on the clone depth and on finding the right commit.
  Snapshots hold pins only, never evidence or qualification.
- **Changes between releases are unreleased notes.** start.pyahu.io generates `main`, so a change to the templates
  is recorded at the top of the notes the day it ships; `guidekit check --base` fails a change to what the
  generator produces without one. Renovate's own updates are exempt: the generated project has its own Renovate.
- **Published notes stay.** A released entry keeps its date, the anchor of every step and its snapshot. Text can be
  corrected; the GitHub release follows the file.

## Consequences

- Every baseline update and every template change costs a few lines of notes, written while the change is fresh.
- Teams and coding agents can upgrade a project step by step; `AGENTS.md` in a generated project links to the
  notes after its baseline.
- v0.1-b2 is the first release kept, with its pins and no notes. Projects generated earlier start from its
  upgrade steps, whose checks cover what they lack.
