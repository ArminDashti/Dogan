<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchImages } from '@/services/api/gallery'
import { getErrorMessage } from '@/services/api/errors'
import type { GalleryImage } from '@/types/api'

const images = ref<GalleryImage[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

onMounted(async () => {
  loading.value = true
  errorMessage.value = null

  try {
    images.value = await fetchImages()
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to load gallery images.')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <AppLayout title="Image Gallery" subtitle="Captured snapshots from your devices.">
    <div class="space-y-6">
      <ErrorAlert v-if="errorMessage" title="Gallery unavailable" :message="errorMessage" />

      <div v-if="loading" class="yoda-card px-5 py-12 text-center text-[var(--muted)]">
        Loading images…
      </div>

      <div
        v-else-if="images.length === 0"
        class="yoda-card border-dashed px-5 py-12 text-center text-[var(--muted)]"
      >
        No images yet. Once your backend starts storing captures, they will appear here.
      </div>

      <div v-else class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <article v-for="image in images" :key="image.id" class="yoda-card overflow-hidden">
          <img
            :src="image.thumbnailUrl ?? image.url"
            :alt="image.caption ?? 'Captured image'"
            class="aspect-video w-full object-cover"
            loading="lazy"
          />
          <div class="space-y-1 px-4 py-3">
            <p class="font-medium text-[var(--text)]">{{ image.caption ?? 'Untitled capture' }}</p>
            <p class="text-xs text-[var(--muted)]">{{ formatDate(image.capturedAt) }}</p>
          </div>
        </article>
      </div>
    </div>
  </AppLayout>
</template>
