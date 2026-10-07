# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 wikirace \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin wikirace
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/wikirace-backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl --fail --silent --show-error http://127.0.0.1:${PORT}/api/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
