# GovCMS 内容生命周期技术设计

## 0. 2026-03-17 实现对齐说明

- 当前实现已按 `docs/09-content-lifecycle-prd.md` 落地六态生命周期：`draft`、`pending_review`、`rejected`、`approved`、`published`、`offline`。
- 审核通过只进入 `approved`，正式发布统一通过发布中心成功后写回 `published`。
- 内容流转历史已通过 `ArticleLifecycleHistory` 留痕，并支持前端查看。
