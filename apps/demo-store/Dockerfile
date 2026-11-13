# syntax=docker/dockerfile:1
# Build context = apps/demo-store. Runs as its own container so the demo can stop it to show webhook retries.
FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=4000
COPY --chown=node:node . .
USER node
EXPOSE 4000
CMD ["node", "server.js"]
