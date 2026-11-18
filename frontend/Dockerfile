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

ARG APP_NAME

ENV PORT=8080
ENV APP_NAME=${APP_NAME}

WORKDIR /app

# No env file enters the image. The image holds no deployment's URLs or
# secrets: the public URLs (BASE_URL, API_BASE_URL, the *_URL keys) and the
# AUTH_* values arrive at run time through Kagi injection.
# .dockerignore keeps every .env* out of the build context, including any copy
# Next.js writes into .next/standalone.
COPY apps/${APP_NAME}/.next .next
COPY apps/${APP_NAME}/public .next/standalone/apps/${APP_NAME}/public

# Create non-root user for security
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001 -G nodejs && \
    chown -R nextjs:nodejs /app

USER nextjs

EXPOSE 8080

ENV SERVER_PATH=.next/standalone/apps/${APP_NAME}/server.js

CMD ["sh", "-c", "node $SERVER_PATH"]
