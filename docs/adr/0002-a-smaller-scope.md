# 2. Prove what protects readers, freeze the generator, learn from real teams

Date: 2026-11-20. Status: accepted. Owner: Pyahu (Terson Rigaud).

## Context

The repository carried three fronts at once: the guides, a generator with 16 combinations and an evidence
system that recorded every claim, qualified every pinned version and kept frozen baseline revisions with saved
CI reports. Of 260 catalogued claims, 54 had a test; the other 206 records only said "not exercised". Proving a
claim took two commits (require the test, then record the passing run), and the Guides workflow waited up to
25 minutes for the fixture. An outage of an external site or of Maven Central could hold back a correct guide.
No team outside Pyahu had used the kit yet.

The rigour is what sets the guides apart. Its cost should go where it protects a reader.

## Decision

- **Only tested claims get a record.** A record names a required fixture test, and CI on `main` is the proof.
  Untested steps say they rest on the official documentation listed in the guide and on Pyahu's practice.
- **Test what protects readers most:** database roles and connection limits, migrations, authentication,
  startup, failure handling. Cite the official documentation for library defaults instead of reproducing them.
- **One baseline**, updated in place; git keeps the history.
- **Online checks report and never block publication.** Publication waits for the Guides and fixture workflows.
- **The generator is frozen** at its current combinations. New options wait for evidence that teams need them.
- **Next:** one recommended path (REST, PostgreSQL, authentication, observability, deployment) end to end, used
  by two or three teams, measuring time to the first deploy, questions and rework.

## Consequences

- `data/` and `internal/` are much smaller, and adding a tested claim is one change.
- A claim measured on a specific run (a startup time) keeps its run link; reproduced claims do not need one.
- The fixture is still a separate, hand-written service; whether the generator's recommended path should become
  the fixture is open until we know which path teams use.
