<template>
  <div>
    <el-button :disabled="disabled || !canQuery" @click="open">选择系统成片</el-button>
    <span v-if="!canQuery" class="hint">需要成片查询权限</span>
    <el-dialog v-model="visible" title="选择已生成的视频" width="min(800px, 95vw)" :close-on-click-modal="false">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="任务标题">
          <el-input v-model="query.title" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item><el-button @click="search">搜索</el-button></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <el-alert v-if="facebookSelected" title="Facebook Reels 支持范围" description="9:16、至少 540×960、23–60 fps、4–60 秒。此列表不含完整媒体参数，适用性待服务端校验。" type="info" :closable="false" />
      <el-table v-loading="loading" :data="videos">
        <el-table-column prop="id" label="任务 ID" width="100" />
        <el-table-column prop="title" label="成片标题" min-width="200" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button :disabled="!row.id || row.status !== 'SUCCESS' || !row.outputUrl" @click="select(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
      <p class="hint">仅显示生成成功的视频。服务端会校验成片归属和平台视频要求。</p>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, reactive, watch } from 'vue'
import { TkGenerationApi, type TkGenerationTaskVO } from '@/api/tk/generation'
import { hasPermission } from '@/directives/permission/hasPermi'

defineProps<{ disabled?: boolean; facebookSelected?: boolean }>()
const emit = defineEmits<{ select: [task: TkGenerationTaskVO] }>()
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
    if (requestId === revision) error.value = reason instanceof Error ? reason.message : '成片加载失败'
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
