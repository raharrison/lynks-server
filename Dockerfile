FROM gradle:7-jdk11 AS build

USER gradle
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src/ ./src

RUN gradle installDist --no-daemon

FROM openjdk:11-jre-slim-buster

RUN apt-get update \
    && apt-get install -y python3 \
    && apt-get autoremove -y \
    && apt-get autoclean -y \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/bin/python3 /usr/bin/python

EXPOSE 8080

WORKDIR /app

COPY --from=build /app/build/install/lynks-server .

ENTRYPOINT ["./bin/lynks-server"]
