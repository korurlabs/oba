# Base image: Java 25 JRE on Alpine Linux.
#
# BASE_IMAGE:
#   The default is the public docker.io/library/eclipse-temurin:25-jre-alpine,
#   pinned by the digest of its multi-arch index, so the same Dockerfile always
#   builds on the same base.
#   A deployer who keeps a patched copy of that image passes it instead:
#   --build-arg BASE_IMAGE=<registry>/<image>:<tag>
#
# UPDATE:
#   Look up the current digest with
#   skopeo inspect --format '{{.Digest}}' docker://docker.io/library/eclipse-temurin:25-jre-alpine
#   and replace the digest below.
ARG BASE_IMAGE=docker.io/library/eclipse-temurin:25-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89
FROM ${BASE_IMAGE}

WORKDIR /app

COPY ./target/*.jar oba-api.jar

# The API runs as a non-root user. The numeric USER lets Kubernetes verify
# runAsNonRoot without reading /etc/passwd; the chart runs every component as
# 10001.
RUN addgroup -S -g 10001 oba && \
    adduser -S -D -H -u 10001 -G oba oba

USER 10001:10001

ENTRYPOINT ["java", "-jar", "oba-api.jar"]
