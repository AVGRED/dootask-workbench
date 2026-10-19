# syntax=docker/dockerfile:1

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY workbench-common/pom.xml workbench-common/
COPY workbench-auth/pom.xml workbench-auth/
COPY workbench-user/pom.xml workbench-user/
COPY workbench-project/pom.xml workbench-project/
COPY workbench-task/pom.xml workbench-task/
COPY workbench-planning/pom.xml workbench-planning/
COPY workbench-worklog/pom.xml workbench-worklog/
COPY workbench-report/pom.xml workbench-report/
COPY workbench-app/pom.xml workbench-app/
RUN mvn -B -q -DskipTests dependency:go-offline || true
COPY workbench-common workbench-common
COPY workbench-auth workbench-auth
COPY workbench-user workbench-user
COPY workbench-project workbench-project
COPY workbench-task workbench-task
COPY workbench-planning workbench-planning
COPY workbench-worklog workbench-worklog
COPY workbench-report workbench-report
COPY workbench-app workbench-app
RUN mvn -B -DskipTests package -pl workbench-app -am

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system workbench && useradd --system --gid workbench --home /app workbench \
    && mkdir -p /app/data/uploads && chown -R workbench:workbench /app

COPY --from=build /build/workbench-app/target/workbench-app-*.jar /app/app.jar

USER workbench
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport" \
    SERVER_PORT=9000 \
    STORAGE_ROOT=/app/data/uploads

EXPOSE 9000

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
