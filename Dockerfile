FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY shared-lib ./shared-lib
COPY service-achievements ./service-achievements

ARG CLEAN_GRADLE_CACHE=0

# Build shared-lib
RUN if [ "$CLEAN_GRADLE_CACHE" = "1" ]; then rm -rf /root/.gradle/caches /root/.gradle/wrapper/dists; fi && \
    cd ./shared-lib && \
    sed -i 's/\r$//' ./gradlew && \
    chmod +x ./gradlew && \
    ./gradlew publishToMavenLocal --no-daemon

# Build service
RUN cd ./service-achievements && \
    sed -i 's/\r$//' ./gradlew && \
    chmod +x ./gradlew && \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/service-achievements/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
