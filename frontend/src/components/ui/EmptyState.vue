<script setup lang="ts">
import { FileTextOutlined, InboxOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

interface Props {
  title: string
  description?: string
  icon?: 'file' | 'inbox' | 'search' | 'warning'
  actionText?: string
  actionTo?: string
  variant?: 'default' | 'compact'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default'
})

const router = useRouter()

const iconComponent = computed(() => {
  switch (props.icon) {
    case 'search': return SearchOutlined
    case 'warning': return FileTextOutlined
    case 'file': return FileTextOutlined
    case 'inbox':
    default: return InboxOutlined
  }
})

const handleAction = () => {
  if (props.actionTo) {
    router.push(props.actionTo)
  }
}
</script>

<template>
  <div class="empty-state" :class="`variant-${variant}`">
    <div class="empty-state-icon">
      <component :is="iconComponent" />
    </div>
    <h4 class="empty-state-title">{{ title }}</h4>
    <p v-if="description" class="empty-state-desc">{{ description }}</p>
    <div v-if="$slots.footer || actionText" class="empty-state-footer">
      <slot name="footer">
        <button v-if="actionText && actionTo" class="admin-primary-btn" @click="handleAction">
          {{ actionText }}
        </button>
      </slot>
    </div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  text-align: center;
  gap: var(--space-3);
}
.empty-state.variant-compact {
  padding: var(--space-4);
  gap: var(--space-2);
}
.empty-state-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: var(--color-background);
  color: var(--color-text-muted);
  font-size: 28px;
}
.variant-compact .empty-state-icon {
  width: 40px;
  height: 40px;
  font-size: 20px;
  border-radius: 10px;
}
.empty-state-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}
.empty-state-desc {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
  max-width: 320px;
}
.empty-state-footer {
  margin-top: var(--space-2);
}
</style>
