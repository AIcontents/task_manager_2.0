# syntax=docker/dockerfile:1

# ---------- Этап 1: сборка (нужен JDK и Maven; в итоговый образ не попадёт) ----------
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Сначала только файлы сборки: слой с зависимостями кешируется и пересобирается,
# лишь когда меняется pom.xml (а не при каждой правке исходников)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
# chmod: на Windows файл может потерять право на выполнение при копировании
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
# Тесты запускаются отдельно (./mvnw verify локально и в CI); для образа нужен только JAR.
# maven.test.skip также пропускает компиляцию тестов
RUN ./mvnw package -Dmaven.test.skip=true -B

# ---------- Этап 2: запуск (только JRE и готовый JAR) ----------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Не запускаем приложение от root: при уязвимости в приложении у злоумышленника не будет прав root в контейнере
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=builder /build/target/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
