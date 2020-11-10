ARG jdk=openjdk:8u181-jdk-stretch
ARG jre=openjdk:8u181-jdk-slim-stretch


### BUILD STEP ###
FROM $jdk as app-builder
ARG COMMIT_HASH=""
ENV STAGE dev
ENV COMMIT_HASH $COMMIT_HASH

RUN apt-get update && \
    apt-get install -qy apt-transport-https \
                        software-properties-common && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \
    add-apt-repository "deb https://dl.bintray.com/sbt/debian /" && \
    apt-get update && \
    apt-get install -qy sbt

    #rm -rf /var/lib/apt/lists/*

WORKDIR /build
ADD ./lib ./lib
ADD ./project ./project
ADD ./build.sbt ./build.sbt
RUN sbt update
ADD ./docker/build.sh build.sh
ADD ./src ./src
RUN ./build.sh

### LIST LIBCORE DEPENDENCIES (jdk env because of jar command) ###
FROM $jdk as libcore-deps

RUN apt-get update && \
    apt-get install -yq apt-file \
                        libc-bin && \
    apt-file update

WORKDIR /env
COPY ./lib ./lib
COPY ./docker/list_debian_libcore_packages_deps.sh .
RUN ./list_debian_libcore_packages_deps.sh > lib_core.deps

ENTRYPOINT cat /env/lib_core.deps

#### RUN STEP ###
FROM $jre
ENV HTTP_PORT 9200
ENV ADMIN_PORT 0
ENV STAGE dev

WORKDIR /app

COPY ./docker/run.sh .
COPY --from=app-builder /build/target/universal/stage .

COPY --from=libcore-deps /env/lib_core.deps .
COPY ./docker/check_libcore_packages_deps.sh .
COPY ./docker/install_run_deps.sh .

RUN  ./install_run_deps.sh

RUN cat lib_core.deps | ./check_libcore_packages_deps.sh > missing.deps
RUN [ ! -s missing.deps ]

RUN rm -f install_run_deps.sh && \
    rm -f ./lib_core.deps && \
    rm -f ./check_libcore_packages_deps.sh

CMD ["/app/run.sh"]
