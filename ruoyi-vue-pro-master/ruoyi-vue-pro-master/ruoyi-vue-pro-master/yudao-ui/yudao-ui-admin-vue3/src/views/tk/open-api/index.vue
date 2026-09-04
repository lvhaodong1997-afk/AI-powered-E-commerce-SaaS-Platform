<template>
  <ContentWrap class="open-api-page">
    <div class="page-toolbar">
      <div>
        <h1>开放 API 管理</h1>
        <p>调用方、调用统计与回调事件</p>
      </div>
      <el-button :loading="activeLoading" @click="refreshActiveTab">
        <Icon icon="ep:refresh" class="mr-5px" /> 刷新
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="调用方" name="clients">
        <el-form ref="clientQueryFormRef" :model="clientQuery" :inline="true" class="query-form">
          <el-form-item label="调用方 ID" prop="clientId">
            <el-input
              v-model="clientQuery.clientId"
              placeholder="请输入调用方 ID"
              clearable
              class="!w-220px"
              @keyup.enter="handleClientQuery"
            />
          </el-form-item>
          <el-form-item label="调用方名称" prop="clientName">
            <el-input
              v-model="clientQuery.clientName"
              placeholder="请输入调用方名称"
              clearable
              class="!w-220px"
              @keyup.enter="handleClientQuery"
            />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select
              v-model="clientQuery.status"
              placeholder="全部状态"
              clearable
              class="!w-140px"
            >
              <el-option label="启用" :value="0" />
              <el-option label="停用" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleClientQuery"
              ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
            >
            <el-button @click="resetClientQuery"
              ><Icon icon="ep:refresh-left" class="mr-5px" /> 重置</el-button
            >
            <el-button type="primary" @click="openClientForm()">
              <Icon icon="ep:plus" class="mr-5px" /> 新增调用方
            </el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="clientLoading" :data="clientList" stripe empty-text="暂无调用方">
          <el-table-column label="调用方" min-width="230">
            <template #default="{ row }">
              <div class="primary-cell">{{ valueOrDash(row.clientName) }}</div>
              <div class="secondary-cell">{{ valueOrDash(row.clientId) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="回调地址" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <div>授权：{{ valueOrDash(row.authCallbackUrl) }}</div>
              <div class="secondary-cell">发布：{{ valueOrDash(row.publishCallbackUrl) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="权限" min-width="170">
            <template #default="{ row }">
              <el-tag
                v-for="permission in permissionList(row.permissions)"
                :key="permission"
                size="small"
                class="mr-4px"
              >
                {{ permissionLabel(permission) }}
              </el-tag>
              <span v-if="!permissionList(row.permissions).length">-</span>
            </template>
          </el-table-column>
          <el-table-column label="限额" min-width="160">
            <template #default="{ row }">
              <div>{{ formatNumber(row.rateLimitPerMinute) }} / 分钟</div>
              <div class="secondary-cell">{{ formatNumber(row.dailyQuota) }} / 日</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 0"
                active-text="启用"
                inactive-text="停用"
                :loading="statusUpdatingId === row.clientId"
                @change="handleClientStatusChange(row, $event)"
              />
            </template>
          </el-table-column>
          <el-table-column label="更新时间" prop="updateTime" width="180">
            <template #default="{ row }">{{ valueOrDash(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-tooltip content="编辑调用方" placement="top">
                <el-button link type="primary" aria-label="编辑调用方" @click="openClientForm(row)">
                  <Icon icon="ep:edit-pen" />
                </el-button>
              </el-tooltip>
              <el-tooltip content="轮换调用密钥" placement="top">
                <el-button
                  link
                  type="warning"
                  aria-label="轮换调用密钥"
                  :loading="isClientActionLoading(row, 'CLIENT')"
                  @click="rotateSecret(row, 'CLIENT')"
                >
                  <Icon icon="ep:key" />
                </el-button>
              </el-tooltip>
              <el-tooltip content="轮换回调密钥" placement="top">
                <el-button
                  link
                  type="warning"
                  aria-label="轮换回调密钥"
                  :loading="isClientActionLoading(row, 'CALLBACK')"
                  @click="rotateSecret(row, 'CALLBACK')"
                >
                  <Icon icon="ep:connection" />
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除调用方" placement="top">
                <el-button
                  link
                  type="danger"
                  aria-label="删除调用方"
                  :loading="isClientActionLoading(row, 'DELETE')"
                  @click="deleteClient(row)"
                >
                  <Icon icon="ep:delete" />
                </el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="clientTotal"
          v-model:page="clientQuery.pageNo"
          v-model:limit="clientQuery.pageSize"
          @pagination="getClientList"
        />
      </el-tab-pane>

      <el-tab-pane label="调用统计" name="usage">
        <el-form :inline="true" class="query-form">
          <el-form-item label="调用方 ID">
            <el-input
              v-model="usageQuery.clientId"
              placeholder="请输入调用方 ID"
              clearable
              class="!w-240px"
              @keyup.enter="getUsageList"
            />
          </el-form-item>
          <el-form-item label="统计日期">
            <el-date-picker
              v-model="usageDateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              class="!w-280px"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="getUsageList"
              ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
            >
            <el-button @click="resetUsageQuery"
              ><Icon icon="ep:refresh-left" class="mr-5px" /> 重置</el-button
            >
          </el-form-item>
        </el-form>

        <el-table
          v-loading="usageLoading"
          :data="usageList"
          stripe
          empty-text="当前条件下暂无调用统计"
        >
          <el-table-column label="日期" prop="requestDate" min-width="150">
            <template #default="{ row }">{{ valueOrDash(row.requestDate) }}</template>
          </el-table-column>
          <el-table-column label="调用方 ID" prop="clientId" min-width="220">
            <template #default="{ row }">{{ valueOrDash(row.clientId) }}</template>
          </el-table-column>
          <el-table-column label="请求总数" min-width="130" align="right">
            <template #default="{ row }">{{ formatNumber(row.requestCount) }}</template>
          </el-table-column>
          <el-table-column label="成功" min-width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.successCount) }}</template>
          </el-table-column>
          <el-table-column label="失败" min-width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.failureCount) }}</template>
          </el-table-column>
          <el-table-column label="平均耗时" min-width="140" align="right">
            <template #default="{ row }">{{ formatDuration(row.averageDurationMs) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="回调事件" name="events">
        <el-form ref="eventQueryFormRef" :model="eventQuery" :inline="true" class="query-form">
          <el-form-item label="调用方 ID" prop="clientId">
            <el-input
              v-model="eventQuery.clientId"
              placeholder="请输入调用方 ID"
              clearable
              class="!w-210px"
              @keyup.enter="handleEventQuery"
            />
          </el-form-item>
          <el-form-item label="事件类型" prop="eventType">
            <el-select
              v-model="eventQuery.eventType"
              placeholder="全部事件"
              clearable
              class="!w-190px"
            >
              <el-option
                v-for="item in eventTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select
              v-model="eventQuery.status"
              placeholder="全部状态"
              clearable
              class="!w-150px"
            >
              <el-option
                v-for="item in eventStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="创建时间">
            <el-date-picker
              v-model="eventDateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="!w-350px"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="handleEventQuery"
              ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
            >
            <el-button @click="resetEventQuery"
              ><Icon icon="ep:refresh-left" class="mr-5px" /> 重置</el-button
            >
          </el-form-item>
        </el-form>

        <el-table
          v-loading="eventLoading"
          :data="eventList"
          stripe
          empty-text="当前条件下暂无回调事件"
        >
          <el-table-column label="事件" min-width="250">
            <template #default="{ row }">
              <div class="primary-cell">{{ valueOrDash(row.eventType) }}</div>
              <div class="secondary-cell">{{ valueOrDash(row.eventId) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="调用方 ID" prop="clientId" min-width="190">
            <template #default="{ row }">{{ valueOrDash(row.clientId) }}</template>
          </el-table-column>
          <el-table-column label="资源" min-width="180">
            <template #default="{ row }">
              <div>{{ valueOrDash(row.resourceType) }}</div>
              <div class="secondary-cell">{{ valueOrDash(row.resourceId) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="125">
            <template #default="{ row }">
              <el-tag :type="eventStatusTagType(row.status)" effect="plain">{{
                eventStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="尝试 / HTTP" min-width="130">
            <template #default="{ row }">
              <div>{{ formatNumber(row.attemptCount) }} 次</div>
              <div class="secondary-cell">{{
                row.lastHttpStatus ? `HTTP ${row.lastHttpStatus}` : '-'
              }}</div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" prop="createTime" width="180">
            <template #default="{ row }">{{ valueOrDash(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-tooltip content="查看详情" placement="top">
                <el-button
                  link
                  type="primary"
                  aria-label="查看回调事件详情"
                  @click="openEventDetail(row.eventId)"
                >
                  <Icon icon="ep:view" />
                </el-button>
              </el-tooltip>
              <el-tooltip content="重放事件" placement="top">
                <el-button
                  link
                  type="warning"
                  aria-label="重放回调事件"
                  :loading="eventReplayingId === row.eventId"
                  :disabled="!isEventReplayable(row)"
                  @click="replayEvent(row)"
                >
                  <Icon icon="ep:refresh-right" />
                </el-button>
              </el-tooltip>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="eventTotal"
          v-model:page="eventQuery.pageNo"
          v-model:limit="eventQuery.pageSize"
          @pagination="getEventList"
        />
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <Dialog
    v-model="clientDialogVisible"
    :title="clientForm.clientId ? '编辑调用方' : '新增调用方'"
    width="760px"
  >
    <el-form
      ref="clientFormRef"
      v-loading="clientFormLoading"
      :model="clientForm"
      :rules="clientFormRules"
      label-width="120px"
    >
      <el-form-item label="调用方名称" prop="clientName">
        <el-input
          v-model="clientForm.clientName"
          placeholder="请输入调用方名称"
          maxlength="128"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="授权回调地址" prop="authCallbackUrl">
        <el-input
          v-model="clientForm.authCallbackUrl"
          placeholder="https://example.com/auth/callback"
          maxlength="512"
        />
      </el-form-item>
      <el-form-item label="发布回调地址" prop="publishCallbackUrl">
        <el-input
          v-model="clientForm.publishCallbackUrl"
          placeholder="https://example.com/publish/callback"
          maxlength="512"
        />
      </el-form-item>
      <el-form-item label="允许 IP" prop="allowedIps">
        <el-input
          v-model="clientForm.allowedIps"
          type="textarea"
          :rows="2"
          placeholder="多个 IP 或 CIDR 规则使用逗号分隔；留空表示不限制"
          maxlength="2048"
          show-word-limit
        />
      </el-form-item>
      <el-form-item label="权限" prop="permissions">
        <el-checkbox-group v-model="clientForm.permissions">
          <el-checkbox label="auth">授权</el-checkbox>
          <el-checkbox label="media">媒体</el-checkbox>
          <el-checkbox label="publish">发布</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="每分钟限额" prop="rateLimitPerMinute">
        <el-input-number
          v-model="clientForm.rateLimitPerMinute"
          :min="1"
          :max="10000"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="每日限额" prop="dailyQuota">
        <el-input-number v-model="clientForm.dailyQuota" :min="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="clientForm.status">
          <el-radio :label="0">启用</el-radio>
          <el-radio :label="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="clientForm.remark"
          type="textarea"
          :rows="3"
          placeholder="可选"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="clientDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="clientFormLoading" @click="submitClientForm"
        >确定</el-button
      >
    </template>
  </Dialog>

  <Dialog
    v-model="credentialDialogVisible"
    title="一次性凭证"
    width="680px"
    @closed="clearCredentials"
  >
    <el-alert
      title="请立即保存凭证。关闭此窗口后，系统不会再次展示明文密钥。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-descriptions :column="1" border class="credential-list">
      <el-descriptions-item label="调用方 ID">{{
        valueOrDash(credentials.clientId)
      }}</el-descriptions-item>
      <el-descriptions-item v-if="credentials.clientSecret" label="调用密钥">
        <div class="credential-value">
          <code>{{ credentials.clientSecret }}</code>
          <el-tooltip content="复制调用密钥" placement="top">
            <el-button
              circle
              aria-label="复制调用密钥"
              @click="copyCredential(credentials.clientSecret)"
            >
              <Icon icon="ep:copy-document" />
            </el-button>
          </el-tooltip>
        </div>
      </el-descriptions-item>
      <el-descriptions-item v-if="credentials.callbackSecret" label="回调密钥">
        <div class="credential-value">
          <code>{{ credentials.callbackSecret }}</code>
          <el-tooltip content="复制回调密钥" placement="top">
            <el-button
              circle
              aria-label="复制回调密钥"
              @click="copyCredential(credentials.callbackSecret)"
            >
              <Icon icon="ep:copy-document" />
            </el-button>
          </el-tooltip>
        </div>
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button type="primary" @click="credentialDialogVisible = false">我已保存，关闭</el-button>
    </template>
  </Dialog>

  <el-drawer v-model="eventDetailVisible" title="回调事件详情" size="680px" destroy-on-close>
    <div v-loading="eventDetailLoading" class="event-detail">
      <template v-if="eventDetail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="事件 ID">{{
            valueOrDash(eventDetail.eventId)
          }}</el-descriptions-item>
          <el-descriptions-item label="调用方 ID">{{
            valueOrDash(eventDetail.clientId)
          }}</el-descriptions-item>
          <el-descriptions-item label="事件类型">{{
            valueOrDash(eventDetail.eventType)
          }}</el-descriptions-item>
          <el-descriptions-item label="资源">
            {{ valueOrDash(eventDetail.resourceType) }} / {{ valueOrDash(eventDetail.resourceId) }}
          </el-descriptions-item>
          <el-descriptions-item label="回调地址">{{
            valueOrDash(eventDetail.callbackUrl)
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="eventStatusTagType(eventDetail.status)" effect="plain">
              {{ eventStatusLabel(eventDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="投递信息">
            {{ formatNumber(eventDetail.attemptCount) }} 次 /
            {{ eventDetail.lastHttpStatus ? `HTTP ${eventDetail.lastHttpStatus}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="下次重试">{{
            valueOrDash(eventDetail.nextRetryTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="投递时间">{{
            valueOrDash(eventDetail.deliveredTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="最后错误">{{
            valueOrDash(eventDetail.lastError)
          }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            valueOrDash(eventDetail.createTime)
          }}</el-descriptions-item>
        </el-descriptions>
        <section class="payload-section">
          <h2>Payload</h2>
          <pre>{{ formattedPayload }}</pre>
        </section>
      </template>
      <el-empty v-else-if="!eventDetailLoading" description="暂无事件详情" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import {
  TkOpenApiApi,
  type OpenApiClientSaveReq,
  type OpenApiClientVO,
  type OpenApiCredentialResp,
  type OpenApiEventVO,
  type OpenApiUsageVO
} from '@/api/tk/openApi'

defineOptions({ name: 'TkOpenApi' })

type ActiveTab = 'clients' | 'usage' | 'events'
type SecretType = 'CLIENT' | 'CALLBACK'

interface ClientFormData extends Omit<OpenApiClientSaveReq, 'permissions'> {
  permissions: string[]
}

const message = useMessage()
const activeTab = ref<ActiveTab>('clients')

const clientQueryFormRef = ref<FormInstance>()
const clientLoading = ref(false)
const clientList = ref<OpenApiClientVO[]>([])
const clientTotal = ref(0)
const clientQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  clientId: undefined as string | undefined,
  clientName: undefined as string | undefined,
  status: undefined as number | undefined
})

const statusUpdatingId = ref<string>()
const clientActionLoadingKey = ref<string>()
const clientDialogVisible = ref(false)
const clientFormLoading = ref(false)
const clientFormRef = ref<FormInstance>()
const clientForm = reactive<ClientFormData>(createClientForm())
const clientFormRules = reactive<FormRules<ClientFormData>>({
  clientName: [{ required: true, message: '调用方名称不能为空', trigger: 'blur' }],
  permissions: [
    { type: 'array', required: true, min: 1, message: '至少选择一项权限', trigger: 'change' }
  ],
  rateLimitPerMinute: [{ required: true, message: '请输入每分钟限额', trigger: 'change' }],
  dailyQuota: [{ required: true, message: '请输入每日限额', trigger: 'change' }]
})

const credentialDialogVisible = ref(false)
const credentials = reactive<OpenApiCredentialResp>({})

const usageLoading = ref(false)
const usageList = ref<OpenApiUsageVO[]>([])
const usageQuery = reactive({ clientId: undefined as string | undefined })
const usageDateRange = ref<string[]>([])

const eventQueryFormRef = ref<FormInstance>()
const eventLoading = ref(false)
const eventList = ref<OpenApiEventVO[]>([])
const eventTotal = ref(0)
const eventQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  clientId: undefined as string | undefined,
  eventType: undefined as string | undefined,
  status: undefined as string | undefined
})
const eventDateRange = ref<string[]>([])
const eventDetailVisible = ref(false)
const eventDetailLoading = ref(false)
const eventDetail = ref<OpenApiEventVO>()
const eventReplayingId = ref<string>()

const eventTypeOptions = [
  { label: '授权完成', value: 'authorization.completed' },
  { label: '授权失败', value: 'authorization.failed' },
  { label: '发布处理中', value: 'publish.processing' },
  { label: '发布成功', value: 'publish.success' },
  { label: '发布失败', value: 'publish.failed' }
]

const eventStatusOptions = [
  { label: '待投递', value: 'PENDING' },
  { label: '投递中', value: 'DELIVERING' },
  { label: '重试中', value: 'RETRYING' },
  { label: '已投递', value: 'DELIVERED' },
  { label: '失败', value: 'FAILED' },
  { label: '已跳过', value: 'SKIPPED' }
]

const activeLoading = computed(() => {
  if (activeTab.value === 'clients') return clientLoading.value
  if (activeTab.value === 'usage') return usageLoading.value
  return eventLoading.value
})

const formattedPayload = computed(() => formatJson(eventDetail.value?.payloadJson))

function createClientForm(): ClientFormData {
  return {
    clientName: '',
    authCallbackUrl: '',
    publishCallbackUrl: '',
    allowedIps: '',
    permissions: ['auth', 'media', 'publish'],
    rateLimitPerMinute: 120,
    dailyQuota: 10000,
    status: 0,
    remark: ''
  }
}

const getClientList = async () => {
  clientLoading.value = true
  try {
    const data = await TkOpenApiApi.getClientPage(clientQuery)
    clientList.value = data.list || []
    clientTotal.value = data.total || 0
  } finally {
    clientLoading.value = false
  }
}

const handleClientQuery = () => {
  clientQuery.pageNo = 1
  getClientList()
}

const resetClientQuery = () => {
  clientQueryFormRef.value?.resetFields()
  handleClientQuery()
}

const openClientForm = async (row?: OpenApiClientVO) => {
  Object.assign(clientForm, createClientForm())
  clientDialogVisible.value = true
  await nextTick()
  clientFormRef.value?.clearValidate()
  if (!row) return

  clientFormLoading.value = true
  try {
    const detail = await TkOpenApiApi.getClient(row.clientId)
    Object.assign(clientForm, toClientForm(detail))
  } finally {
    clientFormLoading.value = false
  }
}

const toClientForm = (client: OpenApiClientVO): ClientFormData => ({
  clientId: client.clientId,
  clientName: client.clientName || '',
  authCallbackUrl: client.authCallbackUrl || '',
  publishCallbackUrl: client.publishCallbackUrl || '',
  allowedIps: client.allowedIps || '',
  permissions: permissionList(client.permissions),
  rateLimitPerMinute: client.rateLimitPerMinute ?? 120,
  dailyQuota: client.dailyQuota ?? 10000,
  status: client.status ?? 0,
  remark: client.remark || ''
})

const submitClientForm = async () => {
  const valid = await clientFormRef.value?.validate()
  if (!valid) return

  clientFormLoading.value = true
  try {
    const payload: OpenApiClientSaveReq = {
      clientId: clientForm.clientId,
      clientName: clientForm.clientName.trim(),
      authCallbackUrl: blankToUndefined(clientForm.authCallbackUrl),
      publishCallbackUrl: blankToUndefined(clientForm.publishCallbackUrl),
      allowedIps: blankToUndefined(clientForm.allowedIps),
      permissions: clientForm.permissions.join(','),
      rateLimitPerMinute: clientForm.rateLimitPerMinute,
      dailyQuota: clientForm.dailyQuota,
      status: clientForm.status,
      remark: blankToUndefined(clientForm.remark)
    }
    if (payload.clientId) {
      await TkOpenApiApi.updateClient({ ...payload, clientId: payload.clientId })
      message.success('调用方已更新')
    } else {
      const credential = await TkOpenApiApi.createClient(payload)
      showCredentials(credential)
      message.success('调用方已创建')
    }
    clientDialogVisible.value = false
    await getClientList()
  } finally {
    clientFormLoading.value = false
  }
}

const handleClientStatusChange = async (
  row: OpenApiClientVO,
  enabled: string | number | boolean
) => {
  const status = enabled ? 0 : 1
  const action = status === 0 ? '启用' : '停用'
  try {
    await message.confirm(
      `确认${action}调用方「${row.clientName || row.clientId}」？`,
      `${action}确认`
    )
  } catch {
    return
  }
  statusUpdatingId.value = row.clientId
  try {
    await TkOpenApiApi.updateClientStatus(row.clientId, status)
    message.success(`调用方已${action}`)
    await getClientList()
  } finally {
    statusUpdatingId.value = undefined
  }
}

const deleteClient = async (row: OpenApiClientVO) => {
  try {
    await message.delConfirm(
      `确认删除调用方「${row.clientName || row.clientId}」？删除后无法恢复。`,
      '删除调用方'
    )
  } catch {
    return
  }
  clientActionLoadingKey.value = `${row.clientId}:DELETE`
  try {
    await TkOpenApiApi.deleteClient(row.clientId)
    message.success('调用方已删除')
    if (clientList.value.length === 1 && clientQuery.pageNo > 1) clientQuery.pageNo -= 1
    await getClientList()
  } finally {
    clientActionLoadingKey.value = undefined
  }
}

const rotateSecret = async (row: OpenApiClientVO, type: SecretType) => {
  const label = type === 'CLIENT' ? '调用密钥' : '回调密钥'
  try {
    await message.confirm(
      `确认轮换调用方「${row.clientName || row.clientId}」的${label}？旧密钥将立即失效。`,
      '轮换密钥'
    )
  } catch {
    return
  }
  clientActionLoadingKey.value = `${row.clientId}:${type}`
  try {
    const response = await TkOpenApiApi.rotateSecret(row.clientId, type)
    const secret = extractRotatedSecret(response, type)
    if (!secret) {
      message.error('轮换成功，但未收到明文密钥')
      return
    }
    showCredentials({
      clientId: response.clientId || row.clientId,
      clientSecret: type === 'CLIENT' ? secret : undefined,
      callbackSecret: type === 'CALLBACK' ? secret : undefined
    })
    message.success(`${label}已轮换`)
  } finally {
    clientActionLoadingKey.value = undefined
  }
}

const showCredentials = (value: OpenApiCredentialResp) => {
  Object.assign(credentials, value)
  credentialDialogVisible.value = true
}

const clearCredentials = () => {
  credentials.clientId = undefined
  credentials.clientSecret = undefined
  credentials.callbackSecret = undefined
}

const copyCredential = async (value?: string) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败，请手动复制')
  }
}

const getUsageList = async () => {
  usageLoading.value = true
  try {
    usageList.value = await TkOpenApiApi.getUsage({
      clientId: blankToUndefined(usageQuery.clientId),
      startDate: usageDateRange.value[0],
      endDate: usageDateRange.value[1]
    })
  } finally {
    usageLoading.value = false
  }
}

const resetUsageQuery = () => {
  usageQuery.clientId = undefined
  usageDateRange.value = []
  getUsageList()
}

const getEventList = async () => {
  eventLoading.value = true
  try {
    const data = await TkOpenApiApi.getEventPage({
      ...eventQuery,
      createTimeStart: eventDateRange.value[0],
      createTimeEnd: eventDateRange.value[1]
    })
    eventList.value = data.list || []
    eventTotal.value = data.total || 0
  } finally {
    eventLoading.value = false
  }
}

const handleEventQuery = () => {
  eventQuery.pageNo = 1
  getEventList()
}

const resetEventQuery = () => {
  eventQueryFormRef.value?.resetFields()
  eventDateRange.value = []
  handleEventQuery()
}

const openEventDetail = async (eventId: string) => {
  eventDetail.value = undefined
  eventDetailVisible.value = true
  eventDetailLoading.value = true
  try {
    eventDetail.value = await TkOpenApiApi.getEvent(eventId)
  } finally {
    eventDetailLoading.value = false
  }
}

const replayEvent = async (row: OpenApiEventVO) => {
  try {
    await message.confirm(
      `确认重放事件「${row.eventId}」？系统将再次向回调地址发送该事件。`,
      '重放回调事件'
    )
  } catch {
    return
  }
  eventReplayingId.value = row.eventId
  try {
    await TkOpenApiApi.replayEvent(row.eventId)
    message.success('已提交回调事件重放')
    await getEventList()
    if (eventDetail.value?.eventId === row.eventId) {
      eventDetail.value = await TkOpenApiApi.getEvent(row.eventId)
    }
  } finally {
    eventReplayingId.value = undefined
  }
}

const isEventReplayable = (event: OpenApiEventVO) =>
  Boolean(event.callbackUrl) && !['PENDING', 'DELIVERING', 'RETRYING'].includes(event.status || '')

const handleTabChange = (tabName: string | number) => {
  if (tabName === 'usage' && !usageList.value.length) getUsageList()
  if (tabName === 'events' && !eventList.value.length) getEventList()
}

const refreshActiveTab = () => {
  if (activeTab.value === 'clients') return getClientList()
  if (activeTab.value === 'usage') return getUsageList()
  return getEventList()
}

const permissionList = (value?: string) =>
  (value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

const permissionLabel = (value: string) => {
  const labels: Record<string, string> = { auth: '授权', media: '媒体', publish: '发布' }
  return labels[value] || value
}

const eventStatusLabel = (value?: string) => {
  const labels: Record<string, string> = {
    PENDING: '待投递',
    DELIVERING: '投递中',
    RETRYING: '重试中',
    DELIVERED: '已投递',
    FAILED: '失败',
    SKIPPED: '已跳过'
  }
  return value ? labels[value] || value : '-'
}

const eventStatusTagType = (value?: string) => {
  if (value === 'DELIVERED') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'SKIPPED') return 'info'
  return 'warning'
}

const extractRotatedSecret = (response: OpenApiCredentialResp, type: SecretType) => {
  return type === 'CLIENT' ? response.clientSecret : response.callbackSecret
}

const isClientActionLoading = (row: OpenApiClientVO, action: SecretType | 'DELETE') => {
  return clientActionLoadingKey.value === `${row.clientId}:${action}`
}

const blankToUndefined = (value?: string) => {
  const normalized = value?.trim()
  return normalized ? normalized : undefined
}

const valueOrDash = (value?: string | number) =>
  value === null || value === undefined || value === '' ? '-' : value

const formatNumber = (value?: number) =>
  value === null || value === undefined ? '-' : value.toLocaleString()

const formatDuration = (value?: number) =>
  value === null || value === undefined ? '-' : `${value.toLocaleString()} ms`

const formatJson = (value?: string) => {
  if (!value) return '-'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(getClientList)
</script>

<style scoped>
.open-api-page {
  min-height: 100%;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.page-toolbar h1,
.page-toolbar p,
.payload-section h2 {
  margin: 0;
}

.page-toolbar h1 {
  color: var(--el-text-color-primary);
  font-size: 20px;
  line-height: 1.35;
}

.page-toolbar p,
.secondary-cell {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.page-toolbar p {
  margin-top: 3px;
}

.query-form {
  margin: 4px 0 14px;
}

.primary-cell {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.secondary-cell {
  margin-top: 3px;
}

.credential-list {
  margin-top: 16px;
}

.credential-value {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.credential-value code {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.event-detail {
  min-height: 180px;
}

.payload-section {
  margin-top: 18px;
}

.payload-section h2 {
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-size: 15px;
}

.payload-section pre {
  max-height: 360px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-regular);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 768px) {
  .page-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .query-form :deep(.el-form-item) {
    margin-right: 0;
  }

  .query-form :deep(.el-form-item__content) {
    width: 100%;
  }

  .query-form :deep(.el-input),
  .query-form :deep(.el-select),
  .query-form :deep(.el-date-editor) {
    width: 100% !important;
  }
}
</style>
