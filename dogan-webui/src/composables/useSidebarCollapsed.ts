import { onMounted, onUnmounted, ref, watch } from 'vue'

const STORAGE_KEY = 'dogan.sidebar.collapsed'
const NARROW_QUERY = '(max-width: 900px)'

const collapsed = ref(false)

export function useSidebarCollapsed() {
  let media: MediaQueryList | null = null

  function syncFromViewport(): void {
    if (!media) {
      return
    }
    if (media.matches) {
      collapsed.value = true
      return
    }
    collapsed.value = localStorage.getItem(STORAGE_KEY) === '1'
  }

  function onMediaChange(): void {
    syncFromViewport()
  }

  onMounted(() => {
    media = window.matchMedia(NARROW_QUERY)
    syncFromViewport()
    media.addEventListener('change', onMediaChange)
  })

  onUnmounted(() => {
    media?.removeEventListener('change', onMediaChange)
  })

  watch(collapsed, (value) => {
    if (media?.matches) {
      return
    }
    localStorage.setItem(STORAGE_KEY, value ? '1' : '0')
  })

  function toggleCollapsed(): void {
    collapsed.value = !collapsed.value
  }

  return { collapsed, toggleCollapsed }
}
