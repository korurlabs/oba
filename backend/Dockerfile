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
ARG BASE_IMAGE=docker.io/library/eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
FROM ${BASE_IMAGE}

WORKDIR /app

COPY ./target/*.jar oba-api.jar

ENTRYPOINT ["java", "-jar", "oba-api.jar"]
