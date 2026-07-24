<script setup lang="ts">
import { h, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Modal } from 'ant-design-vue'
import {
  DashboardOutlined,
  FileTextOutlined,
  UserOutlined,
  TeamOutlined,
  MenuOutlined,
  LockOutlined,
  SettingOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BellOutlined,
  SearchOutlined,
  FolderOutlined,
  GlobalOutlined,
  CloudOutlined,
  AuditOutlined,
  LayoutOutlined,
  SendOutlined
} from '@ant-design/icons-vue'
import api from '../../utils/api'
import { clearSession, getRoles, getUsername } from '../../utils/session'

interface MenuItem {
  key: string
  label: string
  icon?: any
  path?: string
  children?: MenuItem[]
}

interface NotificationItem {
  id: number
  title: string
  content: string
  type: string
  read: boolean
  createdAt: string
}

interface MenuApiItem {
  id: number
  name: string
  path?: string
  icon?: string
  children?: MenuApiItem[]
}

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const loading = ref(true)
const selectedKeys = ref<string[]>([])
const openKeys = ref<string[]>([])
const menuItems = ref<MenuItem[]>([])
const username = ref(getUsername() || 'Admin')
const roleLabel = ref('\u7528\u6237')
const notifications = ref<NotificationItem[]>([])
const unreadCount = ref(0)
const notificationVisible = ref(false)

const iconMap: Record<string, any> = {
  DashboardOutlined,
  FileTextOutlined,
  UserOutlined,
  TeamOutlined,
  MenuOutlined,
  LockOutlined,
  SettingOutlined,
  FolderOutlined,
  GlobalOutlined,
  CloudOutlined,
  AuditOutlined,
  LayoutOutlined,
  SendOutlined,
  SearchOutlined,
}

const convertToAntMenu = (menus: MenuApiItem[]): MenuItem[] => {
  return menus.map(menu => {
    const item: MenuItem = {
      key: menu.path ? menu.path.replace('/', '') : 'menu-' + menu.id,
      label: menu.name,
      icon: menu.icon && iconMap[menu.icon] ? h(iconMap[menu.icon]) : undefined,
      path: menu.path
    }

    if (menu.children && menu.children.length > 0) {
      item.children = convertToAntMenu(menu.children)
    }

    return item
  })
}

const fetchMenus = async () => {
  loading.value = true
  try {
    const res = await api.get('/menus/user')
    menuItems.value = convertToAntMenu(res.data || [])
    openKeys.value = menuItems.value
      .filter(item => item.children && item.children.length > 0)
      .map(item => item.key)
  } catch (err) {
    console.warn('fetchMenus failed:', err)
    menuItems.value = []
  } finally {
    loading.value = false
  }
}

const findPathByKey = (items: MenuItem[], key: string): string | undefined => {
  for (const item of items) {
    if (item.key === key) {
      return item.path
    }

    if (item.children) {
      const found = findPathByKey(item.children, key)
      if (found) {
        return found
      }
    }
  }

  return undefined
}

const findKeyByPath = (items: MenuItem[], targetPath: string): string | undefined => {
  for (const item of items) {
    if (item.path === targetPath) {
      return item.key
    }

    if (item.children) {
      const found = findKeyByPath(item.children, targetPath)
      if (found) {
        return found
      }
    }
  }

  return undefined
}

const updateSelectedKeys = () => {
  const key = findKeyByPath(menuItems.value, route.path)
  selectedKeys.value = key ? [key] : []
}

const handleMenuClick = (info: { key: string }) => {
  const targetPath = findPathByKey(menuItems.value, info.key)
  if (targetPath && targetPath !== route.path) {
    router.push(targetPath)
  }
}

const handleOpenChange = (keys: string[]) => {
  openKeys.value = keys
}

const handleLogout = () => {
  Modal.confirm({
    title: '确认退出',
    content: '您确定要退出当前账号吗？',
    okText: '退出',
    cancelText: '取消',
    onOk: () => {
      clearSession()
      router.push('/login')
    }
  })
}

const fetchUnreadCount = async () => {
  try {
    const res = await api.get('/notifications/unread-count')
    unreadCount.value = res.data.count || 0
  } catch (err) {
    console.warn('fetchUnreadCount failed:', err)
  }
}

const fetchNotifications = async () => {
  try {
    const res = await api.get('/notifications?page=0&size=5')
    notifications.value = res.data.content || []
  } catch (err) {
    console.warn('fetchNotifications failed:', err)
    notifications.value = []
  }
}

const markAsRead = async (id: number) => {
  try {
    await api.put(`/notifications/${id}/read`)
    await fetchUnreadCount()
    await fetchNotifications()
  } catch (err) {
    console.warn('markAsRead failed:', err)
  }
}

const markAllAsRead = async () => {
  try {
    await api.put('/notifications/read-all')
    await fetchUnreadCount()
    await fetchNotifications()
  } catch (err) {
    console.warn('markAllAsRead failed:', err)
  }
}

watch(() => route.path, () => {
  updateSelectedKeys()
})

let pollInterval: ReturnType<typeof setInterval> | null = null

const onVisibilityChange = () => {
  if (!document.hidden) {
    fetchUnreadCount()
  }
}

onMounted(async () => {
  username.value = getUsername() || 'Admin'
  const roleCode = getRoles()[0] || ''
  roleLabel.value = ({ admin: '\u7ba1\u7406\u5458', site_admin: '\u7ad9\u70b9\u7ba1\u7406\u5458', editor: '\u7f16\u8f91', reviewer: '\u5ba1\u6838\u5458', publisher: '\u53d1\u5e03\u5458' } as Record<string, string>)[roleCode] || '\u7528\u6237'
  await fetchMenus()
  updateSelectedKeys()
  await fetchUnreadCount()
  await fetchNotifications()

  pollInterval = setInterval(fetchUnreadCount, 30000)
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onUnmounted(() => {
  if (pollInterval) {
    clearInterval(pollInterval)
  }
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <div class="app-layout">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="sidebar-header">
        <div class="logo">
          <div class="logo-icon">
            <svg width="20" height="20" viewBox="0 0 32 32" fill="none">
              <rect width="32" height="32" rx="8" fill="#2563eb"/>
              <path d="M8 16C8 11.5817 11.5817 8 16 8V8C20.4183 8 24 11.5817 24 16V24H16C11.5817 24 8 20.4183 8 16V16Z" fill="white"/>
              <circle cx="16" cy="16" r="4" fill="#2563eb"/>
            </svg>
          </div>
          <span v-if="!collapsed" class="logo-text">GovCMS</span>
        </div>
      </div>

      <nav class="sidebar-nav">
        <a-menu
          v-if="!collapsed"
          v-model:selectedKeys="selectedKeys"
          v-model:openKeys="openKeys"
          mode="inline"
          :inline-collapsed="collapsed"
          :items="menuItems"
          @click="handleMenuClick"
          @openChange="handleOpenChange"
        />

        <div v-else class="collapsed-menu">
          <div
            v-for="item in menuItems"
            :key="item.key"
            class="nav-item"
            :class="{ active: selectedKeys.includes(item.key) }"
            @click="handleMenuClick({ key: item.key })"
          >
            <component :is="item.icon" class="nav-icon" v-if="item.icon" />
          </div>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info" :class="{ collapsed }">
          <div class="user-avatar">A</div>
          <div v-if="!collapsed" class="user-detail">
            <span class="user-name">{{ username }}</span>
            <span class="user-role">{{ roleLabel }}</span>
          </div>
          <LogoutOutlined v-if="!collapsed" class="logout-icon" @click="handleLogout" />
        </div>
      </div>
    </aside>

    <!-- 主内容区 -->
    <div class="main-wrapper" :class="{ collapsed }">
      <!-- 顶部导航栏 -->
      <header class="top-header">
        <div class="header-left">
          <MenuFoldOutlined v-if="collapsed" class="trigger" @click="collapsed = !collapsed" />
          <MenuUnfoldOutlined v-else class="trigger" @click="collapsed = !collapsed" />
        </div>

        <div class="header-right">
          <div class="search-trigger">
            <SearchOutlined />
            <span>搜索...</span>
            <kbd>⌘K</kbd>
          </div>
          <div class="header-action">
            <a-popover
              v-model:open="notificationVisible"
              placement="bottomRight"
              trigger="click"
              @open-change="(visible: boolean) => { if (visible) fetchNotifications() }"
            >
              <template #content>
                <div style="width: 320px;">
                  <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                    <span style="font-weight: 600;">消息通知</span>
                    <a v-if="unreadCount > 0" @click="markAllAsRead">全部已读</a>
                  </div>
                  <div v-if="notifications.length === 0" style="color: #94a3b8; text-align: center; padding: 16px;">暂无消息</div>
                  <div
                    v-for="n in notifications"
                    :key="n.id"
                    style="padding: 8px 0; border-bottom: 1px solid #f1f5f9; cursor: pointer;"
                    @click="!n.read && markAsRead(n.id)"
                  >
                    <div style="display: flex; justify-content: space-between;">
                      <span :style="{ fontWeight: n.read ? 'normal' : '600', color: '#1e293b' }">{{ n.title }}</span>
                      <span style="font-size: 12px; color: #94a3b8;">{{ n.createdAt }}</span>
                    </div>
                    <div style="font-size: 13px; color: #64748b; margin-top: 4px;">{{ n.content }}</div>
                  </div>
                </div>
              </template>
              <a-badge :count="unreadCount" :offset="[-4, 4]">
                <BellOutlined />
              </a-badge>
            </a-popover>
          </div>
        </div>
      </header>

      <!-- 页面内容 -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background: #f8fafc;
}

.sidebar {
  width: 260px;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 100;
}

.sidebar.collapsed {
  width: 72px;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid #f1f5f9;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  letter-spacing: -0.5px;
}

.sidebar-nav {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

/* Ant Design Menu 闂佸搫绉撮崲鑼閿涘嫭鍟洪柛鈩冪懄绾?*/
.sidebar-nav :deep(.ant-menu) {
  border: none;
  background: transparent;
}

.sidebar-nav :deep(.ant-menu-item),
.sidebar-nav :deep(.ant-menu-submenu-title) {
  margin: 2px 0;
  border-radius: 8px;
}

.sidebar-nav :deep(.ant-menu-item:hover),
.sidebar-nav :deep(.ant-menu-submenu-title:hover) {
  background: #f1f5f9;
}

.sidebar-nav :deep(.ant-menu-item-selected) {
  background: #eff6ff !important;
  color: #2563eb !important;
}

.sidebar-nav :deep(.ant-menu-inline) {
  background: transparent;
}

.sidebar-nav :deep(.ant-menu-sub.ant-menu-inline) {
  background: transparent;
}

.sidebar-nav :deep(.ant-menu-item-selected)::after {
  display: none;
}

/* 闂佺鍩栭敋鐟滅増妫冮幃鈺呮嚋绾版ê浜惧ù锝堟缂嶅懘鏌?*/
.collapsed-menu {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 8px;
}

.collapsed-menu .nav-item {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #64748b;
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.15s;
}

.collapsed-menu .nav-item:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.collapsed-menu .nav-item.active {
  background: #eff6ff;
  color: #2563eb;
}

.nav-icon {
  font-size: 18px;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #f1f5f9;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}

.user-info:hover {
  background: #f1f5f9;
}

.user-info.collapsed {
  justify-content: center;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.user-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  white-space: nowrap;
}

.user-role {
  font-size: 12px;
  color: #94a3b8;
}

.logout-icon {
  color: #94a3b8;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s;
}

.logout-icon:hover {
  background: #fef2f2;
  color: #ef4444;
}

.main-wrapper {
  flex: 1;
  margin-left: 260px;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left 0.2s ease;
}

.main-wrapper.collapsed {
  margin-left: 72px;
}

.top-header {
  height: calc(64px + env(safe-area-inset-top, 0px));
  padding-top: env(safe-area-inset-top, 0px);
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 24px;
  padding-right: 24px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.header-left {
  display: flex;
  align-items: center;
}

.trigger {
  font-size: 18px;
  color: #64748b;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.15s;
}

.trigger:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f1f5f9;
  border-radius: 8px;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.search-trigger:hover {
  background: #e2e8f0;
}

.search-trigger kbd {
  background: #fff;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  border: 1px solid #e2e8f0;
}

.header-action {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  color: #64748b;
  cursor: pointer;
  position: relative;
  transition: all 0.15s;
}

.header-action:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.notification-dot {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  border: 2px solid #fff;
}

.main-content {
  flex: 1;
  padding: 24px;
  overflow-x: hidden;
}

@media (max-width: 768px) {
  .sidebar {
    width: 72px;
  }
  
  .sidebar .logo-text {
    display: none;
  }
  
  .main-wrapper {
    margin-left: 72px;
  }
}
</style>



