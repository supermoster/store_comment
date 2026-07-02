# FoodieGo

## Project Overview

FoodieGo is a local food review and flash-deal platform backend built on Spring Boot. It provides
shop discovery, voucher flash sales (seckill), user social features, and more.

## Tech Stack

- **Language**: Java 8+
- **Framework**: Spring Boot 2.x
- **Database**: MySQL 5.7+
- **Cache**: Redis 6.x + Redisson
- **Message Queue**: RabbitMQ
- **ORM**: MyBatis Plus
- **API Docs**: Swagger

## Project Structure

```
src/main/java/com/foodiego/
├── FoodieGoApplication.java       # Application entry point
├── controller/                    # REST controllers
│   ├── UserController.java        # User management
│   ├── ShopController.java        # Shop management
│   ├── VoucherController.java     # Voucher management
│   ├── VoucherOrderController.java# Flash-sale orders
│   ├── BlogController.java        # Blog/social feed
│   ├── BlogCommentsController.java# Comments
│   ├── FollowController.java      # Follow/unfollow
│   ├── ShopTypeController.java    # Shop categories
│   └── UploadController.java      # File upload
├── service/                       # Business service layer
├── mapper/                        # Data access layer
├── entity/                        # Entity classes
├── dto/                           # Data transfer objects
├── config/                        # Configuration classes
└── utils/                         # Utility classes
```

## Quick Start

### Prerequisites

- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- Redis 6.x+
- RabbitMQ 3.8+

### Configuration

1. Create the database and run the init script:

```sql
CREATE DATABASE foodiego CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Execute `src/main/resources/db/hmdp.sql`

2. Update database, Redis, and RabbitMQ connection settings in `src/main/resources/application.yaml`

### Running the Application

**Development mode:**

```bash
mvn spring-boot:run
```

**Build & package:**

```bash
mvn clean package
java -jar target/foodiego-0.0.1-SNAPSHOT.jar
```

**Docker deployment:**

```bash
docker compose up -d
```

## Core Features

| Module | Feature | Description |
|--------|---------|-------------|
| User | Login/Register, Profile | Token-based stateless auth |
| Shop | Shop CRUD, Categories | Redis cache-optimized |
| Voucher | Regular & Flash-sale Vouchers | Time-window restrictions |
| Seckill | High-concurrency flash sales | Redis + Lua atomicity |
| Social | Follow, Blog, Comments | Scroll-based pagination |

## API Documentation

After startup, visit: `http://localhost:8081/swagger-ui.html`

## Key Features

- **Distributed Lock**: Redisson-based, supports reentrant & fair locks
- **Cache Strategy**: Redis cache with TTL, logical expiration, and active invalidation
- **Seckill Optimization**: Lua scripts ensure atomic stock deduction
- **Message Queue**: RabbitMQ for async order processing
- **Rate Limiting**: Sentinel flow control
