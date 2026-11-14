<template>
  <ContentWrap>
    <el-result
      v-if="!tenantAdmin"
      icon="warning"
      :title="text('仅租户管理员可充值', 'Tenant administrator access required')"
    />
    <div v-else v-loading="loading" class="recharge-page">
      <header class="recharge-header">
        <div
          ><h1>{{ text('积分充值', 'Add credits') }}</h1
          ><p>{{
            text(
              '先查看实时套餐，再确认实际支付金额。',
              'Review current packages, then confirm the actual payment amount.'
            )
          }}</p></div
        >
        <el-button @click="load">{{
          text('刷新套餐与订单', 'Refresh packages and orders')
        }}</el-button>
      </header>
      <el-alert
        :closable="false"
        type="info"
        :title="
          text(
            '新购积分自成功支付起有效期为六个自然月，历史积分不追溯失效。支付确认与积分到账分别显示。',
            'New paid credits expire six calendar months after successful payment. Historic credits are unaffected. Payment and credit allocation are shown separately.'
          )
        "
      />
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
      <section class="package-grid">
        <button
          v-for="item in catalog?.packages || []"
          :key="item.code"
          type="button"
          class="package-option"
          :class="{ selected: packageCode === item.code }"
          :disabled="!catalog?.enabled || !!draft"
          @click="packageCode = item.code"
        >
          <span>{{
            item.code === 'VERIFY_1' ? text('临时支付测试', 'Temporary payment test') : item.code
          }}</span
          ><strong>{{ money(item.amountCents, 'CNY', lang) }}</strong>
          <span>{{ item.points.toLocaleString() }} {{ text('积分', 'credits') }}</span>
        </button>
      </section>
      <div v-if="packageCode === 'VERIFY_1'" class="verification-controls">
        <strong>{{ text('测试数量', 'Test quantity') }}</strong>
        <el-radio-group v-model="quantity" :disabled="!!draft || quoting">
          <el-radio-button v-for="count in [1, 5, 10]" :key="count" :value="count">
            {{ count }} {{ text('份', 'units') }}
          </el-radio-button>
        </el-radio-group>
        <p>{{
          text(
            '每份 ¥1 / 1 积分。仅限指定账号临时验收，若低于渠道限额会明确提示，不会自动增加金额。',
            'Each unit is CNY 1 / 1 credit. Available temporarily to approved accounts. Channel minimum errors are shown without automatically increasing the amount.'
          )
        }}</p>
      </div>
      <p v-if="chosenPackage">
        {{ text('本次人民币基准金额', 'CNY base total') }}：{{
          money(chosenPackage.amountCents * purchaseQuantity, 'CNY', lang)
        }}
        · {{ chosenPackage.points * purchaseQuantity }} {{ text('积分', 'credits') }}
      </p>
      <div class="quote-controls">
        <el-radio-group v-model="paymentMethod" :disabled="!catalog?.enabled || !!draft">
          <el-radio-button v-for="method in methods" :key="method" :value="method">{{
            method === 'alipay' ? text('支付宝', 'Alipay') : text('微信支付', 'WeChat Pay')
          }}</el-radio-button>
        </el-radio-group>
        <el-button
          type="primary"
          :loading="quoting"
          :disabled="
            !draft && (!catalog?.enabled || !packageCode || !methods.includes(paymentMethod))
          "
          @click="createQuote"
        >
          {{
            draft
              ? text('恢复原报价', 'Recover original quote')
              : text('获取实际支付报价', 'Get payment quote')
          }}
        </el-button>
      </div>
      <el-alert
        v-if="draft"
        class="mt-16px"
        :closable="false"
        type="warning"
        :title="
          text(
            '原报价结果待确认，将保留原套餐、渠道与请求标识重试。',
            'The original quote needs confirmation. Recovery preserves its package, channel and request ID.'
          )
        "
      />
      <el-card v-if="selected && selected.status !== 'created'" class="mt-20px" shadow="never">
        <template #header
          ><strong>{{ text('订单报价', 'Order quote') }}</strong></template
        >
        <div class="quote-summary"
          ><strong>{{ money(selected.amountCents, selected.currency, lang) }}</strong
          ><span>{{ selected.points }} {{ text('积分', 'credits') }}</span></div
        >
        <p
          >{{ text('人民币基准金额', 'CNY base amount') }}：{{
            money(selected.baseAmountCents, 'CNY', lang)
          }}
          · {{ text('换算系数', 'FX rate') }}：{{ selected.fx }}</p
        >
        <p
          >{{ text('报价截止', 'Quote expires') }}：{{
            new Date(expiryMillis(selected.expiresAt)).toLocaleString(lang)
          }}</p
        >
        <p
          >{{ text('支付状态', 'Payment') }}：{{ statusLabel(selected.status) }} ·
          {{ text('积分状态', 'Credits') }}：{{ creditLabel(selected.creditStatus) }}</p
        >
        <div class="order-actions">
          <el-button
            v-if="selected.status === 'quoted'"
            type="primary"
            :loading="paying"
            :disabled="expired"
            @click="confirmPay"
          >
            {{
              expired
                ? text('报价已过期', 'Quote expired')
                : text('确认金额并支付', 'Confirm amount and pay')
            }}
          </el-button>
          <el-button @click="dialogVisible = true">{{
            text('查看 / 刷新支付状态', 'View / refresh payment')
          }}</el-button>
          <el-button
            v-if="['quoted', 'payment_pending'].includes(selected.status)"
            :loading="canceling"
            @click="cancelOrder"
            >{{ text('取消订单', 'Cancel order') }}</el-button
          >
        </div>
        <p class="order-id">{{ selected.orderNo }}</p>
      </el-card>
      <section class="order-history">
        <h2>{{ text('订单记录', 'Order history') }}</h2>
        <el-table :data="orders" stripe>
          <el-table-column prop="orderNo" :label="text('订单号', 'Order')" min-width="260" />
          <el-table-column :label="text('金额', 'Amount')" min-width="140"
            ><template #default="{ row }">{{
              row.currency ? money(row.amountCents, row.currency, lang) : '—'
            }}</template></el-table-column
          >
          <el-table-column prop="points" :label="text('积分', 'Credits')" min-width="80" />
          <el-table-column :label="text('支付状态', 'Payment')" min-width="150"
            ><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column
          >
          <el-table-column :label="text('积分状态', 'Credit allocation')" min-width="150"
            ><template #default="{ row }">{{
              creditLabel(row.creditStatus)
            }}</template></el-table-column
          >
          <el-table-column :label="text('操作', 'Action')" width="140"
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
      { type: 'warning' }
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
      { type: 'warning' }
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
  max-width: 1100px;
  margin: 0 auto;
}

.recharge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.recharge-header h1 {
  margin: 0;
  font-size: 24px;
}

.recharge-header p {
  color: var(--el-text-color-secondary);
}

.package-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 24px 0;
}

.package-option {
  display: flex;
  padding: 24px 20px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  flex-direction: column;
  gap: 12px;
}

.package-option strong {
  font-size: 24px;
}

.package-option.selected {
  padding: 23px 19px;
  background: var(--el-color-primary-light-9);
  border: 2px solid var(--el-color-primary);
}

.package-option:disabled {
  cursor: default;
  opacity: 0.7;
}

.verification-controls {
  margin-bottom: 20px;
}

.verification-controls .el-radio-group {
  margin-left: 16px;
}

.verification-controls p {
  color: var(--el-text-color-secondary);
}

.quote-controls,
.order-actions,
.quote-summary {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.quote-summary strong {
  font-size: 28px;
}

.order-id {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.order-history {
  margin-top: 32px;
}

@media (width <= 800px) {
  .package-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recharge-header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (width <= 420px) {
  .package-grid {
    grid-template-columns: 1fr;
  }

  .quote-controls > * {
    width: 100%;
  }
}
</style>
