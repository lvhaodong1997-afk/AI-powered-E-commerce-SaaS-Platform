<template>
  <div class="content-display-page">
    <ContentWrap>
      <div class="toolbar">
        <el-form :inline="true" :model="query" class="query-form">
          <el-form-item :label="tt('contentDisplay.account')">
            <el-select
              v-model="query.accountId"
              clearable
              filterable
              :placeholder="tt('contentDisplay.allAccounts')"
              class="account-select"
            >
              <el-option
                v-for="account in accounts"
                :key="account.id"
                :label="accountLabel(account)"
                :value="account.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="tt('contentDisplay.keyword')">
            <el-input
              v-model="query.keyword"
              clearable
              :placeholder="tt('contentDisplay.keywordPlaceholder')"
              @keyup.enter="loadVideos"
            />
          </el-form-item>
          <el-form-item :label="tt('contentDisplay.status')">
            <el-select
              v-model="query.status"
              clearable
              :placeholder="tt('contentDisplay.allStatuses')"
              class="status-select"
            >
              <el-option :label="tt('contentDisplay.public')" value="PUBLIC" />
              <el-option :label="tt('contentDisplay.noLongerPublic')" value="NO_LONGER_PUBLIC" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadVideos"
              ><Icon icon="ep:search" class="mr-5px" />{{ tt('contentDisplay.query') }}</el-button
            >
            <el-button @click="resetQuery"
              ><Icon icon="ep:refresh" class="mr-5px" />{{ tt('contentDisplay.reset') }}</el-button
            >
          </el-form-item>
        </el-form>
        <el-button
          type="primary"
          plain
          :loading="syncingAccountId !== undefined"
          :disabled="!query.accountId"
          @click="syncSelectedAccount"
        >
          <Icon icon="ep:refresh" class="mr-5px" />{{ tt('contentDisplay.syncAccountVideos') }}
        </el-button>
      </div>
      <el-alert
        :title="tt('contentDisplay.notice')"
        type="info"
        :closable="false"
        class="mb-12px"
      />
      <el-table v-loading="loading" :data="videos" stripe>
        <el-table-column :label="tt('contentDisplay.cover')" width="90">
          <template #default="scope">
            <el-image
              v-if="scope.row.coverImageUrl"
              :src="scope.row.coverImageUrl"
              fit="cover"
              class="video-cover"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="tt('contentDisplay.account')" width="150">
          <template #default="scope">{{ findAccountLabel(scope.row.accountId) }}</template>
        </el-table-column>
        <el-table-column :label="tt('contentDisplay.title')" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{
            scope.row.title || scope.row.videoDescription || '-'
          }}</template>
        </el-table-column>
        <el-table-column
          :label="tt('contentDisplay.videoId')"
          prop="videoId"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column :label="tt('contentDisplay.metrics')" width="210">
          <template #default="scope">
            <div class="video-metrics">
              <span>{{ tt('contentDisplay.views') }} {{ scope.row.viewCount ?? '-' }}</span>
              <span>{{ tt('contentDisplay.likes') }} {{ scope.row.likeCount ?? '-' }}</span>
              <span>{{ tt('contentDisplay.comments') }} {{ scope.row.commentCount ?? '-' }}</span>
              <span>{{ tt('contentDisplay.shares') }} {{ scope.row.shareCount ?? '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="tt('contentDisplay.status')" width="130">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'PUBLIC' ? 'success' : 'warning'">{{
              statusLabel(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="tt('contentDisplay.publishedAt')" width="175">
          <template #default="scope">{{ formatTimestamp(scope.row.videoCreateTime) }}</template>
        </el-table-column>
        <el-table-column :label="tt('contentDisplay.actions')" width="180" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="primary"
              :disabled="!scope.row.embedLink"
              @click="preview(scope.row)"
              >{{ tt('contentDisplay.preview') }}</el-button
            >
            <el-button
              link
              type="primary"
              :disabled="!scope.row.shareUrl"
              @click="openTikTok(scope.row.shareUrl)"
              >{{ tt('contentDisplay.openTikTok') }}</el-button
            >
            <el-button link type="warning" @click="refreshVideo(scope.row)">{{
              tt('contentDisplay.refresh')
            }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        :total="total"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        @pagination="loadVideos"
      />
    </ContentWrap>

    <el-dialog v-model="previewVisible" :title="tt('contentDisplay.previewTitle')" width="760px">
      <div class="preview-frame-wrap">
        <iframe
          v-if="previewUrl"
          :src="previewUrl"
          title="TikTok video"
          class="preview-frame"
          allow="encrypted-media"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { TkTiktokAccountApi, TkTiktokContentDisplayApi } from '@/api/tk/videoPublishCenter'
import type { TkTiktokAccountVO, TkTiktokContentVideoVO } from '@/api/tk/videoPublishCenter'
import { formatDate } from '@/utils/formatTime'
import { useTkI18n } from '@/hooks/web/useTkI18n'

defineOptions({ name: 'TkTiktokContentDisplay' })

const message = useMessage()
const { tt } = useTkI18n()
const loading = ref(false)
const syncingAccountId = ref<number>()
const accounts = ref<TkTiktokAccountVO[]>([])
const videos = ref<TkTiktokContentVideoVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  accountId: undefined as number | undefined,
  keyword: undefined as string | undefined,
  status: undefined as string | undefined
})
const previewVisible = ref(false)
const previewUrl = ref('')

const accountLabel = (account: TkTiktokAccountVO) =>
  account.displayName ||
  account.username ||
  account.openId ||
  `${tt('contentDisplay.account')} #${account.id}`
const findAccountLabel = (accountId: number) =>
  accountLabel(
    accounts.value.find((item) => item.id === accountId) || {
      id: accountId,
      openId: String(accountId),
      companyId: 0,
      tokenStatus: '',
      authStatus: ''
    }
  )
const statusLabel = (status?: string) =>
  status === 'PUBLIC'
    ? tt('contentDisplay.public')
    : status === 'NO_LONGER_PUBLIC'
      ? tt('contentDisplay.noLongerPublic')
      : status || '-'
const formatTimestamp = (value?: number) =>
  value ? formatDate(value < 1_000_000_000_000 ? value * 1000 : value) : '-'

const loadAccounts = async () => {
  const data = await TkTiktokAccountApi.getPage({
    pageNo: 1,
    pageSize: 100,
    authStatus: 'AUTHORIZED'
  })
  accounts.value = data.list || []
}

const loadVideos = async () => {
  loading.value = true
  try {
    const data = await TkTiktokContentDisplayApi.getPage(query)
    videos.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  query.pageNo = 1
  query.accountId = undefined
  query.keyword = undefined
  query.status = undefined
  loadVideos()
}

const syncSelectedAccount = async () => {
  if (!query.accountId) return
  syncingAccountId.value = query.accountId
  try {
    const result = await TkTiktokContentDisplayApi.sync(query.accountId)
    message.success(
      tt('contentDisplay.syncSuccess').replace('{{count}}', String(result.syncedCount))
    )
    await loadVideos()
    await loadAccounts()
  } finally {
    syncingAccountId.value = undefined
  }
}

const refreshVideo = async (row: TkTiktokContentVideoVO) => {
  try {
    await TkTiktokContentDisplayApi.refresh(row.accountId, row.videoId)
    message.success(tt('contentDisplay.refreshSuccess'))
    await loadVideos()
  } catch {
    await loadVideos()
  }
}

const preview = (row: TkTiktokContentVideoVO) => {
  if (!row.embedLink) return
  previewUrl.value = row.embedLink
  previewVisible.value = true
}

const openTikTok = (url?: string) => {
  if (url) window.open(url, '_blank')
}

onMounted(async () => {
  await Promise.all([loadAccounts(), loadVideos()])
})
</script>

<style scoped>
.content-display-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.query-form {
  flex: 1;
}
.account-select {
  width: 190px;
}
.status-select {
  width: 150px;
}
.video-cover {
  width: 56px;
  height: 76px;
  border-radius: 4px;
}
.video-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 12px;
}
.video-metrics > span {
  overflow-wrap: anywhere;
}
.preview-frame-wrap {
  display: flex;
  justify-content: center;
  min-height: 420px;
}
.preview-frame {
  width: 100%;
  min-height: 420px;
  border: 0;
}
@media (max-width: 960px) {
  .toolbar {
    flex-direction: column;
  }
}
</style>
