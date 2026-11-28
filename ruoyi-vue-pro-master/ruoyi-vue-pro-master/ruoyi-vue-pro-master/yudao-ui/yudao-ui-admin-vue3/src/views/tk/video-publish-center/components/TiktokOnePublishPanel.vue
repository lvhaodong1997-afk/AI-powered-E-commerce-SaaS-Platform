<template>
  <ContentWrap>
    <div class="mb-12px flex flex-wrap items-center justify-between gap-8px">
      <h3 class="m-0 text-base font-semibold">TikTok One 合作发布</h3>
      <div class="flex flex-wrap gap-8px">
        <el-button @click="load"><Icon icon="ep:refresh" class="mr-4px" />刷新</el-button>
        <el-button type="primary" @click="showCreate = true" v-hasPermi="['tk:tiktok-publish:create']">
          <Icon icon="ep:plus" class="mr-4px" />新建合作任务
        </el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="rows" row-key="id" empty-text="暂无合作发布任务">
      <el-table-column label="项目" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <div>{{ row.projectName }}</div>
          <div class="text-secondary">@{{ row.tiktokUsername }} · 成片 #{{ row.generationTaskId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="视频发布" width="132">
        <template #default="{ row }">
          <el-link v-if="row.videoUrl" :href="row.videoUrl" target="_blank" rel="noopener noreferrer" type="primary">已登记视频</el-link>
          <span v-else>待发布登记</span>
        </template>
      </el-table-column>
      <el-table-column label="项目关联" width="130">
        <template #default="{ row }">{{ row.projectConfirmed ? '用户已确认' : '待确认' }}</template>
      </el-table-column>
      <el-table-column label="网页锚点" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="!row.anchorName">未要求</span>
          <div v-else>
            <div>{{ row.anchorName }}</div>
            <div>{{ row.anchorConfirmed ? '用户已确认' : '待确认' }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="225">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row.generationTaskId)"><Icon icon="ep:download" class="mr-4px" />成片</el-button>
          <el-button link type="primary" @click="openHandoff(row)">官方入口</el-button>
          <el-button link type="primary" @click="openResult(row)" v-hasPermi="['tk:tiktok-publish:create']">登记结果</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="pager.pageNo" v-model:limit="pager.pageSize" @pagination="load" />
  </ContentWrap>

  <Dialog v-model="showCreate" title="新建 TikTok One 合作任务" width="min(560px, 94vw)">
    <el-form label-position="top" @submit.prevent="create">
      <el-form-item label="成片" required>
        <el-select v-model="form.generationTaskId" filterable class="w-full" placeholder="选择成片">
          <el-option v-for="video in videos" :key="video.id" :value="video.id!" :label="video.title || `成片 #${video.id}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="TikTok 达人用户名" required>
        <el-input v-model.trim="form.tiktokUsername" maxlength="25" placeholder="@username" />
      </el-form-item>
      <el-form-item label="TikTok One 项目名称或编号" required>
        <el-input v-model.trim="form.projectName" maxlength="160" />
      </el-form-item>
      <el-checkbox v-model="form.anchorRequired">要求展示网页锚点</el-checkbox>
      <template v-if="form.anchorRequired">
        <el-form-item label="已审核的网页锚点名称" required class="mt-8px">
          <el-input v-model.trim="form.anchorName" maxlength="160" />
        </el-form-item>
        <el-checkbox v-model="form.anchorApproved" class="wrap-checkbox">已核对锚点审核通过，且与项目市场一致</el-checkbox>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="showCreate = false">取消</el-button>
      <el-button type="primary" :disabled="!canCreate" :loading="saving" @click="create">创建任务</el-button>
    </template>
  </Dialog>

  <Dialog v-model="showHandoff" title="前往 TikTok One" width="min(560px, 94vw)" @closed="invite = ''">
    <el-form label-position="top" @submit.prevent="openInvite">
      <el-form-item label="TikTok One 官方邀请链接">
        <el-input v-model.trim="invite" maxlength="2048" autocomplete="off" placeholder="https://…" />
      </el-form-item>
      <el-link href="https://www.tiktok.com/tiktokone" target="_blank" rel="noopener noreferrer" type="primary">打开 TikTok One</el-link>
    </el-form>
    <template #footer>
      <el-button @click="showHandoff = false">关闭</el-button>
      <el-button type="primary" :disabled="!invite" @click="openInvite">打开邀请链接</el-button>
    </template>
  </Dialog>

  <Dialog v-model="showResult" title="登记 TikTok One 发布结果" width="min(560px, 94vw)">
    <el-form label-position="top" @submit.prevent="confirm">
      <el-form-item label="视频完整链接" required>
        <el-input v-model.trim="result.videoUrl" :disabled="!!selected?.videoUrl" maxlength="2048" placeholder="https://www.tiktok.com/@username/video/…" />
      </el-form-item>
      <div><el-checkbox v-model="result.projectConfirmed" :disabled="!!selected?.projectConfirmed">已在 TikTok 确认视频关联此项目</el-checkbox></div>
      <div v-if="selected?.anchorName">
        <el-checkbox v-model="result.anchorConfirmed" :disabled="!result.projectConfirmed || !!selected.anchorConfirmed">已在目标市场确认网页锚点可见</el-checkbox>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="showResult = false">取消</el-button>
      <el-button type="primary" :disabled="!result.videoUrl" :loading="saving" @click="confirm">保存登记</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import request from '@/config/axios'
import { TkGenerationApi, type TkGenerationTaskVO } from '@/api/tk/generation'

interface PublishRecord {
  id: number
  generationTaskId: number
  tiktokUsername: string
  projectName: string
  anchorName?: string
  videoUrl?: string
  projectConfirmed: boolean
  anchorConfirmed: boolean
}

const props = defineProps<{ videos: TkGenerationTaskVO[]; source?: TkGenerationTaskVO }>()
const endpoint = '/tk/tiktok-one/publish/tasks'
const message = useMessage()
const pager = reactive({ pageNo: 1, pageSize: 10 })
const rows = ref<PublishRecord[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)
const showResult = ref(false)
const showHandoff = ref(false)
const invite = ref('')
const selected = ref<PublishRecord>()
const emptyForm = () => ({ generationTaskId: undefined as number | undefined, tiktokUsername: '', projectName: '',
  anchorRequired: false, anchorName: '', anchorApproved: false })
const form = reactive(emptyForm())
const result = reactive({ videoUrl: '', projectConfirmed: false, anchorConfirmed: false })
const canCreate = computed(() => form.generationTaskId && /^@?[A-Za-z0-9_.]{1,24}$/.test(form.tiktokUsername) &&
  form.projectName && (!form.anchorRequired || (form.anchorName && form.anchorApproved)))

const load = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const data = await request.get<{ list: PublishRecord[]; total: number }>({ url: endpoint, params: { ...pager } })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const create = async () => {
  if (!canCreate.value || saving.value) return
  saving.value = true
  try {
    await request.post({ url: endpoint, data: { ...form } })
    showCreate.value = false
    Object.assign(form, emptyForm())
    message.success('合作任务已创建，待官方发布')
    pager.pageNo = 1
    await load()
    showHandoff.value = true
  } finally {
    saving.value = false
  }
}

const openHandoff = (_row: PublishRecord) => {
  showHandoff.value = true
}

const openInvite = () => {
  try {
    const url = new URL(invite.value)
    if (url.protocol !== 'https:' || url.port || url.username || url.password ||
        !['tiktok.com', 'www.tiktok.com', 'ads.tiktok.com', 'creatormarketplace.tiktok.com', 'tto.tiktok.com', 'vm.tiktok.com', 'vt.tiktok.com'].includes(url.hostname)) {
      throw new Error('Invalid TikTok host')
    }
    window.open(url.href, '_blank', 'noopener,noreferrer')
  } catch {
    message.warning('请输入 TikTok 官方 HTTPS 邀请链接')
  }
}

const download = async (id: number) => {
  const source = props.videos.find(video => video.id === id) || await TkGenerationApi.getGeneration(id)
  if (!source?.outputUrl) {
    message.warning('当前任务暂无成片')
    return
  }
  const link = document.createElement('a')
  link.href = source.outputUrl
  link.download = `video-${id}.mp4`
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  link.click()
}

const openResult = (row: PublishRecord) => {
  selected.value = row
  Object.assign(result, { videoUrl: row.videoUrl || '', projectConfirmed: row.projectConfirmed,
    anchorConfirmed: row.anchorConfirmed })
  showResult.value = true
}

const confirm = async () => {
  if (saving.value || !selected.value || !result.videoUrl) return
  saving.value = true
  try {
    await request.post({ url: `${endpoint}/${selected.value.id}/confirm`, data: { ...result,
      anchorConfirmed: result.projectConfirmed && result.anchorConfirmed } })
    showResult.value = false
    message.success('发布结果已登记')
    await load()
  } finally {
    saving.value = false
  }
}

watch(() => props.source, source => {
  if (!source?.id) return
  form.generationTaskId = source.id
  showCreate.value = true
}, { immediate: true })
onMounted(load)
</script>

<style scoped>
.wrap-checkbox {
  height: auto;
  white-space: normal;
}
</style>
