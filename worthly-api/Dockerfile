FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080 8081
USER 65534
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
