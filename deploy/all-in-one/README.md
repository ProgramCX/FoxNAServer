# FoxNAS All-in-One 单容器发布方案

该方案会把以下组件放进 **同一个容器**：

- FoxNAServer（Spring Boot）
- FoxNAS-Web（Nginx 静态站点）
- MySQL
- Redis
- RabbitMQ
- Nginx

> 说明：单容器方案便于一键体验，但生产环境仍建议拆分为多容器。

## 1. 快速启动（默认配置即可运行）

先确认目录结构如下（因为构建需要同时读取前后端源码）：

```text
<workspace>/
  ├─ FoxNAServer/
  └─ FoxNAS-Web/
```

在 `FoxNAServer/deploy/all-in-one` 目录执行：

```bash
docker compose up -d --build
```

启动后访问：

- Web: `http://localhost`
- API 文档: `http://localhost/doc.html`

## 2. 数据与目录映射

`docker-compose.yml` 已默认映射：

- `./data/files:/data/files`（文件管理模块目录）
- `./data/mysql:/var/lib/mysql`
- `./data/redis:/data/redis`
- `./data/rabbitmq:/var/lib/rabbitmq`
- `./config:/opt/foxnas/config`
- `./logs:/var/log/foxnas`

> 文件管理建议在系统里给用户授权目录时使用 `/data/files` 下的路径。

## 3. HTTPS 链接（用户可自由修改）

你提到的“硬编码 https 链接”已改成可配置，支持两种方式：

### 方式 A：改环境变量（推荐）

编辑 `docker-compose.yml`：

```yaml
environment:
  OAUTH_BASE_URL: https://your-domain.com
  OAUTH_FRONTEND_BASE_URL: https://your-domain.com
```

然后重启：

```bash
docker compose up -d
```

### 方式 B：改挂载配置文件

容器第一次启动会生成：

- `./config/config.properties`

直接编辑：

```properties
oauth.base.url=https://your-domain.com
oauth.frontend.base.url=https://your-domain.com
```

然后重启容器。

## 4. 运行前建议修改项

至少修改这些默认值（在 `docker-compose.yml`）：

- `MYSQL_ROOT_PASSWORD`
- `MYSQL_PASSWORD`
- `JWT_SECRET`

## 5. 日志排查

```bash
docker compose logs -f
```

如果要看容器内进程日志：

- `/var/log/foxnas/backend.log`
- `/var/log/foxnas/mysql.log`
- `/var/log/foxnas/redis.log`
- `/var/log/foxnas/rabbitmq.log`
- `/var/log/foxnas/nginx.log`

## 6. 停止与清理

停止：

```bash
docker compose down
```

彻底清理（会删除数据库与缓存数据）：

```bash
docker compose down -v
```
