<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchSettings, SENSOR_INTERVAL_DEFAULTS, updateSettings } from '@/services/api/settings'
import { getErrorMessage } from '@/services/api/errors'
import type { AppSettings, Device } from '@/types/api'

const devices = ref<Device[]>([])
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

interface IntervalField {
  key:
    | 'imageIntervalSec'
    | 'gpsIntervalSec'
    | 'batteryIntervalSec'
    | 'temperatureIntervalSec'
    | 'noiseIntervalSec'
    | 'joltIntervalSec'
    | 'networkTypeIntervalSec'
    | 'sim1SignalIntervalSec'
    | 'sim2SignalIntervalSec'
    | 'latencyIntervalSec'
  label: string
  help?: string
}

const intervalGroups: { title: string; fields: IntervalField[] }[] = [
  {
    title: 'Camera & location',
    fields: [
      { key: 'imageIntervalSec', label: 'Camera image', help: 'How often the phone POSTs a frame.' },
      { key: 'gpsIntervalSec', label: 'GPS', help: 'How often the phone updates GPS.' },
    ],
  },
  {
    title: 'Power & thermal',
    fields: [
      { key: 'batteryIntervalSec', label: 'Battery level', help: 'How often the phone reports charge.' },
      { key: 'temperatureIntervalSec', label: 'Battery temperature', help: 'How often the phone reports temperature.' },
    ],
  },
  {
    title: 'Motion & cabin',
    fields: [
      { key: 'noiseIntervalSec', label: 'Noise', help: 'How often the phone updates cabin noise.' },
      { key: 'joltIntervalSec', label: 'Jolt', help: 'How often the phone reports smoothed motion (m/s²).' },
    ],
  },
  {
    title: 'Network',
    fields: [
      { key: 'networkTypeIntervalSec', label: 'Network type', help: 'How often the phone updates the network generation.' },
      { key: 'sim1SignalIntervalSec', label: 'SIM 1 signal', help: 'How often the phone samples SIM 1.' },
      { key: 'sim2SignalIntervalSec', label: 'SIM 2 signal', help: 'How often the phone samples SIM 2. Not sampled by the app yet.' },
      { key: 'latencyIntervalSec', label: 'Latency', help: 'How often the phone measures server latency.' },
    ],
  },
]

const form = reactive<AppSettings>({
  notificationsEnabled: true,
  temperatureUnit: 'celsius',
  noiseAlertThresholdDb: 70,
  defaultDeviceId: undefined,
  ...SENSOR_INTERVAL_DEFAULTS,
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
  <AppLayout title="Settings" subtitle="One device selection, display, alerts and sensor timing.">
    <div class="settings-panel">
      <ErrorAlert v-if="errorMessage" title="Settings error" :message="errorMessage" />

      <div v-if="successMessage" class="alert-success" role="status">
        {{ successMessage }}
      </div>

      <div v-if="loading" class="yoda-card px-5 py-12 text-center text-[var(--muted)]">
        Loading settings…
      </div>

      <form v-else class="settings-form" @submit.prevent="handleSave">
        <section>
          <h2 class="settings-section-title">Device</h2>
          <div class="settings-card">
            <div class="settings-row settings-row--stacked">
              <div>
                <p class="settings-row-label">Selected device</p>
                <p class="settings-row-help">
                  Used by Device, Stream, Gallery and Metrics. Picking a row on Device changes it too.
                </p>
              </div>
              <div class="settings-row-control">
                <select v-model="form.defaultDeviceId" class="yoda-select">
                  <option :value="undefined">Automatic (first online device)</option>
                  <option v-for="device in devices" :key="device.id" :value="device.id">
                    {{ device.name || device.id }}{{ device.status ? ` · ${device.status}` : '' }}
                  </option>
                </select>
              </div>
            </div>
          </div>
        </section>

        <section>
          <h2 class="settings-section-title">Display</h2>
          <div class="settings-card">
            <div class="settings-row">
              <div>
                <p class="settings-row-label">Temperature unit</p>
                <p class="settings-row-help">Celsius or Fahrenheit in readings and charts.</p>
              </div>
              <div class="settings-row-control">
                <select v-model="form.temperatureUnit" class="yoda-select">
                  <option value="celsius">Celsius</option>
                  <option value="fahrenheit">Fahrenheit</option>
                </select>
              </div>
            </div>
          </div>
        </section>

        <section>
          <h2 class="settings-section-title">Alerts</h2>
          <div class="settings-card">
            <div class="settings-row">
              <div>
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

            <div class="settings-row">
              <div>
                <p class="settings-row-label">Noise alert threshold</p>
                <p class="settings-row-help">Trigger notifications above this cabin level (dB).</p>
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
          </div>
        </section>

        <section>
          <h2 class="settings-section-title">Sensor intervals</h2>
          <p class="settings-section-hint">Seconds between phone updates for each sensor.</p>

          <div class="settings-groups">
            <div v-for="group in intervalGroups" :key="group.title" class="settings-card">
              <p class="settings-group-title">{{ group.title }}</p>
              <div v-for="field in group.fields" :key="field.key" class="settings-row">
                <div class="min-w-0">
                  <p class="settings-row-label">{{ field.label }}</p>
                  <p v-if="field.help" class="settings-row-help">{{ field.help }}</p>
                </div>
                <div class="settings-row-control">
                  <input
                    v-model.number="form[field.key]"
                    type="number"
                    min="1"
                    max="3600"
                    step="1"
                    class="yoda-input settings-interval-input"
                    :aria-label="`${field.label} interval in seconds`"
                  />
                </div>
              </div>
            </div>
          </div>
        </section>

        <div class="settings-save-bar">
          <button type="submit" class="yoda-btn yoda-btn--primary cursor-pointer" :disabled="saving">
            <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="1.75" />
              <path d="M17 21v-8H7v8" stroke="currentColor" stroke-width="1.75" />
              <path d="M7 3v5h8" stroke="currentColor" stroke-width="1.75" />
            </svg>
            {{ saving ? 'Saving…' : 'Save settings' }}
          </button>
        </div>
      </form>
    </div>
  </AppLayout>
</template>

<style scoped>
.settings-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding-bottom: 4.5rem;
}

.settings-section-hint {
  margin: -0.25rem 0 0.5rem;
  font-size: 0.8125rem;
  color: var(--muted);
}

.settings-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 0.75rem;
}

.settings-group-title {
  margin: 0;
  padding: 0.55rem 0.9rem;
  border-bottom: 1px solid var(--border);
  font-size: 0.75rem;
  font-weight: 650;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--muted);
}

.settings-interval-input {
  width: 5.5rem;
}

.settings-save-bar {
  position: sticky;
  bottom: 0;
  z-index: 20;
  display: flex;
  justify-content: flex-end;
  padding: 0.75rem 0;
  background: linear-gradient(to top, var(--bg) 65%, transparent);
}
</style>
