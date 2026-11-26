# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
# -Pproduction builds the Vaadin frontend bundle
RUN --mount=type=cache,target=/root/.m2 mvn -B -Pproduction -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home bonflow
COPY --from=build /src/target/bonflow-server-*.jar app.jar
USER bonflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
