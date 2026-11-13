FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --chown=10001:10001 build/libs/*.jar app.jar
USER 10001:10001
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
