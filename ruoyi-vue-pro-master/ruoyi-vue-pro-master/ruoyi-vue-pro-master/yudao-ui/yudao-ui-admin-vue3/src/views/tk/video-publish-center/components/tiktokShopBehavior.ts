import type { ShopCreate, ShopTask } from '@/api/tk/tiktokShop'

export function acceptShopOAuthMessage(
  event: Pick<MessageEvent, 'origin' | 'source' | 'data'>,
  expected: { callbackOrigin: string; sessionId: number | string },
  popup: Window
) {
  return event.origin === expected.callbackOrigin && event.source === popup &&
    event.data?.type === 'tiktok-shop-auth' && event.data.sessionId === expected.sessionId &&
    ['SUCCESS', 'FAILED'].includes(event.data.status)
}

export function shopKey(cryptoSource: Crypto = globalThis.crypto): string {
  if (cryptoSource.randomUUID) return cryptoSource.randomUUID()
  return Array.from(cryptoSource.getRandomValues(new Uint8Array(24)), (n) => n.toString(16).padStart(2, '0')).join('')
}

export function createShopFormGuard() {
  let fingerprint = '', key = '', confirmed = ''
  const serialize = (form: object) => JSON.stringify(form)
  return {
    keyFor(form: object) {
      const next = serialize(form)
      if (next !== fingerprint) { fingerprint = next; key = shopKey(); confirmed = '' }
      return key
    },
    confirm(form: object) { confirmed = serialize(form) },
    canConfirm(form: object) { return confirmed === serialize(form) },
    clear() { fingerprint = ''; key = ''; confirmed = '' }
  }
}

export interface ShopIssue { code?: string; risk?: string; suggestions?: string }
export function normalizeShopPrecheck(value?: string | Record<string, any>) {
  let parsed: Record<string, any> = {}
  try { parsed = typeof value === 'string' ? JSON.parse(value) : value || {} } catch { /* Old or incomplete result fails closed. */ }
  const passed = parsed.violationStatus === 'SUCCESS' && (!parsed.qualityPresent || ['SUCCESS', 'FAIL'].includes(parsed.qualityStatus))
  return {
    passed, failed: parsed.violationStatus === 'FAIL',
    violationStatus: parsed.violationStatus || 'UNKNOWN', qualityStatus: parsed.qualityStatus || 'UNKNOWN',
    qualityAdvisory: parsed.qualityPresent === true && parsed.qualityStatus === 'FAIL',
    violationIssues: (Array.isArray(parsed.violationIssues) ? parsed.violationIssues.slice(0, 20) : []) as ShopIssue[],
    qualityIssues: (Array.isArray(parsed.qualityIssues) ? parsed.qualityIssues.slice(0, 20) : []) as ShopIssue[]
  }
}

export function isShopTerminal(stage: string) { return ['SUCCESS', 'FAILED', 'RECOVERY_REQUIRED'].includes(stage) }
export function canConfirmShopTask(task?: ShopTask) {
  return task?.stage === 'AWAITING_CONFIRMATION' && normalizeShopPrecheck(task.precheckResult).passed
}
export function validateShopForm(form: Partial<ShopCreate>): string | undefined {
  if (!form.accountId) return 'shop.accountRequired'
  if (!!form.mediaId === !!form.generationTaskId) return 'shop.mediaRequired'
  if (!form.productId || !form.selectionProof) return 'shop.productRequired'
  if (!form.title?.trim() || form.title.length > 2200) return 'shop.captionInvalid'
  if (!form.productLinkTitle?.trim() || [...form.productLinkTitle].length >= 30 || !/^[\p{L}\p{N} ]+$/u.test(form.productLinkTitle)) return 'shop.anchorInvalid'
  if (typeof form.isAiGenerated !== 'boolean') return 'shop.aiRequired'
}

/** One POST per displayed snapshot; an ambiguous confirm is reconciled only with GET. */
export function createShopSubmitter(api: {
  create: (form: ShopCreate) => Promise<ShopTask>
  confirm: (id: number, data: { confirm: true; version: number; requestHash: string }) => Promise<ShopTask>
  get: (id: number) => Promise<ShopTask>
}) {
  const guard = createShopFormGuard()
  let task: ShopTask | undefined, creating: Promise<ShopTask> | undefined, createFingerprint = ''
  const sent = new Set<number>()
  let confirming: Promise<ShopTask> | undefined
  return {
    get task() { return task },
    get uncertain() { return !!task && sent.has(task.id) && canConfirmShopTask(task) },
    create(form: Omit<ShopCreate, 'idempotencyKey'>) {
      const fingerprint = JSON.stringify(form)
      if (creating && fingerprint === createFingerprint) return creating
      createFingerprint = fingerprint
      const operation = api.create({ ...form, idempotencyKey: guard.keyFor(form) }).then((result) => { task = result; return result })
      creating = operation
      void operation.finally(() => { if (creating === operation) creating = undefined }).catch(() => undefined)
      return operation
    },
    confirm(snapshot: ShopTask, _form?: object) {
      if (sent.has(snapshot.id)) return confirming || Promise.resolve(task || snapshot)
      sent.add(snapshot.id)
      task = snapshot
      confirming = api.confirm(snapshot.id, { confirm: true, version: snapshot.version, requestHash: snapshot.requestHash })
        .catch(() => api.get(snapshot.id))
        .then((result) => { task = result; return result })
      void confirming.finally(() => { confirming = undefined }).catch(() => undefined)
      return confirming
    }
  }
}

/** Revision is changed before requests begin, so old pages cannot repopulate another account. */
export function createShopRevision() {
  let revision = 0
  return { next: () => ++revision, current: (value: number) => value === revision, invalidate: () => { revision++ } }
}

export function createShopPoller(
  read: () => Promise<boolean>,
  onError: () => void,
  timer: { set: (callback: () => void, delay: number) => any; clear: (handle: any) => void },
  limit = 120
) {
  let handle: any, revision = 0, count = 0
  const stop = () => { revision++; if (handle !== undefined) timer.clear(handle); handle = undefined }
  const schedule = (session: number) => {
    if (session !== revision || count >= limit) return
    handle = timer.set(async () => {
      handle = undefined
      try {
        count++
        const again = await read()
        if (session !== revision) return
        if (again && count < limit) schedule(session)
        else if (again) onError()
      } catch { if (session === revision) onError() }
    }, Math.min(30_000, 2_000 * Math.pow(1.4, count)))
  }
  return { start() { stop(); count = 0; schedule(revision) }, stop }
}

export function shopStartup(has: (permissions: string[]) => boolean) {
  const shop = has(['tk:tiktok-shop-account:query', 'tk:tiktok-shop-publish:query'])
  // The parent menu is common to both products; it is not ordinary TikTok access.
  const ordinary = has(['tk:generation:query', 'tk:tiktok-account:authorize', 'tk:tiktok-account:update', 'tk:tiktok-account-group:manage', 'tk:tiktok-publish:create', 'tk:tiktok-publish:retry'])
  return { shopOnly: shop && !ordinary, initialTab: shop && !ordinary ? 'shop' : 'videos' }
}
