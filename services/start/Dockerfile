# syntax=docker/dockerfile:1
#
# start: the Initializer service, built from the repository root (monorepo guide, step 8):
#   docker build -f services/start/Dockerfile .
# Same stages as the fixture image (guides: JVM in containers, container image).
#
#   build    compiles the Boot jar (Gradle cache in a BuildKit cache mount)
#   extract  splits the jar into layers (jarmode=tools, without --launcher)
#   runtime  JRE + layers + AOT cache from a training run in this same stage
#
# Base images are pinned by digest (data/versions.yaml, baseline v0.1-b1). The training run happens
# in the runtime stage, so training and production use the same JRE build: any other build rejects
# the cache.

FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:a75cd2f1bd3e1e45da898bf658f1f1039ab7392b34844862514d230fd6a516d4 AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY services/start/build.gradle.kts services/start/
COPY data/versions.yaml data/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet :start:dependencies > /dev/null
COPY services/start/src/ services/start/src/
# Tests run in CI before the image build.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet :start:bootJar \
 && cp services/start/build/libs/start-0.1.0.jar /workspace/application.jar

FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:a75cd2f1bd3e1e45da898bf658f1f1039ab7392b34844862514d230fd6a516d4 AS extract
WORKDIR /extract
COPY --from=build /workspace/application.jar application.jar
# --layers without --launcher: application/application.jar plus lib/*.jar on its manifest class
# path, started with java -jar. The AOT cache needs this layout.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:37c16059e13b3dfa59f4c24af72dd0f7798d5f1b06647e306feb914f4163a721 AS runtime
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
