<script setup lang="ts">
import { FileTextOutlined, EditOutlined, CloudUploadOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import type { ActivityItem } from './types'

interface Props {
  item: ActivityItem
}

defineProps<Props>()

const iconMap: Record<string, any> = {
  publish: FileTextOutlined,
  edit: EditOutlined,
  upload: CloudUploadOutlined,
  review: CheckCircleOutlined,
  system: FileTextOutlined
}
</script>

<template>
  <div class="activity-item">
    <div class="activity-icon" :class="item.type">
      <component :is="iconMap[item.type] || FileTextOutlined" />
    </div>
    <div class="activity-content">
      <div class="activity-main">
        <span class="activity-user">{{ item.user }}</span>
        <span class="activity-action">{{ item.action }}</span>
      </div>
      <div class="activity-target">{{ item.target }}</div>
    </div>
    <div class="activity-time">{{ item.time }}</div>
  </div>
</template>

<style scoped>
.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--color-background);
  border-radius: 12px;
  transition: background var(--duration-fast);
}
.activity-item:hover { background: #f1f5f9; }
.activity-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: #eff6ff;
  color: var(--color-primary);
  flex-shrink: 0;
}
.activity-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.activity-main {
  display: flex;
  align-items: center;
  gap: 6px;
}
.activity-user { font-weight: 600; color: var(--color-text); }
.activity-action { color: var(--color-text-secondary); }
.activity-target {
  font-size: 13px;
  color: var(--color-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.activity-time {
  font-size: 12px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}
</style>
