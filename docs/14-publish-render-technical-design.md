# GovCMS 发布渲染契约技术设计

## 0. 2026-03-17 实现对齐说明

- 当前实现已按 `docs/15-publish-center-mvp-prd.md` 落地发布中心 MVP。
- 发布单位已支持 `content`、`category`、`template`、`site`；发布模式已支持 `incremental`、`full`、`offline`、`rollback`。
- 当前发布中心采用同步执行，已能记录任务、影响项、产物与回滚记录。
