

### BUILD STEP ###
FROM openjdk:8u272-slim-buster as builder
ARG COMMIT_HASH=""
ENV STAGE dev
ENV COMMIT_HASH $COMMIT_HASH

WORKDIR /build
ADD . /build
RUN ./docker/build.sh

#### RUN STEP ###
FROM openjdk:8u272-jre-slim-buster
ARG docker_tag

ENV HTTP_PORT 9200
ENV ADMIN_PORT 0
ENV STAGE dev

ENV DD_SERVICE=wallet-daemon
ENV DD_VERSION=$docker_tag

WORKDIR /app
COPY --from=builder /build/target/universal/stage .
COPY ./docker/install_run_deps.sh .
COPY ./docker/run.sh .
RUN ./install_run_deps.sh && rm -f install_run_deps.sh

CMD ["/app/run.sh"]
