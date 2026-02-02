# FoxNAServer Dockerfile
# 多阶段构建，减小最终镜像体积

# 阶段一：构建阶段
FROM eclipse-temurin:21-jdk-alpine AS builder

# 安装 Maven
RUN apk add --no-cache maven

# 设置工作目录
WORKDIR /build

# 先复制 pom.xml 和源码，利用 Docker 缓存层
COPY pom.xml .
COPY src ./src

# 构建项目（跳过测试以加快构建速度）
RUN mvn clean package -DskipTests

# 阶段二：运行阶段
FROM eclipse-temurin:21-jre-alpine

# 安装必要的工具（用于健康检查等）
RUN apk add --no-cache curl

# 创建非 root 用户运行应用，提高安全性
RUN addgroup -S foxnas && adduser -S foxnas -G foxnas

# 设置工作目录
WORKDIR /app

# 从构建阶段复制 jar 包
COPY --from=builder /build/target/*.jar app.jar

# 创建日志目录并设置权限
RUN mkdir -p /app/logs && chown -R foxnas:foxnas /app

# 切换到非 root 用户
USER foxnas

# 暴露应用端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
