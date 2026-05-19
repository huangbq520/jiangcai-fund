import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '../api/auth'

const routes = [
  {
    path: '/auth',
    component: () => import('../views/AuthView.vue'),
    meta: { guest: true }
  },
  {
    path: '/login',
    redirect: '/auth'
  },
  {
    path: '/register',
    redirect: '/auth'
  },
  {
    path: '/detail/:fundCode',
    component: () => import('../views/DashboardView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/',
    component: () => import('../views/DashboardView.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = getToken()
  if (to.meta.requiresAuth && !token) {
    next('/auth')
  } else if (to.meta.guest && token) {
    next('/')
  } else {
    next()
  }
})

export default router
