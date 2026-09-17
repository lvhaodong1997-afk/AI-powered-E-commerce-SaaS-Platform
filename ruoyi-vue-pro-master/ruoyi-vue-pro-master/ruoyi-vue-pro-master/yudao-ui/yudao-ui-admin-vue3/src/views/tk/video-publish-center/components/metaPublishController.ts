import { reactive } from 'vue'
import type {
  SocialPublishApi, SocialAccount, SocialAuthSession, SocialCapabilities, SocialCreate,
  SocialDetail, SocialMedia, SocialPlatform, SocialTask, FacebookPage
} from '@/api/tk/socialPublish'
import { uploadSocialVideoToOss } from '@/api/tk/socialPublish'

export const canRetrySocialDetail = (status: string) => ['FAILED', 'REAUTH_REQUIRED'].includes(status)
export const isSocialAccountAuthorized = (status: string) => ['AUTHORIZED', 'ACTIVE'].includes(status)
export const socialStatusLabel = (status: string) => ({
  PENDING: '排队中', PROCESSING: '平台处理中', SUCCESS: '发布成功', PARTIAL_SUCCESS: '部分成功',
  FAILED: '失败', REAUTH_REQUIRED: '需要重新授权', UNKNOWN: '结果待人工核验',
  AUTHORIZED: '已授权', ACTIVE: '可用', UNBOUND: '已解绑', EXPIRED: '已过期', INACTIVE: '不可用'
}[status] || status)
export const platformLabel = (platform: SocialPlatform) => platform === 'INSTAGRAM' ? 'Instagram' : 'Facebook Page'
export const accountLabel = (account: SocialAccount) => account.accountName || account.username || account.externalAccountId || `账号 #${account.id}`

interface Popup {
  closed: boolean
  location: { href: string }
  opener: unknown
  close: () => void
}
interface Environment {
  openPopup: () => Popup | null
  setTimeout: (fn: () => Promise<void>, delay: number) => number
  clearTimeout: (id: number) => void
  now: () => number
  idempotencyKey: () => string
  notify: (message: string) => void
}

export function createMetaPublishController(
  api: typeof SocialPublishApi,
  permitted: (permission: string) => boolean,
  env: Environment
) {
  const state = reactive({
    loading: false, enabled: false, initialized: false, loadError: '', pollError: '',
    mediaTypes: [] as SocialCapabilities['mediaTypes'], platforms: [] as SocialPlatform[], maxVideoSizeMb: 100, maxVideoBytes: 100 * 1024 * 1024,
    accounts: [] as SocialAccount[], accountsLoading: false,
    tasks: [] as SocialTask[], taskTotal: 0, taskQuery: { pageNo: 1, pageSize: 10, status: '' },
    details: [] as SocialDetail[], detailTotal: 0, detailTaskId: undefined as number | undefined,
    detailQuery: { pageNo: 1, pageSize: 10 }, lastTaskId: undefined as number | undefined,
    draft: { title: '', accountIds: [] as number[], instagramCaption: '', facebookMessage: '', generationTaskId: undefined as number | undefined },
    media: undefined as SocialMedia | undefined, uploading: false, uploadPercent: 0, submitting: false,
    auth: { status: 'PENDING', message: '' } as SocialAuthSession,
    authBusy: false, sessionId: '', authPlatform: undefined as SocialPlatform | undefined,
    pages: [] as FacebookPage[], pageIds: [] as string[], pagePickerVisible: false, binding: false,
    busyAccounts: [] as number[], retrying: [] as number[], syncing: [] as number[]
  })
  let running = false, epoch = 0, authVersion = 0, detailVersion = 0, taskVersion = 0
  let authTimer: number | undefined, taskTimer: number | undefined, popup: Popup | null = null
  let authStarted = 0, previousBody = '', submissionKey = ''
  let accountsNeedRefresh = false
  const live = (version: number) => running && epoch === version
  const requirePermission = (permission: string) => {
    if (!permitted(permission)) throw new Error('没有执行此操作的权限')
    if (!running || !state.enabled) throw new Error('Meta 发布配置待完成，请刷新后重试')
  }
  const clearAuthTimer = () => { if (authTimer !== undefined) env.clearTimeout(authTimer); authTimer = undefined }
  const closePopup = () => {
    try { popup?.close() } catch { /* A cross-origin popup may no longer be accessible. */ }
    popup = null
  }
  const cancelAuth = () => {
    authVersion++; clearAuthTimer(); closePopup()
    state.authBusy = false; state.pagePickerVisible = false; state.binding = false
    state.sessionId = ''; state.pages = []; state.pageIds = []
  }

  async function refreshAccounts() {
    if (!running || !state.enabled || !permitted('tk:social-account:query')) return
    const version = epoch
    state.accountsLoading = true
    try {
      const list: SocialAccount[] = []
      let pageNo = 1
      while (live(version)) {
        const page = await api.accountPage({ pageNo: pageNo++, pageSize: 100 })
        if (!live(version)) return
        list.push(...page.list)
        if (list.length >= page.total || !page.list.length) break
      }
      if (live(version)) { state.accounts = list; accountsNeedRefresh = false }
    } catch (error) {
      if (live(version)) accountsNeedRefresh = true
      throw error
    } finally { if (live(version)) state.accountsLoading = false }
  }
  async function refreshTasks() {
    if (!running || !state.enabled || !permitted('tk:social-publish:query')) return
    const version = epoch, requestId = ++taskVersion
    const page = await api.taskPage({ ...state.taskQuery, status: state.taskQuery.status || undefined })
    if (!live(version) || requestId !== taskVersion) return
    state.tasks = page.list; state.taskTotal = page.total
  }
  async function refreshDetails() {
    if (!running || !state.enabled || !permitted('tk:social-publish:query') || !state.detailTaskId) return
    const version = epoch, taskId = state.detailTaskId, requestId = ++detailVersion
    const page = await api.detailPage({ ...state.detailQuery, taskId })
    if (!live(version) || requestId !== detailVersion || state.detailTaskId !== taskId) return
    state.details = page.list; state.detailTotal = page.total
  }
  async function openDetails(taskId: number) {
    requirePermission('tk:social-publish:query')
    state.detailTaskId = taskId; state.detailQuery.pageNo = 1; state.details = []; state.detailTotal = 0
    await refreshDetails()
  }
  function scheduleTasks(version: number) {
    if (!live(version) || !state.enabled || !permitted('tk:social-publish:query') || taskTimer !== undefined) return
    taskTimer = env.setTimeout(async () => {
      taskTimer = undefined
      if (!live(version) || !state.enabled || !permitted('tk:social-publish:query')) return
      try {
        const reads = [refreshTasks(), refreshDetails()]
        if (accountsNeedRefresh) reads.push(refreshAccounts())
        const results = await Promise.allSettled(reads)
        if (live(version)) state.pollError = results.some(result => result.status === 'rejected')
          ? '状态刷新失败，正在重连；请勿重复创建任务。' : ''
      } catch { if (live(version)) state.pollError = '状态刷新失败，正在重连；请勿重复创建任务。' }
      finally { scheduleTasks(version) }
    }, 5000)
  }
  async function start() {
    if (running) return
    running = true
    const version = ++epoch
    state.loading = true; state.enabled = false; state.loadError = ''; state.pollError = ''; accountsNeedRefresh = false
    try {
      if (!permitted('tk:social-publish:query')) {
        state.loadError = '需要 Meta 发布查询权限才能读取平台配置。'
        return
      }
      const capability = await api.capabilities()
      if (!live(version)) return
      state.enabled = capability.enabled; state.mediaTypes = capability.mediaTypes; state.platforms = capability.platforms
      state.maxVideoBytes = capability.maxVideoBytes && capability.maxVideoBytes > 0 ? capability.maxVideoBytes
        : (capability.maxVideoSizeMb && capability.maxVideoSizeMb > 0 ? capability.maxVideoSizeMb : 100) * 1024 * 1024
      state.maxVideoSizeMb = state.maxVideoBytes / (1024 * 1024)
      if (state.enabled) {
        const results = await Promise.allSettled([refreshAccounts(), refreshTasks()])
        if (live(version) && results.some(result => result.status === 'rejected')) {
          state.pollError = '账号或任务读取失败，正在重连；请勿重复创建任务。'
        }
      }
    } catch (error) { if (live(version)) state.loadError = error instanceof Error ? error.message : '加载 Meta 配置失败' }
    finally {
      if (live(version)) {
        state.loading = false; state.initialized = true
        scheduleTasks(version)
      }
    }
  }
  function stop() {
    running = false; epoch++; cancelAuth()
    if (taskTimer !== undefined) env.clearTimeout(taskTimer)
    taskTimer = undefined
    state.loading = false; state.accountsLoading = false
  }

  function scheduleAuth(version: number, authId: number) {
    if (!live(version) || authId !== authVersion || !state.authBusy) return
    clearAuthTimer()
    authTimer = env.setTimeout(async () => {
      authTimer = undefined
      if (!live(version) || authId !== authVersion) return
      if (env.now() - authStarted >= 10 * 60_000) {
        state.auth = { status: 'EXPIRED', message: '授权等待已超时，请重新连接账号。' }
        state.authBusy = false; state.pagePickerVisible = false; closePopup(); return
      }
      try {
        const session = await api.session(state.sessionId)
        if (!live(version) || authId !== authVersion) return
        state.auth = session
        if (session.status === 'SUCCESS') {
          state.authBusy = false; state.pagePickerVisible = false; closePopup()
          env.notify('账号授权成功'); await refreshAccounts()
        } else if (session.status === 'FAILED' || session.status === 'EXPIRED') {
          state.authBusy = false; state.pagePickerVisible = false; closePopup()
        } else if (session.status === 'PAGES_READY' && state.authPlatform === 'FACEBOOK_PAGE' && !state.pagePickerVisible) {
          const pages = await api.facebookPages(state.sessionId)
          if (!live(version) || authId !== authVersion) return
          state.pages = pages; state.pageIds = []; state.pagePickerVisible = true; closePopup()
        } else if (popup?.closed) {
          state.auth.message = '授权窗口已关闭，仍在等待服务器确认；可取消等待后重新连接。'
        }
      } catch { if (live(version) && authId === authVersion) state.auth.message = '授权状态暂时无法读取，正在重试。' }
      finally { scheduleAuth(version, authId) }
    }, 2500)
  }
  async function connect(platform: SocialPlatform) {
    requirePermission('tk:social-account:authorize')
    if (!state.platforms.includes(platform)) throw new Error('服务器暂不支持此平台')
    if (state.authBusy) return
    cancelAuth()
    // Open in the user's click stack, before the network request (popup blockers).
    popup = env.openPopup()
    if (!popup) throw new Error('浏览器拦截了授权弹窗，请允许弹窗后重试')
    popup.opener = null
    const version = epoch, authId = authVersion
    state.authBusy = true; state.authPlatform = platform; state.auth = { status: 'PENDING', message: '等待官方授权完成' }
    authStarted = env.now()
    try {
      const session = await api.authorize(platform)
      if (!live(version) || authId !== authVersion) return
      state.sessionId = session.sessionId
      const url = new URL(session.authorizeUrl)
      if (url.protocol !== 'https:') throw new Error('授权地址无效，请检查配置')
      if (popup && !popup.closed) popup.location.href = url.href
      scheduleAuth(version, authId)
    } catch (error) {
      if (live(version) && authId === authVersion) {
        state.authBusy = false; closePopup(); state.auth = { status: 'FAILED', message: '授权启动失败，请重试。' }
        throw error
      }
    }
  }
  async function bindPages() {
    requirePermission('tk:social-account:authorize')
    if (state.binding) return
    if (!state.sessionId || state.auth.status !== 'PAGES_READY' || !state.pageIds.length ||
      state.pageIds.some((id) => !state.pages.some((page) => page.id === id))) throw new Error('请从当前授权会话中选择有效主页')
    const version = epoch, authId = authVersion
    state.binding = true
    try {
      await api.bindPages({ sessionId: state.sessionId, pageIds: [...state.pageIds] })
      if (!live(version) || authId !== authVersion) return
      clearAuthTimer(); authVersion++; state.authBusy = false; state.pagePickerVisible = false
      state.auth = { status: 'SUCCESS', message: 'Facebook 主页绑定成功' }
      await refreshAccounts()
    } finally { if (live(version)) state.binding = false }
  }
  async function accountAction(action: 'validate' | 'unbind' | 'delete', id: number) {
    requirePermission('tk:social-account:update')
    if (state.busyAccounts.includes(id)) return
    const version = epoch
    state.busyAccounts.push(id)
    try {
      await api[action](id)
      if (!live(version)) return
      if (action !== 'validate') state.draft.accountIds = state.draft.accountIds.filter((value) => value !== id)
      await refreshAccounts()
    } finally { state.busyAccounts = state.busyAccounts.filter((value) => value !== id) }
  }
  function clearMedia() { state.media = undefined; state.draft.generationTaskId = undefined }
  function selectGenerated(id: number) {
    requirePermission('tk:social-publish:create')
    if (!state.mediaTypes.includes('VIDEO')) throw new Error('服务器暂不支持视频发布')
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('请选择有效的成片')
    state.media = undefined; state.draft.generationTaskId = id
  }

  function readVideoMetadata(file: File): Promise<{ width: number; height: number; durationSeconds: number; frameRate: number }> {
    return new Promise((resolve, reject) => {
      const video = document.createElement('video')
      const source = URL.createObjectURL(file)
      const cleanup = () => { URL.revokeObjectURL(source); video.remove() }
      video.preload = 'metadata'
      video.onloadedmetadata = () => {
        const metadata = { width: video.videoWidth, height: video.videoHeight, durationSeconds: video.duration, frameRate: 30 }
        cleanup()
        if (!metadata.width || !metadata.height || !Number.isFinite(metadata.durationSeconds) || metadata.durationSeconds <= 0) {
          reject(new Error('无法读取视频元数据，请重新选择 MP4 视频'))
        } else resolve(metadata)
      }
      video.onerror = () => { cleanup(); reject(new Error('无法读取视频元数据，请重新选择 MP4 视频')) }
      video.src = source
      video.load()
    })
  }

  async function upload(file: File) {
    requirePermission('tk:social-publish:create')
    if (state.uploading || state.submitting) return
    const type = file.type === 'image/jpeg' ? 'IMAGE' : file.type === 'video/mp4' ? 'VIDEO' : undefined
    if (!type) throw new Error('仅支持 MP4 视频和 JPEG 图片')
    if (!state.mediaTypes.includes(type)) throw new Error('服务器暂不支持此媒体类型')
    const maxMb = type === 'VIDEO' ? state.maxVideoSizeMb : 8
    if (!file.size || file.size > (type === 'VIDEO' ? state.maxVideoBytes : 8 * 1024 * 1024)) throw new Error(`文件大小须大于 0 且不超过 ${maxMb} MB`)
    const version = epoch
    state.uploading = true; state.uploadPercent = 0
    try {
      let media: SocialMedia
      if (type === 'VIDEO') {
        const metadata = await readVideoMetadata(file)
        const session = await api.videoUploadSession({ fileName: file.name, fileSize: file.size, contentType: 'video/mp4' })
        await uploadSocialVideoToOss(session, file, percent => { if (live(version)) state.uploadPercent = percent })
        media = await api.videoUploadComplete({
          uploadId: session.uploadId, fileName: file.name, fileSize: file.size, contentType: 'video/mp4',
          objectKey: session.objectKey, ...metadata
        })
      } else media = await api.upload(file)
      if (!live(version)) return
      state.media = media; state.draft.generationTaskId = undefined
    } finally { state.uploading = false; state.uploadPercent = 0 }
  }
  async function submit() {
    requirePermission('tk:social-publish:create')
    requirePermission('tk:social-account:query')
    if (state.submitting) return
    if (state.uploading) throw new Error('请等待媒体上传完成')
    const draft = state.draft
    if (!draft.title.trim()) throw new Error('请填写任务标题')
    if (draft.title.trim().length > 255) throw new Error('任务标题最多 255 字符')
    if (draft.instagramCaption.length > 2200) throw new Error('Instagram 文案最多 2200 字符')
    if (draft.facebookMessage.length > 5000) throw new Error('Facebook 正文最多 5000 字符')
    if (!draft.accountIds.length) throw new Error('请选择至少一个发布账号')
    if (draft.accountIds.length > 20) throw new Error('每次最多选择 20 个发布账号')
    const targets = draft.accountIds.map((id) => state.accounts.find((account) => account.id === id))
    if (targets.some((account) => !account || !isSocialAccountAuthorized(account.status) || !state.platforms.includes(account.platform))) {
      throw new Error('所选账号不可用或平台暂不支持，请刷新账号并重新授权')
    }
    const mediaType = draft.generationTaskId ? 'VIDEO' : state.media?.mediaType
    if (mediaType && !state.mediaTypes.includes(mediaType)) throw new Error('服务器暂不支持此媒体类型')
    if (!mediaType && targets.some((account) => account?.platform === 'INSTAGRAM')) throw new Error('Instagram 需要视频或图片媒体')
    if (!mediaType && !draft.facebookMessage.trim()) throw new Error('Facebook 纯文本发布需要填写正文')
    const body = {
      accountIds: [...new Set(draft.accountIds)], title: draft.title.trim(),
      instagramCaption: draft.instagramCaption, facebookMessage: draft.facebookMessage,
      ...(draft.generationTaskId ? { generationTaskId: draft.generationTaskId } : state.media ? { mediaId: state.media.id } : {})
    }
    const fingerprint = JSON.stringify(body)
    if (!submissionKey || previousBody !== fingerprint) { submissionKey = env.idempotencyKey(); previousBody = fingerprint }
    const payload: SocialCreate = { ...body, idempotencyKey: submissionKey }
    const version = epoch
    state.submitting = true
    try {
      const taskId = await api.create(payload)
      if (!live(version)) return
      state.lastTaskId = taskId; state.detailTaskId = taskId; state.detailQuery.pageNo = 1
      state.details = []; state.detailTotal = 0; state.taskQuery.pageNo = 1; state.taskQuery.status = ''
      submissionKey = ''; previousBody = ''
      env.notify(`任务 #${taskId} 已加入队列，平台正在异步处理。`)
      // A read failure must not present an accepted create as a failed publish.
      try { await Promise.all([refreshTasks(), refreshDetails()]) }
      catch { if (live(version)) state.pollError = '任务已创建，状态暂时读取失败，请稍后刷新。' }
    } finally { state.submitting = false }
  }
  async function retry(detail: SocialDetail) {
    requirePermission('tk:social-publish:retry')
    if (!canRetrySocialDetail(detail.status)) throw new Error('此状态不可重试；结果未知时须先人工核验平台，避免重复发布')
    if (state.retrying.includes(detail.id)) return
    const version = epoch
    state.retrying.push(detail.id)
    try {
      await api.retry(detail.id)
      if (!live(version)) return
      detail.status = 'PENDING'
      env.notify('重试已加入队列')
      await Promise.all([refreshTasks(), refreshDetails()])
    } finally { state.retrying = state.retrying.filter((id) => id !== detail.id) }
  }
  async function sync(taskId: number) {
    requirePermission('tk:social-publish:query')
    if (state.syncing.includes(taskId)) return
    const version = epoch
    state.syncing.push(taskId)
    try {
      await api.sync(taskId)
      if (live(version)) env.notify('已请求核对平台状态，请等待异步更新。')
    } finally { state.syncing = state.syncing.filter((id) => id !== taskId) }
  }
  return { state, start, stop, connect, cancelAuth, bindPages, refreshAccounts, refreshTasks,
    refreshDetails, openDetails, accountAction, clearMedia, selectGenerated, upload, submit, retry, sync }
}
