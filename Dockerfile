### BUILD STEP ###
FROM openjdk:8u181-jdk-stretch as app-builder
ARG COMMIT_HASH=""
ENV STAGE dev
ENV COMMIT_HASH $COMMIT_HASH

RUN apt-get update
RUN apt-get install -qy apt-transport-https software-properties-common
RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
RUN add-apt-repository "deb https://dl.bintray.com/sbt/debian /"
RUN apt-get update
RUN apt-get install -qy sbt

WORKDIR /build
ADD ./lib ./lib
ADD ./project ./project
ADD ./build.sbt ./build.sbt
RUN sbt update
ADD ./docker/build.sh build.sh
ADD ./src ./src
RUN ./build.sh

### LIST LIBCORE DEPENDENCIES (jdk env because of jar command) ###
FROM openjdk:8u181-jdk-slim-stretch as libcore-deps

RUN apt-get update && apt-get install -yq apt-file && apt-file update
RUN apt-get install -yq libc-bin

WORKDIR /env
COPY ./lib ./lib
COPY ./docker/list_debian_libcore_packages_deps.sh .
RUN ./list_debian_libcore_packages_deps.sh > lib_core.deps


#### RUN STEP ###
FROM openjdk:8u181-jre-slim-stretch
ENV HTTP_PORT 9200
ENV ADMIN_PORT 0
ENV STAGE dev

WORKDIR /app
COPY ./docker/install_run_deps.sh .
COPY --from=libcore-deps /env/lib_core.deps .
RUN ./install_run_deps.sh && rm -f install_run_deps.sh && rm -rf ./lib_core.deps

COPY ./docker/run.sh .
COPY --from=app-builder /build/target/universal/stage .


CMD ["/app/run.sh"]
