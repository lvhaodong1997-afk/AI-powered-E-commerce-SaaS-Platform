<template>
  <ContentWrap class="generation-record-page">
    <div class="record-workbench-head">
      <div>
        <div class="record-kicker">生成链路记录</div>
        <h2>{{ activeTab === 'analysis' ? 'AI 分析记录' : '视频生成记录' }}</h2>
        <p>
          {{
            activeTab === 'analysis'
              ? 'Check reference analysis, selling points, and script title generation status.'
              : 'Track video generation progress and quickly handle failures.'
          }}
        </p>
      </div>
      <el-button type="primary" plain @click="push('/tk/dashboard')">
        <Icon icon="ep:video-play" class="mr-5px" /> 去生成视频
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="AI 分析记录" name="analysis" />
      <el-tab-pane label="视频生成记录" name="generation" />
    </el-tabs>

    <div class="record-stats">
      <div
        v-for="item in activeRecordStats"
        :key="item.label"
        class="record-stat"
        :class="item.tone"
      >
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <el-form
      v-if="activeTab === 'analysis'"
      :model="analysisQuery"
      ref="analysisQueryFormRef"
      :inline="true"
      label-width="90px"
      class="-mb-15px"
    >
      <el-form-item label="关键词" prop="keyword">
        <el-input
          v-model="analysisQuery.keyword"
          placeholder="链接 / 产品 / 卖点"
          clearable
          class="!w-260px"
          @keyup.enter="handleAnalysisQuery"
        />
      </el-form-item>
      <el-form-item label="流水号" prop="businessTraceId">
        <el-input
          v-model="analysisQuery.businessTraceId"
          placeholder="请输入业务流水号"
          clearable
          class="!w-260px"
          @keyup.enter="handleAnalysisQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="analysisQuery.status"
          clearable
          placeholder="请选择状态"
          class="!w-180px"
        >
          <el-option
            v-for="status in analysisStatuses"
            :key="status"
            :label="statusLabel(status)"
            :value="status"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleAnalysisQuery"
          ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
        >
        <el-button @click="resetAnalysisQuery"
          ><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button
        >
      </el-form-item>
    </el-form>

    <el-form
      v-else
      :model="generationQuery"
      ref="generationQueryFormRef"
      :inline="true"
      label-width="90px"
      class="-mb-15px"
    >
      <el-form-item label="任务标题" prop="title">
        <el-input
          v-model="generationQuery.title"
          placeholder="任务标题"
          clearable
          class="!w-220px"
          @keyup.enter="handleGenerationQuery"
        />
      </el-form-item>
      <el-form-item label="流水号" prop="businessTraceId">
        <el-input
          v-model="generationQuery.businessTraceId"
          placeholder="请输入业务流水号"
          clearable
          class="!w-260px"
          @keyup.enter="handleGenerationQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
          v-model="generationQuery.status"
          clearable
          placeholder="请选择状态"
          class="!w-180px"
        >
          <el-option
            v-for="status in generationStatuses"
            :key="status"
            :label="generationStatusLabel(status)"
            :value="status"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleGenerationQuery"
          ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
        >
        <el-button @click="resetGenerationQuery"
          ><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap v-if="activeTab === 'analysis'">
    <el-table v-loading="analysisLoading" :data="analysisList" stripe>
      <el-table-column label="分析对象" min-width="280">
        <template #default="scope">
          <div class="record-title">{{ scope.row.productName || 'TikTok 对标链接' }}</div>
          <div class="record-link">{{ scope.row.sourceUrl }}</div>
          <div class="creator-line">{{ analysisCreatorLabel(scope.row) }}</div>
          <div class="trace-line">
            <span>流水号：{{ scope.row.businessTraceId || '-' }}</span>
            <el-button
              v-if="scope.row.businessTraceId"
              link
              type="primary"
              @click="copyBusinessTraceId(scope.row.businessTraceId)"
            >
              复制
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="素材库ID" prop="libraryId" width="110" />
      <el-table-column label="链路状态" min-width="260">
        <template #default="scope">
          <div class="stage-tags">
            <el-tag :type="stageTagType(scope.row.analysisStageStatus)">
              Reference analysis: {{ stageStatusLabel(scope.row.analysisStageStatus) }}
            </el-tag>
            <el-tag :type="stageTagType(scope.row.sellingPointStageStatus)">
              Selling point analysis: {{ stageStatusLabel(scope.row.sellingPointStageStatus) }}
            </el-tag>
            <el-tag :type="stageTagType(scope.row.scriptStageStatus)">
              Script title: {{ stageStatusLabel(scope.row.scriptStageStatus) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        label="核心卖点"
        prop="coreSellingPoints"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column label="默认文案标题" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ selectedScriptTitle(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="视频时长" width="90">
        <template #default="scope">{{
          scope.row.videoDuration ? `${scope.row.videoDuration}s` : '-'
        }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <el-tag
            :type="
              scope.row.status === 'SUCCESS'
                ? 'success'
                : scope.row.status === 'FAILED'
                  ? 'danger'
                  : 'warning'
            "
          >
            {{ statusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="180" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.status === 'FAILED' ? scope.row.failReason || '-' : '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" min-width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="goReplayFromAnalysis(scope.row)">回溯</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="analysisTotal"
      v-model:page="analysisQuery.pageNo"
      v-model:limit="analysisQuery.pageSize"
      @pagination="getAnalysisList"
    />
  </ContentWrap>

  <ContentWrap v-else>
    <el-table v-loading="generationLoading" :data="generationList" stripe>
      <el-table-column label="任务标题" min-width="260">
        <template #default="scope">
          <div class="record-title">{{ scope.row.title || '-' }}</div>
          <div class="record-badges">
            <el-tag size="small" :type="scope.row.materialPurpose === 'LEAD_GENERATION' ? 'warning' : 'success'">
              {{ materialPurposeLabel(scope.row.materialPurpose) }}
            </el-tag>
            <el-tag v-if="scope.row.batchId" size="small" type="info">批次 {{ scope.row.batchId }}</el-tag>
          </div>
          <div class="creator-line">{{ generationCreatorLabel(scope.row) }}</div>
          <div class="trace-line">
            <span>流水号：{{ scope.row.businessTraceId || '-' }}</span>
            <el-button
              v-if="scope.row.businessTraceId"
              link
              type="primary"
              @click="copyBusinessTraceId(scope.row.businessTraceId)"
            >
              复制
            </el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="目标时长" prop="targetDuration" width="90">
        <template #default="scope"
          >{{ scope.row.targetDuration || scope.row.referenceDuration || '-' }}s</template
        >
      </el-table-column>
      <el-table-column label="进度" width="180">
        <template #default="scope">
          <div class="progress-cell">
            <el-progress :percentage="scope.row.progress || 0" />
            <span>{{ scope.row.currentStep || generationStatusLabel(scope.row.status) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="最新发布链接" min-width="260">
        <template #default="scope">
          <div v-if="scope.row.latestPublishUrl" class="publish-link-cell">
            <el-link
              :href="scope.row.latestPublishUrl"
              target="_blank"
              type="primary"
              :title="scope.row.latestPublishUrl"
            >
              {{ scope.row.latestPublishAccountName || '发布链接' }}
            </el-link>
            <el-button link type="primary" @click="openPublishUrlDialog(scope.row)">编辑</el-button>
          </div>
          <el-button v-else link type="primary" @click="openPublishUrlDialog(scope.row)">
            登记链接
          </el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="scope">
          <el-tag
            :type="
              scope.row.status === 'SUCCESS'
                ? 'success'
                : scope.row.status === 'FAILED'
                  ? 'danger'
                  : 'warning'
            "
          >
            {{ generationStatusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.status === 'FAILED' ? scope.row.failReason || '-' : '-' }}</template>
      </el-table-column>
      <el-table-column label="输出文件" min-width="240">
        <template #default="scope">
          <div v-if="scope.row.outputUrl" class="output-file">
            <el-link
              type="primary"
              :href="scope.row.outputUrl"
              target="_blank"
              :download="outputDownloadFileName(scope.row)"
              :title="`${outputDisplayName(scope.row)}\n${scope.row.outputUrl}`"
            >
              {{ outputDisplayName(scope.row) }}
            </el-link>
            <el-button
              v-if="scope.row.status === 'SUCCESS'"
              link
              type="primary"
              @click="downloadOutput(scope.row)"
            >
              <Icon icon="ep:download" class="mr-3px" />下载
            </el-button>
          </div>
          <div v-else-if="isOutputExpired(scope.row)" class="output-expired">
            <Icon icon="ep:clock" class="mr-3px" />视频已过期
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 'SUCCESS' && scope.row.outputUrl"
            link
            type="success"
            @click="downloadOutput(scope.row)"
          >
            下载
          </el-button>
          <el-button
            v-if="scope.row.status === 'FAILED'"
            link
            type="danger"
            @click="retryGeneration(scope.row)"
          >
            重试
          </el-button>
          <el-button link type="primary" @click="openGenerationDetail(scope.row)">明细</el-button>
          <el-button link type="primary" @click="goReplayFromGeneration(scope.row)">回溯</el-button>
          <el-button link type="primary" @click="openClipDetail(scope.row)"> 剪辑明细 </el-button>
          <el-button
            v-if="scope.row.status === 'SUCCESS' && scope.row.outputUrl"
            link
            type="primary"
            @click="goPublish(scope.row)"
            v-hasPermi="['tk:tiktok-publish:create']"
          >
            发布
          </el-button>
          <el-button link type="primary" @click="openPublishUrlDialog(scope.row)">登记链接</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="generationTotal"
      v-model:page="generationQuery.pageNo"
      v-model:limit="generationQuery.pageSize"
      @pagination="getGenerationList"
    />
  </ContentWrap>

  <el-dialog v-model="clipDetailVisible" title="剪辑明细" width="980px" class="clip-detail-dialog">
    <div class="clip-detail-head">
      <div>
        <strong>{{ selectedClipTask?.title || '生成任务' }}</strong>
        <span
          >共 {{ selectedClipDetails.length }} 个片段，合计 {{ selectedClipTotalDuration }}s</span
        >
      </div>
    </div>
    <el-table :data="selectedClipDetails" stripe max-height="520">
      <el-table-column label="顺序" prop="orderNo" width="64" />
      <el-table-column label="来源" prop="sourceTypeLabel" width="88" />
      <el-table-column label="用途" prop="sectionLabel" min-width="130" show-overflow-tooltip />
      <el-table-column label="素材名称" prop="fileName" min-width="180" show-overflow-tooltip />
      <el-table-column label="素材ID" prop="materialVideoIdText" width="82" />
      <el-table-column label="使用方式" prop="usageMode" width="120" />
      <el-table-column label="素材时长" width="86">
        <template #default="scope">{{ scope.row.durationSecond }}s</template>
      </el-table-column>
      <el-table-column label="抽取说明" prop="reason" min-width="260" show-overflow-tooltip />
    </el-table>
  </el-dialog>

  <el-drawer
    v-model="generationDetailVisible"
    title="生成详情"
    size="720px"
    class="generation-detail-drawer"
  >
    <el-skeleton v-if="generationDetailLoading" :rows="8" animated />
    <div v-else-if="selectedGenerationDetail" class="generation-detail">
      <div class="detail-title">
        <strong>{{ selectedGenerationDetail.title || '生成任务' }}</strong>
        <el-tag
          :type="
            selectedGenerationDetail.status === 'SUCCESS'
              ? 'success'
              : selectedGenerationDetail.status === 'FAILED'
                ? 'danger'
                : 'warning'
          "
        >
          {{ generationStatusLabel(selectedGenerationDetail.status) }}
        </el-tag>
      </div>
      <video
        v-if="selectedGenerationDetail.outputUrl"
        :src="selectedGenerationDetail.outputUrl"
        controls
        preload="metadata"
        playsinline
        class="generation-output-video"
      ></video>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="任务ID">{{
          selectedGenerationDetail.id
        }}</el-descriptions-item>
        <el-descriptions-item label="流水号">
          <span>{{ selectedGenerationDetail.businessTraceId || '-' }}</span>
          <el-button
            v-if="selectedGenerationDetail.businessTraceId"
            link
            type="primary"
            @click="copyBusinessTraceId(selectedGenerationDetail.businessTraceId)"
          >
            复制
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="TikTok 链接">
          <el-link
            v-if="selectedGenerationDetail.sourceUrl"
            :href="selectedGenerationDetail.sourceUrl"
            target="_blank"
            type="primary"
          >
            {{ selectedGenerationDetail.sourceUrl }}
          </el-link>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="AI文案">
          <span class="detail-preline">{{ selectedGenerationDetail.scriptText || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="配音">{{
          selectedGenerationDetail.audioUrl || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="口播状态">
          {{ enabledStatusLabel(selectedGenerationDetail.voiceEnabled, true) }}
        </el-descriptions-item>
        <el-descriptions-item label="BGM状态">
          {{ enabledStatusLabel(selectedGenerationDetail.bgmEnabled, false) }}
        </el-descriptions-item>
        <el-descriptions-item label="字幕状态">
          {{ enabledStatusLabel(selectedGenerationDetail.subtitleEnabled, true) }}
        </el-descriptions-item>
        <el-descriptions-item label="字幕">{{
          selectedGenerationDetail.subtitleUrl || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="最新发布链接">
          <div v-if="selectedGenerationDetail.latestPublishUrl" class="detail-publish-link">
            <el-link
              :href="selectedGenerationDetail.latestPublishUrl"
              target="_blank"
              type="primary"
            >
              {{
                selectedGenerationDetail.latestPublishAccountName ||
                selectedGenerationDetail.latestPublishUrl
              }}
            </el-link>
            <el-button link type="primary" @click="openPublishUrlDialog(selectedGenerationDetail)">
              编辑
            </el-button>
          </div>
          <el-button v-else link type="primary" @click="openPublishUrlDialog(selectedGenerationDetail)">
            登记链接
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="当前步骤">
          {{ selectedGenerationDetail.currentStep || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="失败原因">
          {{ selectedGenerationDetail.status === 'FAILED' ? selectedGenerationDetail.failReason || '-' : '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <div v-if="clipSectionSummary(selectedGenerationDetail).length" class="detail-section">
        <div class="detail-section-title">剪辑结构</div>
        <div class="clip-sections">
          <el-tag
            v-for="item in clipSectionSummary(selectedGenerationDetail)"
            :key="item.section"
            :type="item.type"
          >
            {{ item.label }} 路 {{ item.duration }}s
          </el-tag>
        </div>
      </div>
    </div>
  </el-drawer>

  <el-dialog v-model="publishUrlDialogVisible" title="登记发布链接" width="560px">
    <el-form ref="publishUrlFormRef" :model="publishUrlForm" :rules="publishUrlRules" label-width="110px">
      <el-form-item label="生成任务ID">
        <el-input :model-value="publishUrlForm.generationTaskId" disabled />
      </el-form-item>
      <el-form-item label="发布明细ID">
        <el-input :model-value="publishUrlForm.publishDetailId || '-'" disabled />
      </el-form-item>
      <el-form-item label="发布链接" prop="publishUrl">
        <el-input v-model="publishUrlForm.publishUrl" placeholder="请输入 TikTok 发布链接" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="publishUrlDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="publishUrlSubmitting" @click="submitPublishUrl">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import {
  TkGenerationApi,
  TkGenerationTaskSummaryVO,
  TkGenerationTaskStatusVO,
  TkGenerationTaskVO
} from '@/api/tk/generation'
import {
  TkReferenceApi,
  TkReferenceAnalysisVO,
  TkReferenceScriptOptionVO
} from '@/api/tk/reference'
import { TkTiktokPublishApi } from '@/api/tk/videoPublishCenter'
import {
  buildGenerationCreatorLabel,
  buildGenerationOutputDisplayName,
  buildGenerationOutputDownloadName
} from '@/utils/tkGenerationOutputName'
import { buildClipPlanDetails, buildClipSectionSummary, ClipPlanDetailItem } from './clipPlan'

defineOptions({ name: 'TkGeneration' })

const TK_GENERATION_REPLAY_KEY = 'tk:generation:replay'
const { push } = useRouter()
const route = useRoute()
const message = useMessage()
const activeTab = ref<'analysis' | 'generation'>('analysis')

const analysisLoading = ref(false)
const analysisList = ref<TkReferenceAnalysisVO[]>([])
const analysisTotal = ref(0)
const analysisQueryFormRef = ref()
const analysisQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  businessTraceId: undefined,
  keyword: undefined,
  status: undefined
})

const generationLoading = ref(false)
const generationList = ref<TkGenerationTaskSummaryVO[]>([])
const generationTotal = ref(0)
const generationQueryFormRef = ref()
const clipDetailVisible = ref(false)
const selectedClipTask = ref<TkGenerationTaskVO>()
const selectedClipDetails = ref<ClipPlanDetailItem[]>([])
const selectedClipTotalDuration = computed(() =>
  selectedClipDetails.value.reduce((total, item) => total + item.durationSecond, 0)
)
const generationQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  id: undefined as number | undefined,
  businessTraceId: undefined as string | undefined,
  title: undefined as string | undefined,
  status: undefined as string | undefined
})
const generationDetailVisible = ref(false)
const generationDetailLoading = ref(false)
const selectedGenerationDetail = ref<TkGenerationTaskVO>()
const publishUrlDialogVisible = ref(false)
const publishUrlSubmitting = ref(false)
const publishUrlFormRef = ref()
const publishUrlForm = reactive({
  generationTaskId: undefined as number | undefined,
  publishDetailId: undefined as number | undefined,
  publishUrl: ''
})
const publishUrlRules = {
  publishUrl: [{ required: true, message: '发布链接不能为空', trigger: 'blur' }]
}

const analysisStatuses = ['WAITING', 'RUNNING', 'SUCCESS', 'FAILED']
const generationStatuses = [
  'PENDING',
  'ANALYZING',
  'SCRIPT_READY',
  'VOICE_READY',
  'MATERIAL_MATCHING',
  'MATERIAL_MATCHED',
  'SUBTITLE_TIMELINE_READY',
  'VISUAL_ANALYZED',
  'CLIP_PLANNED',
  'RENDERING',
  'EXPORTING',
  'SUCCESS',
  'FAILED'
]
const runningGenerationStatuses = new Set([
  'PENDING',
  'ANALYZING',
  'SCRIPT_READY',
  'VOICE_READY',
  'MATERIAL_MATCHING',
  'MATERIAL_MATCHED',
  'SUBTITLE_TIMELINE_READY',
  'VISUAL_ANALYZED',
  'CLIP_PLANNED',
  'RENDERING',
  'EXPORTING'
])
type RecordStatTone = 'default' | 'success' | 'warning' | 'danger'
type RecordStatItem = {
  label: string
  value: number
  tone: RecordStatTone
}
let generationPollingTimer: number | undefined
let generationPollingRequesting = false
const generationPollingStartedAt = ref<number>()

const materialPurposeLabel = (value?: string) =>
  value === 'LEAD_GENERATION' ? '引流素材' : '电商素材'

const analysisStats = computed<RecordStatItem[]>(() => [
  { label: '全部分析', value: analysisTotal.value, tone: 'default' },
  {
    label: '分析中',
    value: analysisList.value.filter((item) => ['WAITING', 'RUNNING'].includes(item.status || '')).length,
    tone: 'warning'
  },
  {
    label: '已成功',
    value: analysisList.value.filter((item) => item.status === 'SUCCESS').length,
    tone: 'success'
  },
  {
    label: '失败',
    value: analysisList.value.filter((item) => item.status === 'FAILED').length,
    tone: 'danger'
  }
])
const generationStats = computed<RecordStatItem[]>(() => [
  { label: '全部任务', value: generationTotal.value, tone: 'default' },
  {
    label: '生成中',
    value: generationList.value.filter((item) => runningGenerationStatuses.has(item.status || '')).length,
    tone: 'warning'
  },
  {
    label: '已成功',
    value: generationList.value.filter((item) => item.status === 'SUCCESS').length,
    tone: 'success'
  },
  {
    label: '失败',
    value: generationList.value.filter((item) => item.status === 'FAILED').length,
    tone: 'danger'
  },
  {
    label: '可下载',
    value: generationList.value.filter((item) => item.status === 'SUCCESS' && item.outputUrl).length,
    tone: 'success'
  }
])
const activeRecordStats = computed(() =>
  activeTab.value === 'analysis' ? analysisStats.value : generationStats.value
)

const statusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    WAITING: '待执行',
    RUNNING: '分析中',
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return status ? labels[status] || status : '-'
}

const generationStatusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    PENDING: '排队中',
    ANALYZING: '分析文案',
    SCRIPT_READY: '文案就绪',
    VOICE_READY: '配音就绪',
    MATERIAL_MATCHING: '随机抽取素材中',
    MATERIAL_MATCHED: '素材随机抽取',
    SUBTITLE_TIMELINE_READY: '字幕时间轴已就绪',
    VISUAL_ANALYZED: '画面分析就绪',
    CLIP_PLANNED: '剪辑方案就绪',
    RENDERING: '渲染中',
    EXPORTING: '导出中',
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return status ? labels[status] || status : '-'
}

const stageStatusLabel = (status?: string) => {
  const labels: Record<string, string> = {
    SUCCESS: '已完成',
    RUNNING: '分析中',
    FAILED: '失败',
    WAITING: '待生成'
  }
  return status ? labels[status] || status : '-'
}

const stageTagType = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

const clipSectionSummary = (row: TkGenerationTaskVO) => buildClipSectionSummary(row.clipPlan)

const clipPlanDetails = (row: TkGenerationTaskVO) => buildClipPlanDetails(row.clipPlan)

const enabledStatusLabel = (value: boolean | undefined, defaultEnabled: boolean) =>
  (value ?? defaultEnabled) ? '已开启' : '已关闭'

const selectedScriptTitle = (row: TkReferenceAnalysisVO) => {
  const options = row.scriptOptions || []
  const selected = options.find((option: TkReferenceScriptOptionVO) => option.selected)
  return selected?.title || options[0]?.title || '-'
}

const isOutputExpired = (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) =>
  row.status === 'SUCCESS' && !row.outputUrl

const outputDisplayName = (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) =>
  buildGenerationOutputDisplayName(row)

const outputDownloadFileName = (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) =>
  buildGenerationOutputDownloadName(row)

const generationCreatorLabel = (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) =>
  buildGenerationCreatorLabel(row)

const analysisCreatorLabel = (row: TkReferenceAnalysisVO) => {
  const creatorName = row.creatorName == null ? '' : String(row.creatorName).trim()
  if (creatorName) {
    return `分析用户：${creatorName}`
  }
  const creator = row.creator == null ? '' : String(row.creator).trim()
  return `分析用户：${creator ? `用户ID ${creator}` : '-'}`
}

const downloadOutput = (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  if (!row.outputUrl) {
    message.warning('当前任务暂无输出文件')
    return
  }
  const link = document.createElement('a')
  link.href = row.outputUrl
  link.download = outputDownloadFileName(row)
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const copyBusinessTraceId = async (businessTraceId?: string) => {
  if (!businessTraceId) return
  await navigator.clipboard.writeText(businessTraceId)
  message.success('业务流水号已复制')
}

const fetchGenerationDetail = async (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  if (!row.id) {
    throw new Error('missing generation task id')
  }
  return (await TkGenerationApi.getGeneration(row.id)) as TkGenerationTaskVO
}

const openGenerationDetail = async (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  generationDetailVisible.value = true
  generationDetailLoading.value = true
  try {
    selectedGenerationDetail.value = await fetchGenerationDetail(row)
  } finally {
    generationDetailLoading.value = false
  }
}

const openClipDetail = async (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  const detail = await fetchGenerationDetail(row)
  selectedClipTask.value = detail
  selectedClipDetails.value = clipPlanDetails(detail)
  clipDetailVisible.value = true
}

const openPublishUrlDialog = async (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  const detail = row.id && row.latestPublishDetailId ? await fetchGenerationDetail(row) : row
  publishUrlForm.generationTaskId = detail.id
  publishUrlForm.publishDetailId = detail.latestPublishDetailId
  publishUrlForm.publishUrl = detail.latestPublishUrl || ''
  publishUrlDialogVisible.value = true
}

const submitPublishUrl = async () => {
  await publishUrlFormRef.value?.validate()
  if (!publishUrlForm.generationTaskId) return
  publishUrlSubmitting.value = true
  try {
    await TkTiktokPublishApi.registerPublishUrl({
      generationTaskId: publishUrlForm.generationTaskId,
      publishDetailId: publishUrlForm.publishDetailId,
      publishUrl: publishUrlForm.publishUrl
    })
    message.success('发布链接已登记')
    publishUrlDialogVisible.value = false
    await getGenerationList()
  } finally {
    publishUrlSubmitting.value = false
  }
}

const getAnalysisList = async () => {
  analysisLoading.value = true
  try {
    const data = await TkReferenceApi.getAnalysisPage(analysisQuery)
    analysisList.value = data.list
    analysisTotal.value = data.total
  } finally {
    analysisLoading.value = false
  }
}

const hasRunningGenerationTasks = () =>
  generationList.value.some((item) => runningGenerationStatuses.has(item.status || ''))

const clearGenerationPolling = () => {
  if (generationPollingTimer) {
    window.clearTimeout(generationPollingTimer)
  }
  generationPollingTimer = undefined
  generationPollingStartedAt.value = undefined
}

const nextGenerationPollingInterval = () => {
  const startedAt = generationPollingStartedAt.value || Date.now()
  const elapsed = Date.now() - startedAt
  if (elapsed < 60_000) return 5000
  if (elapsed < 300_000) return 10000
  return 15000
}

const mergeGenerationStatus = (items: TkGenerationTaskStatusVO[]) => {
  const statusMap = new Map(items.map((item) => [item.id, item]))
  generationList.value = generationList.value.map((item) => {
    if (!item.id) return item
    const status = statusMap.get(item.id)
    return status ? { ...item, ...status } : item
  })
}

const refreshGenerationStatuses = async () => {
  if (generationPollingRequesting) return
  const ids = generationList.value
    .filter((item) => item.id && runningGenerationStatuses.has(item.status || ''))
    .map((item) => item.id as number)
  if (!ids.length) {
    clearGenerationPolling()
    return
  }
  generationPollingRequesting = true
  try {
    const statuses = await TkGenerationApi.getGenerationStatusBatch(ids)
    mergeGenerationStatus(statuses)
  } finally {
    generationPollingRequesting = false
    syncGenerationPolling()
  }
}

const syncGenerationPolling = () => {
  if (activeTab.value !== 'generation' || !hasRunningGenerationTasks()) {
    clearGenerationPolling()
    return
  }
  if (generationPollingTimer) return
  generationPollingStartedAt.value ||= Date.now()
  generationPollingTimer = window.setTimeout(() => {
    generationPollingTimer = undefined
    refreshGenerationStatuses()
  }, nextGenerationPollingInterval())
}

const getGenerationList = async () => {
  clearGenerationPolling()
  generationLoading.value = true
  try {
    const data = await TkGenerationApi.getGenerationSummaryPage(generationQuery)
    generationList.value = data.list
    generationTotal.value = data.total
  } finally {
    generationLoading.value = false
    syncGenerationPolling()
  }
}

const handleTabChange = () => {
  if (activeTab.value === 'analysis') {
    clearGenerationPolling()
    if (!analysisList.value.length) {
      getAnalysisList()
    }
  }
  if (activeTab.value === 'generation') {
    getGenerationList()
  }
}

const handleAnalysisQuery = () => {
  analysisQuery.pageNo = 1
  getAnalysisList()
}

const resetAnalysisQuery = () => {
  analysisQueryFormRef.value?.resetFields()
  handleAnalysisQuery()
}

const handleGenerationQuery = () => {
  generationQuery.pageNo = 1
  getGenerationList()
}

const resetGenerationQuery = () => {
  generationQueryFormRef.value?.resetFields()
  generationQuery.id = undefined
  handleGenerationQuery()
}

const retryGeneration = async (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  if (!row.id) return
  await TkGenerationApi.retryGeneration(row.id)
  message.success('重试任务已提交')
  getGenerationList()
}

const goPublish = (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  push({ path: '/tk/video-publish-center', query: { generationTaskId: row.id } })
}

const goReplayFromAnalysis = (row: TkReferenceAnalysisVO) => {
  sessionStorage.setItem(
    TK_GENERATION_REPLAY_KEY,
    JSON.stringify({
      type: 'analysis',
      analysis: row,
      createdAt: Date.now()
    })
  )
  push({ path: '/tk/dashboard', query: { replay: 'analysis', replayId: row.id } })
}

const goReplayFromGeneration = async (row: TkGenerationTaskSummaryVO | TkGenerationTaskVO) => {
  const detail = await fetchGenerationDetail(row)
  sessionStorage.setItem(
    TK_GENERATION_REPLAY_KEY,
    JSON.stringify({
      type: 'generation',
      generation: detail,
      createdAt: Date.now()
    })
  )
  push({ path: '/tk/dashboard', query: { replay: 'generation', replayId: row.id } })
}

const applyRouteGenerationFilters = () => {
  const id = Number(route.query.id || 0)
  const status = typeof route.query.status === 'string' ? route.query.status : undefined
  if (!id && !status) {
    getAnalysisList()
    return
  }
  activeTab.value = 'generation'
  generationQuery.pageNo = 1
  generationQuery.id = id > 0 ? id : undefined
  generationQuery.status = status
  getGenerationList()
}

onMounted(applyRouteGenerationFilters)
onBeforeUnmount(clearGenerationPolling)
</script>

<style scoped>
.generation-record-page {
  background: #f3f6fb;
}

.record-workbench-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.record-kicker {
  margin-bottom: 6px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.record-workbench-head h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 30px;
}

.record-workbench-head p {
  margin: 6px 0 0;
  color: #667085;
}

.record-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin: 2px 0 14px;
}

.record-stat {
  min-height: 72px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, .05);
}

.record-stat span {
  display: block;
  margin-bottom: 8px;
  color: #667085;
  font-size: 12px;
}

.record-stat strong {
  color: #111827;
  font-size: 24px;
  line-height: 28px;
}

.record-stat.success {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.record-stat.success strong {
  color: #15803d;
}

.record-stat.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.record-stat.warning strong {
  color: #c2410c;
}

.record-stat.danger {
  border-color: #fecaca;
  background: #fef2f2;
}

.record-stat.danger strong {
  color: #b91c1c;
}

.record-title {
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.record-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.record-link {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stage-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.clip-sections {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.output-file {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.output-file :deep(.el-link) {
  min-width: 0;
  max-width: 220px;
}

.output-file :deep(.el-link__inner) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.output-expired {
  display: inline-flex;
  align-items: center;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 600;
}

.creator-line {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.progress-cell {
  display: grid;
  gap: 4px;
}

.progress-cell span {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.publish-link-cell,
.detail-publish-link {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.clip-detail-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -4px 0 14px;
  gap: 12px;
}

.clip-detail-head strong {
  display: block;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.clip-detail-head span {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.generation-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-title strong {
  min-width: 0;
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generation-output-video {
  width: 100%;
  max-height: 420px;
  background: #111827;
  border-radius: 6px;
}

.detail-preline {
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-section-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 768px) {
  .record-workbench-head {
    flex-direction: column;
  }

  .record-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
