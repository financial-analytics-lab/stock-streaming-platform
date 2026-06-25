#!/bin/sh
set -e

# Default PORT to 3000 if Railway hasn't set it yet (local testing)
export PORT="${PORT:-3000}"

if [ -z "$BACKEND_URL" ]; then
    echo "ERROR: BACKEND_URL env var is required (e.g. https://backend.railway.app)"
    exit 1
fi

# Substitute only ${PORT} and ${BACKEND_URL} in the template.
# All other nginx variables ($host, $uri, etc.) are left untouched.
envsubst '${PORT} ${BACKEND_URL}' \
    < /etc/nginx/templates/default.conf.template \
    > /etc/nginx/conf.d/default.conf

echo "nginx config written (PORT=$PORT, BACKEND_URL=$BACKEND_URL)"

exec nginx -g 'daemon off;'
