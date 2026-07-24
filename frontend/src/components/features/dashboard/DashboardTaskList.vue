<script setup lang="ts">
import type { TaskItem } from './types'
import EmptyState from '@/components/ui/EmptyState.vue'

interface Props {
  tasks: TaskItem[]
  title: string
  emptyText?: string
}

withDefaults(defineProps<Props>(), {
  emptyText: '暂无待处理内容'
})
</script>

<template>
  <div class="task-list">
    <h3 class="task-list-title">{{ title }}</h3>
    <div v-if="tasks.length" class="task-items">
      <div v-for="task in tasks" :key="task.id" class="task-item">
        <div class="task-info">
          <span class="task-title">{{ task.title }}</span>
          <span class="task-meta">{{ task.author }} · {{ task.date }}</span>
        </div>
        <span class="task-type">{{ task.type }}</span>
      </div>
    </div>
    <EmptyState v-else :title="emptyText" icon="inbox" variant="compact" />
  </div>
</template>

<style scoped>
.task-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.task-list-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}
.task-items {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 12px;
  background: var(--color-background);
  border-radius: 12px;
}
.task-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.task-title {
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.task-meta {
  font-size: 12px;
  color: var(--color-text-muted);
}
.task-type {
  font-size: 12px;
  padding: 4px 10px;
  background: #e2e8f0;
  border-radius: 999px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
</style>
