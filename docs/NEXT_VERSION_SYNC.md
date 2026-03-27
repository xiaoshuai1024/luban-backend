# 下版本协同同步（后端）

本文件用于后端与管理后台并行开发时的协同与防冲突。

## 协同边界

- 后端（`luban-backend`）负责：
  - 公开页面查询与鉴权策略
  - 业务错误码与数据契约稳定性
- 管理后台（`luban`）负责：
  - 页面管理、用户管理交互
  - BFF 接口调用与错误展示

## 契约版本

- 权威接口文档：`docs/API.md`
- 本轮新增公开接口：
  - `GET /backend/public/sites/:slug/pages?path=:path`
  - path 缺省 `/`，只返回 `status=published` 页面

## 当前进度

- [x] PublicController + PublicPageService 已落地
- [x] AuthFilter 放行 `/backend/public/*` 无鉴权访问
- [x] Mapper 支持按 `siteId + path + published` 查询

## 并行开发规则

- 接口变更先更新 `docs/API.md`，再通知 BFF 与管理后台对接。
- 只在本仓库内修改后端实现，不直接改管理后台业务逻辑。
- 若涉及新字段，保持与 Go 版本契约语义一致（字段名、错误码、状态语义）。

## 联调清单入口

- 管理后台侧联调文档：`luban/docs/NEXT_VERSION_INTEGRATION_CHECKLIST.md`
