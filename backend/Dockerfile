# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Prefer IPv4 inside Docker VMs to avoid Maven Central HTTP/2 / IPv6 stalls
ENV MAVEN_OPTS="-Djava.net.preferIPv4Stack=true"

COPY pom.xml .
COPY .mvn/maven.config .mvn/maven.config
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.resolver.transport=wagon dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.resolver.transport=wagon package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /app/target/*.jar /app/app.jar
RUN chown appuser:appgroup /app/app.jar

USER appuser
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
