<script setup lang="ts">
import type { ActivityItem } from './types'
import ActivityItemComponent from './ActivityItem.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

interface Props {
  items: ActivityItem[]
}

defineProps<Props>()
</script>

<template>
  <div class="activity-feed">
    <h3 class="feed-title">近期活动</h3>
    <TransitionGroup v-if="items.length" name="activity" tag="div" class="feed-list">
      <ActivityItemComponent v-for="item in items" :key="item.id" :item="item" />
    </TransitionGroup>
    <EmptyState v-else title="暂无活动记录" description="当用户执行发布、编辑等操作后，将在此显示" icon="inbox" />
  </div>
</template>

<style scoped>
.activity-feed {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.feed-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}
.feed-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.activity-enter-active, .activity-leave-active {
  transition: all var(--duration-normal) var(--ease-out);
}
.activity-enter-from, .activity-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
