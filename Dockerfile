# syntax=docker/dockerfile:1

# ---------- Stage 1: build the Spring Boot jar ----------
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Wrapper + build scripts first, so the Gradle distribution download is cached
# independently of the application source.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew

COPY src ./src

# The cache mount keeps the Gradle dependency cache between builds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test

# ---------- Stage 2: explode the layered jar ----------
FROM eclipse-temurin:17-jre-alpine AS extract
WORKDIR /layers
COPY --from=build /workspace/build/libs/*.jar application.jar
RUN java -Djarmode=layertools -jar application.jar extract

# ---------- Stage 3: runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S -G spring spring

# Each layer is copied separately: dependencies change rarely, app code often.
COPY --from=extract --chown=spring:spring /layers/dependencies/ ./
COPY --from=extract --chown=spring:spring /layers/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /layers/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /layers/application/ ./

USER spring

ENV SERVER_PORT=8090 \
    PAYMENTS_BANK_API_URL=http://bank_simulator:8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8090

# springdoc's api-docs endpoint doubles as a liveness probe (no actuator on the
# classpath). Swap for /actuator/health if spring-boot-starter-actuator is added.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -q --spider "http://localhost:${SERVER_PORT}/v3/api-docs" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
