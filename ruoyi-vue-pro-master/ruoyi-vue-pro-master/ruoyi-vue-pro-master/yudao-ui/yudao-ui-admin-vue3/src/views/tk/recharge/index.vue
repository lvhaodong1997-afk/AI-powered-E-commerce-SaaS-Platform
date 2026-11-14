<template>
  <ContentWrap>
    <el-result
      v-if="!tenantAdmin"
      icon="warning"
      :title="text('仅租户管理员可充值', 'Tenant administrator access required')"
    />
    <div v-else v-loading="loading" class="clipforge-theme recharge-page">
      <header class="recharge-header">
        <div
          ><div class="page-eyebrow">CLIPFORGE · {{ text('账户与充值', 'ACCOUNT & BILLING') }}</div
          ><h1>{{ text('积分充值', 'Add credits') }}</h1
          ><p>{{
            text(
              '选择适合你的套餐，让创作持续发生。',
              'Choose the credits you need to keep creating.'
            )
          }}</p></div
        >
        <div class="account-header"
          ><span class="account-avatar"><Icon icon="ep:wallet" /></span
          ><div
            ><strong>{{ user.getUser.nickname || text('租户账户', 'Tenant account') }}</strong
            ><span>{{ text('充值与订单管理', 'Credits and order management') }}</span></div
          ><el-button :loading="loading" @click="load"
            ><Icon icon="ep:refresh" />{{ text('刷新', 'Refresh') }}</el-button
          ></div
        >
      </header>
      <div class="validity-note"
        ><Icon icon="ep:info-filled" /><span>{{
          text(
            '新购积分自成功支付起有效期为六个自然月，历史积分不追溯失效。支付确认与积分到账分别显示。',
            'New paid credits expire six calendar months after successful payment. Historic credits are unaffected. Payment and credit allocation are shown separately.'
          )
        }}</span></div
      >
      <el-alert
        v-if="loadError"
        class="mt-16px"
        type="error"
        :closable="false"
        :title="loadError"
      />
      <el-alert
        v-if="catalog && !catalog.enabled"
        class="mt-16px"
        type="warning"
        :closable="false"
        :title="
          text(
            '暂未开放新购买，仍可查询原订单。',
            'New purchases are currently unavailable. Existing orders remain accessible.'
          )
        "
      />
      <div class="recharge-layout">
        <div class="purchase-panel">
          <section class="selection-section">
            <div class="section-heading"
              ><div
                ><span class="section-step">01</span
                ><h2>{{ text('选择充值套餐', 'Choose a package') }}</h2></div
              ><span>{{ text('人民币基准价格', 'CNY base prices') }}</span></div
            >
            <div class="package-grid">
              <button
                v-for="item in regularPackages"
                :key="item.code"
                type="button"
                class="package-option"
                :class="{ selected: packageCode === item.code }"
                :aria-pressed="packageCode === item.code"
                :disabled="!catalog?.enabled || !!draft"
                @click="packageCode = item.code"
              >
                <div class="package-top"
                  ><span>{{ item.code }}</span
                  ><span class="selection-check" aria-hidden="true"
                    ><Icon v-if="packageCode === item.code" icon="ep:check" /></span
                ></div>
                <strong
                  >{{ item.points.toLocaleString()
                  }}<small>{{ text('积分', 'credits') }}</small></strong
                >
                <div class="package-price">{{ money(item.amountCents, 'CNY', lang) }}</div>
                <span class="package-validity">{{
                  text('付款后六个月有效', 'Valid for six months')
                }}</span>
              </button>
            </div>
          </section>
          <section
            v-if="verificationPackage"
            class="verification-section"
            :class="{ active: packageCode === 'VERIFY_1' }"
          >
            <button
              type="button"
              class="verification-choice"
              :aria-pressed="packageCode === 'VERIFY_1'"
              :disabled="!catalog?.enabled || !!draft"
              @click="packageCode = 'VERIFY_1'"
              ><span class="test-badge">{{ text('限时测试', 'LIMITED TEST') }}</span
              ><strong>{{ text('临时支付测试', 'Temporary payment test') }}</strong
              ><span
                >{{ money(verificationPackage.amountCents, 'CNY', lang) }} /
                {{ verificationPackage.points }} {{ text('积分', 'credit') }}</span
              ><Icon
                :icon="packageCode === 'VERIFY_1' ? 'ep:circle-check-filled' : 'ep:arrow-right'"
            /></button>
            <div v-if="packageCode === 'VERIFY_1'" class="verification-controls"
              ><div
                ><strong>{{ text('测试数量', 'Test quantity') }}</strong
                ><el-radio-group v-model="quantity" :disabled="!!draft || quoting"
                  ><el-radio-button v-for="count in [1, 5, 10]" :key="count" :value="count"
                    >{{ count }} {{ text('份', 'units') }}</el-radio-button
                  ></el-radio-group
                ></div
              ><p>{{
                text(
                  '每份 ¥1 / 1 积分。仅限指定账号临时验收，若低于渠道限额会明确提示，不会自动增加金额。',
                  'Each unit is CNY 1 / 1 credit. Available temporarily to approved accounts. Channel minimum errors are shown without automatically increasing the amount.'
                )
              }}</p></div
            >
          </section>
          <section class="selection-section channel-section">
            <div class="section-heading"
              ><div
                ><span class="section-step">02</span
                ><h2>{{ text('选择支付方式', 'Choose a payment method') }}</h2></div
              ><span
                ><Icon icon="ep:lock" />{{ text('渠道安全付款', 'Secure provider checkout') }}</span
              ></div
            >
            <el-radio-group
              v-model="paymentMethod"
              class="payment-methods"
              :disabled="!catalog?.enabled || !!draft"
            >
              <el-radio-button v-for="method in methods" :key="method" :value="method"
                ><span class="method-content"
                  ><span class="channel-logo" :class="{ wechat: method === 'wechat_pay' }">{{
                    method === 'alipay' ? '支' : '微'
                  }}</span
                  ><span
                    ><strong>{{
                      method === 'alipay'
                        ? text('支付宝', 'Alipay')
                        : text('微信支付', 'WeChat Pay')
                    }}</strong
                    ><small>{{
                      method === 'alipay'
                        ? text('跳转安全支付页面', 'Secure payment page')
                        : text('使用微信扫码付款', 'Scan with WeChat')
                    }}</small></span
                  ><Icon
                    :icon="
                      paymentMethod === method ? 'ep:circle-check-filled' : 'ep:circle-check'
                    " /></span
              ></el-radio-button>
            </el-radio-group>
          </section>
          <div class="purchase-assurance"
            ><Icon icon="ep:lock" /><div
              ><strong>{{ text('每笔订单，清晰可查', 'Every order, easy to track') }}</strong
              ><p>{{
                text(
                  '先获取实际币种与金额，再确认付款。支付结果和积分状态可在订单记录中查询。',
                  'Get the final currency and amount before approving payment. Track payment and credit status in your orders.'
                )
              }}</p></div
            ></div
          >
        </div>
        <aside class="purchase-summary">
          <div class="summary-heading"
            ><span class="section-step">03</span
            ><h2>{{ text('购买摘要', 'Purchase summary') }}</h2></div
          >
          <template v-if="chosenPackage"
            ><div class="summary-package"
              ><span>{{
                chosenPackage.code === 'VERIFY_1'
                  ? text('临时支付测试', 'Temporary payment test')
                  : chosenPackage.code
              }}</span
              ><strong
                >{{ (chosenPackage.points * purchaseQuantity).toLocaleString() }}
                <small>{{ text('积分', 'credits') }}</small></strong
              ></div
            ><dl class="selection-details"
              ><div
                ><dt>{{ text('购买数量', 'Quantity') }}</dt
                ><dd>{{ purchaseQuantity }} {{ text('份', 'units') }}</dd></div
              ><div
                ><dt>{{ text('积分有效期', 'Credit validity') }}</dt
                ><dd>{{ text('付款后六个自然月', 'Six calendar months') }}</dd></div
              ></dl
            ><div class="base-total"
              ><span>{{ text('本次人民币基准金额', 'CNY base total') }}</span
              ><strong>{{
                money(chosenPackage.amountCents * purchaseQuantity, 'CNY', lang)
              }}</strong
              ><small
                >{{ chosenPackage.points * purchaseQuantity }}
                {{
                  text('积分 · 实付金额以报价为准', 'credits · Final amount shown in quote')
                }}</small
              ></div
            ></template
          >
          <div class="quote-controls"
            ><el-button
              type="primary"
              size="large"
              :loading="quoting"
              :disabled="
                !draft && (!catalog?.enabled || !packageCode || !methods.includes(paymentMethod))
              "
              @click="createQuote"
              >{{
                draft
                  ? text('恢复原报价', 'Recover original quote')
                  : text('获取实际支付报价', 'Get payment quote')
              }}<Icon icon="ep:arrow-right" /></el-button
          ></div>
          <p class="summary-caption">{{
            text(
              '确认报价后才会进入付款，不会自动扣费。',
              'Review the quote before paying. No automatic charge.'
            )
          }}</p>
          <el-alert
            v-if="draft"
            type="warning"
            :closable="false"
            :title="
              text(
                '原报价结果待确认，将保留原套餐、渠道与请求标识重试。',
                'The original quote needs confirmation. Recovery preserves its package, channel and request ID.'
              )
            "
          />
          <section v-if="selected && selected.status !== 'created'" class="current-quote">
            <div class="quote-heading"
              ><h3>{{ text('订单报价', 'Order quote') }}</h3
              ><span>{{ statusLabel(selected.status) }}</span></div
            >
            <div class="quote-summary"
              ><strong>{{ money(selected.amountCents, selected.currency, lang) }}</strong
              ><span
                >{{ selected.points }} {{ text('积分', 'credits') }} ·
                {{ selected.packageCode }}</span
              ></div
            >
            <p
              >{{ text('人民币基准金额', 'CNY base amount') }}：{{
                money(selected.baseAmountCents, 'CNY', lang)
              }}
              · {{ text('换算系数', 'FX rate') }}：{{ selected.fx }}</p
            >
            <p v-if="selected.status === 'quoted'"
              >{{ text('报价截止', 'Quote expires') }}：{{
                new Date(expiryMillis(selected.expiresAt)).toLocaleString(lang)
              }}</p
            >
            <p>{{ text('积分状态', 'Credits') }}：{{ creditLabel(selected.creditStatus) }}</p>
            <div class="order-actions">
              <el-button
                v-if="selected.status === 'quoted'"
                type="primary"
                :loading="paying"
                :disabled="expired"
                @click="confirmPay"
                >{{
                  expired
                    ? text('报价已过期', 'Quote expired')
                    : text('确认金额并支付', 'Confirm amount and pay')
                }}</el-button
              >
              <el-button @click="dialogVisible = true">{{
                text('查看 / 刷新支付状态', 'View / refresh payment')
              }}</el-button>
              <el-button
                v-if="['quoted', 'payment_pending'].includes(selected.status)"
                link
                :loading="canceling"
                @click="cancelOrder"
                >{{ text('取消订单', 'Cancel order') }}</el-button
              >
            </div>
            <p class="order-id">{{ selected.orderNo }}</p>
          </section>
        </aside>
      </div>
      <section class="order-history">
        <div class="history-heading"
          ><div
            ><h2>{{ text('订单记录', 'Order history') }}</h2
            ><p>{{
              text(
                '查看每一笔充值的付款与积分状态',
                'Track the payment and credits for every order'
              )
            }}</p></div
          ><span>{{ text('共', 'Total') }} {{ total }} {{ text('笔订单', 'orders') }}</span></div
        >
        <el-table :data="orders" stripe>
          <el-table-column prop="orderNo" :label="text('订单号', 'Order')" min-width="260"
            ><template #default="{ row }"
              ><span class="history-order-id">{{ row.orderNo }}</span></template
            ></el-table-column
          >
          <el-table-column :label="text('金额', 'Amount')" min-width="120"
            ><template #default="{ row }"
              ><strong>{{
                row.currency ? money(row.amountCents, row.currency, lang) : '—'
              }}</strong></template
            ></el-table-column
          >
          <el-table-column prop="points" :label="text('积分', 'Credits')" min-width="75" />
          <el-table-column :label="text('支付状态', 'Payment')" min-width="120"
            ><template #default="{ row }"
              ><span class="status-label" :class="'status-' + row.status"
                ><i></i>{{ statusLabel(row.status) }}</span
              ></template
            ></el-table-column
          >
          <el-table-column :label="text('积分状态', 'Credit allocation')" min-width="130"
            ><template #default="{ row }">{{
              creditLabel(row.creditStatus)
            }}</template></el-table-column
          >
          <el-table-column :label="text('操作', 'Action')" width="105" fixed="right"
            ><template #default="{ row }"
              ><el-button link type="primary" @click="restoreOrder(row)">{{
                row.status === 'created'
                  ? text('恢复报价', 'Recover quote')
                  : text('查看订单', 'View order')
              }}</el-button></template
            ></el-table-column
          >
        </el-table>
        <Pagination
          v-model:page="pageNo"
          v-model:limit="pageSize"
          :total="total"
          @pagination="loadOrders"
        />
      </section>
      <PaymentDialog v-model="dialogVisible" v-model:order="selected" />
    </div>
  </ContentWrap>
</template>
<script setup lang="ts">
import '@/styles/clipforge-theme.css'
import { isTenantAdministrator } from '@/utils/tenantOnboarding'
import { ElMessageBox } from 'element-plus'
import { useLocaleStore } from '@/store/modules/locale'
import { useUserStore } from '@/store/modules/user'
import { getCurrentUserId, getTenantId } from '@/utils/auth'
import { PaymentApi, PAYMENT_QUOTE_REJECTED_CODE } from '@/api/tk/payment'
import type { PaymentOrder, RechargeCatalog } from '@/api/tk/payment'
import {
  money,
  expiryMillis,
  quoteAttempt,
  parsePaymentDraft,
  mergePaymentOrder
} from '@/utils/tenantOnboarding'
import type { QuoteDraft } from '@/utils/tenantOnboarding'
import PaymentDialog from './PaymentDialog.vue'
import { paymentStatusText, creditStatusText } from './statusText'

defineOptions({ name: 'TkRecharge' })
const user = useUserStore()
const tenantAdmin = computed(() => isTenantAdministrator(user.getUser.tkUserLevel))
const locale = useLocaleStore()
const lang = computed(() => (locale.getCurrentLocale.lang === 'en' ? 'en-US' : 'zh-CN'))
const text = (zh: string, en: string) => (lang.value === 'en-US' ? en : zh)
const statusLabel = (status: string) => paymentStatusText(status, lang.value === 'en-US')
const creditLabel = (status: string) => creditStatusText(status, lang.value === 'en-US')
const catalog = ref<RechargeCatalog>()
const regularPackages = computed(
  () => catalog.value?.packages.filter((item) => item.code !== 'VERIFY_1') || []
)
const verificationPackage = computed(() =>
  catalog.value?.packages.find((item) => item.code === 'VERIFY_1')
)
const packageCode = ref('')
const quantity = ref<1 | 5 | 10>(1)
const purchaseQuantity = computed(() => (packageCode.value === 'VERIFY_1' ? quantity.value : 1))
const chosenPackage = computed(() =>
  catalog.value?.packages.find((item) => item.code === packageCode.value)
)
const paymentMethod = ref('')
const methods = computed(() =>
  (catalog.value?.paymentMethods || []).filter((method) =>
    ['alipay', 'wechat_pay'].includes(method)
  )
)
const orders = ref<PaymentOrder[]>([])
const selected = ref<PaymentOrder>()
const draft = ref<QuoteDraft>()
const pageNo = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const quoting = ref(false)
const paying = ref(false)
const canceling = ref(false)
const dialogVisible = ref(false)
const loadError = ref('')
const clock = ref(Date.now())
const expired = computed(
  () => !selected.value || expiryMillis(selected.value.expiresAt) <= clock.value
)
const storagePrefix = 'tk-payment:' + getTenantId() + ':' + getCurrentUserId()
const storage = (key: string, value?: string) => {
  try {
    if (value === undefined) return localStorage.getItem(storagePrefix + key)
    if (value) localStorage.setItem(storagePrefix + key, value)
    else localStorage.removeItem(storagePrefix + key)
  } catch {
    return null
  }
}
const rememberOrder = (order: PaymentOrder) => {
  storage(':order', order.orderNo)
}
const loadOrders = async () => {
  if (!tenantAdmin.value) return
  const result = await PaymentApi.page({ pageNo: pageNo.value, pageSize: pageSize.value })
  orders.value = result.list
  total.value = result.total
}
const load = async () => {
  if (!tenantAdmin.value) return
  loading.value = true
  loadError.value = ''
  try {
    const [current] = await Promise.all([PaymentApi.catalog(), loadOrders()])
    catalog.value = current
    if (!packageCode.value) packageCode.value = current.packages[0]?.code || ''
    if (!paymentMethod.value) paymentMethod.value = methods.value[0] || ''
  } catch {
    loadError.value = text('暂时无法加载，请刷新重试。', 'Unable to load. Please refresh.')
  } finally {
    loading.value = false
  }
}
const createQuote = async () => {
  if (quoting.value || !tenantAdmin.value) return
  quoting.value = true
  draft.value = quoteAttempt(draft.value, {
    packageCode: packageCode.value,
    paymentMethod: paymentMethod.value,
    quantity: purchaseQuantity.value
  })
  storage(':draft', JSON.stringify(draft.value))
  try {
    const order = await PaymentApi.quote({ ...draft.value, quantity: draft.value.quantity ?? 1 })
    loadError.value = ''
    selected.value = order
    rememberOrder(order)
    if (order.status !== 'created') {
      draft.value = undefined
      storage(':draft', '')
    }
    await loadOrders()
  } catch (error) {
    if (
      error instanceof Error &&
      'businessCode' in error &&
      error.businessCode === PAYMENT_QUOTE_REJECTED_CODE
    ) {
      // This code is returned only after the server has fenced the rejected no-quote order.
      draft.value = undefined
      storage(':draft', '')
      loadError.value = error.message
      await loadOrders()
    }
    /* Unknown outcomes retain the original choice and idempotency key for recovery. */
  } finally {
    quoting.value = false
  }
}
const restoreOrder = async (order: PaymentOrder) => {
  const current = await PaymentApi.get(order.orderNo)
  if (current.quoteRejected) {
    selected.value = undefined
    dialogVisible.value = false
    storage(':order', '')
    loadError.value = text(
      '此报价已被渠道拒绝，未发起付款。请重新选择套餐或购买数量。',
      'This quote was rejected before payment. Select a package or quantity again.'
    )
    return
  }
  selected.value = current
  rememberOrder(current)
  if (current.status === 'created') {
    loadError.value = text(
      '原报价仍在恢复，请稍后刷新此订单。',
      'The original quote is still recovering. Refresh this order later.'
    )
    return
  }
  dialogVisible.value = true
}
const confirmPay = async () => {
  const quote = selected.value
  if (!quote || expired.value || paying.value) return
  try {
    await ElMessageBox.confirm(
      text('确认支付 ', 'Confirm payment of ') +
        money(quote.amountCents, quote.currency, lang.value) +
        text('，获得 ', ' for ') +
        quote.points +
        text(
          ' 积分？请以此实际币种与金额为准。',
          ' credits? This currency and amount are the charge to approve.'
        ),
      text('确认支付金额', 'Confirm payment amount'),
      { type: 'warning', customClass: 'clipforge-theme' }
    )
  } catch {
    return
  }
  paying.value = true
  try {
    const result = await PaymentApi.pay(quote.orderNo, {
      amountCents: quote.amountCents,
      currency: quote.currency
    })
    selected.value = mergePaymentOrder(quote, result)
    dialogVisible.value = true
    await loadOrders()
  } catch {
    // An uncertain create is recovered by GET on the same order; never create another order here.
    try {
      selected.value = mergePaymentOrder(quote, await PaymentApi.get(quote.orderNo))
    } catch {
      /* Keep original order. */
    }
    dialogVisible.value = true
  } finally {
    paying.value = false
  }
}
const cancelOrder = async () => {
  const order = selected.value
  if (!order || canceling.value) return
  try {
    await ElMessageBox.confirm(
      text('确认取消当前订单？', 'Cancel this order?'),
      text('取消订单', 'Cancel order'),
      { type: 'warning', customClass: 'clipforge-theme' }
    )
  } catch {
    return
  }
  canceling.value = true
  try {
    selected.value = mergePaymentOrder(order, await PaymentApi.cancel(order.orderNo))
    await loadOrders()
  } finally {
    canceling.value = false
  }
}
let ticker: ReturnType<typeof setInterval> | undefined
onMounted(async () => {
  if (!tenantAdmin.value) return
  ticker = setInterval(() => {
    clock.value = Date.now()
  }, 1000)
  draft.value = parsePaymentDraft(storage(':draft') || null)
  if (draft.value) {
    packageCode.value = draft.value.packageCode
    paymentMethod.value = draft.value.paymentMethod
    quantity.value = draft.value.quantity ?? 1
  }
  await load()
  const orderNo = storage(':order')
  if (orderNo && /^TKP_[a-f0-9]{32}$/.test(orderNo)) {
    try {
      await restoreOrder(await PaymentApi.get(orderNo))
    } catch {
      /* History remains accessible. */
    }
  }
})
onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker)
})
</script>
<style scoped>
.recharge-page {
  max-width: 1240px;
  padding: 8px 4px;
  margin: 0 auto;
}

.recharge-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-eyebrow {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1.2px;
  color: #6f89a8;
}

.recharge-header h1 {
  margin: 7px 0;
  font-size: 29px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.8px;
  color: var(--cf-ink);
}

.recharge-header p {
  margin: 0;
  font-size: 13px;
  color: var(--cf-muted);
}

.account-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.account-avatar {
  display: grid;
  width: 42px;
  height: 42px;
  font-size: 20px;
  color: #1677ff;
  background: #edf5ff;
  border: 1px solid #dceaff;
  border-radius: 12px;
  place-items: center;
}

.account-header > div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 110px;
}

.account-header strong {
  font-size: 13px;
  font-weight: 600;
}

.account-header > div > span {
  font-size: 11px;
  color: var(--cf-muted);
}

.account-header .el-button {
  gap: 6px;
  margin-left: 8px;
}

.validity-note {
  display: flex;
  padding: 13px 16px;
  font-size: 11px;
  line-height: 1.8;
  color: #5a769a;
  background: var(--cf-surface);
  border: 1px solid #deebfb;
  border-radius: 9px;
  gap: 9px;
  align-items: flex-start;
}

.validity-note > .el-icon,
.validity-note > svg {
  margin-top: 3px;
  color: #1677ff;
  flex-shrink: 0;
}

.recharge-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: 26px;
  margin-top: 28px;
  align-items: start;
}

.purchase-panel {
  min-width: 0;
}

.section-heading,
.section-heading > div,
.summary-heading {
  display: flex;
  align-items: center;
  gap: 9px;
}

.section-heading {
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-heading h2,
.summary-heading h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 650;
  color: var(--cf-ink);
}

.section-heading > span {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: #8a9ab0;
}

.section-step {
  display: grid;
  width: 23px;
  height: 23px;
  font:
    600 10px ui-monospace,
    Consolas,
    monospace;
  color: #1677ff;
  background: #edf5ff;
  border-radius: 7px;
  place-items: center;
}

.package-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.package-option {
  display: flex;
  min-width: 0;
  padding: 17px 14px 16px;
  color: var(--cf-ink);
  text-align: left;
  cursor: pointer;
  background: white;
  border: 1px solid var(--cf-line);
  border-radius: 12px;
  transition:
    border-color 150ms,
    box-shadow 150ms;
  flex-direction: column;
  align-items: stretch;
  gap: 0;
}

.package-option:hover:not(:disabled) {
  border-color: #7db3ff;
  box-shadow: 0 4px 16px #1677ff0c;
}

.package-option.selected {
  background: #f5f9ff;
  border-color: #1677ff;
  box-shadow: 0 0 0 1px #1677ff;
}

.package-top {
  display: flex;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.4px;
  color: #6d809a;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}

.selection-check {
  display: grid;
  place-items: center;
  width: 15px;
  height: 15px;
  border: 1px solid #cedaeb;
  border-radius: 50%;
}

.selected .selection-check {
  font-size: 10px;
  color: white;
  background: #1677ff;
  border-color: #1677ff;
}

.package-option > strong {
  margin: 22px 0 7px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: -1px;
  font-variant-numeric: tabular-nums;
}

.package-option strong small {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  font-weight: 400;
  letter-spacing: 0;
  color: #7d8fa6;
}

.package-price {
  padding-top: 14px;
  margin-top: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #345d8f;
  border-top: 1px solid #e5edf8;
}

.package-validity {
  margin-top: 6px;
  font-size: 9px;
  line-height: 1.6;
  color: #8a9bb0;
}

.package-option:disabled,
.verification-choice:disabled {
  cursor: default;
  opacity: 0.6;
}

.verification-section {
  margin-top: 17px;
  overflow: hidden;
  background: #fbfdff;
  border: 1px dashed #cadcf3;
  border-radius: 10px;
}

.verification-section.active {
  background: #f5f9ff;
  border-color: #87b8ff;
  border-style: solid;
}

.verification-choice {
  display: flex;
  width: 100%;
  padding: 14px;
  font-size: 11px;
  color: #617a9b;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  align-items: center;
  gap: 11px;
}

.verification-choice strong {
  font-size: 12px;
  font-weight: 600;
  color: #3b5c84;
}

.verification-choice > .el-icon,
.verification-choice > svg {
  margin-left: auto;
  color: #1677ff;
}

.test-badge {
  padding: 3px 6px;
  font-size: 9px;
  font-weight: 600;
  color: #437bbb;
  background: #e9f2ff;
  border-radius: 4px;
}

.verification-controls {
  padding: 0 14px 13px;
}

.verification-controls > div {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 14px;
  border-top: 1px solid #e1ebf7;
}

.verification-controls strong {
  font-size: 12px;
  font-weight: 500;
}

.verification-controls p {
  margin: 12px 0 0;
  font-size: 10px;
  line-height: 1.8;
  color: #7d90aa;
}

.channel-section {
  margin-top: 30px;
}

.payment-methods {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.payment-methods :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 16px 13px;
  color: #3f5878;
  background: white;
  border: 1px solid var(--cf-line);
  border-radius: 10px !important;
  box-shadow: none !important;
}

.payment-methods :deep(.el-radio-button.is-active .el-radio-button__inner) {
  color: #1677ff;
  background: #f5f9ff;
  border-color: #1677ff;
  box-shadow: 0 0 0 1px #1677ff !important;
}

.method-content {
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
}

.method-content > span:nth-child(2) {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.method-content strong {
  font-size: 13px;
  font-weight: 600;
}

.method-content small {
  font-size: 10px;
  font-weight: 400;
  color: #8799b0;
}

.method-content > .el-icon,
.method-content > svg {
  margin-left: auto;
}

.channel-logo {
  display: grid;
  width: 32px;
  height: 32px;
  font-size: 20px;
  font-weight: 700;
  color: white;
  background: #1677ff;
  border-radius: 8px;
  place-items: center;
}

.channel-logo.wechat {
  background: #07a963;
}

.purchase-assurance {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 22px 2px 0;
  color: #8da0b8;
}

.purchase-assurance > .el-icon,
.purchase-assurance > svg {
  margin-top: 2px;
  font-size: 18px;
}

.purchase-assurance strong {
  font-size: 11px;
  font-weight: 500;
  color: #6581a2;
}

.purchase-assurance p {
  margin: 5px 0 0;
  font-size: 10px;
  line-height: 1.8;
}

.purchase-summary {
  padding: 21px;
  background: white;
  border: 1px solid #dbe7f5;
  border-radius: 14px;
  box-shadow: 0 8px 28px #173f7507;
}

.summary-heading {
  gap: 8px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e7eef7;
}

.summary-heading h2 {
  font-size: 15px;
}

.summary-package {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 20px 0;
}

.summary-package > span {
  font-size: 11px;
  font-weight: 600;
  color: #8195ae;
}

.summary-package strong {
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.6px;
  color: #24436a;
}

.summary-package small {
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0;
  color: #6f86a2;
}

.selection-details {
  margin: 0 0 20px;
}

.selection-details > div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 13px 0;
  font-size: 11px;
}

.selection-details dt {
  color: #8395ac;
}

.selection-details dd {
  margin: 0;
  color: #4e6b8f;
}

.base-total {
  display: flex;
  padding: 17px 14px;
  background: var(--cf-surface);
  border-radius: 9px;
  flex-direction: column;
  gap: 8px;
}

.base-total > span {
  font-size: 10px;
  color: #7289a5;
}

.base-total strong {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -1px;
  color: #153d70;
  font-variant-numeric: tabular-nums;
}

.base-total small {
  font-size: 9px;
  line-height: 1.7;
  color: #8b9bb1;
}

.quote-controls {
  margin-top: 17px;
}

.quote-controls .el-button {
  width: 100%;
  font-size: 12px;
  gap: 12px;
}

.summary-caption {
  margin: 11px 0 0;
  font-size: 9px;
  line-height: 1.7;
  color: #98a5b7;
  text-align: center;
}

.current-quote {
  padding-top: 19px;
  margin-top: 22px;
  border-top: 1px solid var(--cf-line);
}

.quote-heading {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.quote-heading h3 {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
}

.quote-heading > span {
  padding: 3px 6px;
  font-size: 9px;
  color: #1677ff;
  background: #edf5ff;
  border-radius: 4px;
}

.quote-summary {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 16px 0;
}

.quote-summary strong {
  font-size: 25px;
  font-weight: 700;
  color: #1677ff;
}

.quote-summary > span {
  font-size: 11px;
  color: #7890ac;
}

.current-quote p {
  font-size: 10px;
  line-height: 1.8;
  color: #8a9ab0;
}

.order-actions {
  display: flex;
  flex-direction: column;
  gap: 9px;
  align-items: stretch;
  margin: 15px 0;
}

.order-actions .el-button + .el-button {
  margin-left: 0;
}

.order-id {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 9px !important;
  overflow-wrap: anywhere;
}

.order-history {
  padding-top: 25px;
  margin-top: 34px;
  border-top: 1px solid var(--cf-line);
}

.history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.history-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
}

.history-heading p {
  margin: 7px 0 0;
  font-size: 11px;
  color: #8b9bb1;
}

.history-heading > span {
  font-size: 10px;
  color: #91a0b3;
}

.order-history :deep(.el-table) {
  --el-table-header-bg-color: #f5f9ff;
  --el-table-row-hover-bg-color: #f5f9ff;
  --el-table-border-color: #e7eef7;

  font-size: 11px;
}

.order-history :deep(.el-table th.el-table__cell) {
  font-weight: 500;
  color: #7690af;
}

.order-history :deep(.el-table td.el-table__cell) {
  padding: 13px 0;
  color: #5e789b;
}

.history-order-id {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 10px;
  color: #8296af;
}

.status-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
}

.status-label i {
  width: 5px;
  height: 5px;
  background: #99a9bf;
  border-radius: 50%;
}

.status-paid {
  color: #20965f;
}

.status-paid i {
  background: #20a56a;
}

.status-payment_pending,
.status-quoted {
  color: #477ebd;
}

.status-payment_pending i,
.status-quoted i {
  background: #1677ff;
}

@media (width <= 1100px) {
  .recharge-layout {
    grid-template-columns: minmax(0, 1fr) 280px;
    gap: 20px;
  }

  .package-grid {
    gap: 9px;
  }

  .package-option {
    padding: 14px 10px;
  }

  .package-option > strong {
    font-size: 25px;
  }

  .method-content small {
    font-size: 9px;
  }
}

@media (width <= 900px) {
  .recharge-layout {
    grid-template-columns: 1fr;
  }

  .purchase-summary {
    max-width: none;
  }

  .recharge-header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (width <= 560px) {
  .package-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .payment-methods {
    grid-template-columns: 1fr;
  }

  .verification-choice {
    flex-wrap: wrap;
  }

  .account-header {
    flex-wrap: wrap;
  }
}
</style>
