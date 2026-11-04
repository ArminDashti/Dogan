<script setup lang="ts">
import NavSidebar from '@/components/NavSidebar.vue'

const { scrollable = true } = defineProps<{
  title: string
  subtitle?: string
  scrollable?: boolean
}>()
</script>

<template>
  <div
    class="flex bg-[var(--bg)]"
    :class="scrollable ? 'min-h-screen' : 'h-screen overflow-hidden'"
  >
    <NavSidebar />

    <div class="flex min-h-0 min-w-0 flex-1 flex-col">
      <header class="page-header shrink-0">
        <h1 class="text-2xl font-semibold text-[var(--text)]">{{ title }}</h1>
        <p v-if="subtitle" class="mt-1 text-sm text-[var(--muted)]">{{ subtitle }}</p>
      </header>

      <main
        class="flex min-h-0 flex-1 flex-col px-8 py-6"
        :class="scrollable ? 'overflow-auto' : 'overflow-hidden'"
      >
        <div class="min-h-0 flex-1">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>
