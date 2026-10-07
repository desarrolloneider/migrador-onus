#!/bin/sh
# Crea la base migrador y sus dos usuarios (arquitectura 10.2 y 11.1):
#   migrador_owner: dueño del esquema, lo usa Liquibase.
#   migrador_app:   lo usa la aplicación. Lee y escribe las tablas, y en auditoria_eventos solo inserta y lee.
#
# En Docker lo corre la imagen de PostgreSQL la primera vez. En el servidor Linux se corre una vez como postgres:
#   MIGRADOR_OWNER_PASSWORD=... MIGRADOR_APP_PASSWORD=... sh inicializar-roles.sh
set -eu

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER:-postgres}" --dbname postgres \
     -v owner_pw="$MIGRADOR_OWNER_PASSWORD" -v app_pw="$MIGRADOR_APP_PASSWORD" <<'SQL'
CREATE ROLE migrador_owner LOGIN PASSWORD :'owner_pw';
CREATE ROLE migrador_app LOGIN PASSWORD :'app_pw';
CREATE DATABASE migrador OWNER migrador_owner ENCODING 'UTF8' TEMPLATE template0;
\connect migrador
ALTER SCHEMA public OWNER TO migrador_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO migrador_app;
ALTER DEFAULT PRIVILEGES FOR ROLE migrador_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO migrador_app;
ALTER DEFAULT PRIVILEGES FOR ROLE migrador_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO migrador_app;
SQL
