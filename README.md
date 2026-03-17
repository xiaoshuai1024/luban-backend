# luban-backend

鲁班低代码平台的**主后端**服务（Java 实现），为管理后台与 Render 提供站点、页面、用户与系统设置等数据接口。BFF（luban-bff）通过 `BACKEND_BASE_URL` 对接本服务。

## 技术栈

- Spring Boot
- MyBatis
- Redis
- MySQL

## API 文档

**平台后端 API 的权威定义在本仓库维护**，BFF 与前端以该文档为对接规范：

- [docs/API.md](docs/API.md) — 领域模型、HTTP 接口、鉴权与错误码

新增或变更接口时，请先更新 `docs/API.md`，再实现代码；其他后端实现（如 luban-backend-go）按该文档对齐契约。

## 与 BFF 的协作

- 本服务不直接面向浏览器，由 luban-bff 以 `/backend/*` 路径调用。
- 鉴权采用 BFF 注入的 Header：`X-User-ID`、`X-User-Role`；登录接口返回 user + claims，由 BFF 签发 JWT。

## 本地运行

1. **环境**：JDK 17+，MySQL 8+，Redis（可选，用于系统设置缓存；未配置时设置仍从 DB 读写）。
2. **数据库**：创建库 `luban`（或通过 `MYSQL_DB` 指定），应用启动时会执行 `schema.sql` 建表。
3. **Redis**：需已启动，用于系统设置缓存（key `settings:global`）。
4. **配置**：通过环境变量或 `application.yml` 配置：
   - `APP_PORT`（默认 8080）
   - `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DB`, `MYSQL_USER`, `MYSQL_PASSWORD`
   - `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
5. **启动**：`mvn spring-boot:run` 或打包后 `java -jar target/luban-backend-*.jar`。
6. **健康检查**：`GET http://localhost:8080/backend/ping` 返回 `{"message":"pong"}`。
