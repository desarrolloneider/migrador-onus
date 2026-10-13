# Imagen del backend. Un solo Dockerfile con dos destinos, porque los dos procesos salen del mismo reactor Maven:
#   docker build --target web    -t migrador-web    ./backend
#   docker build --target worker -t migrador-worker ./backend
# Las pruebas no corren aquí: las de base de datos necesitan Docker (Testcontainers) y corren en el pipeline.

# Si Docker Hub falla: REGISTRO=mirror.gcr.io/library/ en el .env
ARG REGISTRO=
FROM ${REGISTRO}eclipse-temurin:25-jdk AS compilacion
WORKDIR /fuente
COPY . .
# El wrapper puede venir con fin de línea de Windows si se clonó sin .gitattributes.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q package -DskipTests

FROM ${REGISTRO}eclipse-temurin:25-jre AS base
RUN groupadd --system migrador && useradd --system --gid migrador --home /app migrador \
    && mkdir -p /var/lib/migrador/anclas && chown -R migrador:migrador /var/lib/migrador
WORKDIR /app
ENV AUDITORIA_ANCLAS_DIR=/var/lib/migrador/anclas \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
USER migrador

# Proceso web: pantallas, API y tiempo real. Aplica las migraciones al arrancar.
FROM base AS web
COPY --from=compilacion /fuente/arranque-web/target/migrador-web.jar /app/app.jar
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Proceso ejecutor: uno solo (RN-40). No aplica migraciones ni abre puertos.
FROM base AS worker
COPY --from=compilacion /fuente/arranque-worker/target/migrador-worker.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
