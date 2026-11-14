<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { formatUserDisplayName, userInitials } from '@/utils/userDisplay'

defineProps<{
  collapsed?: boolean
}>()

const authStore = useAuthStore()

const displayName = computed(() => formatUserDisplayName(authStore.user))
const initials = computed(() => userInitials(authStore.user))
</script>

<template>
  <div v-if="authStore.user" class="sidebar-user-row" :class="{ 'flex-col': collapsed }">
    <RouterLink
      to="/profile"
      class="sidebar-avatar"
      :title="displayName"
      :aria-label="`Profile: ${displayName}`"
    >
      {{ initials }}
    </RouterLink>
    <RouterLink
      v-if="!collapsed"
      to="/profile"
      class="sidebar-user-name"
      :title="displayName"
    >
      {{ displayName }}
    </RouterLink>
    <RouterLink
      to="/settings"
      class="sidebar-gear"
      aria-label="Settings"
      title="Settings"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path
          d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <path
          d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z"
          stroke="currentColor"
          stroke-width="1.75"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </RouterLink>
  </div>
</template>
