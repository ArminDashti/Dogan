<script setup lang="ts">
import NavSidebar from '@/components/NavSidebar.vue'

const { scrollable = true } = defineProps<{
  title?: string
  subtitle?: string
  scrollable?: boolean
}>()
</script>

<template>
  <div
    class="flex bg-transparent"
    :class="scrollable ? 'min-h-screen' : 'h-screen overflow-hidden'"
  >
    <NavSidebar />

    <div class="flex min-h-0 min-w-0 flex-1 flex-col">
      <main
        class="reveal flex min-h-0 flex-1 flex-col px-5 py-5 sm:px-8 sm:py-6"
        :class="scrollable ? 'overflow-auto' : 'overflow-hidden'"
      >
        <div class="min-h-0 flex-1">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>
