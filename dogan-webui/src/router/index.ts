import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/device',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/device',
      name: 'device',
      component: () => import('@/views/DeviceView.vue'),
    },
    {
      path: '/dashboard',
      redirect: '/device',
    },
    {
      path: '/stream',
      name: 'stream',
      component: () => import('@/views/StreamView.vue'),
    },
    {
      path: '/gallery',
      name: 'gallery',
      component: () => import('@/views/GalleryView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
    },
    {
      path: '/logs',
      name: 'logs',
      component: () => import('@/views/LogsView.vue'),
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
    },
    {
      path: '/metrics',
      name: 'metrics',
      component: () => import('@/views/MetricsView.vue'),
    },
    {
      path: '/metrics/:metric',
      redirect: '/metrics',
    },
    {
      path: '/devices/:id/metrics',
      name: 'device-metrics',
      redirect: '/metrics',
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/device',
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (!authStore.initialized) {
    await authStore.initializeSession()
  }

  const isPublicRoute = Boolean(to.meta.public)

  if (!authStore.isAuthenticated && !isPublicRoute) {
    return {
      name: 'login',
      query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined,
    }
  }

  if (authStore.isAuthenticated && to.name === 'login') {
    return { name: 'device' }
  }

  return true
})

export default router
