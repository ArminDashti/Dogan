<script setup lang="ts">
import { computed } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { formatUserDisplayName, userInitials } from '@/utils/userDisplay'

const authStore = useAuthStore()

const displayName = computed(() => formatUserDisplayName(authStore.user))
const initials = computed(() => userInitials(authStore.user))
</script>

<template>
  <AppLayout title="Profile" subtitle="Your account and workspace identity.">
    <div class="settings-panel space-y-8">
      <section>
        <h2 class="settings-section-title">Account</h2>
        <div class="settings-card">
          <div class="settings-row">
            <div class="flex items-center gap-4">
              <span class="sidebar-avatar !h-12 !w-12 text-sm">{{ initials }}</span>
              <div>
                <p class="settings-row-label">{{ displayName }}</p>
                <p class="settings-row-help">{{ authStore.user?.email }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section>
        <h2 class="settings-section-title">About</h2>
        <div class="settings-card p-5 text-sm text-[var(--muted)]">
          <p>Dogan monitors edge devices, live streams, and telemetry from one control center.</p>
          <p class="mt-3">
            <a
              href="https://github.com/armindashti"
              target="_blank"
              rel="noopener noreferrer"
              class="text-[var(--text)] underline-offset-2 hover:underline"
            >
              GitHub — armindashti
            </a>
          </p>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
