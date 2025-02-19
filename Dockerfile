# Sử dụng OpenJDK 17 trên Alpine Linux
FROM eclipse-temurin:17-jdk-alpine

# Cập nhật hệ thống và cài đặt FFmpeg
RUN apk add --no-cache ffmpeg

# Thiết lập thư mục làm việc
WORKDIR /app

# Sao chép file JAR của ứng dụng vào container
COPY target/java_intern-0.0.1-SNAPSHOT.jar app.jar


# Chạy ứng dụng Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]
