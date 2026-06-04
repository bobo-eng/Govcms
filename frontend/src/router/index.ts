import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import MainLayout from '../components/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'
import Users from '../views/Users.vue'
import Roles from '../views/Roles.vue'
import Permissions from '../views/Permissions.vue'
import Menus from '../views/Menus.vue'
import Content from '../views/Content.vue'
import Review from '../views/Review.vue'
import PublishCenter from '../views/PublishCenter.vue'
import Categories from '../views/Categories.vue'
import Templates from '../views/Templates.vue'
import Sites from '../views/Sites.vue'
import NavigationManagement from '../views/NavigationManagement.vue'
import Topics from '../views/Topics.vue'
import Media from '../views/Media.vue'
import SearchOps from '../views/SearchOps.vue'
import AuditLogs from '../views/AuditLogs.vue'
import { clearSession, getToken, hasStoredPermissions } from '../utils/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'Login', component: Login },
    {
      path: '/',
      component: MainLayout,
      meta: { requiresAuth: true },
      children: [
        { path: 'dashboard', name: 'Dashboard', component: Dashboard },
        { path: 'system/users', name: 'Users', component: Users },
        { path: 'system/roles', name: 'Roles', component: Roles },
        { path: 'system/permissions', name: 'Permissions', component: Permissions },
        { path: 'system/menus', name: 'Menus', component: Menus },
        { path: 'content', name: 'Content', component: Content },
        { path: 'content/articles', name: 'Articles', component: Content },
        { path: 'content/review', name: 'Review', component: Review },
        { path: 'content/publish', name: 'PublishCenter', component: PublishCenter },
        { path: 'publish/tasks', name: 'PublishTasks', component: () => import('../views/PublishTasks.vue') },
        { path: 'content/categories', name: 'Categories', component: Categories },
        { path: 'content/templates', name: 'Templates', component: Templates },
        { path: 'content/navigation', name: 'NavigationManagement', component: NavigationManagement },
        { path: 'content/topics', name: 'Topics', component: Topics },
        { path: 'site-ops/sites', name: 'Sites', component: Sites },
        { path: 'site-ops/media', name: 'Media', component: Media },
        { path: 'site-ops/search-ops', name: 'SearchOps', component: SearchOps },
        { path: 'system/audit-logs', name: 'AuditLogs', component: AuditLogs }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = getToken()
  const permissionCacheReady = hasStoredPermissions()

  if (to.meta.requiresAuth) {
    if (!token) {
      next('/login')
      return
    }

    if (!permissionCacheReady) {
      clearSession()
      next('/login')
      return
    }
  }

  if (to.path === '/login' && token) {
    if (!permissionCacheReady) {
      clearSession()
      next()
      return
    }

    next('/dashboard')
    return
  }

  next()
})

export default router
