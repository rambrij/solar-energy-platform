FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN for attempt in 1 2 3 4 5; do \
    mvn -B -U package -DskipTests > /tmp/maven-build.log 2>&1; status=$?; \
    cat /tmp/maven-build.log; \
    if [ "$status" -eq 0 ]; then exit 0; fi; \
    if ! grep -Eqi 'status code: 429|Too Many Requests' /tmp/maven-build.log; then exit "$status"; fi; \
    if [ "$attempt" -eq 5 ]; then exit "$status"; fi; \
    delay=$((attempt * 15)); \
    echo "Maven Central rate limited the build (HTTP 429); retrying in ${delay}s (attempt ${attempt}/5)."; \
    sleep "$delay"; \
  done; exit 1
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/pos-2.0.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
