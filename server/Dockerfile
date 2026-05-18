# syntax=docker/dockerfile:1.7

FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

COPY --chown=gradle:gradle gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle composeApp/build.gradle.kts ./composeApp/build.gradle.kts
COPY --chown=gradle:gradle server/build.gradle.kts ./server/build.gradle.kts
COPY --chown=gradle:gradle shared/build.gradle.kts ./shared/build.gradle.kts

RUN --mount=type=cache,id=pricetagparser-gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew :server:buildFatJar :composeApp:composeCompatibilityBrowserDistribution --no-daemon --parallel --dry-run

COPY --chown=gradle:gradle composeApp/src ./composeApp/src
COPY --chown=gradle:gradle server/src ./server/src
COPY --chown=gradle:gradle shared/src ./shared/src
COPY --chown=gradle:gradle generate_csv.sh ./generate_csv.sh

RUN --mount=type=cache,id=pricetagparser-gradle-cache,target=/home/gradle/.gradle,uid=1000,gid=1000,sharing=locked \
    ./gradlew :server:buildFatJar :composeApp:composeCompatibilityBrowserDistribution --no-daemon --parallel

FROM pytorch/pytorch:2.5.0-cuda12.4-cudnn9-devel

EXPOSE 8080
WORKDIR /app

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-21-jre-headless \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    git-lfs \
    && rm -rf /var/lib/apt/lists/*

COPY priceTagRecognition/requirements.txt /app/priceTagRecognition/requirements.txt
RUN pip install --no-cache-dir -r /app/priceTagRecognition/requirements.txt

COPY --from=build /workspace/server/build/libs/fat.jar /app/app.jar
COPY --from=build /workspace/composeApp/build/dist/composeWebCompatibility/productionExecutable /app/web
COPY --from=build /workspace/generate_csv.sh /app/generate_csv.sh
COPY priceTagRecognition /app/priceTagRecognition

RUN chmod +x /app/generate_csv.sh
ENTRYPOINT ["java","-jar","/app/app.jar"]
