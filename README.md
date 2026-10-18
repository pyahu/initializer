# Pyahu Initializer

A course on production Spring Boot, Java and Next.js and later a generator that applies it. The guides come
first: each step answers one question with the failure it prevents and every claim has a test behind it.

- Site: https://initializer.pyahu.io
- [The course](https://initializer.pyahu.io/course/): 24 guides in six modules (foundations, build the service,
  events and data, run in production, ship, grow)
- How to contribute: [CONTRIBUTING.md](CONTRIBUTING.md)

## How a claim is proven

A fixture in `fixtures/` is a real service built with the exact versions of a baseline in
`data/versions.yaml`. CI builds it, runs the Go acceptance tests in `acceptance/` against its image and
writes an evidence report. Every claim a guide makes has a record in `data/evidence/` with its test, run and
commit. A claim nothing proves yet says why.

## Layout

| Path | What |
| ---- | ---- |
| `guides/` | The Astro and Starlight site, its components and diagrams and the guide pages |
| `data/` | Baselines, official documentation, checklist, evidence records, schemas |
| `fixtures/` | Services written by hand that prove the guides |
| `acceptance/` | Go behavioural tests run against the fixture images (build tag `acceptance`) |
| `cmd/guidekit/`, `internal/` | Go tooling: checks, generated files, evidence, site and link checks |

## Quick start

```sh
go run ./cmd/guidekit check            # data, guides, cross-references
cd guides && npm ci && npm run dev     # the site at http://localhost:4321 (Node 24.22.0)
```

## License

Code, data and fixtures: MIT ([LICENSE](LICENSE)). Guide text and diagrams: CC BY 4.0
([LICENSE-GUIDES.md](LICENSE-GUIDES.md)). By Terson Rigaud, under the Pyahu community.
