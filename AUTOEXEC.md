# 🤖 自动执行框架

## 文件结构

```
/workspace/govcms/
├── executor.py          # 自动执行器
├── task_plan.md        # 任务规划 (待执行队列)
├── progress.md         # 执行进度日志
├── findings.md         # 研究发现
├── requirement_analysis.json  # 原始需求
└── system_design.json  # 架构设计
```

## 使用方式

### 1. 查看待办任务
```bash
cd /workspace/govcms
python3 executor.py --list
```

### 2. 执行所有任务
```bash
cd /workspace/govcms
python3 executor.py
```

### 3. 执行 N 个任务
```bash
python3 executor.py /workspace/govcms 5
```

### 4. 执行单个任务
```bash
python3 executor.py --task "SpringBoot 3.x 项目初始化"
```

## 工作原理

```
┌─────────────────────────────────────────────────────────────┐
│  executor.py                                              │
├─────────────────────────────────────────────────────────────┤
│  1. 读取 task_plan.md 中的 - [ ] 待办任务                  │
│  2. 调用 Claude Code (MiniMax API) 执行任务                │
│  3. 更新 task_plan.md 标记 - [x] 完成                      │
│  4. 更新 progress.md 记录执行结果                          │
│  5. 重复直到所有任务完成                                   │
└─────────────────────────────────────────────────────────────┘
```

## 配置

- Claude Code API: MiniMax-M2.5
- 超时: 5分钟/任务
- 环境变量自动设置

## 示例

```bash
# 查看当前进度
cat progress.md

# 手动更新任务状态
# 编辑 task_plan.md 将 - [ ] 改为 - [x]

# 查看下一任务
grep -n "\- \[" task_plan.md | head -1
```

## 注意事项

1. 每个任务最多执行 5 分钟
2. 任务失败会记录到 progress.md
3. 可以随时中断，重启会继续执行
4. 建议先用 --list 查看任务再执行
