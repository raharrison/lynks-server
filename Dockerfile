FROM gradle:9-jdk25 AS build

USER gradle
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src/ ./src

RUN gradle installDist --no-daemon

FROM eclipse-temurin:25-jre-resolute

RUN apt-get update \
    && apt-get install -y python3 ffmpeg \
    && apt-get autoremove -y \
    && apt-get autoclean -y \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/bin/python3 /usr/bin/python

EXPOSE 8080

RUN groupadd -r lynks && useradd -r -g lynks lynks

WORKDIR /app

COPY --from=build /app/build/install/lynks-server .

RUN chown -R lynks:lynks /app

USER lynks

ENTRYPOINT ["./bin/lynks-server"]
