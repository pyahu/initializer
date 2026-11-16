# syntax=docker/dockerfile:1
#
# start-web: the Next.js frontend (Next.js guide, step 6).
#
#   build    npm ci, then next build with output: standalone
#   runtime  Node with the standalone server, the static files and public, as the node user
#
# The base image is pinned by digest. Nothing of an environment is built in: the server reads its settings when a
# request runs, so one image moves through every environment (step 4).

FROM node:24.22.3-trixie-slim@sha256:af02d57b57719b9657a45199a79264a9437940cd55b3c7b3fc99615654f124a0 AS build
WORKDIR /web
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM node:24.22.3-trixie-slim@sha256:af02d57b57719b9657a45199a79264a9437940cd55b3c7b3fc99615654f124a0 AS runtime
# Security patch until the pinned upstream base includes Debian's fixed zlib. Standalone needs no package manager.
RUN apt-get update \
    && apt-get install -y --no-install-recommends zlib1g=1:1.3.dfsg+really1.3.1-1+deb13u1 \
    && rm -rf /var/lib/apt/lists/* /usr/local/lib/node_modules/npm /opt/yarn* \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/yarn /usr/local/bin/yarnpkg
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
