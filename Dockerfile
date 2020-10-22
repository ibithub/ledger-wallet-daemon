### BUILD STEP ###
FROM adoptopenjdk:11-jdk-hotspot as builder
ARG COMMIT_HASH=""
ENV STAGE dev
ENV COMMIT_HASH $COMMIT_HASH

WORKDIR /build
ADD . /build
RUN ./docker/build.sh

#### RUN STEP ###
FROM adoptopenjdk:11-jre-hotspot
ENV HTTP_PORT 9200
ENV ADMIN_PORT 0
ENV STAGE dev

WORKDIR /app
COPY --from=builder /build/target/universal/stage .
COPY ./docker/install_run_deps.sh .
COPY ./docker/run.sh .
RUN ./install_run_deps.sh && rm -f install_run_deps.sh

CMD ["/app/run.sh"]
