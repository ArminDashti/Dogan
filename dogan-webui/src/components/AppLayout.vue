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
        class="reveal flex min-h-0 flex-1 flex-col"
        :class="
          scrollable
            ? 'overflow-auto px-5 py-5 sm:px-8 sm:py-6'
            : 'overflow-hidden px-4 py-3 sm:px-6 sm:py-4'
        "
      >
        <div class="min-h-0 flex-1" :class="{ 'flex flex-col': !scrollable }">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>
