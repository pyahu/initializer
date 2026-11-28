# The orders vertical

It keeps its data in its own PostgreSQL database; the migration Job owns the schema and `DatabaseRolesTest`
checks what each role may do.
Read the root `AGENTS.md` first: its rules hold here.

- **Architecture: Hexagonal.** `ArchitectureTest` keeps each layer to what it may use, and
  `ModulithTest` keeps one capability to the public API of another
  ([Architecture](https://initializer.pyahu.io/guides/architecture/#arch-hexagonal)).
- **Contract: `api/src/main/openapi/orders.yaml`.** Change it first: the build generates the interfaces the
  controllers implement, in `io.pyahu.fixture.api.orders`, and other verticals call the same interfaces.
- **Never depend on another vertical** other than through its `api` project.

`ping` is the sample capability. It shows the shape of one: add yours as a package beside it, then remove it.

```sh
./gradlew :orders:test             # the fast tests
./gradlew :orders:integrationTest  # the tests tagged integration; they need Docker
./gradlew :orders:build            # every gate, the jar and the SBOM
./gradlew :orders:bootRun          # on port 8120, with compose.yaml started for it
```
