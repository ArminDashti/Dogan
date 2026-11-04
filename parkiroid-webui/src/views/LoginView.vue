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
  <div class="flex min-h-screen items-center justify-center bg-[var(--bg)] px-4">
    <div class="yoda-card w-full max-w-md p-8 shadow-lg">
      <div class="mb-8 text-center">
        <div
          class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-[var(--bg)] text-lg font-bold text-[var(--accent)]"
          aria-hidden="true"
        >
          P
        </div>
        <p class="text-xs font-semibold uppercase tracking-[0.16em] text-[var(--muted)]">Parkiroid</p>
        <h1 class="mt-2 text-2xl font-semibold text-[var(--text)]">Sign in</h1>
        <p class="mt-2 text-sm text-[var(--muted)]">Access devices, streams, and metrics.</p>
      </div>

      <form class="space-y-4" @submit.prevent="handleSubmit">
        <ErrorAlert v-if="errorMessage" title="Sign in failed" :message="errorMessage" />

        <label class="block">
          <span class="mb-1.5 block text-sm font-medium text-[var(--text)]">Email or username</span>
          <input
            v-model="email"
            type="text"
            autocomplete="username"
            required
            class="yoda-input"
            placeholder="admin"
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
