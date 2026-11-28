<script setup lang="ts">
import { computed } from 'vue'
import type { ChartPoint } from '@/types/api'

const props = defineProps<{
  title: string
  unit?: string
  today: ChartPoint[]
  yesterday: ChartPoint[]
  todayLabel?: string
  yesterdayLabel?: string
  min?: number
  max?: number
  colorByNetwork?: boolean
  emptyMessage?: string
  formatValue?: (value: number) => string
}>()

const WIDTH = 360
const HEIGHT = 150
const PAD_LEFT = 38
const PAD_RIGHT = 8
const PAD_TOP = 10
const PAD_BOTTOM = 24
const PLOT_WIDTH = WIDTH - PAD_LEFT - PAD_RIGHT
const PLOT_HEIGHT = HEIGHT - PAD_TOP - PAD_BOTTOM

const NETWORK_COLORS: Record<string, string> = {
  '2G': '#f59e0b',
  '3G': '#38bdf8',
  '4G': '#22c55e',
  '5G': '#a855f7',
  unknown: '#64748b',
}

function generationOf(networkType?: string): string {
  if (!networkType) {
    return 'unknown'
  }
  const value = networkType.toUpperCase()
  if (value.includes('5G') || value.includes('NR')) return '5G'
  if (value.includes('4G') || value.includes('LTE')) return '4G'
  if (value.includes('3G') || value.includes('UMTS') || value.includes('HSPA')) return '3G'
  if (value.includes('2G') || value.includes('EDGE') || value.includes('GSM')) return '2G'
  return 'unknown'
}

function networkColor(networkType?: string): string {
  return NETWORK_COLORS[generationOf(networkType)] ?? NETWORK_COLORS.unknown
}

const allPoints = computed(() => [...props.today, ...props.yesterday])

const domain = computed(() => {
  const values = allPoints.value.map((point) => point.value)
  let low = props.min
  let high = props.max
  if (low === undefined) {
    low = values.length > 0 ? Math.floor(Math.min(...values)) : 0
  }
  if (high === undefined) {
    high = values.length > 0 ? Math.ceil(Math.max(...values)) : 1
  }
  if (high - low < 1) {
    high = low + 1
  }
  return { low, high }
})

const hasData = computed(
  () => props.today.length > 0 || props.yesterday.length > 0,
)

const yTicks = computed(() => {
  const { low, high } = domain.value
  return [0, 0.5, 1].map((ratio) => {
    const value = low + (high - low) * ratio
    return { value, y: valueToY(value) }
  })
})

function valueToX(hour: number): number {
  const clamped = Math.max(0, Math.min(hour, 24))
  return PAD_LEFT + (clamped / 24) * PLOT_WIDTH
}

function valueToY(value: number): number {
  const { low, high } = domain.value
  const ratio = (value - low) / (high - low)
  return PAD_TOP + (1 - Math.max(0, Math.min(ratio, 1))) * PLOT_HEIGHT
}

function formatValue(value: number): string {
  if (props.formatValue) {
    return props.formatValue(value)
  }
  return Math.abs(value) >= 100 ? value.toFixed(0) : value.toFixed(1)
}

function seriesPath(points: ChartPoint[]): string {
  if (points.length === 0) {
    return ''
  }
  const sorted = [...points].sort((a, b) => a.hour - b.hour)
  return sorted
    .map((point, index) => {
      const command = index === 0 ? 'M' : 'L'
      return `${command}${valueToX(point.hour).toFixed(1)},${valueToY(point.value).toFixed(1)}`
    })
    .join(' ')
}

function segments(points: ChartPoint[]): { x1: number; y1: number; x2: number; y2: number; color: string }[] {
  const sorted = [...points].sort((a, b) => a.hour - b.hour)
  const result: { x1: number; y1: number; x2: number; y2: number; color: string }[] = []
  for (let index = 1; index < sorted.length; index += 1) {
    const previous = sorted[index - 1]
    const current = sorted[index]
    result.push({
      x1: valueToX(previous.hour),
      y1: valueToY(previous.value),
      x2: valueToX(current.hour),
      y2: valueToY(current.value),
      color: networkColor(current.networkType),
    })
  }
  return result
}

const todayLabel = computed(() => props.todayLabel ?? 'Today')
const yesterdayLabel = computed(() => props.yesterdayLabel ?? 'Yesterday')
const emptyMessage = computed(() => props.emptyMessage ?? 'No readings')
const latestTodayValue = computed(() => {
  const length = props.today.length
  return length > 0 ? props.today[length - 1].value : null
})
</script>

<template>
  <article class="metric-card yoda-card">
    <header class="metric-card__head">
      <div class="min-w-0">
        <h2 class="metric-card__title">{{ title }}</h2>
        <p v-if="unit" class="metric-card__unit">{{ unit }}</p>
      </div>
      <span v-if="latestTodayValue !== null" class="metric-card__latest font-mono">
        {{ formatValue(latestTodayValue) }}
      </span>
    </header>

    <div class="metric-card__legend">
      <span class="metric-legend-item">
        <span class="metric-legend-swatch metric-legend-swatch--today" />
        {{ todayLabel }}
      </span>
      <span class="metric-legend-item">
        <span class="metric-legend-swatch metric-legend-swatch--yesterday" />
        {{ yesterdayLabel }}
      </span>
      <template v-if="colorByNetwork">
        <span
          v-for="(color, generation) in NETWORK_COLORS"
          :key="generation"
          class="metric-legend-item"
        >
          <span class="metric-legend-swatch" :style="{ background: color }" />
          {{ generation === 'unknown' ? 'Unknown' : generation }}
        </span>
      </template>
    </div>

    <div v-if="!hasData" class="metric-card__empty">{{ emptyMessage }}</div>

    <svg v-else class="metric-chart" :viewBox="`0 0 ${WIDTH} ${HEIGHT}`" role="img" :aria-label="`${title} chart`">
      <line
        v-for="tick in yTicks"
        :key="`grid-${tick.value}`"
        :x1="PAD_LEFT"
        :x2="WIDTH - PAD_RIGHT"
        :y1="tick.y"
        :y2="tick.y"
        class="metric-chart__grid"
      />
      <text
        v-for="tick in yTicks"
        :key="`label-${tick.value}`"
        :x="PAD_LEFT - 6"
        :y="tick.y + 3"
        text-anchor="end"
        class="metric-chart__axis-label"
      >
        {{ formatValue(tick.value) }}
      </text>

      <text
        v-for="hour in [0, 6, 12, 18, 24]"
        :key="`hour-${hour}`"
        :x="valueToX(hour)"
        :y="HEIGHT - 6"
        text-anchor="middle"
        class="metric-chart__axis-label"
      >
        {{ String(hour).padStart(2, '0') }}
      </text>

      <template v-if="colorByNetwork">
        <line
          v-for="(segment, index) in segments(yesterday)"
          :key="`yesterday-seg-${index}`"
          :x1="segment.x1"
          :y1="segment.y1"
          :x2="segment.x2"
          :y2="segment.y2"
          :stroke="segment.color"
          class="metric-chart__line"
          stroke-opacity="0.4"
        />
        <line
          v-for="(segment, index) in segments(today)"
          :key="`today-seg-${index}`"
          :x1="segment.x1"
          :y1="segment.y1"
          :x2="segment.x2"
          :y2="segment.y2"
          :stroke="segment.color"
          class="metric-chart__line"
        />
      </template>
      <template v-else>
        <path :d="seriesPath(yesterday)" class="metric-chart__line metric-chart__line--yesterday" />
        <path :d="seriesPath(today)" class="metric-chart__line metric-chart__line--today" />
      </template>

      <template v-if="colorByNetwork">
        <circle
          v-for="(point, index) in yesterday"
          :key="`yesterday-dot-${index}`"
          :cx="valueToX(point.hour)"
          :cy="valueToY(point.value)"
          r="1.6"
          :fill="networkColor(point.networkType)"
          fill-opacity="0.45"
        />
        <circle
          v-for="(point, index) in today"
          :key="`today-dot-${index}`"
          :cx="valueToX(point.hour)"
          :cy="valueToY(point.value)"
          r="1.6"
          :fill="networkColor(point.networkType)"
        />
      </template>
    </svg>
  </article>
</template>

<style scoped>
.metric-card {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding: 0.9rem 1rem 1rem;
}

.metric-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
}

.metric-card__title {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 650;
  color: var(--text);
}

.metric-card__unit {
  margin: 0.1rem 0 0;
  font-size: 0.6875rem;
  color: var(--muted);
}

.metric-card__latest {
  font-size: 0.9375rem;
  font-weight: 650;
  color: var(--text);
}

.metric-card__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 0.75rem;
  font-size: 0.6875rem;
  color: var(--muted);
}

.metric-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.metric-legend-swatch {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 2px;
  background: var(--muted);
}

.metric-legend-swatch--today {
  background: var(--accent);
}

.metric-legend-swatch--yesterday {
  background: color-mix(in srgb, var(--muted) 55%, transparent);
}

.metric-card__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 110px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  color: var(--muted);
  font-size: 0.75rem;
}

.metric-chart {
  width: 100%;
  height: auto;
}

.metric-chart__grid {
  stroke: var(--border);
  stroke-width: 1;
  stroke-dasharray: 3 4;
}

.metric-chart__axis-label {
  fill: var(--muted);
  font-size: 8px;
  font-family: var(--font-mono);
}

.metric-chart__line {
  fill: none;
  stroke-width: 1.8;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.metric-chart__line--today {
  stroke: var(--accent);
}

.metric-chart__line--yesterday {
  stroke: color-mix(in srgb, var(--muted) 60%, transparent);
  stroke-dasharray: 4 3;
}
</style>
