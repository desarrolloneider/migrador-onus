#!/usr/bin/env sh
# Prepara el .env en un servidor nuevo: lo copia de .env.example y genera claves aleatorias para la base de datos.
# Uso, en la carpeta del proyecto:   sh preparar-env.sh
# No toca un .env que ya exista. Los tokens de SmartOLT y Wispro, la consola de las OLT y la clave del admin
# no se pueden inventar: quedan vacíos para llenarlos a mano.
set -eu

if [ -f .env ]; then
  echo "Ya existe un .env: no se toca. Si quieres empezar de cero, bórralo primero."
  exit 1
fi

clave() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 24
  else
    head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'
  fi
}

cp .env.example .env
for variable in POSTGRES_SUPERUSER_PASSWORD DATABASE_OWNER_PASSWORD DATABASE_APP_PASSWORD; do
  valor=$(clave)
  sed -i "s|^${variable}=.*|${variable}=${valor}|" .env
done
chmod 600 .env

echo "Listo: .env creado con claves nuevas para la base de datos."
echo "Falta llenar a mano en .env:"
grep -nE '^(SMARTOLT_API_TOKEN|WISPRO_API_TOKEN|CONSOLA_GENERAL_USUARIO|CONSOLA_GENERAL_CLAVE|CONSOLA_OLT_CONEJO_HUELLA|MIGRADOR_ADMIN_CLAVE_INICIAL)=$' .env || true
echo "Para abrir la página a la red interna: MIGRADOR_IP_PUBLICACION=0.0.0.0"
echo "Después: docker compose up -d --build"
