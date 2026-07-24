<script setup lang="ts">
interface Props {
  title: string
  value: number | string
  icon: any
  status?: 'normal' | 'warning' | 'danger'
  description?: string
  index?: number
}

withDefaults(defineProps<Props>(), {
  status: 'normal'
})
</script>

<template>
  <div class="stat-card" :class="`status-${status}`" :style="{ animationDelay: `${(index ?? 0) * 50}ms` }">
    <div class="stat-card-header">
      <span class="stat-card-title">{{ title }}</span>
      <div class="stat-card-icon"><component :is="icon" /></div>
    </div>
    <div class="stat-card-value">{{ value }}</div>
    <div v-if="description" class="stat-card-desc">{{ description }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  opacity: 0;
  transform: translateY(12px);
  animation: card-enter var(--duration-normal) var(--ease-out) forwards;
}
.stat-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-card-title {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 500;
}
.stat-card-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #eff6ff;
  color: var(--color-primary);
  font-size: 18px;
}
.stat-card-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}
.stat-card-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.status-warning .stat-card-desc { color: var(--color-warning); }
.status-danger .stat-card-desc { color: var(--color-danger); }
@media (prefers-reduced-motion: reduce) {
  .stat-card { animation: none; opacity: 1; transform: none; }
}
</style>
