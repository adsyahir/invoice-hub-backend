# syntax=docker/dockerfile:1

# ---- Stage 1: build -------------------------------------------------------
# Temurin 25 JDK to match <java.version>25</java.version> in pom.xml.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper and POM first, then the source. The build uses a
# BuildKit cache mount for ~/.m2, so downloaded dependencies persist across
# builds (and survive POM edits) instead of being re-fetched every time — more
# reliable than `dependency:go-offline`, which can miss plugins/transitives.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B clean package -DskipTests

# Split the fat jar into Spring Boot layers so Docker can cache the slow-moving
# ones (dependencies) separately from the fast-moving app code.
RUN mkdir -p target/extracted \
    && java -Djarmode=tools -jar target/*.jar extract --layers --destination target/extracted

# ---- Stage 2: runtime -----------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system spring && useradd --system --gid spring spring

ARG EXTRACTED=/workspace/target/extracted
COPY --from=build ${EXTRACTED}/dependencies/ ./
COPY --from=build ${EXTRACTED}/spring-boot-loader/ ./
COPY --from=build ${EXTRACTED}/snapshot-dependencies/ ./
COPY --from=build ${EXTRACTED}/application/ ./

USER spring:spring

# Context path is /api and the app listens on 8100 (see application.properties).
EXPOSE 8100
ENV JAVA_OPTS=""

# The `jarmode=tools extract --layers` output keeps a runnable jar in the
# application layer plus its dependencies in ./lib (referenced via the jar's
# manifest Class-Path), so a plain `-jar` launch works.
#
# `exec` replaces the shell so the JVM becomes PID 1 and receives SIGTERM
# directly on `docker stop` — enabling graceful shutdown (drain requests, close
# the connection pool) instead of a SIGKILL after the grace period.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar *.jar"]
