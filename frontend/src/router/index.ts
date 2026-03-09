import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import MainLayout from '../components/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'
import Users from '../views/Users.vue'
import Roles from '../views/Roles.vue'
import Permissions from '../views/Permissions.vue'
import Menus from '../views/Menus.vue'
import Content from '../views/Content.vue'

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
        { path: 'content', name: 'Content', component: Content }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router