<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
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
  CloudOutlined
} from '@ant-design/icons-vue'
import { Menu } from 'ant-design-vue'
import axios from 'axios'

interface MenuItem {
  key: string
  label: string
  icon?: any
  path?: string
  children?: MenuItem[]
}

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const loading = ref(true)

// Menu state
const selectedKeys = ref<string[]>([])
const openKeys = ref<string[]>([])

// Icon mapping
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
  CloudOutlined
}

// Convert backend menu tree to Ant Design format
const convertToAntMenu = (menus: any[]): MenuItem[] => {
  return menus.map(menu => {
    const item: MenuItem = {
      key: menu.path ? menu.path.replace('/', '') : `menu-${menu.id}`,
      label: menu.name,
      icon: iconMap[menu.icon] ? h(iconMap[menu.icon]) : undefined,
      path: menu.path
    }
    
    // Process children
    if (menu.children && menu.children.length > 0) {
      item.children = convertToAntMenu(menu.children)
    }
    
    return item
  })
}

// Menu items for a-menu
const menuItems = ref<MenuItem[]>([])

// Fetch user menus from API
const fetchMenus = async () => {
  loading.value = true
  try {
    const token = localStorage.getItem('token')
    const res = await axios.get('/api/menus/user', {
      headers: { Authorization: `Bearer ${token}` }
    })
    
    // Convert backend tree to Ant Design format
    menuItems.value = convertToAntMenu(res.data || [])
    
    // Auto expand first level menus
    const firstLevelKeys = menuItems.value
      .filter(m => m.children && m.children.length > 0)
      .map(m => m.key)
    openKeys.value = firstLevelKeys
    
  } catch (e) {
    console.error('Failed to fetch menus:', e)
    menuItems.value = []
  } finally {
    loading.value = false
  }
}

// Handle menu click
const handleMenuClick = (info: { key: string }) => {
  const findPath = (items: MenuItem[], key: string): string | undefined => {
    for (const item of items) {
      if (item.key === key) return item.path
      if (item.children) {
        const found = findPath(item.children, key)
        if (found) return found
      }
    }
    return undefined
  }
  
  const path = findPath(menuItems.value, info.key)
  if (path && path !== '/') {
    router.push(path)
  }
}

// Handle submenu open/close
const handleOpenChange = (keys: string[]) => {
  openKeys.value = keys
}

// Compute selected key based on current route
const updateSelectedKeys = () => {
  const path = route.path
  const findKey = (items: MenuItem[]): string | undefined => {
    for (const item of items) {
      if (item.path === path) return item.key
      if (item.children) {
        const found = findKey(item.children)
        if (found) return found
      }
    }
    return undefined
  }
  const key = findKey(menuItems.value)
  if (key) {
    selectedKeys.value = [key]
  }
}

// Watch route changes
import { watch } from 'vue'
// ... existing imports

// Add this after the setup
watch(() => route.path, () => {
  updateSelectedKeys()
})

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  router.push('/login')
}

const username = localStorage.getItem('username') || 'Admin'

onMounted(() => {
  fetchMenus().then(() => {
    updateSelectedKeys()
  })
})
</script>

<template>
  <div class="app-layout">
    <!-- 左侧侧边栏 -->
    <aside class="sidebar" :class="{ collapsed }">
      <!-- Logo -->
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

      <!-- 菜单 - 使用 Ant Design Menu 组件 -->
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
        
        <!-- 折叠状态下的菜单 -->
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

      <!-- 底部用户 -->
      <div class="sidebar-footer">
        <div class="user-info" :class="{ collapsed }">
          <div class="user-avatar">A</div>
          <div v-if="!collapsed" class="user-detail">
            <span class="user-name">{{ username }}</span>
            <span class="user-role">管理员</span>
          </div>
          <LogoutOutlined v-if="!collapsed" class="logout-icon" @click="handleLogout" />
        </div>
      </div>
    </aside>

    <!-- 右侧主内容 -->
    <div class="main-wrapper" :class="{ collapsed }">
      <!-- 顶部导航 -->
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
            <BellOutlined />
            <span class="notification-dot"></span>
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

/* Ant Design Menu 样式覆盖 */
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

/* 折叠状态菜单 */
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
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
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
