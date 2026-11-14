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
    class="flex bg-transparent"
    :class="scrollable ? 'min-h-screen' : 'h-screen overflow-hidden'"
  >
    <NavSidebar />

    <div class="flex min-h-0 min-w-0 flex-1 flex-col">
      <header class="page-header shrink-0">
        <p class="page-kicker">Dogan</p>
        <h1 class="mt-1 text-2xl font-semibold tracking-tight text-[var(--text)]">{{ title }}</h1>
        <p v-if="subtitle" class="mt-1 text-sm text-[var(--muted)]">{{ subtitle }}</p>
      </header>

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
