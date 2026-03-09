import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import MainLayout from '../components/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'
import Users from '../views/Users.vue'
import Roles from '../views/Roles.vue'
import Permissions from '../views/Permissions.vue'
import Menus from '../views/Menus.vue'
import Content from '../views/Content.vue'
import Sites from '../views/Sites.vue'
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
        { path: 'users', name: 'Users', component: Users },
        { path: 'roles', name: 'Roles', component: Roles },
        { path: 'permissions', name: 'Permissions', component: Permissions },
        { path: 'menus', name: 'Menus', component: Menus },
        { path: 'content', name: 'Content', component: Content },
        { path: 'sites', name: 'Sites', component: Sites }
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
