# 빌드 스테이지 - Gradle로 bootJar 생성
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x ./gradlew
# 의존성만 먼저 받아 레이어 캐싱 - 소스만 바뀌면 이 레이어는 재사용됨
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# 실행 스테이지 - JRE만 포함해 이미지 크기 축소
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Render가 PORT 환경변수로 리스닝 포트를 지정 - application.yml의 server.port가
# 이미 ${SERVER_PORT:8080}이라 PORT를 SERVER_PORT에 매핑만 해주면 됨.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
