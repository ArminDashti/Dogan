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

function mapMetadata(image: GalleryImageWire): GalleryImage {
  return {
    id: image.id,
    url: image.url,
    thumbnailUrl: image.thumbnail_url ?? image.url,
    caption: image.caption,
    capturedAt: image.captured_at,
    deviceId: image.device_id,
  }
}

export async function fetchImages(): Promise<GalleryImage[]> {
  const raw = await apiClient.get<GalleryImageWire[]>('/images')
  const items = (raw ?? []).map(mapMetadata)
  const withThumbs = await Promise.all(
    items.map(async (image) => {
      try {
        const thumbnailUrl = await apiClient.getBlobObjectUrl(`/images/${image.id}/thumbnail`)
        return { ...image, thumbnailUrl }
      } catch {
        return image
      }
    }),
  )
  return withThumbs
}

export async function fetchImageFullUrl(imageId: string): Promise<string> {
  return apiClient.getBlobObjectUrl(`/images/${imageId}`)
}

export async function fetchLatestDeviceImage(deviceId: string): Promise<GalleryImage | null> {
  try {
    const image = await apiClient.get<GalleryImageWire>(`/devices/${deviceId}/last-image`)
    const mapped = mapMetadata(image)
    const [thumbnailUrl, url] = await Promise.all([
      apiClient.getBlobObjectUrl(`/images/${mapped.id}/thumbnail`),
      apiClient.getBlobObjectUrl(`/images/${mapped.id}`),
    ])
    return { ...mapped, thumbnailUrl, url }
  } catch {
    return null
  }
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