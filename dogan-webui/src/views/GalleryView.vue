<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchImageFullUrl, fetchImages, revokeGalleryObjectUrls } from '@/services/api/gallery'
import { getErrorMessage } from '@/services/api/errors'
import { formatTehranDateTime } from '@/utils/tehranTime'
import type { GalleryImage } from '@/types/api'

const images = ref<GalleryImage[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const lightboxImage = ref<GalleryImage | null>(null)
const lightboxFullUrl = ref<string | null>(null)
const lightboxLoading = ref(false)

function revokeLightboxUrl(): void {
  if (lightboxFullUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(lightboxFullUrl.value)
  }
  lightboxFullUrl.value = null
}

async function openLightbox(image: GalleryImage): Promise<void> {
  closeLightbox()
  lightboxImage.value = image
  lightboxLoading.value = true
  try {
    lightboxFullUrl.value = await fetchImageFullUrl(image.id)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to load full image.')
    closeLightbox()
  } finally {
    lightboxLoading.value = false
  }
}

function closeLightbox(): void {
  revokeLightboxUrl()
  lightboxImage.value = null
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

onBeforeUnmount(() => {
  revokeGalleryObjectUrls(images.value)
  closeLightbox()
})
</script>

<template>
  <AppLayout title="Image Gallery" subtitle="Captures from today. Older photos are removed automatically.">
    <div class="space-y-6">
      <ErrorAlert v-if="errorMessage" title="Gallery unavailable" :message="errorMessage" />

      <div v-if="loading" class="yoda-card px-5 py-12 text-center text-[var(--muted)]">
        Loading images…
      </div>

      <div
        v-else-if="images.length === 0"
        class="yoda-card border-dashed px-5 py-12 text-center text-[var(--muted)]"
      >
        No captures today yet. Photos from your device appear here as they arrive.
      </div>

      <div v-else class="gallery-grid">
        <button
          v-for="image in images"
          :key="image.id"
          type="button"
          class="gallery-thumb-button"
          :title="`${image.caption ?? 'Untitled capture'} — ${formatTehranDateTime(image.capturedAt)}`"
          :aria-label="`Open ${image.caption ?? 'captured image'} captured ${formatTehranDateTime(image.capturedAt)}`"
          @click="openLightbox(image)"
        >
          <img
            :src="image.thumbnailUrl ?? image.url"
            :alt="image.caption ?? 'Captured image'"
            class="gallery-thumb"
            loading="lazy"
          />
        </button>
      </div>
    </div>

    <div v-if="lightboxImage" class="gallery-lightbox" role="dialog" aria-modal="true" @click.self="closeLightbox">
      <div class="gallery-lightbox__panel yoda-card">
        <button type="button" class="gallery-lightbox__close" @click="closeLightbox">Close</button>
        <p class="gallery-lightbox__meta font-mono text-xs text-[var(--muted)]">
          {{ formatTehranDateTime(lightboxImage.capturedAt) }}
        </p>
        <div v-if="lightboxLoading" class="gallery-lightbox__loading">Loading full image…</div>
        <img
          v-else-if="lightboxFullUrl"
          :src="lightboxFullUrl"
          :alt="lightboxImage.caption ?? 'Captured image'"
          class="gallery-lightbox__image"
        />
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.gallery-grid {
  --gallery-thumb-size: 88px;
  display: grid;
  grid-template-columns: repeat(auto-fill, var(--gallery-thumb-size));
  gap: 0.375rem;
  justify-content: start;
  align-content: start;
}

.gallery-thumb-button {
  display: block;
  width: var(--gallery-thumb-size);
  height: var(--gallery-thumb-size);
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-raised);
  cursor: zoom-in;
}

.gallery-thumb {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-lightbox {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  background: color-mix(in srgb, #020617 72%, transparent);
}

.gallery-lightbox__panel {
  position: relative;
  max-width: min(96vw, 1100px);
  max-height: 92vh;
  width: 100%;
  padding: 0.75rem;
  overflow: auto;
}

.gallery-lightbox__close {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  z-index: 1;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 0.25rem 0.6rem;
  background: var(--surface);
  font-size: 0.75rem;
  cursor: pointer;
}

.gallery-lightbox__meta {
  margin: 0 0 0.5rem;
}

.gallery-lightbox__loading {
  padding: 3rem 1rem;
  text-align: center;
  color: var(--muted);
}

.gallery-lightbox__image {
  display: block;
  width: 100%;
  height: auto;
  max-height: calc(92vh - 3rem);
  object-fit: contain;
}
</style>