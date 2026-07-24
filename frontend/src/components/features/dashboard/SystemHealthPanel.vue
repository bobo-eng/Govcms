<script setup lang="ts">
import type { HealthItem } from './types'

interface Props {
  services: HealthItem[]
}

defineProps<Props>()
</script>

<template>
  <div class="health-panel">
    <h3 class="health-title">系统状态</h3>
    <div class="health-grid">
      <div
        v-for="service in services"
        :key="service.name"
        class="health-item"
        :class="{ down: service.status === 'DOWN', unknown: service.status === 'UNKNOWN' }"
      >
        <div class="health-indicator" :class="service.status.toLowerCase()" />
        <span class="health-name">{{ service.label }}</span>
        <span class="health-status">{{ service.status === 'UP' ? '正常' : service.status === 'DOWN' ? '异常' : '未知' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.health-panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: var(--space-4);
}
.health-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 var(--space-3) 0;
}
.health-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-3);
}
.health-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: 10px;
  background: var(--color-background);
  transition: background var(--duration-fast);
}
.health-indicator {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-success);
}
.health-indicator.down {
  background: var(--color-danger);
  animation: pulse-danger 1.5s infinite;
}
.health-indicator.unknown {
  background: var(--color-warning);
}
.health-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}
.health-status {
  font-size: 12px;
  color: var(--color-text-muted);
}
@media (max-width: 768px) {
  .health-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
