# syntax=docker/dockerfile:1
# One Dockerfile for every Spring service. Build context = repo root:
#   docker build -f services/Dockerfile --build-arg SERVICE=auth-service .
# The build stage is identical for all services, so BuildKit runs it once per compose build.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY services/ services/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q package -DskipTests -Djacoco.skip=true

FROM eclipse-temurin:21-jre-alpine
ARG SERVICE
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/services/${SERVICE}/target/${SERVICE}-*.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
