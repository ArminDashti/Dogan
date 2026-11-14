<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { useAuthStore } from '@/stores/auth'
import { getErrorMessage } from '@/services/api/errors'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const errorMessage = ref<string | null>(null)

const redirectPath = computed(() => {
  const redirect = route.query.redirect
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/dashboard'
})

async function handleSubmit(): Promise<void> {
  errorMessage.value = null

  if (!email.value || !password.value) {
    errorMessage.value = 'Enter your username and password.'
    return
  }

  try {
    await authStore.login(email.value.trim(), password.value)
    await router.replace(redirectPath.value)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to sign in.')
  }
}
</script>

<template>
  <div class="login-shell">
    <div class="login-panel reveal">
      <div class="mb-8">
        <div class="flex items-center gap-3">
          <span class="brand-mark" aria-hidden="true">P</span>
          <div>
            <p class="page-kicker">Dogan</p>
            <p class="text-lg font-semibold tracking-tight text-[var(--text)]">Control Center</p>
          </div>
        </div>
        <h1 class="mt-6 text-3xl font-semibold tracking-tight text-[var(--text)]">Sign in</h1>
        <p class="mt-2 text-sm text-[var(--muted)]">
          Access devices, streams, and field telemetry.
        </p>
      </div>

      <form class="yoda-card space-y-4 p-6 sm:p-7" @submit.prevent="handleSubmit">
        <ErrorAlert v-if="errorMessage" title="Sign in failed" :message="errorMessage" />

        <label class="block">
          <span class="mb-1.5 block text-sm font-medium text-[var(--text)]">Email or username</span>
          <input
            v-model="email"
            type="text"
            autocomplete="username"
            required
            class="yoda-input"
            placeholder="admin@local.local"
          />
        </label>

        <label class="block">
          <span class="mb-1.5 block text-sm font-medium text-[var(--text)]">Password</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            required
            class="yoda-input"
            placeholder="••••••••"
          />
        </label>

        <button
          type="submit"
          class="yoda-btn yoda-btn--primary w-full cursor-pointer"
          :disabled="authStore.loading"
        >
          <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" stroke="currentColor" stroke-width="1.75" />
            <path d="M10 17l5-5-5-5" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
            <path d="M15 12H3" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" />
          </svg>
          {{ authStore.loading ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </div>
  </div>
</template>
