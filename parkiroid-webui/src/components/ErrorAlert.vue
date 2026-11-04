<script setup lang="ts">
defineProps<{
  title?: string
  message: string
}>()
</script>

<template>
  <div class="alert-error" role="alert">
    <p v-if="title" class="mb-1 font-semibold text-red-100">{{ title }}</p>
    <p>{{ message }}</p>
  </div>
</template>
