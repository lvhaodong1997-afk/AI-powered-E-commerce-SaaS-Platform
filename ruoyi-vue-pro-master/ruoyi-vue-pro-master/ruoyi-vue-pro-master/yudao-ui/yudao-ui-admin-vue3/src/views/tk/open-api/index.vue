<template>
  <ContentWrap class="open-api-page">
    <div class="page-toolbar">
      <div>
        <h1>{{ tt('openApi.title') }}</h1>
        <p>{{ tt('openApi.subtitle') }}</p>
      </div>
      <el-button :loading="activeLoading" @click="refreshActiveTab">
        <Icon icon="ep:refresh" class="mr-5px" /> {{ tt('openApi.refresh') }}
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane :label="tt('openApi.tabs.clients')" name="clients">
        <el-form ref="clientQueryFormRef" :model="clientQuery" :inline="true" class="query-form">
          <el-form-item :label="tt('openApi.client.id')" prop="clientId">
            <el-input
              v-model="clientQuery.clientId"
              :placeholder="tt('openApi.client.idPlaceholder')"
              clearable
              class="!w-220px"
              @keyup.enter="handleClientQuery"
            />
          </el-form-item>
          <el-form-item :label="tt('openApi.client.name')" prop="clientName">
            <el-input
              v-model="clientQuery.clientName"
              :placeholder="tt('openApi.client.namePlaceholder')"
              clearable
              class="!w-220px"
              @keyup.enter="handleClientQuery"
            />
          </el-form-item>
          <el-form-item :label="tt('openApi.client.status')" prop="status">
            <el-select
              v-model="clientQuery.status"
              :placeholder="tt('openApi.allStatuses')"
              clearable
              class="!w-140px"
            >
              <el-option :label="tt('openApi.enabled')" :value="0" />
              <el-option :label="tt('openApi.disabled')" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="handleClientQuery"
              ><Icon icon="ep:search" class="mr-5px" /> {{ tt('openApi.search') }}</el-button
            >
            <el-button @click="resetClientQuery"
              ><Icon icon="ep:refresh-left" class="mr-5px" /> {{ tt('openApi.reset') }}</el-button
            >
            <el-button type="primary" @click="openClientForm()">
              <Icon icon="ep:plus" class="mr-5px" /> {{ tt('openApi.addClient') }}
            </el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="clientLoading"
          :data="clientList"
          stripe
          :empty-text="tt('openApi.client.empty')"
        >
          <el-table-column :label="tt('openApi.client.caller')" min-width="230">
            <template #default="{ row }">
              <div class="primary-cell">{{ valueOrDash(row.clientName) }}</div>
              <div class="secondary-cell">{{ valueOrDash(row.clientId) }}</div>
            </template>
          </el-table-column>
          <el-table-column
            :label="tt('openApi.client.callbackUrl')"
            min-width="260"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <div
                >{{ tt('openApi.client.authorization') }}:
                {{ valueOrDash(row.authCallbackUrl) }}</div
              >
              <div class="secondary-cell"
                >{{ tt('openApi.client.publish') }}: {{ valueOrDash(row.publishCallbackUrl) }}</div
              >
            </template>
          </el-table-column>
          <el-table-column :label="tt('openApi.client.permissions')" min-width="170">
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
          <el-table-column :label="tt('openApi.client.quota')" min-width="160">
            <template #default="{ row }">
              <div
                >{{ formatNumber(row.rateLimitPerMinute) }} /
                {{ tt('openApi.client.perMinute') }}</div
              >
              <div class="secondary-cell"
                >{{ formatNumber(row.dailyQuota) }} / {{ tt('openApi.client.perDay') }}</div
              >
            </template>
          </el-table-column>
          <el-table-column :label="tt('openApi.client.status')" width="120">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === 0"
                :active-text="tt('openApi.enabled')"
                :inactive-text="tt('openApi.disabled')"
                :loading="statusUpdatingId === row.clientId"
                @change="handleClientStatusChange(row, $event)"
              />
            </template>
          </el-table-column>
          <el-table-column :label="tt('openApi.client.updatedAt')" prop="updateTime" width="180">
            <template #default="{ row }">{{ valueOrDash(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column :label="tt('openApi.actions')" width="180" fixed="right">
            <template #default="{ row }">
              <el-tooltip :content="tt('openApi.client.edit')" placement="top">
                <el-button
                  link
                  type="primary"
                  :aria-label="tt('openApi.client.edit')"
                  @click="openClientForm(row)"
                >
                  <Icon icon="ep:edit-pen" />
                </el-button>
              </el-tooltip>
              <el-tooltip :content="tt('openApi.client.rotateClientSecret')" placement="top">
                <el-button
                  link
                  type="warning"
                  :aria-label="tt('openApi.client.rotateClientSecret')"
                  :loading="isClientActionLoading(row, 'CLIENT')"
                  @click="rotateSecret(row, 'CLIENT')"
                >
                  <Icon icon="ep:key" />
                </el-button>
              </el-tooltip>
              <el-tooltip :content="tt('openApi.client.rotateCallbackSecret')" placement="top">
                <el-button
                  link
                  type="warning"
                  :aria-label="tt('openApi.client.rotateCallbackSecret')"
                  :loading="isClientActionLoading(row, 'CALLBACK')"
                  @click="rotateSecret(row, 'CALLBACK')"
                >
                  <Icon icon="ep:connection" />
                </el-button>
              </el-tooltip>
              <el-tooltip :content="tt('openApi.client.delete')" placement="top">
                <el-button
                  link
                  type="danger"
                  :aria-label="tt('openApi.client.delete')"
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

      <el-tab-pane :label="tt('openApi.tabs.usage')" name="usage">
        <el-form :inline="true" class="query-form">
          <el-form-item :label="tt('openApi.usage.clientId')">
            <el-input
              v-model="usageQuery.clientId"
              :placeholder="tt('openApi.client.idPlaceholder')"
              clearable
              class="!w-240px"
              @keyup.enter="getUsageList"
            />
          </el-form-item>
          <el-form-item :label="tt('openApi.usage.date')">
            <el-date-picker
              v-model="usageDateRange"
              type="daterange"
              :range-separator="tt('openApi.dateRangeSeparator')"
              :start-placeholder="tt('openApi.startDate')"
              :end-placeholder="tt('openApi.endDate')"
              value-format="YYYY-MM-DD"
              class="!w-280px"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="getUsageList"
              ><Icon icon="ep:search" class="mr-5px" /> {{ tt('openApi.search') }}</el-button
            >
            <el-button @click="resetUsageQuery"
              ><Icon icon="ep:refresh-left" class="mr-5px" /> {{ tt('openApi.reset') }}</el-button
            >
          </el-form-item>
        </el-form>

        <el-table
          v-loading="usageLoading"
          :data="usageList"
          stripe
          :empty-text="tt('openApi.usage.empty')"
        >
          <el-table-column
            :label="tt('openApi.usage.requestDate')"
            prop="requestDate"
            min-width="150"
          >
            <template #default="{ row }">{{ valueOrDash(row.requestDate) }}</template>
          </el-table-column>
          <el-table-column :label="tt('openApi.usage.clientId')" prop="clientId" min-width="220">
            <template #default="{ row }">{{ valueOrDash(row.clientId) }}</template>
          </el-table-column>
          <el-table-column :label="tt('openApi.usage.requestCount')" min-width="130" align="right">
            <template #default="{ row }">{{ formatNumber(row.requestCount) }}</template>
          </el-table-column>
          <el-table-column :label="tt('openApi.usage.success')" min-width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.successCount) }}</template>
          </el-table-column>
          <el-table-column :label="tt('openApi.usage.failure')" min-width="120" align="right">
            <template #default="{ row }">{{ formatNumber(row.failureCount) }}</template>
          </el-table-column>
          <el-table-column
            :label="tt('openApi.usage.averageDuration')"
            min-width="140"
            align="right"
          >
            <template #default="{ row }">{{ formatDuration(row.averageDurationMs) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="tt('openApi.tabs.events')" name="events">
        <el-form ref="eventQueryFormRef" :model="eventQuery" :inline="true" class="query-form">
          <el-form-item :label="tt('openApi.events.clientId')" prop="clientId">
            <el-input
              v-model="eventQuery.clientId"
              :placeholder="tt('openApi.client.idPlaceholder')"
              clearable
              class="!w-210px"
              @keyup.enter="handleEventQuery"
            />
          </el-form-item>
          <el-form-item :label="tt('openApi.events.eventType')" prop="eventType">
            <el-select
              v-model="eventQuery.eventType"
              :placeholder="tt('openApi.events.allEvents')"
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
          <el-form-item :label="tt('openApi.events.status')" prop="status">
            <el-select
              v-model="eventQuery.status"
              :placeholder="tt('openApi.allStatuses')"
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
          <el-form-item :label="tt('openApi.events.createdAt')">
            <el-date-picker
              v-model="eventDateRange"
              type="datetimerange"
              :range-separator="tt('openApi.dateRangeSeparator')"
              :start-placeholder="tt('openApi.startDateTime')"
              :end-placeholder="tt('openApi.endDateTime')"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="!w-350px"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="handleEventQuery"
              ><Icon icon="ep:search" class="mr-5px" /> {{ tt('openApi.search') }}</el-button
            >
            <el-button @click="resetEventQuery"
              ><Icon icon="ep:refresh-left" class="mr-5px" /> {{ tt('openApi.reset') }}</el-button
            >
          </el-form-item>
        </el-form>

        <el-table
          v-loading="eventLoading"
          :data="eventList"
          stripe
          :empty-text="tt('openApi.events.empty')"
        >
          <el-table-column :label="tt('openApi.events.event')" min-width="250">
            <template #default="{ row }">
              <div class="primary-cell">{{ valueOrDash(row.eventType) }}</div>
              <div class="secondary-cell">{{ valueOrDash(row.eventId) }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="tt('openApi.events.clientId')" prop="clientId" min-width="190">
            <template #default="{ row }">{{ valueOrDash(row.clientId) }}</template>
          </el-table-column>
          <el-table-column :label="tt('openApi.events.resource')" min-width="180">
            <template #default="{ row }">
              <div>{{ valueOrDash(row.resourceType) }}</div>
              <div class="secondary-cell">{{ valueOrDash(row.resourceId) }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="tt('openApi.events.status')" width="125">
            <template #default="{ row }">
              <el-tag :type="eventStatusTagType(row.status)" effect="plain">{{
                eventStatusLabel(row.status)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="tt('openApi.events.attemptsHttp')" min-width="130">
            <template #default="{ row }">
              <div>{{ formatNumber(row.attemptCount) }} {{ tt('openApi.events.attempts') }}</div>
              <div class="secondary-cell">{{
                row.lastHttpStatus ? `HTTP ${row.lastHttpStatus}` : '-'
              }}</div>
            </template>
          </el-table-column>
          <el-table-column :label="tt('openApi.events.createdAt')" prop="createTime" width="180">
            <template #default="{ row }">{{ valueOrDash(row.createTime) }}</template>
          </el-table-column>
          <el-table-column :label="tt('openApi.actions')" width="110" fixed="right">
            <template #default="{ row }">
              <el-tooltip :content="tt('openApi.events.viewDetails')" placement="top">
                <el-button
                  link
                  type="primary"
                  :aria-label="tt('openApi.events.viewDetails')"
                  @click="openEventDetail(row.eventId)"
                >
                  <Icon icon="ep:view" />
                </el-button>
              </el-tooltip>
              <el-tooltip :content="tt('openApi.events.replay')" placement="top">
                <el-button
                  link
                  type="warning"
                  :aria-label="tt('openApi.events.replay')"
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
    :title="clientForm.clientId ? tt('openApi.form.editTitle') : tt('openApi.form.addTitle')"
    width="760px"
  >
    <el-form
      ref="clientFormRef"
      v-loading="clientFormLoading"
      :model="clientForm"
      :rules="clientFormRules"
      label-width="120px"
    >
      <el-form-item :label="tt('openApi.form.clientName')" prop="clientName">
        <el-input
          v-model="clientForm.clientName"
          :placeholder="tt('openApi.form.clientNamePlaceholder')"
          maxlength="128"
          show-word-limit
        />
      </el-form-item>
      <el-form-item :label="tt('openApi.form.authCallbackUrl')" prop="authCallbackUrl">
        <el-input
          v-model="clientForm.authCallbackUrl"
          placeholder="https://example.com/auth/callback"
          maxlength="512"
        />
      </el-form-item>
      <el-form-item :label="tt('openApi.form.publishCallbackUrl')" prop="publishCallbackUrl">
        <el-input
          v-model="clientForm.publishCallbackUrl"
          placeholder="https://example.com/publish/callback"
          maxlength="512"
        />
      </el-form-item>
      <el-form-item :label="tt('openApi.form.allowedIps')" prop="allowedIps">
        <el-input
          v-model="clientForm.allowedIps"
          type="textarea"
          :rows="2"
          :placeholder="tt('openApi.form.allowedIpsPlaceholder')"
          maxlength="2048"
          show-word-limit
        />
      </el-form-item>
      <el-form-item :label="tt('openApi.form.permissions')" prop="permissions">
        <el-checkbox-group v-model="clientForm.permissions">
          <el-checkbox label="auth">{{ tt('openApi.permission.auth') }}</el-checkbox>
          <el-checkbox label="media">{{ tt('openApi.permission.media') }}</el-checkbox>
          <el-checkbox label="publish">{{ tt('openApi.permission.publish') }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item :label="tt('openApi.form.perMinuteQuota')" prop="rateLimitPerMinute">
        <el-input-number
          v-model="clientForm.rateLimitPerMinute"
          :min="1"
          :max="10000"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item :label="tt('openApi.form.dailyQuota')" prop="dailyQuota">
        <el-input-number v-model="clientForm.dailyQuota" :min="1" controls-position="right" />
      </el-form-item>
      <el-form-item :label="tt('openApi.form.status')" prop="status">
        <el-radio-group v-model="clientForm.status">
          <el-radio :label="0">{{ tt('openApi.enabled') }}</el-radio>
          <el-radio :label="1">{{ tt('openApi.disabled') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="tt('openApi.form.remark')" prop="remark">
        <el-input
          v-model="clientForm.remark"
          type="textarea"
          :rows="3"
          :placeholder="tt('openApi.form.remarkPlaceholder')"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="clientDialogVisible = false">{{ tt('openApi.cancel') }}</el-button>
      <el-button type="primary" :loading="clientFormLoading" @click="submitClientForm">{{
        tt('openApi.confirm')
      }}</el-button>
    </template>
  </Dialog>

  <Dialog
    v-model="credentialDialogVisible"
    :title="tt('openApi.credential.title')"
    width="680px"
    @closed="clearCredentials"
  >
    <el-alert
      :title="tt('openApi.credential.warning')"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-descriptions :column="1" border class="credential-list">
      <el-descriptions-item :label="tt('openApi.credential.clientId')">{{
        valueOrDash(credentials.clientId)
      }}</el-descriptions-item>
      <el-descriptions-item
        v-if="credentials.clientSecret"
        :label="tt('openApi.credential.clientSecret')"
      >
        <div class="credential-value">
          <code>{{ credentials.clientSecret }}</code>
          <el-tooltip :content="tt('openApi.credential.copyClientSecret')" placement="top">
            <el-button
              circle
              :aria-label="tt('openApi.credential.copyClientSecret')"
              @click="copyCredential(credentials.clientSecret)"
            >
              <Icon icon="ep:copy-document" />
            </el-button>
          </el-tooltip>
        </div>
      </el-descriptions-item>
      <el-descriptions-item
        v-if="credentials.callbackSecret"
        :label="tt('openApi.credential.callbackSecret')"
      >
        <div class="credential-value">
          <code>{{ credentials.callbackSecret }}</code>
          <el-tooltip :content="tt('openApi.credential.copyCallbackSecret')" placement="top">
            <el-button
              circle
              :aria-label="tt('openApi.credential.copyCallbackSecret')"
              @click="copyCredential(credentials.callbackSecret)"
            >
              <Icon icon="ep:copy-document" />
            </el-button>
          </el-tooltip>
        </div>
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button type="primary" @click="credentialDialogVisible = false">{{
        tt('openApi.credential.close')
      }}</el-button>
    </template>
  </Dialog>

  <el-drawer
    v-model="eventDetailVisible"
    :title="tt('openApi.detail.title')"
    size="680px"
    destroy-on-close
  >
    <div v-loading="eventDetailLoading" class="event-detail">
      <template v-if="eventDetail">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="tt('openApi.detail.eventId')">{{
            valueOrDash(eventDetail.eventId)
          }}</el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.clientId')">{{
            valueOrDash(eventDetail.clientId)
          }}</el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.eventType')">{{
            valueOrDash(eventDetail.eventType)
          }}</el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.resource')">
            {{ valueOrDash(eventDetail.resourceType) }} / {{ valueOrDash(eventDetail.resourceId) }}
          </el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.callbackUrl')">{{
            valueOrDash(eventDetail.callbackUrl)
          }}</el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.status')">
            <el-tag :type="eventStatusTagType(eventDetail.status)" effect="plain">
              {{ eventStatusLabel(eventDetail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.deliveryInfo')">
            {{ formatNumber(eventDetail.attemptCount) }} {{ tt('openApi.events.attempts') }} /
            {{ eventDetail.lastHttpStatus ? `HTTP ${eventDetail.lastHttpStatus}` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.nextRetry')">{{
            valueOrDash(eventDetail.nextRetryTime)
          }}</el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.deliveredAt')">{{
            valueOrDash(eventDetail.deliveredTime)
          }}</el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.lastError')">{{
            valueOrDash(eventDetail.lastError)
          }}</el-descriptions-item>
          <el-descriptions-item :label="tt('openApi.detail.createdAt')">{{
            valueOrDash(eventDetail.createTime)
          }}</el-descriptions-item>
        </el-descriptions>
        <section class="payload-section">
          <h2>{{ tt('openApi.detail.payload') }}</h2>
          <pre>{{ formattedPayload }}</pre>
        </section>
      </template>
      <el-empty v-else-if="!eventDetailLoading" :description="tt('openApi.detail.empty')" />
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
import { useTkI18n } from '@/hooks/web/useTkI18n'

defineOptions({ name: 'TkOpenApi' })

type ActiveTab = 'clients' | 'usage' | 'events'
type SecretType = 'CLIENT' | 'CALLBACK'

interface ClientFormData extends Omit<OpenApiClientSaveReq, 'permissions'> {
  permissions: string[]
}

const message = useMessage()
const { tt } = useTkI18n()
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
const clientFormRules = computed<FormRules<ClientFormData>>(() => ({
  clientName: [
    { required: true, message: tt('openApi.validation.clientNameRequired'), trigger: 'blur' }
  ],
  permissions: [
    {
      type: 'array',
      required: true,
      min: 1,
      message: tt('openApi.validation.permissionRequired'),
      trigger: 'change'
    }
  ],
  rateLimitPerMinute: [
    { required: true, message: tt('openApi.validation.perMinuteRequired'), trigger: 'change' }
  ],
  dailyQuota: [
    { required: true, message: tt('openApi.validation.dailyQuotaRequired'), trigger: 'change' }
  ]
}))

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

const eventTypeOptions = computed(() => [
  { label: tt('openApi.eventType.authorizationCompleted'), value: 'authorization.completed' },
  { label: tt('openApi.eventType.authorizationFailed'), value: 'authorization.failed' },
  { label: tt('openApi.eventType.publishProcessing'), value: 'publish.processing' },
  { label: tt('openApi.eventType.publishSuccess'), value: 'publish.success' },
  { label: tt('openApi.eventType.publishFailed'), value: 'publish.failed' }
])

const eventStatusOptions = computed(() => [
  { label: tt('openApi.eventStatus.PENDING'), value: 'PENDING' },
  { label: tt('openApi.eventStatus.DELIVERING'), value: 'DELIVERING' },
  { label: tt('openApi.eventStatus.RETRYING'), value: 'RETRYING' },
  { label: tt('openApi.eventStatus.DELIVERED'), value: 'DELIVERED' },
  { label: tt('openApi.eventStatus.FAILED'), value: 'FAILED' },
  { label: tt('openApi.eventStatus.SKIPPED'), value: 'SKIPPED' }
])

const activeLoading = computed(() => {
  if (activeTab.value === 'clients') return clientLoading.value
  if (activeTab.value === 'usage') return usageLoading.value
  return eventLoading.value
})

const formattedPayload = computed(() => formatJson(eventDetail.value?.payloadJson))

const interpolate = (key: string, values: Record<string, string | number>) =>
  Object.entries(values).reduce(
    (result, [name, value]) => result.replaceAll(`{{${name}}}`, String(value)),
    tt(key)
  )

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
      message.success(tt('openApi.messages.clientUpdated'))
    } else {
      const credential = await TkOpenApiApi.createClient(payload)
      showCredentials(credential)
      message.success(tt('openApi.messages.clientCreated'))
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
  const action = status === 0 ? tt('openApi.enabled') : tt('openApi.disabled')
  try {
    await message.confirm(
      interpolate('openApi.messages.confirmStatus', {
        action,
        name: row.clientName || row.clientId
      }),
      interpolate('openApi.messages.statusConfirm', { action })
    )
  } catch {
    return
  }
  statusUpdatingId.value = row.clientId
  try {
    await TkOpenApiApi.updateClientStatus(row.clientId, status)
    message.success(interpolate('openApi.messages.clientStatusUpdated', { action }))
    await getClientList()
  } finally {
    statusUpdatingId.value = undefined
  }
}

const deleteClient = async (row: OpenApiClientVO) => {
  try {
    await message.delConfirm(
      interpolate('openApi.messages.deleteConfirm', { name: row.clientName || row.clientId }),
      tt('openApi.messages.deleteTitle')
    )
  } catch {
    return
  }
  clientActionLoadingKey.value = `${row.clientId}:DELETE`
  try {
    await TkOpenApiApi.deleteClient(row.clientId)
    message.success(tt('openApi.messages.clientDeleted'))
    if (clientList.value.length === 1 && clientQuery.pageNo > 1) clientQuery.pageNo -= 1
    await getClientList()
  } finally {
    clientActionLoadingKey.value = undefined
  }
}

const rotateSecret = async (row: OpenApiClientVO, type: SecretType) => {
  const label =
    type === 'CLIENT'
      ? tt('openApi.credential.clientSecret')
      : tt('openApi.credential.callbackSecret')
  try {
    await message.confirm(
      interpolate('openApi.messages.rotateConfirm', {
        name: row.clientName || row.clientId,
        label
      }),
      tt('openApi.messages.rotateTitle')
    )
  } catch {
    return
  }
  clientActionLoadingKey.value = `${row.clientId}:${type}`
  try {
    const response = await TkOpenApiApi.rotateSecret(row.clientId, type)
    const secret = extractRotatedSecret(response, type)
    if (!secret) {
      message.error(tt('openApi.messages.rotateMissingSecret'))
      return
    }
    showCredentials({
      clientId: response.clientId || row.clientId,
      clientSecret: type === 'CLIENT' ? secret : undefined,
      callbackSecret: type === 'CALLBACK' ? secret : undefined
    })
    message.success(interpolate('openApi.messages.rotated', { label }))
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
    message.success(tt('openApi.messages.copied'))
  } catch {
    message.error(tt('openApi.messages.copyFailed'))
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
      interpolate('openApi.messages.replayConfirm', { eventId: row.eventId }),
      tt('openApi.messages.replayTitle')
    )
  } catch {
    return
  }
  eventReplayingId.value = row.eventId
  try {
    await TkOpenApiApi.replayEvent(row.eventId)
    message.success(tt('openApi.messages.replaySubmitted'))
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
  const labels: Record<string, string> = {
    auth: tt('openApi.permission.auth'),
    media: tt('openApi.permission.media'),
    publish: tt('openApi.permission.publish')
  }
  return labels[value] || value
}

const eventStatusLabel = (value?: string) => {
  const labels: Record<string, string> = {
    PENDING: tt('openApi.eventStatus.PENDING'),
    DELIVERING: tt('openApi.eventStatus.DELIVERING'),
    RETRYING: tt('openApi.eventStatus.RETRYING'),
    DELIVERED: tt('openApi.eventStatus.DELIVERED'),
    FAILED: tt('openApi.eventStatus.FAILED'),
    SKIPPED: tt('openApi.eventStatus.SKIPPED')
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
