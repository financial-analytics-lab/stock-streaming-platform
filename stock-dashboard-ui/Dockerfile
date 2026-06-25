# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

# Cache npm deps separately from source
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts

# Copy source and build
COPY . .
# VITE_* vars are baked into the bundle at build time.
# Pass them via --build-arg in Railway's build settings.
ARG VITE_REASONING_AGENT_URL
ENV VITE_REASONING_AGENT_URL=${VITE_REASONING_AGENT_URL}
RUN npm run build

# ── Stage 2: nginx ───────────────────────────────────────────────────────────
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# Remove the default nginx site and static files
RUN rm -rf /etc/nginx/conf.d/default.conf /usr/share/nginx/html/*

# Copy Vite build output
COPY --from=build /app/dist .

# nginx config template — BACKEND_URL and PORT are substituted at startup
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Entrypoint runs envsubst then starts nginx
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Railway sets PORT at runtime; 3000 is the local default
EXPOSE 3000

ENTRYPOINT ["/docker-entrypoint.sh"]
