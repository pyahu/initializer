# syntax=docker/dockerfile:1
#
# orders: multi-stage image for Java 25 and Spring Boot (guides: JVM in containers, container image), built from
# the repository root so the build sees build-logic, the catalog and the contract (monorepo guide, step 10):
#   docker build -f verticals/orders/service/Dockerfile .
#
#   build    compiles the Boot jar (Gradle cache in a BuildKit cache mount)
#   extract  splits the jar into layers (jarmode=tools, without --launcher)
#   runtime  JRE + layers + AOT cache from a training run in this same stage
#
# Base images are pinned by digest. The training run happens in the runtime stage, so training and
# production use the same JRE build: any other build rejects the cache.
# CI supplies the tested jar at workspace/application.jar through --build-context build=...,
# replacing only the build stage. Extraction, runtime layers and AOT training still run here.

FROM eclipse-temurin:25.0.5.1_2-jdk-noble@sha256:c7eef3dfeb81468533ea48c67bddaa8c7ca916454259e4d0d734d12bcff8e125 AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY config/ config/
COPY .github/scripts/gradle-ci.sh .github/scripts/
COPY libs/ libs/
# This vertical with every contract it may use; its allow list, Dockerfile.dockerignore, keeps out the rest.
COPY verticals/ verticals/
RUN --mount=type=cache,target=/root/.gradle \
    bash .github/scripts/gradle-ci.sh ./gradlew --no-daemon --quiet :orders:dependencies > /tmp/gradle-dependencies.log 2>&1 \
    || { cat /tmp/gradle-dependencies.log; exit 1; }
# Tests run in CI before the image build.
RUN --mount=type=cache,target=/root/.gradle bash .github/scripts/gradle-ci.sh ./gradlew --no-daemon --quiet :orders:bootJar \
 && cp verticals/orders/service/build/libs/orders-0.1.0.jar /workspace/application.jar

FROM eclipse-temurin:25.0.5.1_2-jdk-noble@sha256:c7eef3dfeb81468533ea48c67bddaa8c7ca916454259e4d0d734d12bcff8e125 AS extract
WORKDIR /extract
COPY --from=build /workspace/application.jar application.jar
# --layers without --launcher: application/application.jar plus lib/*.jar on its manifest class
# path, started with java -jar. The AOT cache needs this layout.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25.0.5.1_2-jre-noble@sha256:2ec98aa522c472deadfd2b7c5b39bca680e33ef522a53a72f10789bdf89a6bb5 AS runtime
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /nonexistent --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./

# Sizing defaults. A Deployment that sets JAVA_TOOL_OPTIONS replaces this whole value, so nothing
# here is required for correctness; GC, cache and crash-log flags live on the command line.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

# Training run (JDK 25 one-step workflow). spring.context.exit=onRefresh stops after bean creation,
# before the web server starts. Nothing here talks to a remote system during bean creation.
RUN java -XX:+UseG1GC -XX:AOTCacheOutput=/app/app.aot -Dspring.context.exit=onRefresh -jar /app/application.jar \
 && chmod 0444 /app/app.aot

USER 10001:10001
EXPOSE 8080 8081
# Exec form: java is PID 1 and gets SIGTERM. G1 is explicit: below 2 CPUs or about 1792 MB the JVM
# would pick SerialGC. Keep these flags identical to the training run or the cache is rejected.
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:AOTCache=/app/app.aot", "-XX:ErrorFile=/tmp/hs_err_pid%p.log", "-jar", "/app/application.jar"]
