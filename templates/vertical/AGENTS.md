# The [[name]] vertical

[[#postgres]]
It keeps its data in its own PostgreSQL database; the migration Job owns the schema and `DatabaseRolesTest`
checks what each role may do.
[[/postgres]]
[[^postgres]]
It has no database.
[[/postgres]]
Read the root `AGENTS.md` first: its rules hold here.

- **Architecture: [[architectureTitle]].** `ArchitectureTest` keeps each layer to what it may use, and
  `ModulithTest` keeps one capability to the public API of another
  ([Architecture]([[architectureGuide]])).
- **Service: `service/`.** Its code, its image (`Dockerfile`), its manifests (`deploy/`), its local dependencies
  (`compose.yaml`) and its smoke test (`ci/smoke.sh`).
- **Contract: `api/src/main/openapi/[[name]].yaml`.** Change it first: the build generates the interfaces the
  controllers implement, in `[[apiPackage]]`, and other verticals call the same interfaces.
- **Never depend on another vertical** other than through its `api` project.

`ping` is the sample capability. It shows the shape of one: add yours as a package beside it, then remove it.

```sh
./gradlew :[[name]]:test             # the fast tests
./gradlew :[[name]]:integrationTest  # the tests tagged integration; they need Docker
./gradlew :[[name]]:build            # every gate, the jar and the SBOM
./gradlew :[[name]]:bootRun          # on port [[port]], with service/compose.yaml started for it
```
