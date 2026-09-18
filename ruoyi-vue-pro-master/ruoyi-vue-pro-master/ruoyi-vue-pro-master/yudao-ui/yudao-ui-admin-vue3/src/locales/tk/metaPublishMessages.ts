export type MetaLocale = 'zh-CN' | 'en'

const entries: Record<string, [string, string]> = {
  'meta.title': ['Instagram / Facebook 发布', 'Instagram / Facebook Publishing'],
  'meta.description': [
    '上传视频或选择系统成片，为每个平台设置独立文案。',
    'Upload a video or choose a generated video, then set separate copy for each platform.'
  ],
  'meta.refreshConfig': ['刷新配置', 'Refresh configuration'],
  'meta.configPending': ['Meta 发布配置待完成', 'Meta publishing setup is incomplete'],
  'meta.configPendingDescription': [
    '管理员启用并完成平台配置后，可在此连接账号和发布视频。',
    'Connect accounts and publish videos after an administrator completes the platform configuration.'
  ],
  'meta.availablePlatforms': ['可用平台', 'Available platforms'],
  'meta.supportedMedia': ['支持媒体', 'Supported media'],
  'meta.none': ['暂无', 'None'],
  'meta.videoUnsupported': [
    '当前服务端尚不支持视频，请完成视频发布配置后重试。',
    'Video publishing is not supported by the current server. Complete the video publishing configuration and try again.'
  ],
  'meta.platformAccounts': ['平台账号', 'Platform accounts'],
  'meta.connectInstagram': ['连接 Instagram', 'Connect Instagram'],
  'meta.connectFacebook': ['连接 Facebook Page', 'Connect Facebook Page'],
  'meta.refreshAccounts': ['刷新账号', 'Refresh accounts'],
  'meta.cancelWaiting': ['取消等待', 'Stop waiting'],
  'meta.account': ['账号', 'Account'],
  'meta.platform': ['平台', 'Platform'],
  'meta.status': ['状态', 'Status'],
  'meta.followers': ['粉丝', 'Followers'],
  'meta.following': ['关注', 'Following'],
  'meta.mediaCount': ['作品数', 'Media'],
  'meta.statsSync': ['数据同步', 'Statistics sync'],
  'meta.statsUpdated': ['数据更新时间', 'Statistics updated'],
  'meta.tokenExpires': ['授权到期时间', 'Authorization expiry'],
  'meta.lastValidated': ['最近校验', 'Last validated'],
  'meta.failureReason': ['异常说明', 'Failure reason'],
  'meta.actions': ['操作', 'Actions'],
  'meta.viewData': ['查看数据', 'View data'],
  'meta.syncData': ['同步数据', 'Sync data'],
  'meta.testInsights': ['测试数据分析', 'Test insights'],
  'meta.validate': ['校验', 'Validate'],
  'meta.reauthorize': ['重新授权', 'Reauthorize'],
  'meta.unbind': ['解绑', 'Unbind'],
  'meta.delete': ['删除', 'Delete'],
  'meta.accountQueryPermission': [
    '需要账号查询权限才能选择发布目标。',
    'Account query permission is required to choose publishing targets.'
  ],
  'meta.newTask': ['新建发布任务', 'New publishing task'],
  'meta.taskTitle': ['任务标题', 'Task title'],
  'meta.taskTitlePlaceholder': ['便于查找的任务名称', 'A task name that is easy to find'],
  'meta.publishTargets': [
    '发布目标（可多选，每个账号独立执行）',
    'Publishing targets (multiple selection; each account runs independently)'
  ],
  'meta.publishTargetsPlaceholder': [
    '选择 Instagram / Facebook Page 账号（最多 20 个）',
    'Select Instagram / Facebook Page accounts (up to 20)'
  ],
  'meta.media': ['视频 / 图片', 'Video / image'],
  'meta.search': ['搜索', 'Search'],
  'meta.select': ['选择', 'Select'],
  'meta.generationQueryPermission': [
    '需要成片查询权限',
    'Generated video query permission is required'
  ],
  'meta.generatedTitle': ['成片标题', 'Generated video title'],
  'meta.generatedHint': [
    '仅显示生成成功的视频。服务端会校验成片归属和平台视频要求。',
    'Only successfully generated videos are shown. The server validates ownership and platform video requirements.'
  ],
  'meta.generatedFacebookHint': [
    '9:16、至少 540×960、23–60 fps、4–60 秒。此列表不含完整媒体参数，适用性待服务端校验。',
    '9:16, at least 540×960, 23–60 fps, and 4–60 seconds. This list does not include full media metadata; the server validates compatibility.'
  ],
  'meta.generatedLoadFailed': ['成片加载失败', 'Failed to load generated videos'],
  'meta.uploadMedia': ['上传 MP4 / JPEG', 'Upload MP4 / JPEG'],
  'meta.selectGenerated': ['选择系统成片', 'Choose generated video'],
  'meta.removeMedia': ['移除媒体', 'Remove media'],
  'meta.mediaHint': [
    'MP4 最大 {{max}} MB，JPEG 最大 8 MB。视频将直接上传到 OSS，Instagram 必须选择媒体；Facebook 可只填写正文。服务端将进一步校验视频格式和平台限制。',
    'MP4 up to {{max}} MB and JPEG up to 8 MB. Videos upload directly to OSS. Instagram requires media; Facebook can publish text only. The server will validate media format and platform limits.'
  ],
  'meta.facebookVideoTitle': [
    'Facebook 视频将发布为 Reels',
    'Facebook videos will be published as Reels'
  ],
  'meta.facebookVideoDescription': [
    '本发布流程支持 9:16 竖屏、分辨率至少 540×960、帧率 23–60 fps、时长 4–60 秒。系统成片与上传视频都需通过服务端校验。',
    'This flow supports 9:16 portrait videos, at least 540×960 resolution, 23–60 fps, and 4–60 seconds. Generated and uploaded videos must pass server-side validation.'
  ],
  'meta.instagramVideoHint': [
    'Instagram 视频：本发布流程支持 23–60 fps，其他格式要求由服务端进一步校验。',
    'Instagram video: this flow supports 23–60 fps; the server validates other format requirements.'
  ],
  'meta.generatedVideo': ['系统成片', 'Generated video'],
  'meta.video': ['视频', 'Video'],
  'meta.image': ['图片', 'Image'],
  'meta.ready': ['已就绪', 'Ready'],
  'meta.detectionFailed': ['检测失败', 'Detection failed'],
  'meta.waitingDetection': ['等待服务端检测', 'Waiting for server detection'],
  'meta.detecting': ['检测中', 'Detecting'],
  'meta.verified': ['检测通过', 'Detection passed'],
  'meta.unverified': ['尚未验证', 'Not verified'],
  'meta.resolution': ['分辨率', 'Resolution'],
  'meta.duration': ['时长', 'Duration'],
  'meta.frameRate': ['帧率', 'Frame rate'],
  'meta.videoCodec': ['视频编码', 'Video codec'],
  'meta.audioCodec': ['音频编码', 'Audio codec'],
  'meta.source': ['来源', 'Source'],
  'meta.pendingDetection': ['待检测', 'Pending detection'],
  'meta.noAudio': ['无 / 待检测', 'None / pending detection'],
  'meta.normalizedHint': [
    '已由服务端标准化处理，发布使用检测后的媒体。',
    'The server normalized this media. The validated media will be published.'
  ],
  'meta.refreshDetection': ['刷新检测结果', 'Refresh detection'],
  'meta.instagramCaption': ['Instagram 文案', 'Instagram caption'],
  'meta.facebookMessage': ['Facebook 正文', 'Facebook message'],
  'meta.createTask': ['创建异步发布任务', 'Create asynchronous publishing task'],
  'meta.taskCreated': [
    '已创建任务 #{{id}}。排队或平台处理中时无需重复提交，下方会自动刷新。',
    'Task #{{id}} was created. Do not resubmit while it is queued or processing; the list below refreshes automatically.'
  ],
  'meta.taskList': ['发布任务', 'Publishing tasks'],
  'meta.refreshTasks': ['刷新任务', 'Refresh tasks'],
  'meta.refreshDetails': ['刷新明细', 'Refresh details'],
  'meta.allStatuses': ['全部状态', 'All statuses'],
  'meta.taskId': ['任务 ID', 'Task ID'],
  'meta.titleColumn': ['标题', 'Title'],
  'meta.result': ['结果', 'Result'],
  'meta.createdTime': ['创建时间', 'Created time'],
  'meta.viewDetails': ['查看明细', 'View details'],
  'meta.verifyStatus': ['核对状态', 'Check status'],
  'meta.targetCount': ['目标', 'Targets'],
  'meta.successCount': ['成功', 'Succeeded'],
  'meta.failedCount': ['失败', 'Failed'],
  'meta.pendingCount': ['待完成', 'Pending'],
  'meta.unknownWarning': [
    '结果未知时，请先到 Instagram / Facebook 人工核验是否已发布。此状态不允许重试，避免重复发布。',
    'When the result is unknown, verify publication on Instagram / Facebook before retrying. Retrying is disabled to prevent duplicate posts.'
  ],
  'meta.targetAccount': ['目标账号', 'Target account'],
  'meta.platformStatus': ['平台状态', 'Platform status'],
  'meta.descriptionLabel': ['说明', 'Description'],
  'meta.retryCount': ['重试次数', 'Retries'],
  'meta.viewPublished': ['查看发布', 'View post'],
  'meta.retryAfterAuth': ['授权后重试', 'Retry after authorization'],
  'meta.retry': ['重试', 'Retry'],
  'meta.selectFacebookPage': ['选择当前授权的 Facebook 主页', 'Select an authorized Facebook Page'],
  'meta.noBindablePage': [
    '当前授权没有可绑定主页，请检查账号的主页管理权限。',
    'No bindable Page was found for this authorization. Check the account Page management permissions.'
  ],
  'meta.cancel': ['取消', 'Cancel'],
  'meta.bindSelectedPage': ['绑定所选主页', 'Bind selected Pages'],
  'meta.insightsTest': ['Meta 数据分析测试', 'Meta insights test'],
  'meta.metric': ['指标', 'Metric'],
  'meta.period': ['周期', 'Period'],
  'meta.requestInsights': ['请求 Insights', 'Request insights'],
  'meta.accountData': ['账号数据', 'Account data'],
  'meta.mediaData': ['作品数据', 'Media data'],
  'meta.recentSuccess': ['最近成功', 'Last success'],
  'meta.recentAttempt': ['最近尝试', 'Last attempt'],
  'meta.nextSync': ['下次自动同步', 'Next automatic sync'],
  'meta.refreshResult': ['刷新结果', 'Refresh result'],
  'meta.statsNote': [
    '同步请求由后台执行。冷却期间不可重复请求；“—”表示暂无可用值，0 表示平台返回的真实零值。',
    'Sync requests run in the background. Do not repeat requests during the cooldown; “—” means no value is available, while 0 is a real zero returned by the platform.'
  ],
  'meta.metricLabel': ['指标', 'Metric'],
  'meta.value': ['数值', 'Value'],
  'meta.availability': ['可用性 / 说明', 'Availability / notes'],
  'meta.platformMetric': ['平台来源指标', 'Platform metric'],
  'meta.scope': ['统计范围', 'Scope'],
  'meta.periodLabel': ['统计周期', 'Period'],
  'meta.unit': ['单位', 'Unit'],
  'meta.collectedAt': ['采集时间', 'Collected at'],
  'meta.noStats': [
    '暂无统计，请同步或稍后刷新；不会将缺失数据记为 0。',
    'No statistics yet. Sync or refresh later; missing data is never recorded as 0.'
  ],
  'meta.oldValue': ['上次值', 'Previous value'],
  'meta.noAvailableValue': ['本次未取得新数据', 'No new data was retrieved'],
  'meta.authorized': ['已授权', 'Authorized'],
  'meta.available': ['可用', 'Available'],
  'meta.unbound': ['已解绑', 'Unbound'],
  'meta.expired': ['已过期', 'Expired'],
  'meta.unavailable': ['不可用', 'Unavailable'],
  'meta.queued': ['排队中', 'Queued'],
  'meta.platformProcessing': ['平台处理中', 'Processing on platform'],
  'meta.published': ['发布成功', 'Published successfully'],
  'meta.partialSuccess': ['部分成功', 'Partially succeeded'],
  'meta.failed': ['失败', 'Failed'],
  'meta.reauthRequired': ['需要重新授权', 'Reauthorization required'],
  'meta.manualVerification': ['结果待人工核验', 'Manual verification required'],
  'meta.syncSuccess': ['同步成功', 'Sync succeeded'],
  'meta.syncing': ['同步中', 'Syncing'],
  'meta.waitingSync': ['等待同步', 'Waiting for sync'],
  'meta.syncFailed': ['同步失败', 'Sync failed'],
  'meta.partialSync': ['部分成功', 'Partially succeeded'],
  'meta.neverSynced': ['尚未同步', 'Never synced'],
  'meta.cooldown': ['冷却中', 'Cooldown'],
  'meta.disabled': ['已禁用', 'Disabled'],
  'meta.inactive': ['未启用', 'Inactive'],
  'meta.availableState': ['可用', 'Available'],
  'meta.pendingState': ['待同步', 'Pending sync'],
  'meta.unknownState': ['暂未获取', 'Not retrieved'],
  'meta.unsupportedState': ['平台不支持', 'Unsupported by platform'],
  'meta.permissionRequired': ['需要权限', 'Permission required'],
  'meta.authRequired': ['需要重新授权', 'Reauthorization required'],
  'meta.objectUnavailable': ['平台作品或对象不可用', 'Platform media or object unavailable'],
  'meta.readFailed': ['读取失败', 'Read failed'],
  'meta.authWaiting': ['等待授权', 'Waiting for authorization'],
  'meta.authProcessing': ['正在处理授权', 'Processing authorization'],
  'meta.authSelectPage': ['请选择主页', 'Select a Page'],
  'meta.authSuccess': ['授权成功', 'Authorization successful'],
  'meta.authFailed': ['授权失败', 'Authorization failed'],
  'meta.authExpired': ['授权已过期', 'Authorization expired'],
  'meta.accountManagement': ['账号管理', 'Account management'],
  'meta.confirmUnbind': ['确认解绑账号“{{account}}”？', 'Unbind account “{{account}}”?'],
  'meta.confirmDelete': ['确认删除账号“{{account}}”？', 'Delete account “{{account}}”?'],
  'meta.retryAfterAuthConfirm': [
    '请先完成此账号的重新授权，再提交重试。确认已授权？',
    'Complete reauthorization for this account before retrying. Confirm that authorization is complete?'
  ],
  'meta.retryAfterAuthTitle': ['授权后重试', 'Retry after authorization'],
  'meta.noStatsData': ['尚无统计数据', 'No statistics yet'],
  'meta.permissionDenied': [
    '没有执行此操作的权限',
    'You do not have permission to perform this action'
  ],
  'meta.setupIncompleteRetry': [
    'Meta 发布配置待完成，请刷新后重试',
    'Meta publishing setup is incomplete. Refresh and try again'
  ],
  'meta.taskRefreshFailed': [
    '状态刷新失败，正在重连；请勿重复创建任务。',
    'Status refresh failed. Reconnecting; do not create duplicate tasks.'
  ],
  'meta.accountTaskReadFailed': [
    '账号或任务读取失败，正在重连；请勿重复创建任务。',
    'Account or task read failed. Reconnecting; do not create duplicate tasks.'
  ],
  'meta.taskCreatedReadFailed': [
    '任务已创建，状态暂时读取失败，请稍后刷新。',
    'The task was created, but its status could not be read. Refresh later.'
  ],
  'meta.statsPollingPaused': [
    '同步等待较久，已暂停自动刷新，请手动刷新。',
    'Sync is taking longer than expected. Automatic refresh is paused; refresh manually.'
  ],
  'meta.statsReadFailed': ['统计读取失败', 'Failed to read statistics'],
  'meta.syncAccepted': [
    '同步请求已受理，等待后台完成。',
    'Sync request accepted; waiting for the background job.'
  ],
  'meta.syncNotCreated': [
    '请求未新建，等待服务器状态；请勿重复操作。',
    'No new request was created. Waiting for server status; do not repeat the action.'
  ],
  'meta.syncRequestFailed': ['同步请求失败', 'Sync request failed'],
  'meta.authTimeout': [
    '授权等待已超时，请重新连接账号。',
    'Authorization timed out. Connect the account again.'
  ],
  'meta.authWindowClosed': [
    '授权窗口已关闭，仍在等待服务器确认；可取消等待后重新连接。',
    'The authorization window closed, but the server is still processing. Stop waiting and reconnect if needed.'
  ],
  'meta.authReadRetry': [
    '授权状态暂时无法读取，正在重试。',
    'Authorization status is temporarily unavailable. Retrying.'
  ],
  'meta.platformUnavailable': [
    '服务器暂不支持此平台',
    'This platform is not currently supported by the server'
  ],
  'meta.popupBlocked': [
    '浏览器拦截了授权弹窗，请允许弹窗后重试',
    'The browser blocked the authorization popup. Allow popups and try again'
  ],
  'meta.invalidAuthUrl': [
    '授权地址无效，请检查配置',
    'The authorization URL is invalid. Check the configuration'
  ],
  'meta.authStartFailed': [
    '授权启动失败，请重试。',
    'Authorization could not be started. Try again.'
  ],
  'meta.pageBound': ['Facebook 主页绑定成功', 'Facebook Page bound successfully'],
  'meta.invalidPage': [
    '请从当前授权会话中选择有效主页',
    'Select a valid Page from the current authorization session'
  ],
  'meta.videoUnsupportedByServer': [
    '服务器暂不支持视频发布',
    'Video publishing is not currently supported by the server'
  ],
  'meta.invalidGeneratedVideo': ['请选择有效的成片', 'Select a valid generated video'],
  'meta.mediaDetectionPaused': [
    '检测等待较久，已暂停自动刷新，请手动刷新检测结果。',
    'Detection is taking longer than expected. Automatic refresh is paused; refresh manually.'
  ],
  'meta.mediaReadFailed': [
    '读取检测结果失败，请手动刷新',
    'Failed to read detection results. Refresh manually'
  ],
  'meta.mediaTypeUnsupported': [
    '仅支持 MP4 视频和 JPEG 图片',
    'Only MP4 videos and JPEG images are supported'
  ],
  'meta.mediaSizeInvalid': [
    '文件大小须大于 0 且不超过 {{max}} MB',
    'File size must be greater than 0 and no more than {{max}} MB'
  ],
  'meta.mediaUnsupportedByServer': [
    '服务器暂不支持此媒体类型',
    'This media type is not currently supported by the server'
  ],
  'meta.waitMediaUpload': ['请等待媒体上传完成', 'Wait for the media upload to finish'],
  'meta.waitVideoDetection': [
    '请等待服务端视频检测完成（READY）后发布',
    'Wait for server-side video detection to complete (READY) before publishing'
  ],
  'meta.titleRequired': ['请填写任务标题', 'Enter a task title'],
  'meta.titleLimit': ['任务标题最多 255 字符', 'Task title must be no more than 255 characters'],
  'meta.instagramCaptionLimit': [
    'Instagram 文案最多 2200 字符',
    'Instagram caption must be no more than 2200 characters'
  ],
  'meta.facebookMessageLimit': [
    'Facebook 正文最多 5000 字符',
    'Facebook message must be no more than 5000 characters'
  ],
  'meta.accountRequired': ['请选择至少一个发布账号', 'Select at least one publishing account'],
  'meta.accountLimit': [
    '每次最多选择 20 个发布账号',
    'Select no more than 20 publishing accounts at a time'
  ],
  'meta.accountUnavailable': [
    '所选账号不可用或平台暂不支持，请刷新账号并重新授权',
    'The selected account is unavailable or its platform is unsupported. Refresh accounts and reauthorize'
  ],
  'meta.instagramMediaRequired': [
    'Instagram 需要视频或图片媒体',
    'Instagram requires video or image media'
  ],
  'meta.facebookTextRequired': [
    'Facebook 纯文本发布需要填写正文',
    'Facebook text-only publishing requires a message'
  ],
  'meta.retryUnavailable': [
    '此状态不可重试；结果未知时须先人工核验平台，避免重复发布',
    'This status cannot be retried. Verify an unknown result on the platform first to avoid duplicate publishing'
  ],
  'meta.statusSyncRequested': [
    '已请求核对平台状态，请等待异步更新。',
    'A platform status check was requested. Wait for the asynchronous update.'
  ],
  'meta.retryQueued': ['重试已加入队列', 'Retry added to the queue'],
  'meta.insightsFieldsRequired': ['请填写指标和周期', 'Enter a metric and period'],
  'meta.operationFailed': ['操作失败，请重试', 'Operation failed. Try again'],
  'meta.authIncomplete': [
    '授权未完成，请检查账号权限或绑定归属后重试',
    'Authorization is incomplete. Check the account permissions and Page ownership, then try again'
  ],
  'meta.instagramProfileReadFailed': [
    '无法读取 Instagram 专业账号信息，请检查账号类型后重试',
    'Unable to read the Instagram professional account. Check the account type and try again'
  ],
  'meta.ossUploadNetworkFailed': [
    'OSS 上传失败，请检查网络后重试',
    'OSS upload failed. Check the network and try again'
  ],
  'meta.ossUploadCancelled': ['OSS 上传已取消', 'OSS upload was cancelled'],
  'meta.ossUploadHttpFailed': [
    'OSS 上传失败（HTTP {{status}}）',
    'OSS upload failed (HTTP {{status}})'
  ],
  'meta.invalidMp4': ['文件不是有效的 MP4 视频', 'The file is not a valid MP4 video'],
  'meta.mediaUploadBusy': [
    '已有媒体正在上传，请稍后重试',
    'Another media upload is in progress. Try again later'
  ],
  'meta.videoTooLarge': ['视频不能超过 1GB', 'Video must not exceed 1 GB'],
  'meta.imageTooLarge': ['图片不能超过 8MB', 'Image must not exceed 8 MB'],
  'meta.mediaNotReady': ['媒体尚未就绪', 'Media is not ready'],
  'meta.videoInspectionPending': [
    '视频检测中，请稍后重试',
    'Video inspection is in progress. Try again later'
  ],
  'meta.videoCheckFailed': [
    '视频尚未通过服务端检查',
    'The video has not passed server-side checks'
  ],
  'meta.instagramMediaRequiredServer': [
    'Instagram 发布需要图片或视频',
    'Instagram publishing requires an image or video'
  ],
  'meta.facebookCopyRequired': ['请输入 Facebook 发布文案', 'Enter Facebook post copy'],
  'meta.reauthTarget': ['目标账号需要重新授权', 'The target account must be reauthorized'],
  'meta.platformUnsupported': ['不支持的发布平台', 'The publishing platform is not supported'],
  'meta.accountFallback': ['账号 #{{id}}', 'Account #{{id}}'],
  'meta.zeroValue': ['0 表示平台返回的真实零值', '0 is a real zero returned by the platform'],
  'meta.staleValue': ['旧值', 'Previous value'],
  'meta.scopeAccount': ['账号', 'Account'],
  'meta.scopeDetail': ['作品', 'Media'],
  'meta.periodDay': ['天', 'Day'],
  'meta.unitCount': ['数量', 'Count'],
  'meta.likes': ['点赞', 'Likes'],
  'meta.reactions': ['反应数（Reactions）', 'Reactions'],
  'meta.views': ['观看', 'Views'],
  'meta.playCount': ['播放次数', 'Plays'],
  'meta.reach': ['触达', 'Reach'],
  'meta.comments': ['评论', 'Comments'],
  'meta.shares': ['分享', 'Shares'],
  'meta.saves': ['收藏', 'Saves'],
  'meta.platformValidationFallback': ['以平台校验结果为准', 'See platform validation results']
}

export const metaZh: Record<string, string> = Object.fromEntries(
  Object.entries(entries).map(([key, [zh]]) => [key, zh])
)

export const metaEn: Record<string, string> = Object.fromEntries(
  Object.entries(entries).map(([key, [, en]]) => [key, en])
)

export const metaText = (key: string, locale: string = 'zh-CN') =>
  (locale === 'en' ? metaEn : metaZh)[key] || key

const directTextKeys = Object.entries(metaZh).reduce<Record<string, string>>((map, [key, text]) => {
  map[text] = key
  return map
}, {})

export const translateMetaText = (text?: string, locale: string = 'zh-CN') => {
  if (!text || locale !== 'en') return text || ''
  const key = directTextKeys[text]
  if (key) return metaEn[key]
  const patterns: Array<[RegExp, (match: RegExpMatchArray) => string]> = [
    [/^账号 #(\d+)$/, (match) => metaEn['meta.accountFallback'].replace('{{id}}', match[1])],
    [
      /^文件大小须大于 0 且不超过 (\d+) MB$/,
      (match) => `File size must be greater than 0 and no more than ${match[1]} MB`
    ],
    [/^OSS 上传失败（HTTP (\d+)）$/, (match) => `OSS upload failed (HTTP ${match[1]})`],
    [
      /^任务 #(\d+) 已加入队列，平台正在异步处理。$/,
      (match) => `Task #${match[1]} was queued and is being processed asynchronously.`
    ]
  ]
  for (const [pattern, render] of patterns) {
    const match = text.match(pattern)
    if (match) return render(match)
  }
  return text
}
