# syntax=docker/dockerfile:1.7

ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION}-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw \
      --batch-mode \
      --no-transfer-progress \
      dependency:go-offline


COPY src/ src/


RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw \
      --batch-mode \
      --no-transfer-progress \
      clean package \
      -DskipTests


RUN set -eux; \
    JAR_FILE="$(find target \
      -maxdepth 1 \
      -type f \
      -name '*.jar' \
      ! -name '*.jar.original' \
      -print \
      -quit)"; \
    test -n "${JAR_FILE}"; \
    cp "${JAR_FILE}" /workspace/application.jar



FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy AS runtime

# Metadados que podem ser informados durante o docker build.
ARG APP_NAME=gateway-service
ARG APP_VERSION=dev
ARG BUILD_DATE=unknown
ARG VCS_REF=unknown
ARG SOURCE_URL=unknown

LABEL org.opencontainers.image.title="${APP_NAME}" \
      org.opencontainers.image.description="BookCommerce ${APP_NAME}" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.vendor="Sc4rlxrd"


RUN groupadd \
      --system \
      --gid 10001 \
      appgroup \
    && useradd \
      --system \
      --uid 10001 \
      --gid appgroup \
      --no-create-home \
      --shell /usr/sbin/nologin \
      appuser

WORKDIR /application


COPY --from=build \
     --chown=10001:10001 \
     /workspace/application.jar \
     /application/application.jar

USER 10001:10001


EXPOSE 8080

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-jar", "/application/application.jar"]