FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite bakes VITE_* into the bundle at build time.
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=car-rental-realm
ARG VITE_KEYCLOAK_CLIENT_ID=car-rental-fe
ARG VITE_MEDIA_MANAGEMENT_ROLES=ADMIN,STAFF
ARG VITE_HOTLINE=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID VITE_MEDIA_MANAGEMENT_ROLES=$VITE_MEDIA_MANAGEMENT_ROLES VITE_HOTLINE=$VITE_HOTLINE
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# SPA: unknown paths (/vehicles, /payment/callback, ...) fall back to index.html.
RUN printf 'server {\n  listen 80;\n  root /usr/share/nginx/html;\n  location / { try_files $uri $uri/ /index.html; }\n}\n' > /etc/nginx/conf.d/default.conf
