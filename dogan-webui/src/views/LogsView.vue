<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchLoginLogs, fetchWebRtcConnections } from '@/services/api/logs'
import { getErrorMessage } from '@/services/api/errors'
import type { LoginLogEntry, WebRtcConnection } from '@/types/api'

const loginLogs = ref<LoginLogEntry[]>([])
const connections = ref<WebRtcConnection[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

function formatTimestamp(value: string | undefined): string {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function shortenUserAgent(value: string): string {
  const trimmed = value.trim()
  if (trimmed.length <= 96) {
    return trimmed || '--'
  }
  return `${trimmed.slice(0, 93)}…`
}

async function loadLogs(): Promise<void> {
  loading.value = true
  errorMessage.value = null

  try {
    const [signIns, sessions] = await Promise.all([fetchLoginLogs(80), fetchWebRtcConnections(80)])
    loginLogs.value = signIns
    connections.value = sessions
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to load logs.')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadLogs()
})
</script>

<template>
  <AppLayout title="Logs" subtitle="Sign-in audit and LiveKit / WebRTC session history.">
    <div class="space-y-6">
      <ErrorAlert v-if="errorMessage" title="Logs unavailable" :message="errorMessage" />

      <div v-if="loading" class="yoda-card px-5 py-12 text-center text-[var(--muted)]">
        Loading logs…
      </div>

      <template v-else>
        <section class="yoda-card overflow-hidden">
          <div class="border-b border-[var(--border)] px-5 py-4">
            <h2 class="text-base font-semibold tracking-tight text-[var(--text)]">Sign-in attempts</h2>
            <p class="text-sm text-[var(--muted)]">Recent web and admin logins recorded by the API.</p>
          </div>

          <div
            v-if="loginLogs.length === 0"
            class="px-5 py-10 text-center text-sm text-[var(--muted)]"
          >
            No sign-in attempts recorded yet.
          </div>

          <div v-else class="overflow-x-auto">
            <table class="logs-table w-full text-sm">
              <thead>
                <tr>
                  <th>When</th>
                  <th>User</th>
                  <th>IP</th>
                  <th>Result</th>
                  <th class="hidden lg:table-cell">Client</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="entry in loginLogs" :key="entry.id">
                  <td class="font-mono text-xs">{{ formatTimestamp(entry.attemptedAt) }}</td>
                  <td>{{ entry.username }}</td>
                  <td class="font-mono text-xs">{{ entry.ip }}</td>
                  <td>
                    <span class="status-pill" :class="entry.success ? 'text-emerald-400' : 'text-rose-400'">
                      {{ entry.success ? 'OK' : 'Failed' }}
                    </span>
                  </td>
                  <td class="hidden max-w-md truncate text-xs text-[var(--muted)] lg:table-cell">
                    {{ shortenUserAgent(entry.browserInfo) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section class="yoda-card overflow-hidden">
          <div class="border-b border-[var(--border)] px-5 py-4">
            <h2 class="text-base font-semibold tracking-tight text-[var(--text)]">Stream sessions</h2>
            <p class="text-sm text-[var(--muted)]">Recent WebRTC / LiveKit connections from devices and the web UI.</p>
          </div>

          <div
            v-if="connections.length === 0"
            class="px-5 py-10 text-center text-sm text-[var(--muted)]"
          >
            No WebRTC sessions recorded yet.
          </div>

          <div v-else class="overflow-x-auto">
            <table class="logs-table w-full text-sm">
              <thead>
                <tr>
                  <th>When</th>
                  <th>Device</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th class="hidden md:table-cell">Room / identity</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="connection in connections" :key="connection.id">
                  <td class="font-mono text-xs">{{ formatTimestamp(connection.connectedAt) }}</td>
                  <td class="font-mono text-xs">{{ connection.deviceId }}</td>
                  <td>{{ connection.role }}</td>
                  <td><span class="status-pill">{{ connection.status }}</span></td>
                  <td class="hidden font-mono text-xs text-[var(--muted)] md:table-cell">
                    {{ connection.room }} · {{ connection.identity }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </div>
  </AppLayout>
</template>

<style scoped>
.logs-table th {
  padding: 0.625rem 1.25rem;
  text-align: left;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.logs-table td {
  padding: 0.625rem 1.25rem;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

.logs-table tbody tr:last-child td {
  border-bottom: none;
}
</style>