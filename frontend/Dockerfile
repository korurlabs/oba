# Base image: Node 22 on Alpine Linux.
#
# BASE_IMAGE:
#   The default is the public docker.io/library/node:22-alpine, pinned by the
#   digest of its multi-arch index, so the same Dockerfile always builds on the
#   same base.
#   A deployer who keeps a patched copy of that image passes it instead:
#   --build-arg BASE_IMAGE=<registry>/<image>:<tag>
#
# UPDATE:
#   Look up the current digest with
#   skopeo inspect --format '{{.Digest}}' docker://docker.io/library/node:22-alpine
#   and replace the digest below.
ARG BASE_IMAGE=docker.io/library/node:22-alpine@sha256:1314fb30ec37592d5845e5a160d522c0e680972aa534549940b8d73bde88481a
FROM ${BASE_IMAGE}

# The standalone server runs on node alone. The base image also ships npm, npx,
# corepack and Yarn, which nothing in the container runs, and the packages they
# bundle carry vulnerabilities of their own that a scan of the image reports.
# Removing them leaves only what the server needs. The Yarn glob still matches
# when a newer base image ships another Yarn version, and a base image without
# these tools builds unchanged.
RUN rm -rf /usr/local/lib/node_modules /opt/yarn-v* \
        /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack \
        /usr/local/bin/yarn /usr/local/bin/yarnpkg

ARG APP_NAME

ENV PORT=8080
ENV APP_NAME=${APP_NAME}

WORKDIR /app

# No env file enters the image. The image holds no deployment's URLs or
# secrets: the public URLs (BASE_URL, API_BASE_URL, the *_URL keys) and the
# AUTH_* values arrive at run time through Kagi injection.
# .dockerignore keeps every .env* out of the build context, including any copy
# Next.js writes into .next/standalone.
# The app's postbuild step (copy-static-to-standalone.mjs) has already copied
# public/ and .next/static/ into .next/standalone, so this one copy carries
# them. An app without a public/ folder builds the same way.
COPY apps/${APP_NAME}/.next .next

# The server runs as a non-root user. The numeric USER lets Kubernetes verify
# runAsNonRoot without reading /etc/passwd; the chart runs every component as
# 10001.
RUN addgroup -g 10001 -S nodejs && \
    adduser -S nextjs -u 10001 -G nodejs && \
    chown -R nextjs:nodejs /app

USER 10001:10001

EXPOSE 8080

ENV SERVER_PATH=.next/standalone/apps/${APP_NAME}/server.js

CMD ["sh", "-c", "node $SERVER_PATH"]
