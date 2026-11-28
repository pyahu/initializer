# Libraries

Code that several verticals reuse on purpose, such as a logging setup shipped as Spring Boot auto-configuration:
one folder per library, `libs/<name>/`, with a build file that applies `platform.library`. The build names it
`:lib-<name>`, its code lives in `io.pyahu.fixture.lib.<name>`, and it depends on other libraries only. Never put shared
entities or the internals of a vertical here (monorepo guide, step 3).
