FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY config ./config
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -Pruntime verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --gid 10001 ticket && useradd --uid 10001 --gid ticket --create-home ticket \
    && chown ticket:ticket /app
COPY --from=build --chown=ticket:ticket /workspace/target/runtime/ ./runtime/
COPY --from=build --chown=ticket:ticket /workspace/target/ticket-reservation-system.war ./app.war
USER ticket
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "runtime/payara-micro.jar", "--addlibs", "runtime/postgresql.jar", "--deploy", "app.war", "--contextroot", "/", "--disablephonehome", "--nocluster"]
