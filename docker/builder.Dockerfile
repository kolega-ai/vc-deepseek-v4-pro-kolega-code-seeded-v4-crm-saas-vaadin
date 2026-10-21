# Shared builder: downloads the Gradle distribution + dependencies ONCE and
# packages all four tier jars (including the Vaadin production frontend).
# Each service image then just copies its jar out of this image.
# BuildKit cache mounts make rebuilds cheap and resilient to interruptions.
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/root/.vaadin \
    --mount=type=cache,target=/home/gradle/.vaadin \
    ./gradlew bootJar -Pvaadin.productionMode=true --no-daemon -q
