# syntax=docker/dockerfile:1

# ---- Build stage: compile + package the fat jar via the pinned wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dependency layer (cached until pom/wrapper change)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
# Sources + migrations, then package (tests run in CI, skipped here)
COPY src/ src/
COPY db/ db/
RUN ./mvnw -B -ntp -DskipTests package

# ---- Runtime stage: same image runs API and workers; profile selects the role ----
FROM eclipse-temurin:21-jre AS runtime
# Workers shell out to FFmpeg/ffprobe (ProcessBuilder), so they must be on PATH.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*
# Run non-root (defense-in-depth: every input is treated as hostile).
RUN useradd -r -u 1001 -m appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Flyway reads filesystem:db/migrations (see application.yml).
COPY db/ db/
USER appuser
EXPOSE 8080
# SPRING_PROFILES_ACTIVE (api | worker | transcode) is supplied per compose service.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
