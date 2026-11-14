<template>
  <div class="doc-prose">
    <template v-for="(block, index) in blocks" :key="index">
      <component :is="`h${Math.min((block.level || 1) + 1, 6)}`" v-if="block.type === 'heading'">
        <ApiDocText :text="block.text || ''" />
      </component>
      <p v-else-if="block.type === 'paragraph'"><ApiDocText :text="block.text || ''" /></p>
      <div v-else-if="block.type === 'code'" class="doc-code">
        <div class="doc-code-bar"
          ><span>{{ block.language || 'TEXT' }}</span
          ><button type="button" @click="copy(block.text || '')">复制代码</button></div
        >
        <pre><code>{{ block.text }}</code></pre>
      </div>
      <div v-else-if="block.type === 'table'" class="doc-table-scroll">
        <table
          ><thead
            ><tr
              ><th v-for="(header, i) in block.headers" :key="i"
                ><ApiDocText :text="header" /></th></tr
          ></thead>
          <tbody
            ><tr v-for="(row, i) in block.rows" :key="i"
              ><td v-for="(cell, j) in row" :key="j"><ApiDocText :text="cell" /></td></tr
          ></tbody>
        </table>
      </div>
      <component :is="block.ordered ? 'ol' : 'ul'" v-else-if="block.type === 'list'">
        <li v-for="(item, i) in block.items" :key="i"><ApiDocText :text="item" /></li>
      </component>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ApiDocText from './ApiDocText.vue'
import { markdownBlocks } from './apiDocs.mjs'
const props = defineProps<{ source: string }>()
const emit = defineEmits<{ copy: [text: string] }>()
const blocks = computed(() => markdownBlocks(props.source))
const copy = (text: string) => emit('copy', text)
</script>
