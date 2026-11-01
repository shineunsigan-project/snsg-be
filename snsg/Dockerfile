
# syntax=docker/dockerfile:1

# Gradle은 GitHub Runner의 네이티브 아키텍처에서 빌드
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

# 의존성 다운로드를 소스 코드와 분리해 캐싱
RUN chmod +x gradlew && \
    ./gradlew dependencies --configuration runtimeClasspath --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon

# 최종 실행 이미지는 ARM64
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system spring && \
    useradd --system --gid spring spring && \
    mkdir -p /data/files && \
    chown -R spring:spring /data/files

COPY --from=builder --chown=spring:spring \
    /workspace/build/libs/*.jar /app/app.jar

USER spring:spring

ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-Xms1g -Xmx1g -XX:ActiveProcessorCount=1 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
