# core-mock tier: vendor billing core mock + demo webhook receiver.
FROM crmops-builder AS build

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/core-mock/build/libs/core-mock-1.0.0.jar app.jar
EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
    CMD curl -fs http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
