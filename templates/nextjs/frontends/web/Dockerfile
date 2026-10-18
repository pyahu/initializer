# syntax=docker/dockerfile:1
#
# [[name]]-web: the Next.js frontend (Next.js guide, step 6).
#
#   build    npm ci, then next build with output: standalone
#   runtime  Node with the standalone server, the static files and public, as the node user
#
# The base image is pinned by digest. Nothing of an environment is built in: the server reads its settings when a
# request runs, so one image moves through every environment (step 4).

FROM node:24.22.0-trixie-slim@sha256:7f486cabe892bff8fef04c0f7da8dba84a560be3e4e2740b7a86afc57ebec315 AS build
WORKDIR /web
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM node:24.22.0-trixie-slim@sha256:7f486cabe892bff8fef04c0f7da8dba84a560be3e4e2740b7a86afc57ebec315 AS runtime
WORKDIR /web
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
# Owned by root and read only for the node user; the Deployment mounts a writable cache under .next.
COPY --from=build /web/.next/standalone ./
COPY --from=build /web/.next/static ./.next/static
COPY --from=build /web/public ./public
USER node
EXPOSE 3000
# Exec form: node is PID 1 and gets SIGTERM.
CMD ["node", "server.js"]
