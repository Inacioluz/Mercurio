# Build unico para os quatro servicos: o modulo desejado chega por --build-arg.
FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /build

# Copia so os poms primeiro para que a resolucao de dependencias fique numa
# camada propria, reaproveitada enquanto nenhum pom mudar.
COPY pom.xml .
COPY mercurio-contracts/pom.xml       mercurio-contracts/
COPY payment-service/pom.xml          payment-service/
COPY antifraud-service/pom.xml        antifraud-service/
COPY ledger-service/pom.xml           ledger-service/
COPY notification-service/pom.xml     notification-service/
RUN mvn -B -q -pl mercurio-contracts,${SERVICE} -am dependency:go-offline

COPY mercurio-contracts/src   mercurio-contracts/src
COPY ${SERVICE}/src           ${SERVICE}/src
RUN mvn -B -q -pl mercurio-contracts,${SERVICE} -am clean package -DskipTests \
    && cp ${SERVICE}/target/${SERVICE}-*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE
WORKDIR /app

RUN addgroup -S mercurio && adduser -S mercurio -G mercurio
COPY --from=build /build/app.jar app.jar
RUN chown mercurio:mercurio /app/app.jar

USER mercurio

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+UseG1GC"
ENV SERVICE_NAME=${SERVICE}

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:${SERVER_PORT:-8081}/actuator/health/readiness | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
