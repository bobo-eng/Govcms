# 后台体验统一化维护清单

## 1. 文档目的

本文档用于记录后台管理系统体验统一化在当前代码状态下的最终维护项，作为后续零散优化的唯一盘点依据。

本清单只记录体验统一化维护项，不讨论业务逻辑正确性。

## 2. 当前总体结论

截至 2026-04-01，后台体验统一化已经完成 8 批页面改版，并形成共享后台样式层 `admin-refresh.css`。后台高频页已全部纳入统一体验体系，当前状态已进入**维护态**。

当前不再存在“整页未统一”的后台高频页面。

## 3. 当前维护项

当前维护项已从“页面级残留”下降为“局部样式清理”，主要包括：

| 页面 | 当前状态 | 维护项 |
| --- | --- | --- |
| `Templates.vue` | 已统一主骨架 | 仍可继续减少局部历史 class 与局部样式定义 |
| `PublishCenter.vue` | 已统一主骨架 | 仍可继续减少局部历史 class 与局部样式定义 |

说明：

- 这两页已经不再属于“明显旧样式主导页”
- 后续若继续优化，应按零散维护项处理，而不是重新立项为成批页面改版
- `Login.vue` 不属于后台体验统一化范围，不纳入本清单

## 4. 已完成批次回顾

- 第一批：`Users + Categories`
- 第二批：`Sites + PublishCenter`
- 第三批：`SearchOps + Topics`
- 第四批：`Templates + Media`
- 第五批：`NavigationManagement + Menus`
- 第六批：`Roles + Permissions`
- 第七批：`Content + Review`
- 第八批：`Dashboard + Templates 局部收尾 + PublishCenter 局部收尾`

当前后台已形成统一的页面类型参考，包括：

- 标准列表页
- 列表页 + 弹窗表单页
- 工作台页
- 树状管理页
- 树状工作台页
- 内容工作页
- 仪表盘页

## 5. 后续维护建议

如后续继续处理后台体验问题，建议只按零散维护项推进：

1. `Templates.vue` 局部 class 清理
2. `PublishCenter.vue` 局部 class 清理
3. 新增后台页面时直接复用现有共享样式层，不再新增新的改版批次

## 6. 使用规则

后续任何后台页面新增或局部优化，均应先参考：

- `docs/18-admin-ux-visual-and-interaction-guidelines.md`
- `docs/19-admin-ux-key-page-guidance.md`
- `docs/20-admin-ux-refresh-priority.md`
- 本文档

若后续页面状态再发生变化，应优先更新本文档，再继续推进维护项。
