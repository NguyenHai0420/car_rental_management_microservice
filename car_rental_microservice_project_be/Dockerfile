# One image with every backend jar; docker-compose picks the jar per service.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package \
 && mkdir /out \
 && for jar in */*/target/*-1.0-SNAPSHOT.jar; do cp "$jar" "/out/$(basename "$jar" -1.0-SNAPSHOT.jar).jar"; done

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /out/ /app/
USER 10001:10001
