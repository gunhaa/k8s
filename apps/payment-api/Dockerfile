# ---- build stage: Gradle + JDK 21 ----
FROM gradle:9.8.0-jdk21 AS build
WORKDIR /workspace
# 의존성 레이어 캐시: 빌드 스크립트만 먼저 복사해서 의존성을 받아 둔다
COPY settings.gradle build.gradle ./
RUN gradle dependencies --no-daemon -q > /dev/null || true
COPY src ./src
RUN gradle bootJar --no-daemon -q

# ---- runtime stage: JRE only ----
FROM eclipse-temurin:21-jre-noble
WORKDIR /app
# root가 아닌 고정 UID로 실행 (K8s securityContext.runAsNonRoot와 짝)
RUN groupadd --system --gid 1001 app && useradd --system --uid 1001 --gid app app
COPY --from=build /workspace/build/libs/app.jar /app/app.jar
USER 1001
EXPOSE 8080
# 컨테이너 메모리 limit 기준으로 힙 크기를 잡는다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
