# Monorepo fixture

A platform of two verticals in one Gradle build, as the Pyahu Initializer generates it (ADR 0004): `orders`
(hexagonal, PostgreSQL) and `billing` (DDD layered), each with its contract project, image and deployment, sharing
one identity provider. It proves the [monorepo guide](https://initializer.pyahu.io/guides/monorepo/); `AGENTS.md`
says how it is organised.

```sh
./gradlew build :build-logic:check          # every project, every gate, the conventions' tests
docker build -f services/orders/Dockerfile .  # one vertical's image, from the repository root
```
