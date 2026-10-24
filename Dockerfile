# syntax=docker/dockerfile:1

# --- Build: compile and package with the Maven wrapper (tests run in CI, not here) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B --no-transfer-progress package -DskipTests \
    && java -Djarmode=tools -jar target/iso8583-authorizer-*.jar extract --layers --launcher --destination extracted

# --- Runtime: JRE only, non-root. Starts the host by default; the terminal uses the same image ---
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home authorizer \
    && mkdir /state && chown authorizer /state
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER authorizer
EXPOSE 8583
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
