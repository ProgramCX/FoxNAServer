# FoxNAS Standard 单容器发布方案

该方案将前后端与依赖服务放在同一个容器中，通过 Nginx 统一入口并对宿主机暴露端口，适合快速交付与标准化部署。

容器内包含：

- FoxNAServer（Spring Boot）
- FoxNAS-Web（Nginx 静态站点）
- MySQL
- Redis
- RabbitMQ
- Nginx

## 1. 快速启动（默认配置可直接运行）

先确认目录结构（构建镜像需要同时读取前后端源码）：

```text
<workspace>/
  ├─ FoxNAServer/
  └─ FoxNAS-Web/
```

在 `FoxNAServer/deploy/standard` 目录执行：

```bash
docker compose up -d --build
```

启动后访问：

- Web: `http://localhost`
- API 文档: `http://localhost/doc.html`

## 2. 端口与目录映射

默认端口映射：

- `80:80`（Nginx / 前后端统一入口）
- `3306:3306`（MySQL）
- `6379:6379`（Redis）
- `5672:5672`（RabbitMQ AMQP）
- `15672:15672`（RabbitMQ 管理端口）

默认目录映射：

- `./data/files:/data/files`（文件管理模块目录）
- `./data/mysql:/var/lib/mysql`
- `./data/redis:/data/redis`
- `./data/rabbitmq:/var/lib/rabbitmq`
- `./config:/opt/foxnas/config`
- `./logs:/var/log/foxnas`

## 3. 默认配置生成机制

容器首次启动会自动创建：

- `./config/config.properties`

生成来源：`deploy/standard/config/config.properties.template`。

如果 `./config/config.properties` 已存在，则不会覆盖，用户可以长期维护自己的配置。

## 4. 用户必须知道的可修改项

### 4.1 HTTPS / 域名（推荐改环境变量）

编辑 `deploy/standard/docker-compose.yml` 中环境变量：

```yaml
environment:
  OAUTH_BASE_URL: https://your-domain.com
  OAUTH_FRONTEND_BASE_URL: https://your-domain.com
```

重启：

```bash
docker compose up -d
```

### 4.2 数据库与安全密钥（强烈建议修改）

至少修改：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `JWT_SECRET`

### 4.3 OAuth 凭证（可选）

编辑 `./config/config.properties`：

```properties
spring.security.oauth2.client.registration.github.client-id=...
spring.security.oauth2.client.registration.github.client-secret=...
spring.security.oauth2.client.registration.qq.client-id=...
spring.security.oauth2.client.registration.qq.client-secret=...
microsoft.client.id=...
microsoft.client.secret=...
```

## 5. 运维与排查

查看日志：

```bash
docker compose logs -f
```

容器内关键日志：

- `/var/log/foxnas/backend.log`
- `/var/log/foxnas/mysql.log`
- `/var/log/foxnas/redis.log`
- `/var/log/foxnas/rabbitmq.log`
- `/var/log/foxnas/nginx.log`

停止：

```bash
docker compose down
```

清理（会删除数据）：

```bash
docker compose down -v
```

## 6. 注意事项

- 单容器多进程方案便于交付，不是高可用架构；生产建议逐步拆分。
- 默认暴露了 MySQL/Redis/RabbitMQ 端口；公网部署务必配合防火墙白名单。
- RabbitMQ 默认使用 `guest/guest`（仅容器内应用连接）；如需外部连接请自行创建专用用户并调整权限。

