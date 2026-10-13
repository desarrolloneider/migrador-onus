# Imagen del frontend: compila Angular y lo sirve con nginx, que reenvía /api y /ws al proceso web.
#   docker build -t migrador-frontend ./frontend
#   docker build --build-arg CONFIGURACION=ejemplo -t migrador-frontend-ejemplo ./frontend   (con datos de ejemplo)
#   Si Docker Hub falla: REGISTRO=mirror.gcr.io/library/ docker compose build frontend

ARG REGISTRO=
FROM ${REGISTRO}node:24-alpine AS compilacion
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG CONFIGURACION=production
RUN pnpm exec ng build --configuration ${CONFIGURACION}

FROM ${REGISTRO}nginx:1.29-alpine
# La imagen de nginx reemplaza ${BACKEND_URL} en la plantilla al arrancar.
ENV BACKEND_URL=http://backend-web:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=compilacion /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
