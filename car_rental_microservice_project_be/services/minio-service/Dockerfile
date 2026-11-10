FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY services/minio-service/pom.xml ./services/minio-service/pom.xml
COPY services/minio-service/src/main ./services/minio-service/src/main
RUN mvn -B -ntp -f services/minio-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-alpine@sha256:82cf3d9ccbe1ed46252008d51b2f2329e6b9e8a2f4394c4bebefc3ea7f4b1372
WORKDIR /app
COPY --from=build /workspace/services/minio-service/target/minio-service-1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
