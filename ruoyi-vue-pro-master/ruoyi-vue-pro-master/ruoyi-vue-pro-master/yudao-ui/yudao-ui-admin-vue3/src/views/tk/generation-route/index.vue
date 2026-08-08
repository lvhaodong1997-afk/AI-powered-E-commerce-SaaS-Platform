<template>
  <div class="generation-route-page">
    <header class="route-header">
      <div>
        <span class="eyebrow">{{ copy.eyebrow }}</span>
        <h1>{{ copy.title }}</h1>
        <p>{{ copy.subtitle }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="goDashboard">
          <Icon icon="ep:back" />
          {{ copy.backHome }}
        </el-button>
        <el-button type="primary" :loading="loading || statisticsLoading" @click="loadAll">
          <Icon icon="ep:refresh" />
          {{ copy.refresh }}
        </el-button>
      </div>
    </header>

    <section class="route-metrics" v-loading="statisticsLoading">
      <article v-for="metric in summaryMetrics" :key="metric.label" class="metric-card">
        <div class="metric-icon" :class="`tone-${metric.tone}`">
          <Icon :icon="metric.icon" />
        </div>
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <em>{{ metric.hint }}</em>
      </article>
    </section>

    <ContentWrap class="route-query-panel">
      <el-form
        ref="queryFormRef"
        :model="query"
        :inline="true"
        label-width="100px"
        class="-mb-15px"
      >
        <el-form-item :label="copy.materialPurpose" prop="materialPurpose">
          <el-select
            v-model="query.materialPurpose"
            clearable
            :placeholder="copy.all"
            class="!w-190px"
            @change="handleQuery"
          >
            <el-option v-for="item in materialPurposeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="copy.productCategory" prop="productCategoryCode">
          <el-select
            v-model="query.productCategoryCode"
            clearable
            filterable
            :placeholder="copy.all"
            class="!w-210px"
            @change="handleQuery"
          >
            <el-option v-for="item in productCategoryOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="copy.routeCode" prop="routeCode">
          <el-input
            v-model="query.routeCode"
            clearable
            class="!w-190px"
            :placeholder="copy.routeCodePlaceholder"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item :label="copy.enabled" prop="enabled">
          <el-select v-model="query.enabled" clearable :placeholder="copy.all" class="!w-140px" @change="handleQuery">
            <el-option :label="copy.enabledYes" :value="true" />
            <el-option :label="copy.enabledNo" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleQuery">
            <Icon icon="ep:search" class="mr-5px" />
            {{ copy.search }}
          </el-button>
          <el-button @click="resetQuery">
            <Icon icon="ep:refresh-left" class="mr-5px" />
            {{ copy.reset }}
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column :label="copy.materialPurpose" prop="materialPurpose" width="150">
          <template #default="{ row }">{{ purposeLabel(row.materialPurpose) }}</template>
        </el-table-column>
        <el-table-column :label="copy.productCategory" prop="productCategoryCode" min-width="180">
          <template #default="{ row }">{{ categoryLabel(row.productCategoryCode) }}</template>
        </el-table-column>
        <el-table-column :label="copy.routeCode" prop="routeCode" min-width="170" />
        <el-table-column :label="copy.routeName" prop="routeName" min-width="190" />
        <el-table-column :label="copy.version" prop="routeVersion" width="90" />
        <el-table-column :label="copy.weight" prop="trafficWeight" width="100" />
        <el-table-column :label="copy.abGroup" prop="abGroup" width="120" />
        <el-table-column :label="copy.enabled" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? copy.enabledYes : copy.enabledNo }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="copy.routeConfig" min-width="260">
          <template #default="{ row }">
            <code class="config-preview">{{ summarizeConfig(row.routeConfig) }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="copy.updatedAt" prop="updateTime" width="180" />
        <el-table-column :label="copy.actions" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)" v-hasPermi="['tk:generation:create']">
              {{ copy.edit }}
            </el-button>
            <el-button link type="primary" @click="openHistory(row)">
              {{ copy.history }}
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
    </ContentWrap>

    <ContentWrap>
      <div class="section-title">
        <div>
          <h2>{{ copy.statisticsTitle }}</h2>
          <p>{{ copy.statisticsDesc }}</p>
        </div>
      </div>
      <el-table v-loading="statisticsLoading" :data="statistics" stripe>
        <el-table-column :label="copy.productCategory" min-width="180">
          <template #default="{ row }">{{ categoryLabel(row.productCategoryCode) }}</template>
        </el-table-column>
        <el-table-column :label="copy.routeCode" prop="routeCode" min-width="170" />
        <el-table-column :label="copy.generated" prop="generationCount" width="110" />
        <el-table-column :label="copy.success" prop="successCount" width="110" />
        <el-table-column :label="copy.failed" prop="failedCount" width="110" />
        <el-table-column :label="copy.running" prop="runningCount" width="110" />
        <el-table-column :label="copy.successRate" width="120">
          <template #default="{ row }">{{ formatPercent(row.successRate) }}</template>
        </el-table-column>
        <el-table-column :label="copy.averageDuration" width="150">
          <template #default="{ row }">{{ formatSeconds(row.averageDurationSeconds) }}</template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <Dialog v-model="editVisible" :title="copy.editRoute" width="820px">
      <el-form ref="editFormRef" v-loading="editLoading" :model="editForm" :rules="editRules" label-width="130px">
        <el-form-item :label="copy.routeCode">
          <el-input v-model="editForm.routeCode" disabled />
        </el-form-item>
        <el-form-item :label="copy.routeName" prop="routeName">
          <el-input v-model="editForm.routeName" maxlength="128" />
        </el-form-item>
        <el-form-item :label="copy.enabled" prop="enabled">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
        <el-form-item :label="copy.weight" prop="trafficWeight">
          <el-input-number v-model="editForm.trafficWeight" :min="0" :max="100" />
        </el-form-item>
        <el-form-item :label="copy.abGroup" prop="abGroup">
          <el-input v-model="editForm.abGroup" maxlength="32" />
        </el-form-item>
        <el-form-item :label="copy.lastPublishTime" prop="lastPublishTime">
          <el-date-picker
            v-model="editForm.lastPublishTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            :placeholder="copy.lastPublishTime"
            class="!w-260px"
          />
        </el-form-item>
        <el-form-item :label="copy.routeConfig" prop="routeConfig">
          <el-input
            v-model="editForm.routeConfig"
            type="textarea"
            :rows="10"
            class="route-config-input"
            :placeholder="copy.routeConfigPlaceholder"
          />
        </el-form-item>
        <el-form-item :label="copy.remark" prop="remark">
          <el-input v-model="editForm.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ copy.cancel }}</el-button>
        <el-button @click="formatRouteConfig">{{ copy.formatJson }}</el-button>
        <el-button type="primary" :loading="editLoading" @click="submitEdit">{{ copy.save }}</el-button>
      </template>
    </Dialog>

    <el-drawer v-model="historyVisible" :title="historyTitle" size="880px">
      <el-table v-loading="historyLoading" :data="historyList" stripe>
        <el-table-column :label="copy.version" prop="routeVersion" width="90" />
        <el-table-column :label="copy.routeName" prop="routeName" min-width="180" />
        <el-table-column :label="copy.enabled" width="90">
          <template #default="{ row }">{{ row.enabled ? copy.enabledYes : copy.enabledNo }}</template>
        </el-table-column>
        <el-table-column :label="copy.weight" prop="trafficWeight" width="100" />
        <el-table-column :label="copy.abGroup" prop="abGroup" width="120" />
        <el-table-column :label="copy.changedAt" prop="createTime" width="180" />
        <el-table-column :label="copy.changeReason" prop="changeReason" min-width="180" />
        <el-table-column :label="copy.routeConfig" min-width="260">
          <template #default="{ row }">
            <code class="config-preview">{{ summarizeConfig(row.routeConfig) }}</code>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="historyTotal"
        v-model:page="historyQuery.pageNo"
        v-model:limit="historyQuery.pageSize"
        @pagination="getHistory"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { useLocaleStore } from '@/store/modules/locale'
import { TkGenerationRouteApi, type TkGenerationRouteHistoryVO, type TkGenerationRouteStatisticsVO, type TkGenerationRouteVO } from '@/api/tk/generationRoute'
import type { FormRules } from 'element-plus'

defineOptions({ name: 'TkGenerationRoute' })

type ProductCategoryCode =
  | 'DEFAULT'
  | '01'
  | '02'
  | '03'
  | '04'
  | '05'
  | '06'
  | '07'
  | '08'
  | '09'
  | '10'

const router = useRouter()
const message = useMessage()
const localeStore = useLocaleStore()
const isEn = computed(() => localeStore.getCurrentLocale.lang === 'en')

const productCategoryItems: Array<{ zh: string; en: string; value: ProductCategoryCode }> = [
  { zh: '默认生成逻辑', en: 'Default route', value: 'DEFAULT' },
  { zh: '01 服饰鞋包', en: '01 Apparel, Shoes & Bags', value: '01' },
  { zh: '02 美妆个护', en: '02 Beauty & Personal Care', value: '02' },
  { zh: '03 食品饮料', en: '03 Food & Beverage', value: '03' },
  { zh: '04 家居生活', en: '04 Home & Living', value: '04' },
  { zh: '05 3C数码', en: '05 3C Digital', value: '05' },
  { zh: '06 家用电器', en: '06 Home Appliances', value: '06' },
  { zh: '07 母婴儿童', en: '07 Mom, Baby & Kids', value: '07' },
  { zh: '08 运动户外', en: '08 Sports & Outdoors', value: '08' },
  { zh: '09 宠物用品', en: '09 Pet Supplies', value: '09' },
  { zh: '10 汽车用品', en: '10 Auto Supplies', value: '10' }
]

const copy = computed(() =>
  isEn.value
    ? {
        eyebrow: 'Generation routing',
        title: 'Product Route Management',
        subtitle: 'Maintain route configs, versions, history, and route-level generation performance.',
        backHome: 'Back to home',
        refresh: 'Refresh',
        materialPurpose: 'Material purpose',
        productCategory: 'Product category',
        routeCode: 'Route code',
        routeCodePlaceholder: 'Enter route code',
        routeName: 'Route name',
        routeConfig: 'Route config',
        routeConfigPlaceholder: 'JSON object or array, for example {"clipPlanMode":"FULL_POOL_RANDOM"}',
        version: 'Version',
        weight: 'Weight',
        abGroup: 'A/B group',
        enabled: 'Enabled',
        enabledYes: 'Enabled',
        enabledNo: 'Disabled',
        all: 'All',
        search: 'Search',
        reset: 'Reset',
        edit: 'Edit',
        history: 'History',
        actions: 'Actions',
        updatedAt: 'Updated at',
        statisticsTitle: 'Route performance',
        statisticsDesc: 'Aggregated from generation tasks in the last 30 days by default.',
        generated: 'Generated',
        success: 'Success',
        failed: 'Failed',
        running: 'Running',
        successRate: 'Success rate',
        averageDuration: 'Avg. duration',
        editRoute: 'Edit route',
        lastPublishTime: 'Last publish time',
        remark: 'Remark',
        cancel: 'Cancel',
        save: 'Save',
        formatJson: 'Format JSON',
        changedAt: 'Changed at',
        changeReason: 'Reason',
        routeCount: 'Routes',
        routeCountHint: 'Current matched routes',
        totalGenerated: 'Generated tasks',
        totalGeneratedHint: 'Route statistics window',
        overallSuccessRate: 'Overall success',
        overallSuccessRateHint: 'Successful / generated',
        avgDuration: 'Average duration',
        avgDurationHint: 'Completed task average',
        ecommerce: 'E-commerce materials',
        leadGeneration: 'Lead materials',
        routeNameRequired: 'Route name is required.',
        jsonInvalid: 'Route config must be a valid JSON array.',
        saved: 'Route saved'
      }
    : {
        eyebrow: '生成路由',
        title: '商品路由管理',
        subtitle: '维护路由配置、版本、历史记录，并查看不同路由的生成效果。',
        backHome: '返回首页',
        refresh: '刷新',
        materialPurpose: '素材类型',
        productCategory: '商品种类',
        routeCode: '路由编码',
        routeCodePlaceholder: '输入路由编码',
        routeName: '路由名称',
        routeConfig: '路由配置',
        routeConfigPlaceholder: 'JSON 对象或数组，例如 {"clipPlanMode":"FULL_POOL_RANDOM"}',
        version: '版本',
        weight: '权重',
        abGroup: 'A/B组',
        enabled: '启用',
        enabledYes: '启用',
        enabledNo: '停用',
        all: '全部',
        search: '搜索',
        reset: '重置',
        edit: '编辑',
        history: '历史',
        actions: '操作',
        updatedAt: '更新时间',
        statisticsTitle: '路由效果统计',
        statisticsDesc: '默认按最近 30 天生成任务聚合。',
        generated: '生成数',
        success: '成功',
        failed: '失败',
        running: '执行中',
        successRate: '成功率',
        averageDuration: '平均耗时',
        editRoute: '编辑路由',
        lastPublishTime: '最近发布时间',
        remark: '备注',
        cancel: '取消',
        save: '保存',
        formatJson: '格式化 JSON',
        changedAt: '变更时间',
        changeReason: '变更原因',
        routeCount: '路由数',
        routeCountHint: '当前筛选命中',
        totalGenerated: '生成任务',
        totalGeneratedHint: '统计窗口内任务',
        overallSuccessRate: '整体成功率',
        overallSuccessRateHint: '成功 / 生成',
        avgDuration: '平均耗时',
        avgDurationHint: '已完成任务平均值',
        ecommerce: '电商素材',
        leadGeneration: '引流素材',
        routeNameRequired: '路由名称不能为空。',
        jsonInvalid: '路由配置必须是合法 JSON 数组。',
        saved: '路由已保存'
      }
)

const materialPurposeOptions = computed(() => [
  { label: copy.value.ecommerce, value: 'ECOMMERCE' },
  { label: copy.value.leadGeneration, value: 'LEAD_GENERATION' }
])
const productCategoryOptions = computed(() =>
  productCategoryItems.map((item) => ({
    label: isEn.value ? item.en : item.zh,
    value: item.value
  }))
)

const queryFormRef = ref()
const loading = ref(false)
const list = ref<TkGenerationRouteVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  materialPurpose: undefined as string | undefined,
  productCategoryCode: undefined as string | undefined,
  routeCode: undefined as string | undefined,
  enabled: undefined as boolean | undefined
})

const statisticsLoading = ref(false)
const statistics = ref<TkGenerationRouteStatisticsVO[]>([])

const editVisible = ref(false)
const editLoading = ref(false)
const editFormRef = ref()
const editForm = ref<TkGenerationRouteVO>({})
const editRules = computed<FormRules>(() => ({
  routeName: [{ required: true, message: copy.value.routeNameRequired, trigger: 'blur' }]
}))

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref<TkGenerationRouteHistoryVO[]>([])
const historyTotal = ref(0)
const currentHistoryRoute = ref<TkGenerationRouteVO>()
const historyQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  routeId: undefined as number | undefined
})

const summaryMetrics = computed(() => {
  const generated = statistics.value.reduce((sum, item) => sum + Number(item.generationCount || 0), 0)
  const success = statistics.value.reduce((sum, item) => sum + Number(item.successCount || 0), 0)
  const avgDurationRows = statistics.value.filter((item) => Number(item.averageDurationSeconds || 0) > 0)
  const avgDuration = avgDurationRows.length
    ? Math.round(
        avgDurationRows.reduce((sum, item) => sum + Number(item.averageDurationSeconds || 0), 0) /
          avgDurationRows.length
      )
    : 0
  return [
    {
      label: copy.value.routeCount,
      value: String(total.value || list.value.length),
      hint: copy.value.routeCountHint,
      icon: 'ep:connection',
      tone: 'blue'
    },
    {
      label: copy.value.totalGenerated,
      value: String(generated),
      hint: copy.value.totalGeneratedHint,
      icon: 'ep:video-camera',
      tone: 'green'
    },
    {
      label: copy.value.overallSuccessRate,
      value: generated > 0 ? `${Math.round((success / generated) * 100)}%` : '0%',
      hint: copy.value.overallSuccessRateHint,
      icon: 'ep:trend-charts',
      tone: 'orange'
    },
    {
      label: copy.value.avgDuration,
      value: formatSeconds(avgDuration),
      hint: copy.value.avgDurationHint,
      icon: 'ep:timer',
      tone: 'purple'
    }
  ]
})

const historyTitle = computed(() =>
  currentHistoryRoute.value
    ? `${copy.value.history} · ${currentHistoryRoute.value.routeCode || ''}`
    : copy.value.history
)

const getList = async () => {
  loading.value = true
  try {
    const data = await TkGenerationRouteApi.getPage(query)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const getStatistics = async () => {
  statisticsLoading.value = true
  try {
    statistics.value = await TkGenerationRouteApi.getStatistics({
      materialPurpose: query.materialPurpose,
      productCategoryCode: query.productCategoryCode,
      routeCode: query.routeCode
    })
  } finally {
    statisticsLoading.value = false
  }
}

const loadAll = async () => {
  await Promise.all([getList(), getStatistics()])
}

const handleQuery = () => {
  query.pageNo = 1
  loadAll()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  query.pageNo = 1
  loadAll()
}

const openEdit = (row: TkGenerationRouteVO) => {
  editForm.value = {
    ...row,
    routeConfig: row.routeConfig || ''
  }
  editVisible.value = true
  nextTick(() => editFormRef.value?.clearValidate())
}

const submitEdit = async () => {
  const valid = await editFormRef.value?.validate()
  if (!valid) return
  if (!validateRouteConfig(editForm.value.routeConfig)) {
    message.error(copy.value.jsonInvalid)
    return
  }
  editLoading.value = true
  try {
    await TkGenerationRouteApi.update(editForm.value)
    message.success(copy.value.saved)
    editVisible.value = false
    await loadAll()
  } finally {
    editLoading.value = false
  }
}

const validateRouteConfig = (value?: string) => {
  if (!value || !value.trim()) {
    return true
  }
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) || (!!parsed && typeof parsed === 'object')
  } catch (error) {
    return false
  }
}

const formatRouteConfig = () => {
  if (!editForm.value.routeConfig || !editForm.value.routeConfig.trim()) return
  if (!validateRouteConfig(editForm.value.routeConfig)) {
    message.error(copy.value.jsonInvalid)
    return
  }
  editForm.value.routeConfig = JSON.stringify(JSON.parse(editForm.value.routeConfig), null, 2)
}

const openHistory = async (row: TkGenerationRouteVO) => {
  currentHistoryRoute.value = row
  historyQuery.routeId = row.id
  historyQuery.pageNo = 1
  historyVisible.value = true
  await getHistory()
}

const getHistory = async () => {
  if (!historyQuery.routeId) return
  historyLoading.value = true
  try {
    const data = await TkGenerationRouteApi.getHistoryPage(historyQuery)
    historyList.value = data.list || []
    historyTotal.value = data.total || 0
  } finally {
    historyLoading.value = false
  }
}

const purposeLabel = (value?: string) => {
  const matched = materialPurposeOptions.value.find((item) => item.value === value)
  return matched?.label || value || '-'
}

const categoryLabel = (value?: string) => {
  const matched = productCategoryOptions.value.find((item) => item.value === value)
  return matched?.label || value || '-'
}

const summarizeConfig = (value?: string) => {
  if (!value) return '-'
  return value.length > 120 ? `${value.slice(0, 120)}...` : value
}

const formatPercent = (value?: number) => `${Number(value || 0).toFixed(1)}%`

const formatSeconds = (value?: number) => {
  const seconds = Number(value || 0)
  if (seconds <= 0) return '0s'
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

const goDashboard = () => {
  router.push({ path: '/tk/dashboard' })
}

onMounted(loadAll)
</script>

<style scoped>
.generation-route-page {
  min-height: 100%;
  padding: 24px;
  background: #f6f8fb;
}

.route-header {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 18px;
}

.route-header h1 {
  margin: 4px 0 8px;
  font-size: 28px;
  line-height: 1.25;
  color: #172033;
}

.route-header p {
  max-width: 720px;
  margin: 0;
  color: #607088;
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: #2f6fed;
  text-transform: uppercase;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.route-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.metric-card {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 10px 12px;
  align-items: center;
  min-height: 112px;
  padding: 18px;
  background: #fff;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  box-shadow: 0 12px 24px rgb(31 45 61 / 8%);
}

.metric-card span {
  font-size: 13px;
  color: #66758a;
}

.metric-card strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
  line-height: 1.1;
  color: #111827;
}

.metric-card em {
  grid-column: 2;
  font-style: normal;
  color: #8a96a8;
}

.metric-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  font-size: 21px;
}

.tone-blue {
  color: #2364db;
  background: #eaf1ff;
}

.tone-green {
  color: #15803d;
  background: #eaf8ef;
}

.tone-orange {
  color: #b45309;
  background: #fff4df;
}

.tone-purple {
  color: #6d28d9;
  background: #f0eaff;
}

.route-query-panel {
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title h2 {
  margin: 0 0 4px;
  font-size: 18px;
  color: #172033;
}

.section-title p {
  margin: 0;
  color: #718096;
}

.config-preview {
  display: block;
  max-width: 100%;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
  color: #334155;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.route-config-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

@media (max-width: 1200px) {
  .route-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .generation-route-page {
    padding: 16px;
  }

  .route-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }

  .route-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
