<template>
  <template v-for="(token, index) in tokens" :key="index">
    <code v-if="token.type === 'code'">{{ token.text }}</code>
    <strong v-else-if="token.type === 'strong'">{{ token.text }}</strong>
    <a
      v-else-if="token.type === 'link'"
      :href="token.href"
      :target="token.href?.startsWith('http') ? '_blank' : undefined"
      rel="noopener noreferrer"
      >{{ token.text }}</a
    >
    <template v-else>{{ token.text }}</template>
  </template>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { inlineTokens } from './apiDocs.mjs'
const props = defineProps<{ text: string }>()
const tokens = computed(() => inlineTokens(props.text))
</script>
