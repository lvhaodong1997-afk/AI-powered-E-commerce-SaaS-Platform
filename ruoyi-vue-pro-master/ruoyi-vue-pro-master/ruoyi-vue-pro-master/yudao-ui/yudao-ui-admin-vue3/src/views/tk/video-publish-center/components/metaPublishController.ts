import { reactive } from 'vue'
import type {
  SocialPublishApi, SocialAccount, SocialAuthSession, SocialCapabilities, SocialCreate,
  SocialDetail, SocialMedia, SocialPlatform, SocialTask, FacebookPage, SocialStats
} from '@/api/tk/socialPublish'
import { uploadSocialVideoToOss } from '@/api/tk/socialPublish'
import { mergeStats, statsKey, timeMillis } from './metaStats'

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
    mediaError: '', mediaChecking: false,
    stats: {} as Record<string, SocialStats>, statsErrors: {} as Record<string, string>,
    statsLoading: {} as Record<string, boolean>, statsSyncing: {} as Record<string, boolean>,
    statsCooldown: {} as Record<string, number>, statsClock: env.now(),
    statsTarget: undefined as { kind: 'account' | 'detail'; id: number } | undefined,
    auth: { status: 'PENDING', message: '' } as SocialAuthSession,
    authBusy: false, sessionId: '', authPlatform: undefined as SocialPlatform | undefined,
    pages: [] as FacebookPage[], pageIds: [] as string[], pagePickerVisible: false, binding: false,
    busyAccounts: [] as number[], retrying: [] as number[], syncing: [] as number[]
  })
  let running = false, epoch = 0, authVersion = 0, detailVersion = 0, taskVersion = 0
  let authTimer: number | undefined, taskTimer: number | undefined, popup: Popup | null = null
  let authStarted = 0, previousBody = '', submissionKey = ''
  let accountsNeedRefresh = false
  let mediaVersion = 0, mediaTimer: number | undefined
  const statsVersions = new Map<string, number>(), statsTimers = new Map<string, number>()
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

  async function refreshAccounts(refreshStatistics = false) {
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
      if (live(version)) {
        state.accounts = list; accountsNeedRefresh = false
        for (const account of list) {
          const key = statsKey('account', account.id)
          if (state.statsLoading[key] || state.statsSyncing[key]) continue
          const incoming = account.stats, cached = state.stats[key]
          if (incoming) {
            const incomingAttempt = timeMillis(incoming.lastAttemptTime), cachedAttempt = timeMillis(cached?.lastAttemptTime)
            // Equal attempt timestamps may advance an active sync to completion,
            // but must not replace a completed snapshot or a newer read failure.
            const completesAttempt = incomingAttempt === cachedAttempt &&
              ['PENDING', 'PROCESSING'].includes(cached?.syncStatus) &&
              ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'ERROR', 'DISABLED', 'INACTIVE'].includes(incoming.syncStatus)
            if (!cached || incomingAttempt > cachedAttempt || completesAttempt) {
              state.stats[key] = mergeStats(cached, incoming); state.statsErrors[key] = ''
              clearStatsTimer(key)
              statsVersions.set(key, (statsVersions.get(key) || 0) + 1)
            }
          }
          // stop() retains snapshots but disposes timers. Restore active cached
          // syncs even if account-page omits stats or returns an unchanged copy.
          if (!refreshStatistics && ['PENDING', 'PROCESSING'].includes(state.stats[key]?.syncStatus) && !statsTimers.has(key)) {
            const requestId = (statsVersions.get(key) || 0) + 1
            statsVersions.set(key, requestId)
            scheduleStats('account', account.id, version, requestId, 0)
          }
        }
        if (refreshStatistics) await Promise.allSettled(list.map(account => refreshStats('account', account.id)))
      }
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
      state.statsClock = env.now()
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
        if (state.media?.mediaType === 'VIDEO' && state.media.status === 'PROCESSING') scheduleMedia(version, mediaVersion)
      }
    }
  }
  function stop() {
    running = false; epoch++; cancelAuth()
    invalidateMedia()
    closeStats()
    for (const timer of statsTimers.values()) env.clearTimeout(timer)
    statsTimers.clear(); statsVersions.clear()
    state.statsLoading = {}; state.statsSyncing = {}
    if (taskTimer !== undefined) env.clearTimeout(taskTimer)
    taskTimer = undefined
    state.loading = false; state.accountsLoading = false
  }

  const statsPermission = (kind: 'account' | 'detail', sync = false) =>
    kind === 'account' ? (sync ? 'tk:social-account:update' : 'tk:social-account:query') : 'tk:social-publish:query'
  function clearStatsTimer(key: string) {
    const timer = statsTimers.get(key)
    if (timer !== undefined) env.clearTimeout(timer)
    statsTimers.delete(key)
  }
  function scheduleStats(kind: 'account' | 'detail', id: number, version: number, requestId: number, attempts: number, seconds = 3) {
    const key = statsKey(kind, id)
    clearStatsTimer(key)
    if (!live(version) || statsVersions.get(key) !== requestId) return
    if (attempts >= 60) { state.statsErrors[key] = '同步等待较久，已暂停自动刷新，请手动刷新。'; return }
    statsTimers.set(key, env.setTimeout(async () => {
      statsTimers.delete(key)
      if (!live(version) || statsVersions.get(key) !== requestId) return
      await refreshStats(kind, id, attempts + 1)
    }, Math.max(1, Math.min(seconds, 60)) * 1000))
  }
  async function refreshStats(kind: 'account' | 'detail', id: number, attempts = 0) {
    requirePermission(statsPermission(kind))
    // A drawer opened during POST must not invalidate that acknowledgement or its follow-up read.
    if (state.statsSyncing[statsKey(kind, id)]) return
    const key = statsKey(kind, id), version = epoch, requestId = (statsVersions.get(key) || 0) + 1
    statsVersions.set(key, requestId); clearStatsTimer(key)
    state.statsLoading[key] = true
    try {
      const snapshot = await (kind === 'account' ? api.accountStats(id) : api.detailStats(id))
      if (!live(version) || statsVersions.get(key) !== requestId) return
      state.stats[key] = mergeStats(state.stats[key], snapshot); state.statsErrors[key] = ''
      if (['PENDING', 'PROCESSING'].includes(snapshot.syncStatus)) scheduleStats(kind, id, version, requestId, attempts)
    } catch (error) {
      if (!live(version) || statsVersions.get(key) !== requestId) return
      state.statsErrors[key] = error instanceof Error ? error.message : '统计读取失败'
      const previous = state.stats[key]
      if (previous) state.stats[key] = mergeStats(previous, { ...previous, syncStatus: 'FAILED', errorMessage: state.statsErrors[key], metrics: [] })
    } finally {
      if (live(version) && statsVersions.get(key) === requestId) state.statsLoading[key] = false
    }
  }
  function canSyncStats(kind: 'account' | 'detail', id: number) {
    const key = statsKey(kind, id)
    const lastAttempt = state.stats[key]?.lastAttemptTime
    // nextSyncTime schedules automatic collection; it is unrelated to manual cooldown.
    const serverCooldown = lastAttempt == null ? 0 : timeMillis(lastAttempt) + 60_000
    return !state.statsSyncing[key] && !state.statsLoading[key] &&
      !['PENDING', 'PROCESSING'].includes(state.stats[key]?.syncStatus) &&
      Math.max(state.statsCooldown[key] || 0, serverCooldown) <= Math.max(env.now(), state.statsClock)
  }
  async function syncStats(kind: 'account' | 'detail', id: number) {
    requirePermission(statsPermission(kind)); requirePermission(statsPermission(kind, true))
    if (!canSyncStats(kind, id)) return
    const key = statsKey(kind, id), version = epoch, requestId = (statsVersions.get(key) || 0) + 1
    statsVersions.set(key, requestId); clearStatsTimer(key); state.statsSyncing[key] = true
    state.statsCooldown[key] = env.now() + 60_000
    try {
      const result = await (kind === 'account' ? api.accountStatsSync(id) : api.detailStatsSync(id))
      if (!live(version) || statsVersions.get(key) !== requestId) return
      const previous = state.stats[key]
      state.stats[key] = { ...(previous || { objectId: id, platform: state.accounts.find(a => a.id === id)?.platform || 'INSTAGRAM', metrics: [] }), syncStatus: result.syncStatus }
      state.statsErrors[key] = result.accepted ? '同步请求已受理，等待后台完成。' : '请求未新建，等待服务器状态；请勿重复操作。'
      // The response acknowledges scheduling only; fetch the actual snapshot even if accepted is false.
      scheduleStats(kind, id, version, requestId, 0, result.nextPollAfterSeconds)
    } catch (error) {
      if (live(version) && statsVersions.get(key) === requestId) state.statsErrors[key] = error instanceof Error ? error.message : '同步请求失败'
    } finally {
      if (live(version) && statsVersions.get(key) === requestId) state.statsSyncing[key] = false
    }
  }
  function closeStats() {
    const target = state.statsTarget
    if (target) {
      const key = statsKey(target.kind, target.id)
      clearStatsTimer(key); statsVersions.set(key, (statsVersions.get(key) || 0) + 1)
      state.statsLoading[key] = false; state.statsSyncing[key] = false
    }
    state.statsTarget = undefined
  }
  async function openStats(kind: 'account' | 'detail', id: number) {
    requirePermission(statsPermission(kind))
    closeStats(); state.statsTarget = { kind, id }
    await refreshStats(kind, id)
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
          env.notify('账号授权成功'); await refreshAccounts(true)
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
      await refreshAccounts(true)
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
  function invalidateMedia() {
    mediaVersion++
    if (mediaTimer !== undefined) env.clearTimeout(mediaTimer)
    mediaTimer = undefined; state.uploading = false; state.uploadPercent = 0; state.mediaChecking = false; state.mediaError = ''
  }
  function clearMedia() { invalidateMedia(); state.media = undefined; state.draft.generationTaskId = undefined }
  function selectGenerated(id: number) {
    requirePermission('tk:social-publish:create')
    if (!state.mediaTypes.includes('VIDEO')) throw new Error('服务器暂不支持视频发布')
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('请选择有效的成片')
    clearMedia(); state.draft.generationTaskId = id
  }

  function scheduleMedia(version: number, requestId: number, attempts = 0) {
    if (!live(version) || requestId !== mediaVersion || mediaTimer !== undefined ||
      !state.media || state.media.status !== 'PROCESSING') return
    if (attempts >= 120) { state.mediaError = '检测等待较久，已暂停自动刷新，请手动刷新检测结果。'; return }
    mediaTimer = env.setTimeout(async () => {
      mediaTimer = undefined
      if (!live(version) || requestId !== mediaVersion) return
      await refreshMedia(attempts + 1)
    }, 3000)
  }
  async function refreshMedia(attempts = 0) {
    requirePermission('tk:social-publish:create')
    if (!state.media || state.mediaChecking) return
    const version = epoch, requestId = mediaVersion, id = state.media.id
    state.mediaChecking = true
    try {
      const media = await api.mediaGet(id)
      if (!live(version) || requestId !== mediaVersion || state.media?.id !== id) return
      state.media = media; state.mediaError = ''
      scheduleMedia(version, requestId, attempts)
    } catch (error) {
      if (live(version) && requestId === mediaVersion) state.mediaError = error instanceof Error ? error.message : '读取检测结果失败，请手动刷新'
    } finally {
      if (live(version) && requestId === mediaVersion) state.mediaChecking = false
    }
  }

  async function upload(file: File) {
    requirePermission('tk:social-publish:create')
    if (state.uploading || state.submitting) return
    const type = file.type === 'image/jpeg' ? 'IMAGE' : file.type === 'video/mp4' ? 'VIDEO' : undefined
    if (!type) throw new Error('仅支持 MP4 视频和 JPEG 图片')
    if (!state.mediaTypes.includes(type)) throw new Error('服务器暂不支持此媒体类型')
    const maxMb = type === 'VIDEO' ? state.maxVideoSizeMb : 8
    if (!file.size || file.size > (type === 'VIDEO' ? state.maxVideoBytes : 8 * 1024 * 1024)) throw new Error(`文件大小须大于 0 且不超过 ${maxMb} MB`)
    clearMedia()
    const version = epoch, requestId = mediaVersion
    state.uploading = true; state.uploadPercent = 0
    try {
      let media: SocialMedia
      if (type === 'VIDEO') {
        const session = await api.videoUploadSession({ fileName: file.name, fileSize: file.size, contentType: 'video/mp4' })
        if (!live(version) || requestId !== mediaVersion) return
        await uploadSocialVideoToOss(session, file, percent => { if (live(version) && requestId === mediaVersion) state.uploadPercent = percent })
        if (!live(version) || requestId !== mediaVersion) return
        media = await api.videoUploadComplete({
          uploadId: session.uploadId, fileName: file.name, fileSize: file.size, contentType: 'video/mp4',
          objectKey: session.objectKey
        })
      } else media = await api.upload(file)
      if (!live(version) || requestId !== mediaVersion) return
      state.media = media; state.draft.generationTaskId = undefined
      if (type === 'VIDEO') scheduleMedia(version, requestId)
    } finally {
      if (live(version) && requestId === mediaVersion) { state.uploading = false; state.uploadPercent = 0 }
    }
  }
  async function submit() {
    requirePermission('tk:social-publish:create')
    requirePermission('tk:social-account:query')
    if (state.submitting) return
    if (state.uploading) throw new Error('请等待媒体上传完成')
    if (state.media?.mediaType === 'VIDEO' && (state.media.status !== 'READY' || state.media.metadataStatus !== 'VERIFIED')) {
      throw new Error(state.media.metadataError || '请等待服务端视频检测完成（READY）后发布')
    }
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
    refreshDetails, openDetails, accountAction, clearMedia, selectGenerated, upload, submit, retry, sync,
    refreshStats, syncStats, canSyncStats, openStats, closeStats, refreshMedia }
}
