# The billing vertical

It has no database. Read the root `AGENTS.md` first: its rules hold here.

- **Architecture: DDD layered.** `ArchitectureTest` keeps each layer to what it may use, and `ModulithTest` keeps
  one capability to the public API of another ([Architecture](https://initializer.pyahu.io/guides/architecture/#arch-ddd-layered)).
- **Contract: `api/src/main/openapi/billing.yaml`.** Change it first: the build generates the interfaces the
  controllers implement, in `io.pyahu.fixture.api.billing`, and other verticals call the same interfaces.
- **Never depend on another vertical** other than through its `api` project.

`ping` is the sample capability. It shows the shape of one: add yours as a package beside it, then remove it.

```sh
./gradlew :billingest             # the fast tests
./gradlew :billing:integrationTest  # the tests tagged integration; they need Docker
./gradlew :billing:build            # every gate, the jar and the SBOM
./gradlew :billing:bootRun          # with compose.yaml started for it
```
