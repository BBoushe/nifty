# syntax=docker/dockerfile:1

FROM node:22-alpine AS base
WORKDIR /app

# Prefer IPv4 DNS inside Docker VMs (avoids npm ETIMEDOUT stalls on macOS/IPv6 networks)
ENV NODE_OPTIONS="--dns-result-order=ipv4first"
ENV NPM_CONFIG_FETCH_RETRIES=5
ENV NPM_CONFIG_FETCH_RETRY_MINTIMEOUT=20000
ENV NPM_CONFIG_FETCH_RETRY_MAXTIMEOUT=120000
ENV NPM_CONFIG_FETCH_TIMEOUT=120000
ENV NPM_CONFIG_MAXSOCKETS=4
ENV NPM_CONFIG_AUDIT=false
ENV NPM_CONFIG_FUND=false

COPY package.json package-lock.json* ./

FROM base AS dev
RUN --mount=type=cache,target=/root/.npm \
    npm install --prefer-offline
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

FROM base AS build
RUN --mount=type=cache,target=/root/.npm \
    npm ci --prefer-offline
COPY . .
RUN npm run build

FROM nginx:1.27-alpine AS prod
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
