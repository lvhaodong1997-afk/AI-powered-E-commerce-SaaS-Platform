<template>
  <div>
    <el-button :disabled="disabled || !canQuery" @click="open">{{ mt('meta.selectGenerated') }}</el-button>
    <span v-if="!canQuery" class="hint">{{ mt('meta.generationQueryPermission') }}</span>
    <el-dialog v-model="visible" :title="mt('meta.generatedVideo')" width="min(800px, 95vw)" :close-on-click-modal="false">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item :label="mt('meta.taskTitle')">
          <el-input v-model="query.title" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item><el-button @click="search">{{ mt('meta.search') }}</el-button></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="translateText(error)" type="error" :closable="false" />
      <el-alert v-if="facebookSelected" :title="mt('meta.facebookVideoTitle')" :description="mt('meta.generatedFacebookHint')" type="info" :closable="false" />
      <el-table v-loading="loading" :data="videos">
        <el-table-column prop="id" :label="mt('meta.taskId')" width="100" />
        <el-table-column prop="title" :label="mt('meta.generatedTitle')" min-width="200" />
        <el-table-column :label="mt('meta.actions')" width="120">
          <template #default="{ row }">
            <el-button :disabled="!row.id || row.status !== 'SUCCESS' || !row.outputUrl" @click="select(row)">{{ mt('meta.select') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
      <p class="hint">{{ mt('meta.generatedHint') }}</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, reactive, watch } from 'vue'
import { TkGenerationApi, type TkGenerationTaskVO } from '@/api/tk/generation'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { translateMetaText } from '@/locales/tk/metaPublishMessages'

defineProps<{ disabled?: boolean; facebookSelected?: boolean }>()
const emit = defineEmits<{ select: [task: TkGenerationTaskVO] }>()
const { locale, tt } = useTkI18n()
const mt = (key: string) => tt(key)
const translateText = (text?: string) => translateMetaText(text, locale.value)
const canQuery = computed(() => hasPermission(['tk:generation:query']))
const visible = ref(false), loading = ref(false), error = ref('')
const videos = ref<TkGenerationTaskVO[]>([]), total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, title: '', status: 'SUCCESS' })
let revision = 0
watch(visible, (value) => { if (!value) { revision++; loading.value = false } })
onBeforeUnmount(() => { revision++ })
async function load() {
  if (!canQuery.value || !visible.value) return
  const requestId = ++revision
  loading.value = true; error.value = ''
  try {
    const result = await TkGenerationApi.getGenerationPage({ ...query })
    if (requestId !== revision) return
    videos.value = result.list; total.value = result.total
  } catch (reason) {
    if (requestId === revision) error.value = reason instanceof Error ? reason.message : mt('meta.generatedLoadFailed')
  } finally { if (requestId === revision) loading.value = false }
}
function open() { visible.value = true; void load() }
function search() { query.pageNo = 1; void load() }
function select(task: TkGenerationTaskVO) {
  if (!canQuery.value || task.status !== 'SUCCESS' || !task.outputUrl || !task.id) return
  emit('select', task); visible.value = false
}
</script>

<style scoped>
.hint { color: var(--el-text-color-secondary); font-size: 13px; margin-left: 8px; }
</style>
