# channel tier: Vaadin UI + CRM domain. The jar is built once in the shared
# crmops-builder image; this image only copies it into a slim runtime.
FROM crmops-builder AS build

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/channel/build/libs/channel-1.0.0.jar app.jar
RUN mkdir -p /app/data
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
