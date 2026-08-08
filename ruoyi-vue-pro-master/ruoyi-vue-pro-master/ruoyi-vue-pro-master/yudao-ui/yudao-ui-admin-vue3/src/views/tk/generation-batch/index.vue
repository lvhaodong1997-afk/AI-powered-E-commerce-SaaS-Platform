<template>
  <div class="generation-batch-page">
    <header class="page-header">
      <div>
        <span class="eyebrow">{{ copy.eyebrow }}</span>
        <h1>{{ copy.title }}</h1>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="goDashboard">
          <Icon icon="ep:data-analysis" />
          {{ copy.dataDashboard }}
        </el-button>
        <el-button type="primary" :loading="loading" @click="getList">
          <Icon icon="ep:refresh" />
          {{ copy.refresh }}
        </el-button>
      </div>
    </header>

    <section class="filter-bar">
      <el-input
        v-model="query.keyword"
        clearable
        :placeholder="copy.keywordPlaceholder"
        class="filter-control keyword"
        @keyup.enter="handleQuery"
      />
      <el-select
        v-model="query.status"
        clearable
        :placeholder="copy.status"
        class="filter-control"
        @change="handleQuery"
      >
        <el-option v-for="item in batchStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select
        v-model="query.libraryId"
        clearable
        filterable
        :placeholder="copy.materialLibrary"
        class="filter-control"
        @change="handleQuery"
      >
        <el-option v-for="item in libraries" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button @click="handleQuery">
        <Icon icon="ep:search" />
        {{ copy.search }}
      </el-button>
      <el-button @click="resetQuery">
        <Icon icon="ep:refresh-left" />
        {{ copy.reset }}
      </el-button>
    </section>

    <section class="metric-grid">
      <article v-for="item in metrics" :key="item.label" class="metric-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <em>{{ item.hint }}</em>
      </article>
    </section>

    <section class="panel">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column :label="copy.batchInfo" min-width="260">
          <template #default="{ row }">
            <div class="batch-title">{{ row.name || row.batchNo || `${copy.batchId} ${row.id}` }}</div>
            <div class="muted-line">{{ row.batchNo || '-' }}</div>
            <div class="muted-line">{{ copy.createdTime }} {{ row.createTime || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="copy.plan" min-width="170">
          <template #default="{ row }">
            <div class="plan-text">
              {{ row.scriptCount || 0 }} {{ copy.scripts }} x {{ row.videosPerScript || 0 }}
              {{ copy.videos }}
            </div>
            <div class="muted-line">{{ copy.expected }} {{ row.expectedVideoCount || 0 }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="libraryId" :label="copy.libraryId" width="100" />
        <el-table-column prop="targetLanguage" :label="copy.language" width="120">
          <template #default="{ row }">{{ languageLabel(row.targetLanguage) }}</template>
        </el-table-column>
        <el-table-column :label="copy.progress" min-width="230">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent || 0" :status="progressStatus(row.status)" />
            <div class="progress-meta">
              <span>{{ copy.success }} {{ row.successTaskCount || 0 }}</span>
              <span>{{ copy.failed }} {{ row.failedTaskCount || 0 }}</span>
              <span>{{ copy.running }} {{ row.runningTaskCount || 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="copy.status" width="120">
          <template #default="{ row }">
            <el-tag :type="batchStatusTag(row.status)" effect="plain">
              {{ batchStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failSummary" :label="copy.failureSummary" min-width="180" show-overflow-tooltip />
        <el-table-column :label="copy.actions" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ copy.details }}</el-button>
            <el-button
              v-if="(row.failedTaskCount || 0) > 0"
              link
              type="danger"
              v-hasPermi="['tk:generation:create']"
              @click="retryFailed(row)"
            >
              {{ copy.retryFailed }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="total"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        @pagination="getList"
      />
    </section>

    <el-drawer
      v-model="detailVisible"
      :title="copy.batchDetails"
      size="880px"
      class="batch-detail-drawer"
    >
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <div v-else-if="detail" class="detail-body">
        <div class="detail-summary">
          <div>
            <span>{{ copy.batchNo }}</span>
            <strong>{{ detail.batch.batchNo || detail.batch.id }}</strong>
          </div>
          <div>
            <span>{{ copy.progress }}</span>
            <strong>{{ detail.batch.progressPercent || 0 }}%</strong>
          </div>
          <div>
            <span>{{ copy.failed }}</span>
            <strong>{{ detail.batch.failedTaskCount || 0 }}</strong>
          </div>
          <div>
            <span>{{ copy.running }}</span>
            <strong>{{ detail.batch.runningTaskCount || 0 }}</strong>
          </div>
        </div>

        <div class="drawer-title">
          <h2>{{ copy.tasks }}</h2>
          <el-button
            v-if="(detail.batch.failedTaskCount || 0) > 0"
            type="danger"
            plain
            v-hasPermi="['tk:generation:create']"
            @click="retryFailed(detail.batch)"
          >
            <Icon icon="ep:refresh" />
            {{ copy.retryFailed }}
          </el-button>
        </div>
        <el-table :data="detail.tasks || []" stripe max-height="320">
          <el-table-column :label="copy.index" width="120">
            <template #default="{ row }">
              {{ copy.scriptShort }}{{ row.scriptIndex || 1 }} / {{ copy.videoShort }}{{ row.videoIndex || 1 }}
            </template>
          </el-table-column>
          <el-table-column prop="title" :label="copy.taskTitle" min-width="210" show-overflow-tooltip />
          <el-table-column :label="copy.progress" width="150">
            <template #default="{ row }"><el-progress :percentage="row.progress || 0" /></template>
          </el-table-column>
          <el-table-column :label="copy.status" width="120">
            <template #default="{ row }">
              <el-tag :type="taskStatusTag(row.status)" effect="plain">{{ taskStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentStep" :label="copy.currentStep" min-width="150" show-overflow-tooltip />
          <el-table-column prop="failReason" :label="copy.failureReason" min-width="180" show-overflow-tooltip />
        </el-table>

        <div class="drawer-title log-title">
          <h2>{{ copy.stepLogs }}</h2>
        </div>
        <el-timeline v-if="detail.stepLogs?.length" class="step-timeline">
          <el-timeline-item
            v-for="log in detail.stepLogs"
            :key="log.id"
            :type="stepLogType(log.status)"
            :timestamp="log.startTime || '-'"
          >
            <div class="log-row">
              <strong>{{ log.stepName || stepLabel(log.stepCode) }}</strong>
              <el-tag size="small" :type="stepLogType(log.status)" effect="plain">
                {{ stepStatusLabel(log.status) }}
              </el-tag>
            </div>
            <p>
              {{ copy.taskId }} {{ log.taskId }} · {{ copy.duration }}
              {{ formatDuration(log.durationMillis) }}
            </p>
            <p v-if="log.failReason" class="failure-text">{{ log.failReason }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else :description="copy.noStepLogs" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { TkGenerationBatchApi, type TkGenerationBatchDetailVO, type TkGenerationBatchVO } from '@/api/tk/generationBatch'
import { TkMaterialApi } from '@/api/tk/material'
import { useLocaleStore } from '@/store/modules/locale'

defineOptions({ name: 'TkGenerationBatch' })

const router = useRouter()
const message = useMessage()
const localeStore = useLocaleStore()
const isEn = computed(() => localeStore.getCurrentLocale.lang === 'en')

const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const list = ref<TkGenerationBatchVO[]>([])
const total = ref(0)
const detail = ref<TkGenerationBatchDetailVO>()
const libraries = ref<Array<{ id: number; name: string }>>([])
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: undefined as string | undefined,
  status: undefined as string | undefined,
  libraryId: undefined as number | undefined
})

const copy = computed(() =>
  isEn.value
    ? {
        eyebrow: 'Batch operations',
        title: 'Generation Batch Queue',
        subtitle: 'Track batch progress, task distribution, failed tasks, and pipeline step logs.',
        dataDashboard: 'Data dashboard',
        refresh: 'Refresh',
        keywordPlaceholder: 'Batch no. / name / source link',
        status: 'Status',
        materialLibrary: 'Material library',
        search: 'Search',
        reset: 'Reset',
        batchInfo: 'Batch',
        batchId: 'Batch ID',
        batchNo: 'Batch no.',
        createdTime: 'Created',
        plan: 'Plan',
        scripts: 'scripts',
        videos: 'videos',
        expected: 'Expected',
        libraryId: 'Library ID',
        language: 'Language',
        progress: 'Progress',
        success: 'Success',
        failed: 'Failed',
        running: 'Running',
        queued: 'Queued',
        completed: 'Completed',
        partialFailed: 'Partial failed',
        failureSummary: 'Failure summary',
        actions: 'Actions',
        details: 'Details',
        retryFailed: 'Retry failed',
        batchDetails: 'Batch details',
        tasks: 'Tasks',
        index: 'Index',
        scriptShort: 'S',
        videoShort: 'V',
        taskTitle: 'Task title',
        currentStep: 'Current step',
        failureReason: 'Failure reason',
        stepLogs: 'Step logs',
        noStepLogs: 'No step logs yet',
        taskId: 'Task ID',
        duration: 'Duration',
        totalBatches: 'Batches',
        totalTasks: 'Created tasks',
        riskBatches: 'Risk batches',
        latestScope: 'Current page',
        seconds: 's',
        milliseconds: 'ms',
        retrySubmitted: 'Retry submitted.',
        retryEmpty: 'No failed tasks need retry.',
        unknown: 'Unknown'
      }
    : {
        eyebrow: '批量运营',
        title: '生成批次队列',
        subtitle: '集中跟踪批量生成进度、任务分布、失败任务和流水线步骤日志。',
        dataDashboard: '数据看板',
        refresh: '刷新',
        keywordPlaceholder: '批次号 / 名称 / 源链接',
        status: '状态',
        materialLibrary: '素材库',
        search: '搜索',
        reset: '重置',
        batchInfo: '批次',
        batchId: '批次ID',
        batchNo: '批次号',
        createdTime: '创建',
        plan: '生成计划',
        scripts: '条文案',
        videos: '个视频',
        expected: '预计',
        libraryId: '素材库ID',
        language: '语言',
        progress: '进度',
        success: '成功',
        failed: '失败',
        running: '执行中',
        queued: '排队中',
        completed: '已完成',
        partialFailed: '部分失败',
        failureSummary: '失败摘要',
        actions: '操作',
        details: '详情',
        retryFailed: '重试失败',
        batchDetails: '批次详情',
        tasks: '任务明细',
        index: '序号',
        scriptShort: '文案',
        videoShort: '视频',
        taskTitle: '任务标题',
        currentStep: '当前步骤',
        failureReason: '失败原因',
        stepLogs: '步骤日志',
        noStepLogs: '暂无步骤日志',
        taskId: '任务ID',
        duration: '耗时',
        totalBatches: '批次数',
        totalTasks: '已建任务',
        riskBatches: '风险批次',
        latestScope: '当前页',
        seconds: '秒',
        milliseconds: '毫秒',
        retrySubmitted: '已提交重试',
        retryEmpty: '暂无失败任务需要重试',
        unknown: '未知'
      }
)

const batchStatusOptions = computed(() => [
  { label: copy.value.queued, value: 'QUEUED' },
  { label: copy.value.running, value: 'RUNNING' },
  { label: copy.value.completed, value: 'SUCCESS' },
  { label: copy.value.partialFailed, value: 'PARTIAL_FAILED' },
  { label: copy.value.failed, value: 'FAILED' }
])

const metrics = computed(() => {
  const batches = list.value
  const createdTaskCount = batches.reduce((sum, item) => sum + (item.createdTaskCount || 0), 0)
  const riskCount = batches.filter((item) => (item.failedTaskCount || 0) > 0 || item.status === 'FAILED').length
  return [
    { label: copy.value.totalBatches, value: total.value, hint: copy.value.latestScope },
    { label: copy.value.totalTasks, value: createdTaskCount, hint: copy.value.latestScope },
    { label: copy.value.running, value: batches.reduce((sum, item) => sum + (item.runningTaskCount || 0), 0), hint: copy.value.latestScope },
    { label: copy.value.riskBatches, value: riskCount, hint: copy.value.latestScope }
  ]
})

const getList = async () => {
  loading.value = true
  try {
    const data = await TkGenerationBatchApi.getBatchPage(query)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const getLibraries = async () => {
  const data = await TkMaterialApi.getLibraryPage({ pageNo: 1, pageSize: 100 })
  libraries.value = data.list || []
}

const handleQuery = () => {
  query.pageNo = 1
  getList()
}

const resetQuery = () => {
  query.pageNo = 1
  query.keyword = undefined
  query.status = undefined
  query.libraryId = undefined
  getList()
}

const openDetail = async (row: TkGenerationBatchVO) => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    detail.value = await TkGenerationBatchApi.getBatchDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

const retryFailed = async (row: TkGenerationBatchVO) => {
  const count = await TkGenerationBatchApi.retryFailedTasks(row.id)
  message.success(count > 0 ? `${copy.value.retrySubmitted} ${count}` : copy.value.retryEmpty)
  await getList()
  if (detailVisible.value && detail.value?.batch.id === row.id) {
    detail.value = await TkGenerationBatchApi.getBatchDetail(row.id)
  }
}

const goDashboard = () => {
  router.push({ path: '/tk/data-dashboard' })
}

const batchStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    QUEUED: copy.value.queued,
    RUNNING: copy.value.running,
    SUCCESS: copy.value.completed,
    PARTIAL_FAILED: copy.value.partialFailed,
    FAILED: copy.value.failed
  }
  return status ? map[status] || status : copy.value.unknown
}

const taskStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    PENDING: isEn.value ? 'Pending' : '待执行',
    ANALYZING: isEn.value ? 'Analyzing' : '分析中',
    SCRIPT_READY: isEn.value ? 'Script ready' : '文案完成',
    VOICE_READY: isEn.value ? 'Voice ready' : '配音完成',
    MATERIAL_MATCHING: isEn.value ? 'Matching materials' : '匹配素材',
    MATERIAL_MATCHED: isEn.value ? 'Materials matched' : '素材已匹配',
    RENDERING: isEn.value ? 'Rendering' : '合成中',
    EXPORTING: isEn.value ? 'Exporting' : '导出中',
    SUCCESS: copy.value.success,
    FAILED: copy.value.failed
  }
  return status ? map[status] || status : copy.value.unknown
}

const stepStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    SUCCESS: copy.value.success,
    FAILED: copy.value.failed,
    RUNNING: copy.value.running
  }
  return status ? map[status] || status : copy.value.unknown
}

const stepLabel = (step?: string) => {
  const map: Record<string, string> = {
    PENDING: isEn.value ? 'Queued' : '排队',
    ANALYZING: isEn.value ? 'Analysis' : '分析',
    SCRIPT_READY: isEn.value ? 'Script' : '文案',
    VOICE_READY: isEn.value ? 'Voice' : '配音',
    MATERIAL_MATCHING: isEn.value ? 'Material matching' : '素材匹配',
    MATERIAL_MATCHED: isEn.value ? 'Material plan' : '素材方案',
    RENDERING: isEn.value ? 'Rendering' : '合成',
    EXPORTING: isEn.value ? 'Exporting' : '导出'
  }
  return step ? map[step] || step : copy.value.unknown
}

const languageLabel = (language?: string) => {
  const map: Record<string, string> = {
    'zh-CN': isEn.value ? 'Chinese' : '中文',
    'en-US': 'English',
    'es-ES': isEn.value ? 'Spanish' : '西班牙语',
    'fr-FR': isEn.value ? 'French' : '法语',
    'nl-NL': isEn.value ? 'Dutch' : '荷兰语'
  }
  return language ? map[language] || language : '-'
}

const batchStatusTag = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'danger'
  return 'warning'
}

const taskStatusTag = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

const stepLogType = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'primary'
}

const progressStatus = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'PARTIAL_FAILED') return 'exception'
  return undefined
}

const formatDuration = (durationMillis?: number) => {
  if (!durationMillis && durationMillis !== 0) return '-'
  if (durationMillis < 1000) return `${durationMillis}${copy.value.milliseconds}`
  return `${Math.round(durationMillis / 1000)}${copy.value.seconds}`
}

onMounted(() => {
  getList()
  getLibraries()
})
</script>

<style scoped lang="scss">
.generation-batch-page {
  min-height: 100%;
  padding: 24px;
  background: #f6f8fb;
}

.page-header,
.filter-bar,
.panel,
.metric-card {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #fff;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.06);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px;
  border-radius: 8px;
}

.eyebrow {
  display: block;
  margin-bottom: 6px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  color: #111827;
  font-size: 28px;
  line-height: 1.25;
}

.page-header p {
  margin-top: 8px;
  color: #64748b;
}

.header-actions,
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-bar {
  margin-top: 16px;
  padding: 16px;
  border-radius: 8px;
}

.filter-control {
  width: 180px;
}

.filter-control.keyword {
  width: 320px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 16px 0;
}

.metric-card {
  display: grid;
  gap: 6px;
  padding: 18px;
  border-radius: 8px;
}

.metric-card span,
.metric-card em,
.muted-line,
.progress-meta,
.log-row + p {
  color: #64748b;
  font-style: normal;
}

.metric-card strong {
  color: #111827;
  font-size: 26px;
  line-height: 1.1;
}

.panel {
  padding: 18px;
  border-radius: 8px;
}

.batch-title,
.plan-text {
  color: #111827;
  font-weight: 700;
}

.muted-line {
  margin-top: 4px;
  font-size: 12px;
}

.progress-meta {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
}

.detail-body {
  display: grid;
  gap: 18px;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.detail-summary > div {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.detail-summary span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.detail-summary strong {
  display: block;
  margin-top: 6px;
  color: #111827;
  font-size: 20px;
}

.drawer-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.drawer-title h2 {
  color: #111827;
  font-size: 18px;
}

.log-title {
  margin-top: 4px;
}

.step-timeline {
  padding: 4px 4px 0;
}

.log-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.failure-text {
  color: #dc2626;
}

@media (max-width: 900px) {
  .generation-batch-page {
    padding: 14px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-control,
  .filter-control.keyword {
    width: 100%;
  }

  .metric-grid,
  .detail-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .metric-grid,
  .detail-summary {
    grid-template-columns: 1fr;
  }
}
</style>
