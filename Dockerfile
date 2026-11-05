# ---------- Stage 1: build the Spring Boot jar ----------
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace

# Wrapper + build scripts first, so the Gradle distribution download is cached
# independently of the application source.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew

# Pre-fetch the Gradle distribution and dependencies in a layer keyed on the build scripts,
# so editing src does not refetch them. Deliberately no BuildKit cache mount: it would make
# the image unbuildable with the legacy builder.
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null

COPY src ./src

RUN ./gradlew --no-daemon bootJar -x test

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
    PAYMENTS_BANK_API_URL=http://bank-simulator:8080

EXPOSE 8090

# Actuator health endpoint. Use /actuator/health/readiness instead to gate on
# the app being ready to serve traffic rather than merely alive.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -q --spider "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
