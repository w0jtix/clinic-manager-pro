# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
ARG GITHUB_USERNAME
COPY .mvn/ .mvn/
COPY mvnw pom.xml docker-settings.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=secret,id=github_token \
    GITHUB_USERNAME="${GITHUB_USERNAME}" \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./mvnw -s docker-settings.xml dependency:go-offline -q
COPY src/ src/
RUN --mount=type=secret,id=github_token \
    GITHUB_USERNAME="${GITHUB_USERNAME}" \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./mvnw -s docker-settings.xml clean package -DskipTests -q

FROM eclipse-temurin:21.0.13_9-jre-alpine
WORKDIR /app
COPY --from=build /app/target/ClinicManager-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
