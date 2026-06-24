# hm-dianping

## 项目简介

本项目是一个基于 Spring Boot 的本地生活服务平台后端系统，提供商户管理、优惠券、秒杀、用户社交等核心功能。

## 技术栈

- **语言**: Java 8+
- **框架**: Spring Boot 2.x
- **数据库**: MySQL 5.7+
- **缓存**: Redis 6.x + Redisson
- **消息队列**: RabbitMQ
- **ORM**: MyBatis Plus
- **API 文档**: Swagger

## 项目结构

```
src/main/java/com/hmdp/
├── HmDianPingApplication.java    # 启动类
├── controller/                   # 控制层
│   ├── UserController.java       # 用户管理
│   ├── ShopController.java       # 店铺管理
│   ├── VoucherController.java    # 优惠券管理
│   ├── VoucherOrderController.java # 秒杀订单
│   ├── BlogController.java       # 动态管理
│   ├── BlogCommentsController.java # 评论管理
│   ├── FollowController.java     # 关注功能
│   ├── ShopTypeController.java   # 店铺类型
│   └── UploadController.java     # 文件上传
├── service/                      # 服务层
├── mapper/                       # 数据访问层
├── entity/                       # 实体类
├── dto/                          # 数据传输对象
├── config/                       # 配置类
└── utils/                        # 工具类
```

## 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- Redis 6.x+
- RabbitMQ 3.8+

### 配置说明

1. 创建数据库并执行初始化脚本：

```sql
CREATE DATABASE hmdp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行 `src/main/resources/db/hmdp.sql`

2. 修改 `src/main/resources/application.yaml` 中的数据库、Redis、RabbitMQ 连接配置

### 启动方式

**开发态运行：**

```bash
cd hm-dianping
mvn spring-boot:run
```

**打包构建：**

```bash
mvn clean package
java -jar target/hm-dianping-0.0.1-SNAPSHOT.jar
```

**Docker 部署：**

```bash
docker compose up -d
```

## 核心功能

| 模块 | 功能 | 描述 |
|------|------|------|
| 用户模块 | 登录/注册、用户信息管理 | 基于 Token 的无状态认证 |
| 店铺模块 | 店铺 CRUD、分类管理 | 支持缓存优化 |
| 优惠券模块 | 普通券/秒杀券管理 | 支持时间范围限制 |
| 秒杀模块 | 高并发秒杀下单 | Redis + Lua 保证原子性 |
| 社交模块 | 关注、动态、评论 | 支持滚动分页 |

## API 文档

启动服务后访问：`http://localhost:8080/swagger-ui.html`

## 核心特性

- **分布式锁**: Redisson 实现，支持可重入锁、公平锁
- **缓存策略**: Redis 缓存 + 过期时间 + 主动更新
- **秒杀优化**: Lua 脚本保证库存原子性扣减
- **消息队列**: RabbitMQ 异步处理订单
- **限流降级**: Sentinel 流量控制
