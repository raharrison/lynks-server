FROM gradle:7-jdk19-jammy AS build

USER gradle
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src/ ./src

RUN gradle installDist --no-daemon

FROM eclipse-temurin:19-jre-jammy

RUN apt-get update \
    && apt-get install -y python3 ffmpeg \
    && apt-get autoremove -y \
    && apt-get autoclean -y \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/bin/python3 /usr/bin/python

EXPOSE 8080

WORKDIR /app

COPY --from=build /app/build/install/lynks-server .

ENTRYPOINT ["./bin/lynks-server"]
