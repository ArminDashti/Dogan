<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchSettings, updateSettings } from '@/services/api/settings'
import { getErrorMessage } from '@/services/api/errors'
import type { AppSettings, Device } from '@/types/api'

const devices = ref<Device[]>([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

const form = reactive<AppSettings>({
  notificationsEnabled: true,
  temperatureUnit: 'celsius',
  noiseAlertThresholdDb: 70,
  defaultDeviceId: undefined,
})

async function loadPageData(): Promise<void> {
  loading.value = true
  errorMessage.value = null
  successMessage.value = null

  try {
    const [settings, deviceList] = await Promise.all([fetchSettings(), fetchDevices()])
    Object.assign(form, settings)
    devices.value = deviceList
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to load settings.')
  } finally {
    loading.value = false
  }
}

async function handleSave(): Promise<void> {
  saving.value = true
  errorMessage.value = null
  successMessage.value = null

  try {
    const updated = await updateSettings({ ...form })
    Object.assign(form, updated)
    successMessage.value = 'Settings saved successfully.'
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to save settings.')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadPageData()
})
</script>

<template>
  <AppLayout title="Settings" subtitle="Manage alerts and device preferences.">
    <div class="settings-panel space-y-8">
      <ErrorAlert v-if="errorMessage" title="Settings error" :message="errorMessage" />

      <div v-if="successMessage" class="alert-success" role="status">
        {{ successMessage }}
      </div>

      <div v-if="loading" class="yoda-card px-5 py-12 text-center text-[var(--muted)]">
        Loading settings…
      </div>

      <form v-else @submit.prevent="handleSave">
        <section class="mb-8">
          <h2 class="settings-section-title">Notifications</h2>
          <div class="settings-card">
            <div class="settings-row">
              <div class="min-w-0">
                <p class="settings-row-label">Enable alerts</p>
                <p class="settings-row-help">Receive alerts when noise crosses your threshold.</p>
              </div>
              <div class="settings-row-control">
                <input
                  v-model="form.notificationsEnabled"
                  type="checkbox"
                  class="settings-switch"
                  aria-label="Enable notifications"
                />
              </div>
            </div>
          </div>
        </section>

        <section class="mb-8">
          <h2 class="settings-section-title">Devices & units</h2>
          <div class="settings-card">
            <div class="settings-row settings-row--stacked">
              <div>
                <p class="settings-row-label">Temperature unit</p>
                <p class="settings-row-help">Display Celsius or Fahrenheit in metrics views.</p>
              </div>
              <div class="settings-row-control">
                <select v-model="form.temperatureUnit" class="yoda-select">
                  <option value="celsius">Celsius</option>
                  <option value="fahrenheit">Fahrenheit</option>
                </select>
              </div>
            </div>

            <div class="settings-row settings-row--stacked">
              <div>
                <p class="settings-row-label">Noise alert threshold (dB)</p>
                <p class="settings-row-help">Trigger notifications when ambient noise exceeds this level.</p>
              </div>
              <div class="settings-row-control">
                <input
                  v-model.number="form.noiseAlertThresholdDb"
                  type="number"
                  min="0"
                  max="140"
                  step="1"
                  class="yoda-input"
                />
              </div>
            </div>

            <div class="settings-row settings-row--stacked">
              <div>
                <p class="settings-row-label">Default device</p>
                <p class="settings-row-help">Pre-select this device on dashboard and stream pages.</p>
              </div>
              <div class="settings-row-control">
                <select v-model="form.defaultDeviceId" class="yoda-select">
                  <option :value="undefined">None</option>
                  <option v-for="device in devices" :key="device.id" :value="device.id">
                    {{ device.name }}
                  </option>
                </select>
              </div>
            </div>

            <div class="settings-footer-actions">
              <button type="submit" class="yoda-btn yoda-btn--primary cursor-pointer" :disabled="saving">
                <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="1.75" />
                  <path d="M17 21v-8H7v8" stroke="currentColor" stroke-width="1.75" />
                  <path d="M7 3v5h8" stroke="currentColor" stroke-width="1.75" />
                </svg>
                {{ saving ? 'Saving…' : 'Save settings' }}
              </button>
            </div>
          </div>
        </section>
      </form>
    </div>
  </AppLayout>
</template>
