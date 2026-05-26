# syntax=docker/dockerfile:1
FROM gradle:8.2.1-jdk17 AS builder
WORKDIR /app

# Copy dependency files first for layer caching
COPY build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY gradlew ./

# Download dependencies (cached if build.gradle unchanged)
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew dependencies --no-daemon || true

# Copy source and build
COPY src/ src/
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew bootJar --no-daemon

# Runtime image - minimal
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser
EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -jar app.jar"]
