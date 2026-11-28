<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SidebarUserProfile from '@/components/SidebarUserProfile.vue'
import { useSidebarCollapsed } from '@/composables/useSidebarCollapsed'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { collapsed, toggleCollapsed } = useSidebarCollapsed()

const navigation = [
  { label: 'Device', to: '/device', icon: 'device' as const },
  { label: 'Stream', to: '/stream', icon: 'stream' as const },
  { label: 'Gallery', to: '/gallery', icon: 'gallery' as const },
  { label: 'Logs', to: '/logs', icon: 'logs' as const },
  { label: 'Metrics', to: '/metrics', icon: 'metrics' as const },
  { label: 'Settings', to: '/settings', icon: 'settings' as const },
]

const activePath = computed(() => route.path)

function isActive(to: string): boolean {
  if (to === '/metrics') {
    return activePath.value.startsWith('/metrics')
  }
  return activePath.value === to || activePath.value.startsWith(`${to}/`)
}

async function handleLogout(): Promise<void> {
  await authStore.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <aside
    class="app-sidebar flex shrink-0 flex-col"
    :class="{ 'app-sidebar--collapsed': collapsed }"
  >
    <div class="flex items-center gap-2 border-b border-[var(--border)] px-3 py-4">
      <div class="sidebar-brand-row flex min-w-0 flex-1 items-center gap-2.5 px-1">
        <span class="brand-mark" aria-hidden="true">P</span>
        <div v-if="!collapsed" class="min-w-0">
          <p class="page-kicker !tracking-[0.12em]">Dogan</p>
          <p class="sidebar-brand-sub truncate text-sm font-semibold text-[var(--text)]">
            Control Center
          </p>
        </div>
      </div>
      <button
        type="button"
        class="flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-[6px] text-[var(--muted)] transition hover:bg-[var(--surface)] hover:text-[var(--text)]"
        :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        :title="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        @click="toggleCollapsed"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            v-if="!collapsed"
            d="M15 18l-6-6 6-6"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <path
            v-else
            d="M9 18l6-6-6-6"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
    </div>

    <nav class="flex-1 space-y-0.5 p-3" aria-label="Main">
      <div v-for="item in navigation" :key="item.to">
        <RouterLink
          :to="item.to"
          class="nav-link"
          :class="{ 'router-link-active': isActive(item.to) }"
          :title="collapsed ? item.label : undefined"
        >
          <svg class="h-[18px] w-[18px] shrink-0" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <template v-if="item.icon === 'device'">
              <rect x="7" y="2.5" width="10" height="19" rx="2" stroke="currentColor" stroke-width="1.75" />
              <path d="M10 5.5h4" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
              <circle cx="12" cy="18.5" r="1" fill="currentColor" />
            </template>
            <template v-else-if="item.icon === 'stream'">
              <path d="M5 5l12 7-12 7V5Z" stroke="currentColor" stroke-width="1.75" stroke-linejoin="round" />
            </template>
            <template v-else-if="item.icon === 'gallery'">
              <rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="1.75" />
              <circle cx="8.5" cy="10" r="1.5" fill="currentColor" />
              <path d="M21 16l-5-5L8 19" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
            </template>
            <template v-else-if="item.icon === 'settings'">
              <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.75" />
              <path
                d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
                stroke="currentColor"
                stroke-width="1.75"
                stroke-linecap="round"
              />
            </template>
            <template v-else-if="item.icon === 'logs'">
              <path
                d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"
                stroke="currentColor"
                stroke-width="1.75"
                stroke-linejoin="round"
              />
              <path
                d="M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v0a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2Z"
                stroke="currentColor"
                stroke-width="1.75"
              />
              <path d="M9 12h6M9 16h6" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
            </template>
            <template v-else>
              <path d="M4 19V5" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
              <path d="M4 19h16" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
              <path d="M8 17V11" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
              <path d="M12 17V7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
              <path d="M16 17v-4" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
            </template>
          </svg>
          <span class="nav-link-label">{{ item.label }}</span>
        </RouterLink>
      </div>
    </nav>

    <div class="mt-auto">
      <button
        type="button"
        class="mx-3 mb-2 cursor-pointer"
        :class="collapsed ? 'flex h-9 w-9 items-center justify-center rounded-[6px] border border-[var(--border)] text-[var(--muted)] hover:bg-[var(--surface)] hover:text-[var(--text)]' : 'yoda-btn yoda-btn--outline w-[calc(100%-1.5rem)]'"
        :disabled="authStore.loading"
        :aria-label="collapsed ? 'Sign out' : undefined"
        :title="collapsed ? 'Sign out' : undefined"
        @click="handleLogout"
      >
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" stroke="currentColor" stroke-width="1.75" />
          <path d="M16 17l5-5-5-5" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
          <path d="M21 12H9" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
        </svg>
        <span v-if="!collapsed">Sign out</span>
      </button>
      <SidebarUserProfile :collapsed="collapsed" />
    </div>
  </aside>
</template>
