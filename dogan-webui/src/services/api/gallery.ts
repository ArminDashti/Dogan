import { apiClient } from './client'
import type { GalleryImage } from '@/types/api'

interface GalleryImageWire {
  id: string
  url: string
  thumbnail_url?: string
  caption?: string
  captured_at: string
  device_id?: string
}

export async function fetchImages(): Promise<GalleryImage[]> {
  const raw = await apiClient.get<GalleryImageWire[]>('/images')
  return Promise.all(
    (raw ?? []).map(async (image) => {
      const objectUrl = await apiClient.getBlobObjectUrl(`/images/${image.id}`)
      return {
        id: image.id,
        url: objectUrl,
        thumbnailUrl: objectUrl,
        caption: image.caption,
        capturedAt: image.captured_at,
        deviceId: image.device_id,
      }
    }),
  )
}

export function revokeGalleryObjectUrls(images: GalleryImage[]): void {
  for (const image of images) {
    if (image.url.startsWith('blob:')) {
      URL.revokeObjectURL(image.url)
    }
    const thumb = image.thumbnailUrl
    if (thumb && thumb.startsWith('blob:') && thumb !== image.url) {
      URL.revokeObjectURL(thumb)
    }
  }
}
