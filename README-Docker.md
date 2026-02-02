# FoxNAServer Docker 部署指南

本文档介绍如何使用 Docker 部署 FoxNAServer 服务。

## 目录

- [快速开始](#快速开始)
- [环境要求](#环境要求)
- [部署方式](#部署方式)
  - [方式一：开发环境（一键启动）](#方式一开发环境一键启动)
  - [方式二：生产环境（使用外部数据库）](#方式二生产环境使用外部数据库)
- [配置说明](#配置说明)
- [数据库初始化](#数据库初始化)
- [常见问题](#常见问题)

## 快速开始

```bash
# 1. 克隆项目
git clone https://github.com/yourusername/FoxNAServer.git
cd FoxNAServer

# 2. 复制配置文件模板
cp config.properties.example config.properties

# 3. 修改配置文件（详见下方配置说明）
vim config.properties

# 4. 启动服务（开发环境）
docker-compose up -d

# 5. 访问服务
# API 文档：http://localhost:8080/doc.html
```

## 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 内存：至少 2GB 可用内存
- 磁盘：至少 10GB 可用空间

## 部署方式

### 方式一：开发环境（一键启动）

适用于本地开发和测试，使用 `docker-compose.yml` 一键启动所有服务（包含 MySQL、Redis、MongoDB）。

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f foxnaserver

# 停止服务
docker-compose down

# 停止并删除数据卷（⚠️ 会清空数据）
docker-compose down -v
```

**特点：**
- ✅ 一键启动，无需配置外部数据库
- ✅ 适合开发和测试
- ⚠️ 数据存储在 Docker 卷中，删除容器会丢失数据

### 方式二：生产环境（使用外部数据库）

适用于生产环境，使用已有的数据库服务。

#### 步骤 1：准备数据库

**1.1 安装 MySQL 8.0**

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install mysql-server-8.0

# 或 Docker 运行 MySQL
docker run -d \
  --name foxnas-mysql \
  -e MYSQL_ROOT_PASSWORD=your_root_password \
  -e MYSQL_DATABASE=foxnas \
  -e MYSQL_USER=foxnas \
  -e MYSQL_PASSWORD=your_password \
  -p 3306:3306 \
  -v mysql_data:/var/lib/mysql \
  mysql:8.0
```

**1.2 安装 Redis 7**

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install redis-server

# 或 Docker 运行 Redis
docker run -d \
  --name foxnas-redis \
  -p 6379:6379 \
  -v redis_data:/data \
  redis:7-alpine
```

**1.3 安装 MongoDB 7（可选，用于日志存储）**

```bash
# 或 Docker 运行 MongoDB
docker run -d \
  --name foxnas-mongodb \
  -p 27017:27017 \
  -v mongodb_data:/data/db \
  mongo:7
```

#### 步骤 2：初始化数据库

```bash
# 连接 MySQL
mysql -u root -p

# 创建数据库和用户
CREATE DATABASE IF NOT EXISTS foxnas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'foxnas'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON foxnas.* TO 'foxnas'@'%';
FLUSH PRIVILEGES;
```

**注意：** 首次启动时，FoxNAServer 会自动创建所需的表结构。

#### 步骤 3：配置文件

```bash
# 复制配置文件模板
cp config.properties.example config.properties

# 编辑配置文件
vim config.properties
```

**必须修改的配置项：**

```properties
# MySQL 配置
spring.datasource.url=jdbc:mysql://your_mysql_host:3306/foxnas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=foxnas
spring.datasource.password=your_mysql_password

# Redis 配置
spring.data.redis.host=your_redis_host
spring.data.redis.password=your_redis_password

# JWT 密钥（生产环境必须修改！）
jwt.secret=your-super-secret-key-$(openssl rand -base64 32)
```

#### 步骤 4：启动服务

```bash
# 使用生产环境配置启动
docker-compose -f docker-compose.prod.yml up -d

# 查看日志
docker-compose -f docker-compose.prod.yml logs -f

# 停止服务
docker-compose -f docker-compose.prod.yml down
```

## 配置说明

### 配置文件位置

| 文件 | 说明 |
|------|------|
| `config.properties.example` | 配置模板文件 |
| `config.properties` | 实际配置文件（需自己创建） |

### 核心配置项

#### 1. 数据库配置（必填）

```properties
# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/foxnas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=foxnas
spring.datasource.password=your_password

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=

# MongoDB（可选）
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=foxnas_logs
```

#### 2. JWT 安全配置（必填）

```properties
# 生成密钥命令：openssl rand -base64 32
jwt.secret=your-super-secret-key-change-this-in-production
jwt.expiration=86400000
```

⚠️ **重要：** 生产环境必须修改 JWT 密钥，否则存在安全风险！

#### 3. 邮件配置（可选）

用于找回密码等功能。

**QQ 邮箱配置：**
```properties
spring.mail.host=smtp.qq.com
spring.mail.port=465
spring.mail.username=your_qq@qq.com
# 授权码获取：QQ邮箱设置 -> 账户 -> 开启SMTP -> 生成授权码
spring.mail.password=your_auth_code
```

**163 邮箱配置：**
```properties
spring.mail.host=smtp.163.com
spring.mail.port=465
spring.mail.username=your_email@163.com
spring.mail.password=your_auth_code
```

## 数据库初始化

### 自动初始化

FoxNAServer 使用 MyBatis-Plus，首次启动时会自动创建数据库表结构。

### 手动初始化（可选）

如果需要手动初始化，可以执行以下 SQL：

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS foxnas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户
CREATE USER IF NOT EXISTS 'foxnas'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON foxnas.* TO 'foxnas'@'%';
FLUSH PRIVILEGES;
```

## 目录结构

部署后的目录结构：

```
FoxNAServer/
├── docker-compose.yml          # 开发环境配置
├── docker-compose.prod.yml     # 生产环境配置
├── config.properties           # 配置文件（需创建）
├── config.properties.example   # 配置模板
├── data/                       # 用户上传的文件（自动创建）
└── logs/                       # 日志文件（自动创建）
```

## 常见问题

### Q1: 容器启动失败，提示数据库连接错误

**原因：** 数据库配置不正确或数据库未启动

**解决：**
1. 检查 `config.properties` 中的数据库地址、用户名、密码
2. 确保 MySQL/Redis 服务已启动
3. 检查防火墙是否允许连接

### Q2: 如何修改端口？

修改 `docker-compose.yml` 或 `docker-compose.prod.yml`：

```yaml
ports:
  - "8081:8080"  # 将宿主机 8081 映射到容器 8080
```

### Q3: 数据存储在哪里？

- **开发环境：** 使用 Docker 卷，数据在容器内
- **生产环境：** 挂载到宿主机 `./data` 目录

### Q4: 如何备份数据？

**备份用户文件：**
```bash
tar -czvf foxnas_backup_$(date +%Y%m%d).tar.gz ./data ./logs
```

**备份数据库：**
```bash
mysqldump -u foxnas -p foxnas > foxnas_db_$(date +%Y%m%d).sql
```

### Q5: 如何更新到新版？

```bash
# 1. 拉取最新镜像
docker pull yourusername/foxnaserver:latest

# 2. 停止旧容器
docker-compose down

# 3. 启动新容器
docker-compose up -d
```

### Q6: 如何查看日志？

```bash
# 实时查看日志
docker-compose logs -f foxnaserver

# 查看最近 100 行
docker-compose logs --tail=100 foxnaserver

# 查看日志文件
cat logs/foxnas_log.log
```

## 安全建议

1. **修改默认密码：** 生产环境务必修改所有默认密码
2. **JWT 密钥：** 使用随机生成的长字符串
3. **防火墙：** 只开放必要的端口（8080）
4. **HTTPS：** 生产环境建议使用 HTTPS
5. **定期备份：** 定期备份数据和配置文件

## 获取帮助

- 问题反馈：[GitHub Issues](https://github.com/yourusername/FoxNAServer/issues)
- 文档地址：[项目 Wiki](https://github.com/yourusername/FoxNAServer/wiki)

---

**祝你使用愉快！**
