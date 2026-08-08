<template>
  <div class="data-dashboard">
    <header class="dashboard-header">
      <div>
        <span class="eyebrow">{{ copy.eyebrow }}</span>
        <h1>{{ copy.title }}</h1>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="goBatchQueue">
          <Icon icon="ep:operation" />
          {{ copy.batchQueue }}
        </el-button>
        <el-button type="primary" :loading="loading" @click="loadDashboard">
          <Icon icon="ep:refresh" />
          {{ copy.refresh }}
        </el-button>
      </div>
    </header>

    <section class="filter-bar">
      <el-segmented v-model="quickRange" :options="rangeOptions" @change="applyQuickRange" />
      <el-date-picker
        v-model="customRange"
        type="datetimerange"
        :start-placeholder="copy.startTime"
        :end-placeholder="copy.endTime"
        format="YYYY-MM-DD HH:mm"
        value-format="YYYY-MM-DD HH:mm:ss"
        @change="handleCustomRangeChange"
      />
      <el-select
        v-model="query.libraryId"
        clearable
        filterable
        :placeholder="copy.materialLibrary"
        class="filter-control"
        @change="loadDashboard"
      >
        <el-option
          v-for="item in libraries"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>
      <el-select
        v-model="query.targetLanguage"
        clearable
        :placeholder="copy.language"
        class="filter-control"
        @change="loadDashboard"
      >
        <el-option v-for="item in languageOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select
        v-model="query.status"
        clearable
        :placeholder="copy.status"
        class="filter-control"
        @change="loadDashboard"
      >
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </section>

    <section class="metric-grid" v-loading="loading">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card">
        <div class="metric-icon" :class="`tone-${metric.tone}`">
          <Icon :icon="metric.icon" />
        </div>
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <em>{{ metric.hint }}</em>
      </article>
    </section>

    <section class="panel queue-health-panel" v-loading="loading">
      <div class="panel-title">
        <div>
          <h2>{{ copy.queueHealth }}</h2>
          <p>{{ copy.queueHealthDesc }}</p>
        </div>
        <el-button text type="primary" @click="goQueueRecords">
          {{ copy.viewQueue }}
          <Icon icon="ep:arrow-right" />
        </el-button>
      </div>
      <div class="queue-metric-grid">
        <button
          v-for="item in queueMetrics"
          :key="item.label"
          class="queue-metric"
          type="button"
          @click="goGenerationRecordsByStatus(item.status)"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <em>{{ item.hint }}</em>
        </button>
      </div>
      <div class="attention-list">
        <div v-for="task in queueHealth?.attentionTasks || []" :key="task.id" class="attention-row">
          <div>
            <strong>{{ task.title || `${copy.taskId} ${task.id}` }}</strong>
            <span>{{ statusLabel(task.status) }} · {{ task.currentStep || copy.unknown }}</span>
          </div>
          <el-button text type="primary" @click="goGenerationTask(task.id)">
            {{ copy.details }}
          </el-button>
        </div>
        <el-empty v-if="!(queueHealth?.attentionTasks || []).length" :description="copy.noQueueRisk" />
      </div>
    </section>

    <section class="analytics-grid">
      <article class="panel trend-panel" v-loading="loading">
        <div class="panel-title">
          <div>
            <h2>{{ copy.generationTrend }}</h2>
            <p>{{ copy.generationTrendDesc }}</p>
          </div>
          <el-button text type="primary" @click="goGenerationRecords">
            {{ copy.viewRecords }}
            <Icon icon="ep:arrow-right" />
          </el-button>
        </div>
        <div class="trend-chart">
          <div v-for="item in trendItems" :key="item.day" class="trend-column">
            <div class="bar-stack">
              <span class="bar success" :style="{ height: `${barHeight(item.successCount)}%` }"></span>
              <span class="bar failed" :style="{ height: `${barHeight(item.failedCount)}%` }"></span>
              <span class="bar running" :style="{ height: `${barHeight(item.runningCount)}%` }"></span>
            </div>
            <strong>{{ formatShortDate(item.day) }}</strong>
            <small>{{ formatNumber(item.totalCount) }}</small>
          </div>
        </div>
        <div class="legend-row">
          <span><i class="success"></i>{{ copy.success }}</span>
          <span><i class="failed"></i>{{ copy.failed }}</span>
          <span><i class="running"></i>{{ copy.running }}</span>
        </div>
      </article>

      <article class="panel failure-panel" v-loading="loading">
        <div class="panel-title">
          <div>
            <h2>{{ copy.failureReasons }}</h2>
            <p>{{ copy.failureReasonsDesc }}</p>
          </div>
        </div>
        <div class="rank-list">
          <div v-for="item in failureReasons" :key="item.code" class="rank-row">
            <div>
              <strong>{{ failureLabel(item.label || item.code) }}</strong>
              <span>{{ item.code }}</span>
            </div>
            <el-progress
              :percentage="failurePercent(item.count)"
              :stroke-width="8"
              color="#ef4444"
              :show-text="false"
            />
            <em>{{ formatNumber(item.count) }}</em>
          </div>
          <el-empty v-if="!failureReasons.length" :description="copy.noFailure" />
        </div>
      </article>

      <article class="panel diagnosis-panel" v-loading="loading">
        <div class="panel-title">
          <div>
            <h2>{{ copy.failureDiagnosis }}</h2>
            <p>{{ copy.failureDiagnosisDesc }}</p>
          </div>
          <el-button text type="danger" @click="goFailedRecords">
            {{ copy.handleFailures }}
            <Icon icon="ep:arrow-right" />
          </el-button>
        </div>
        <div class="diagnosis-list">
          <button
            v-for="item in diagnosisItems"
            :key="item.category"
            class="diagnosis-row"
            type="button"
            @click="goGenerationRecordsByStatus(item.actionStatus || 'FAILED')"
          >
            <div>
              <strong>{{ failureLabel(item.category) }}</strong>
              <span>{{ diagnosisHint(item) }}</span>
            </div>
            <em>{{ formatNumber(item.count) }}</em>
          </button>
          <el-empty v-if="!diagnosisItems.length" :description="copy.noFailure" />
        </div>
      </article>
    </section>

    <section class="analytics-grid lower">
      <article class="panel material-panel" v-loading="loading">
        <div class="panel-title">
          <div>
            <h2>{{ copy.materialHealth }}</h2>
            <p>{{ copy.materialHealthDesc }}</p>
          </div>
          <el-button text type="primary" @click="goMaterials">
            {{ copy.manageMaterials }}
            <Icon icon="ep:arrow-right" />
          </el-button>
        </div>
        <el-table :data="materialHealth?.libraries || []" height="360">
          <el-table-column prop="libraryName" :label="copy.libraryName" min-width="170" />
          <el-table-column prop="videoCount" :label="copy.videos" width="96" />
          <el-table-column prop="availableVideoCount" :label="copy.available" width="96" />
          <el-table-column prop="failedVideoCount" :label="copy.materialFailed" width="96" />
          <el-table-column prop="generationCount" :label="copy.used" width="96" />
          <el-table-column :label="copy.healthStatus" width="150">
            <template #default="{ row }">
              <el-tag :type="healthTagType(row.healthStatus)" effect="plain">
                {{ healthLabel(row.healthStatus) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel failures-table" v-loading="loading">
        <div class="panel-title">
          <div>
            <h2>{{ copy.recentFailures }}</h2>
            <p>{{ copy.recentFailuresDesc }}</p>
          </div>
          <el-button text type="danger" @click="goFailedRecords">
            {{ copy.handleFailures }}
            <Icon icon="ep:arrow-right" />
          </el-button>
        </div>
        <el-table :data="failureAnalysis?.recentFailures || []" height="360">
          <el-table-column prop="id" :label="copy.taskId" width="86" />
          <el-table-column prop="title" :label="copy.titleColumn" min-width="150" show-overflow-tooltip />
          <el-table-column prop="currentStep" :label="copy.failureStep" width="130" />
          <el-table-column prop="failCode" :label="copy.failureCode" width="150" show-overflow-tooltip />
          <el-table-column prop="createTime" :label="copy.createdTime" width="170" />
          <el-table-column :label="copy.actions" width="120" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" @click="goGenerationTask(row.id)">
                {{ copy.details }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </section>

    <section class="panel slow-task-panel" v-loading="loading">
      <div class="panel-title">
        <div>
          <h2>{{ copy.slowTasks }}</h2>
          <p>{{ copy.slowTasksDesc }}</p>
        </div>
        <el-button text type="primary" @click="goGenerationRecords">
          {{ copy.viewRecords }}
          <Icon icon="ep:arrow-right" />
        </el-button>
      </div>
      <el-table :data="slowTaskItems" height="340">
        <el-table-column prop="taskId" :label="copy.taskId" width="90" />
        <el-table-column prop="title" :label="copy.titleColumn" min-width="190" show-overflow-tooltip />
        <el-table-column :label="copy.status" width="130">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentStep" :label="copy.failureStep" width="150" show-overflow-tooltip />
        <el-table-column :label="copy.duration" width="120">
          <template #default="{ row }">
            {{ formatDuration(row.durationSeconds) }}
          </template>
        </el-table-column>
        <el-table-column prop="failReason" :label="copy.failureReasons" min-width="220" show-overflow-tooltip />
        <el-table-column :label="copy.actions" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="goGenerationTask(row.taskId)">
              {{ copy.details }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { TkDashboardApi } from '@/api/tk/dashboard'
import type {
  DashboardFailureDiagnosis,
  DashboardFailureDiagnosisItem,
  DashboardFailureAnalysis,
  DashboardMaterialHealth,
  DashboardOverview,
  DashboardQuery,
  DashboardQueueHealth,
  DashboardSlowTasks,
  DashboardTrendItem
} from '@/api/tk/dashboard'
import { TkMaterialApi } from '@/api/tk/material'
import { useLocaleStore } from '@/store/modules/locale'

defineOptions({ name: 'TkDataDashboard' })

const router = useRouter()
const localeStore = useLocaleStore()
const isEn = computed(() => localeStore.getCurrentLocale.lang === 'en')
const loading = ref(false)
const quickRange = ref<'today' | '7d' | '30d'>('7d')
const customRange = ref<[string, string] | undefined>()
const overview = ref<DashboardOverview>()
const trendItems = ref<DashboardTrendItem[]>([])
const failureAnalysis = ref<DashboardFailureAnalysis>()
const materialHealth = ref<DashboardMaterialHealth>()
const queueHealth = ref<DashboardQueueHealth>()
const failureDiagnosis = ref<DashboardFailureDiagnosis>()
const slowTasks = ref<DashboardSlowTasks>()
const libraries = ref<Array<{ id: number; name: string }>>([])
const query = reactive<DashboardQuery>({})

const copy = computed(() =>
  isEn.value
    ? {
        eyebrow: 'Operations dashboard',
        title: 'Data Dashboard',
        subtitle: 'Track generation output, failures, material health, credits, and TikTok account readiness.',
        refresh: 'Refresh',
        batchQueue: 'Batch queue',
        startTime: 'Start time',
        endTime: 'End time',
        materialLibrary: 'Material library',
        language: 'Language',
        status: 'Status',
        today: 'Today',
        last7Days: 'Last 7 days',
        last30Days: 'Last 30 days',
        generated: 'Generated',
        successVideos: 'Successful videos',
        failedVideos: 'Failed videos',
        successRate: 'Success rate',
        avgDuration: 'Avg. duration',
        creditsUsed: 'Credits used',
        materials: 'Materials',
        accounts: 'Authorized accounts',
        queueHealth: 'Queue health',
        queueHealthDesc: 'Pending, running, and stale tasks that may block batch generation.',
        viewQueue: 'View queue',
        pendingTasks: 'Pending tasks',
        runningTasks: 'Running tasks',
        staleTasks: 'Stale tasks',
        avgQueueWait: 'Avg. queue wait',
        avgRunningTime: 'Avg. running time',
        noQueueRisk: 'No queue risks in this period',
        taskScope: 'Selected period tasks',
        completedOutputs: 'Completed outputs',
        needAttention: 'Need attention',
        conversionHealth: 'Generation health',
        renderSpeed: 'Render efficiency',
        assetReadiness: 'Available assets',
        accountReadiness: 'Publishing readiness',
        generationTrend: 'Generation trend',
        generationTrendDesc: 'Daily output split by successful, failed, and running tasks.',
        failureReasons: 'Failure reasons',
        failureReasonsDesc: 'Top failure categories in the selected period.',
        failureDiagnosis: 'Failure diagnosis',
        failureDiagnosisDesc: 'Operational categories with direct follow-up actions.',
        materialHealth: 'Material health',
        materialHealthDesc: 'Material libraries with availability and risk status.',
        recentFailures: 'Recent failures',
        recentFailuresDesc: 'Latest failed generation tasks for quick follow-up.',
        slowTasks: 'Slow tasks',
        slowTasksDesc: 'Tasks with the longest queue, running, or total duration.',
        viewRecords: 'View records',
        manageMaterials: 'Manage materials',
        handleFailures: 'Handle failures',
        success: 'Success',
        failed: 'Failed',
        running: 'Running',
        noFailure: 'No failures in this period',
        libraryName: 'Library',
        videos: 'Videos',
        available: 'Available',
        materialFailed: 'Failed',
        used: 'Used',
        healthStatus: 'Health',
        taskId: 'Task ID',
        titleColumn: 'Title',
        failureStep: 'Step',
        failureCode: 'Code',
        createdTime: 'Created time',
        duration: 'Duration',
        actions: 'Actions',
        details: 'Details',
        healthy: 'Healthy',
        insufficient: 'Insufficient',
        hasFailedMaterials: 'Failed materials',
        lowActivity: 'Low activity',
        unknown: 'Unknown',
        seconds: 's'
      }
    : {
        eyebrow: '运营驾驶舱',
        title: '数据看板',
        subtitle: '集中查看生成产出、失败原因、素材健康度、积分消耗和 TK 账号可用情况。',
        refresh: '刷新',
        batchQueue: '批次队列',
        startTime: '开始时间',
        endTime: '结束时间',
        materialLibrary: '素材库',
        language: '语言',
        status: '状态',
        today: '今日',
        last7Days: '近 7 天',
        last30Days: '近 30 天',
        generated: '生成任务',
        successVideos: '成功视频',
        failedVideos: '失败视频',
        successRate: '成功率',
        avgDuration: '平均耗时',
        creditsUsed: '消耗积分',
        materials: '素材视频',
        accounts: '授权账号',
        queueHealth: '任务队列健康',
        queueHealthDesc: '查看排队、生成中和可能卡住的任务，定位批量生成阻塞点。',
        viewQueue: '查看队列',
        pendingTasks: '待处理任务',
        runningTasks: '生成中任务',
        staleTasks: '疑似卡住',
        avgQueueWait: '平均排队',
        avgRunningTime: '平均生成',
        noQueueRisk: '当前周期暂无队列风险',
        taskScope: '所选周期任务',
        completedOutputs: '已完成产出',
        needAttention: '需要处理',
        conversionHealth: '生成健康度',
        renderSpeed: '合成效率',
        assetReadiness: '可用素材',
        accountReadiness: '发布准备',
        generationTrend: '生成趋势',
        generationTrendDesc: '按天拆分成功、失败和执行中的生成任务。',
        failureReasons: '失败原因',
        failureReasonsDesc: '所选周期内出现最多的失败分类。',
        failureDiagnosis: '失败诊断',
        failureDiagnosisDesc: '按业务原因归类失败任务，并给出下一步处理入口。',
        materialHealth: '素材库健康度',
        materialHealthDesc: '按素材可用性、失败素材和使用情况识别风险。',
        recentFailures: '最近失败任务',
        recentFailuresDesc: '最新失败的生成任务，便于快速跟进。',
        slowTasks: '慢任务排行',
        slowTasksDesc: '查看排队、生成中或总耗时最长的任务。',
        viewRecords: '查看记录',
        manageMaterials: '管理素材',
        handleFailures: '处理失败',
        success: '成功',
        failed: '失败',
        running: '执行中',
        noFailure: '当前周期暂无失败',
        libraryName: '素材库',
        videos: '视频数',
        available: '可用',
        materialFailed: '失败',
        used: '使用',
        healthStatus: '健康状态',
        taskId: '任务ID',
        titleColumn: '标题',
        failureStep: '失败环节',
        failureCode: '错误码',
        createdTime: '创建时间',
        duration: '耗时',
        actions: '操作',
        details: '详情',
        healthy: '健康',
        insufficient: '素材不足',
        hasFailedMaterials: '有失败素材',
        lowActivity: '低活跃',
        unknown: '未知',
        seconds: '秒'
      }
)

const rangeOptions = computed(() => [
  { label: copy.value.today, value: 'today' },
  { label: copy.value.last7Days, value: '7d' },
  { label: copy.value.last30Days, value: '30d' }
])

const languageOptions = computed(() => [
  { label: isEn.value ? 'Chinese' : '中文', value: 'zh-CN' },
  { label: isEn.value ? 'English' : 'English', value: 'en-US' },
  { label: isEn.value ? 'Spanish' : 'Espanol', value: 'es-ES' },
  { label: isEn.value ? 'French' : 'Francais', value: 'fr-FR' },
  { label: isEn.value ? 'Dutch' : 'Nederlands', value: 'nl-NL' }
])

const statusOptions = computed(() => [
  { label: copy.value.success, value: 'SUCCESS' },
  { label: copy.value.failed, value: 'FAILED' },
  { label: isEn.value ? 'Pending' : '待执行', value: 'PENDING' },
  { label: isEn.value ? 'Rendering' : '合成中', value: 'RENDERING' },
  { label: isEn.value ? 'Exporting' : '导出中', value: 'EXPORTING' }
])

const metrics = computed(() => {
  const data = overview.value
  return [
    {
      label: copy.value.generated,
      value: formatNumber(data?.generationTaskCount),
      hint: copy.value.taskScope,
      icon: 'ep:video-camera',
      tone: 'blue'
    },
    {
      label: copy.value.successVideos,
      value: formatNumber(data?.successVideoCount),
      hint: copy.value.completedOutputs,
      icon: 'ep:circle-check-filled',
      tone: 'green'
    },
    {
      label: copy.value.failedVideos,
      value: formatNumber(data?.failedVideoCount),
      hint: copy.value.needAttention,
      icon: 'ep:warning-filled',
      tone: 'red'
    },
    {
      label: copy.value.successRate,
      value: `${formatNumber(data?.successRate)}%`,
      hint: copy.value.conversionHealth,
      icon: 'ep:trend-charts',
      tone: 'violet'
    },
    {
      label: copy.value.avgDuration,
      value: `${formatNumber(data?.averageDurationSeconds)}${copy.value.seconds}`,
      hint: copy.value.renderSpeed,
      icon: 'ep:timer',
      tone: 'amber'
    },
    {
      label: copy.value.creditsUsed,
      value: formatNumber(data?.consumedCredits),
      hint: copy.value.creditsUsed,
      icon: 'ep:coin',
      tone: 'orange'
    },
    {
      label: copy.value.materials,
      value: `${formatNumber(data?.availableMaterialVideoCount)}/${formatNumber(data?.materialVideoCount)}`,
      hint: copy.value.assetReadiness,
      icon: 'ep:folder-opened',
      tone: 'cyan'
    },
    {
      label: copy.value.accounts,
      value: formatNumber(data?.authorizedAccountCount),
      hint: copy.value.accountReadiness,
      icon: 'ep:user-filled',
      tone: 'indigo'
    }
  ]
})

const failureReasons = computed(() => failureAnalysis.value?.reasons || [])
const diagnosisItems = computed(() => failureDiagnosis.value?.items || [])
const slowTaskItems = computed(() => slowTasks.value?.items || [])
const maxTrendCount = computed(() => Math.max(1, ...trendItems.value.map((item) => item.totalCount || 0)))
const maxFailureCount = computed(() => Math.max(1, ...failureReasons.value.map((item) => item.count || 0)))
const queueMetrics = computed(() => {
  const data = queueHealth.value
  return [
    {
      label: copy.value.pendingTasks,
      value: formatNumber(data?.pendingCount),
      hint: formatDuration(data?.averagePendingSeconds),
      status: 'PENDING'
    },
    {
      label: copy.value.runningTasks,
      value: formatNumber(data?.runningCount),
      hint: formatDuration(data?.averageRunningSeconds),
      status: 'RENDERING'
    },
    {
      label: copy.value.staleTasks,
      value: formatNumber(data?.staleRunningCount),
      hint: copy.value.needAttention,
      status: 'RENDERING'
    }
  ]
})

function applyQuickRange() {
  const now = dayjs().endOf('day')
  const start =
    quickRange.value === 'today'
      ? dayjs().startOf('day')
      : now.subtract(quickRange.value === '30d' ? 29 : 6, 'day').startOf('day')
  query.startTime = start.format('YYYY-MM-DD HH:mm:ss')
  query.endTime = now.format('YYYY-MM-DD HH:mm:ss')
  customRange.value = [query.startTime, query.endTime]
  loadDashboard()
}

function handleCustomRangeChange(value?: [string, string]) {
  query.startTime = value?.[0]
  query.endTime = value?.[1]
  loadDashboard()
}

async function loadDashboard() {
  loading.value = true
  try {
    const params = normalizeQuery()
    const [
      overviewData,
      trendData,
      failureData,
      materialData,
      queueData,
      diagnosisData,
      slowTaskData
    ] = await Promise.all([
      TkDashboardApi.getOverview(params),
      TkDashboardApi.getGenerationTrend(params),
      TkDashboardApi.getFailureAnalysis(params),
      TkDashboardApi.getMaterialHealth(params),
      TkDashboardApi.getQueueHealth(params),
      TkDashboardApi.getFailureDiagnosis(params),
      TkDashboardApi.getSlowTasks(params)
    ])
    overview.value = overviewData
    trendItems.value = trendData?.items || []
    failureAnalysis.value = failureData
    materialHealth.value = materialData
    queueHealth.value = queueData
    failureDiagnosis.value = diagnosisData
    slowTasks.value = slowTaskData
  } finally {
    loading.value = false
  }
}

async function loadLibraries() {
  const page = await TkMaterialApi.getLibraryPage({ pageNo: 1, pageSize: 100 })
  libraries.value = page?.list || []
}

function normalizeQuery(): DashboardQuery {
  return { ...query }
}

function barHeight(value?: number) {
  return Math.max(4, Math.round(((value || 0) / maxTrendCount.value) * 100))
}

function failurePercent(value?: number) {
  return Math.round(((value || 0) / maxFailureCount.value) * 100)
}

function formatNumber(value?: number) {
  return Number(value || 0).toLocaleString('en-US')
}

function formatShortDate(value?: string) {
  return value ? dayjs(value).format('MM-DD') : '--'
}

function formatDuration(value?: number) {
  const seconds = Number(value || 0)
  if (seconds < 60) return `${seconds}${copy.value.seconds}`
  if (seconds < 3600) return `${Math.round(seconds / 60)}min`
  return `${(seconds / 3600).toFixed(1)}h`
}

function failureLabel(value?: string) {
  const labels: Record<string, string> = isEn.value
    ? {
        REFERENCE_DOWNLOAD: 'Reference download',
        OSS_STORAGE: 'OSS storage',
        FFMPEG_RENDER: 'FFmpeg render',
        VOICEOVER: 'Voiceover',
        SUBTITLE: 'Subtitle',
        MATERIAL_MATCHING: 'Material matching',
        TIKTOK_PUBLISH: 'TikTok publish',
        UNKNOWN: 'Unknown'
      }
    : {
        REFERENCE_DOWNLOAD: '参考视频下载',
        OSS_STORAGE: 'OSS 存储',
        FFMPEG_RENDER: 'FFmpeg 合成',
        VOICEOVER: '配音生成',
        SUBTITLE: '字幕生成',
        MATERIAL_MATCHING: '素材匹配',
        TIKTOK_PUBLISH: 'TK 发布',
        UNKNOWN: '未知异常'
      }
  return labels[value || 'UNKNOWN'] || value || labels.UNKNOWN
}

function healthLabel(value?: string) {
  const labels: Record<string, string> = {
    HEALTHY: copy.value.healthy,
    INSUFFICIENT: copy.value.insufficient,
    HAS_FAILED_MATERIALS: copy.value.hasFailedMaterials,
    LOW_ACTIVITY: copy.value.lowActivity
  }
  return labels[value || ''] || copy.value.unknown
}

function healthTagType(value?: string) {
  if (value === 'HEALTHY') return 'success'
  if (value === 'LOW_ACTIVITY') return 'warning'
  return 'danger'
}

function statusLabel(value?: string) {
  const labels: Record<string, string> = isEn.value
    ? {
        PENDING: 'Pending',
        PRECHECKED: 'Prechecked',
        ANALYZING: 'Analyzing',
        SCRIPT_READY: 'Script ready',
        VOICE_READY: 'Voice ready',
        MATERIAL_MATCHING: 'Matching materials',
        MATERIAL_MATCHED: 'Materials matched',
        SUBTITLE_TIMELINE_READY: 'Subtitle timeline',
        VISUAL_ANALYZED: 'Visual analyzed',
        CLIP_PLANNED: 'Clip planned',
        RENDERING: 'Rendering',
        EXPORTING: 'Exporting',
        SUCCESS: 'Success',
        FAILED: 'Failed'
      }
    : {
        PENDING: '排队中',
        PRECHECKED: '预检完成',
        ANALYZING: '分析文案',
        SCRIPT_READY: '文案就绪',
        VOICE_READY: '配音就绪',
        MATERIAL_MATCHING: '匹配素材',
        MATERIAL_MATCHED: '素材已匹配',
        SUBTITLE_TIMELINE_READY: '字幕时间轴',
        VISUAL_ANALYZED: '画面已分析',
        CLIP_PLANNED: '剪辑已规划',
        RENDERING: '合成中',
        EXPORTING: '导出中',
        SUCCESS: '成功',
        FAILED: '失败'
      }
  return labels[value || ''] || value || copy.value.unknown
}

function statusTagType(value?: string) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'PENDING') return 'info'
  return 'warning'
}

function diagnosisHint(item: DashboardFailureDiagnosisItem) {
  if (isEn.value) return item.actionHint || item.label || item.category
  const hints: Record<string, string> = {
    REFERENCE_DOWNLOAD: '检查源视频访问、下载超时和重试情况',
    OSS_STORAGE: '检查 OSS 权限、对象访问和上传结果',
    FFMPEG_RENDER: '检查合成输入、OSS 源文件访问和 FFmpeg 超时',
    VOICEOVER: '检查配音配置、TTS 返回和音频文件',
    SUBTITLE: '检查字幕时间轴、ASS 文件和字幕渲染',
    MATERIAL_MATCHING: '检查素材库可用量和剪辑规划',
    TIKTOK_PUBLISH: '检查账号授权和发布状态',
    UNKNOWN: '打开任务详情查看完整失败原因'
  }
  return hints[item.category] || item.actionHint || item.label || item.category
}

function goGenerationRecords() {
  router.push({ path: '/tk/generation' })
}

function goBatchQueue() {
  router.push({ path: '/tk/generation-batch' })
}

function goGenerationRecordsByStatus(status?: string) {
  router.push({ path: '/tk/generation', query: status ? { status } : {} })
}

function goQueueRecords() {
  router.push({ path: '/tk/generation', query: { status: 'PENDING' } })
}

function goFailedRecords() {
  router.push({ path: '/tk/generation', query: { status: 'FAILED' } })
}

function goGenerationTask(id: number) {
  router.push({ path: '/tk/generation', query: { id } })
}

function goMaterials() {
  router.push({ path: '/tk/material-library' })
}

onMounted(async () => {
  applyQuickRange()
  await loadLibraries()
})
</script>

<style scoped>
.data-dashboard {
  min-height: 100%;
  padding: 24px;
  background: #f5f7fb;
  color: #172033;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.eyebrow {
  display: block;
  margin-bottom: 6px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.dashboard-header h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
}

.dashboard-header p {
  margin: 8px 0 0;
  color: #667085;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 14px;
  margin-bottom: 16px;
  background: #ffffff;
  border: 1px solid #e4e7ef;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.filter-control {
  width: 168px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.metric-card,
.panel {
  background: #ffffff;
  border: 1px solid #e4e7ef;
  border-radius: 8px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.07);
}

.metric-card {
  position: relative;
  min-height: 118px;
  padding: 18px 18px 16px 74px;
}

.metric-card span,
.metric-card em {
  display: block;
  color: #667085;
  font-size: 13px;
  font-style: normal;
}

.metric-card strong {
  display: block;
  margin: 7px 0;
  color: #111827;
  font-size: 28px;
  line-height: 1;
}

.metric-icon {
  position: absolute;
  top: 20px;
  left: 18px;
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  font-size: 22px;
}

.tone-blue {
  color: #2563eb;
  background: #dbeafe;
}

.tone-green {
  color: #059669;
  background: #d1fae5;
}

.tone-red {
  color: #dc2626;
  background: #fee2e2;
}

.tone-violet {
  color: #7c3aed;
  background: #ede9fe;
}

.tone-amber,
.tone-orange {
  color: #d97706;
  background: #fef3c7;
}

.tone-cyan {
  color: #0891b2;
  background: #cffafe;
}

.tone-indigo {
  color: #4f46e5;
  background: #e0e7ff;
}

.analytics-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(360px, 0.95fr);
  gap: 16px;
  margin-bottom: 16px;
}

.analytics-grid.lower {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.panel {
  padding: 18px;
}

.panel-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-title h2 {
  margin: 0;
  font-size: 18px;
}

.panel-title p {
  margin: 6px 0 0;
  color: #667085;
  font-size: 13px;
}

.trend-chart {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(52px, 1fr));
  align-items: end;
  min-height: 268px;
  gap: 10px;
  padding-top: 12px;
}

.trend-column {
  display: grid;
  gap: 8px;
  justify-items: center;
}

.bar-stack {
  display: flex;
  align-items: end;
  justify-content: center;
  gap: 3px;
  width: 100%;
  height: 190px;
  padding: 0 4px;
  border-bottom: 1px solid #e4e7ef;
}

.bar {
  width: 10px;
  min-height: 4px;
  border-radius: 8px 8px 0 0;
}

.bar.success,
.legend-row .success {
  background: #10b981;
}

.bar.failed,
.legend-row .failed {
  background: #ef4444;
}

.bar.running,
.legend-row .running {
  background: #3b82f6;
}

.trend-column strong {
  font-size: 12px;
  color: #475467;
}

.trend-column small {
  color: #98a2b3;
}

.legend-row {
  display: flex;
  gap: 18px;
  margin-top: 12px;
  color: #667085;
  font-size: 13px;
}

.legend-row span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-row i {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.rank-list {
  display: grid;
  gap: 14px;
}

.rank-row {
  display: grid;
  grid-template-columns: minmax(0, 150px) 1fr 42px;
  gap: 12px;
  align-items: center;
}

.rank-row strong,
.rank-row span {
  display: block;
}

.rank-row strong {
  color: #1f2937;
}

.rank-row span {
  margin-top: 3px;
  color: #98a2b3;
  font-size: 12px;
}

.rank-row em {
  color: #475467;
  font-style: normal;
  text-align: right;
}

.queue-health-panel,
.slow-task-panel {
  margin-bottom: 16px;
}

.queue-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.queue-metric,
.diagnosis-row {
  width: 100%;
  padding: 14px;
  text-align: left;
  background: #f8fafc;
  border: 1px solid #e4e7ef;
  border-radius: 8px;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.16s ease;
}

.queue-metric:hover,
.diagnosis-row:hover {
  border-color: #93c5fd;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.12);
  transform: translateY(-1px);
}

.queue-metric span,
.queue-metric em {
  display: block;
  color: #667085;
  font-size: 12px;
  font-style: normal;
}

.queue-metric strong {
  display: block;
  margin: 8px 0;
  color: #111827;
  font-size: 24px;
  line-height: 1;
}

.attention-list,
.diagnosis-list {
  display: grid;
  gap: 10px;
}

.attention-row,
.diagnosis-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.attention-row {
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid #edf0f5;
  border-radius: 8px;
}

.attention-row strong,
.attention-row span,
.diagnosis-row strong,
.diagnosis-row span {
  display: block;
}

.attention-row strong,
.diagnosis-row strong {
  color: #1f2937;
}

.attention-row span,
.diagnosis-row span {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.diagnosis-row em {
  min-width: 42px;
  color: #dc2626;
  font-size: 20px;
  font-style: normal;
  font-weight: 700;
  text-align: right;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .analytics-grid,
  .analytics-grid.lower {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .data-dashboard {
    padding: 14px;
  }

  .dashboard-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-control {
    width: 100%;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .queue-metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
