import { onMounted, ref, watch } from 'vue'

const STORAGE_KEY = 'parkiroid.sidebar.collapsed'

const collapsed = ref(false)

export function useSidebarCollapsed() {
  onMounted(() => {
    collapsed.value = localStorage.getItem(STORAGE_KEY) === '1'
  })

  watch(collapsed, (value) => {
    localStorage.setItem(STORAGE_KEY, value ? '1' : '0')
  })

  function toggleCollapsed(): void {
    collapsed.value = !collapsed.value
  }

  return { collapsed, toggleCollapsed }
}
