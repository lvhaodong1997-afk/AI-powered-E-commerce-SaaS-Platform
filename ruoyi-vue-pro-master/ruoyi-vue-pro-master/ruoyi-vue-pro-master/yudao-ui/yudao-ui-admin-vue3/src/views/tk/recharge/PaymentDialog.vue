<template>
  <el-dialog
    :model-value="modelValue"
    :title="text('支付收银台', 'Payment checkout')"
    width="min(880px, calc(100vw - 48px))"
    class="clipforge-theme cashier-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="stop"
  >
    <template #header>
      <div class="cashier-title"
        ><span class="cashier-brand">ClipForge</span
        ><span>{{ text('安全收银台', 'Secure checkout') }}</span></div
      >
      <p class="cashier-subtitle">{{
        text('确认订单，安全完成付款', 'Review your order and complete payment securely')
      }}</p>
    </template>
    <div v-if="order" class="cashier-grid">
      <section class="cashier-summary">
        <p class="summary-eyebrow">{{ text('本次实付金额', 'Amount to pay') }}</p>
        <div class="payment-amount">{{ money(order.amountCents, order.currency, lang) }}</div>
        <div class="credits-pill"
          >{{ order.points.toLocaleString() }} {{ text('积分', 'credits') }}</div
        >
        <dl class="summary-details">
          <div
            ><dt>{{ text('充值套餐', 'Package') }}</dt
            ><dd>{{
              order.packageCode === 'VERIFY_1'
                ? text('临时支付测试', 'Temporary payment test')
                : order.packageCode
            }}</dd></div
          >
          <div
            ><dt>{{ text('购买数量', 'Quantity') }}</dt
            ><dd>{{ order.quantity }} {{ text('份', 'units') }}</dd></div
          >
          <div
            ><dt>{{ text('人民币基准金额', 'CNY base amount') }}</dt
            ><dd>{{ money(order.baseAmountCents, 'CNY', lang) }}</dd></div
          >
          <div
            ><dt>{{ text('支付状态', 'Payment status') }}</dt
            ><dd>{{ statusLabel(order.status) }}</dd></div
          >
          <div
            ><dt>{{ text('积分状态', 'Credit status') }}</dt
            ><dd>{{ creditLabel(order.creditStatus) }}</dd></div
          >
          <div v-if="order.status === 'quoted'"
            ><dt>{{ text('报价截止', 'Quote expires') }}</dt
            ><dd>{{ new Date(expiryMillis(order.expiresAt)).toLocaleString(lang) }}</dd></div
          >
        </dl>
        <ol class="cashier-steps">
          <li class="done"><span>1</span>{{ text('确认订单', 'Review order') }}</li>
          <li
            :class="{ done: order.status === 'paid', current: order.status === 'payment_pending' }"
            ><span>2</span>{{ text('渠道付款', 'Pay securely') }}</li
          >
          <li :class="{ done: credited }"
            ><span>3</span>{{ text('积分到账', 'Credits allocated') }}</li
          >
        </ol>
        <div class="order-reference"
          ><span>{{ text('订单号', 'Order number') }}</span
          ><div class="order-number">{{ order.orderNo }}</div></div
        >
        <p class="summary-note"
          ><Icon icon="ep:lock" />{{
            text('付款结果由服务器与支付渠道核实', 'Payment is verified by the server and provider')
          }}</p
        >
      </section>
      <section class="cashier-action" aria-live="polite" data-test="payment-entry">
        <div class="channel-heading">
          <span class="channel-badge" :class="{ wechat: order.paymentMethod === 'wechat_pay' }">{{
            order.paymentMethod === 'wechat_pay' ? '微' : '支'
          }}</span>
          <span>{{
            order.paymentMethod === 'wechat_pay'
              ? text('微信支付', 'WeChat Pay')
              : text('支付宝', 'Alipay')
          }}</span>
        </div>
        <template v-if="entryState === 'qr'">
          <h2>{{ text('微信扫码付款', 'Scan with WeChat to pay') }}</h2>
          <p class="action-description">{{
            text('打开微信「扫一扫」，完成本笔订单付款', 'Open WeChat Scan and pay for this order')
          }}</p>
          <div class="payment-qr"
            ><img
              :src="qrUrl"
              :alt="text('支付二维码', 'Payment QR code')"
              referrerpolicy="no-referrer"
              data-test="payment-qr"
              @error="markImageFailed"
          /></div>
          <div class="awaiting-label"
            ><span></span>{{ text('等待支付渠道确认', 'Awaiting payment confirmation') }}</div
          >
        </template>
        <template v-else-if="entryState === 'redirect'">
          <div class="state-symbol"><Icon icon="ep:wallet" /></div>
          <h2>{{ text('前往支付宝完成付款', 'Continue to Alipay') }}</h2>
          <p class="action-description">{{
            text(
              '将在新窗口打开支付渠道的安全页面，付款后可返回这里查看结果。',
              'A secure provider page opens in a new window. Return here to check the result.'
            )
          }}</p>
          <el-button type="primary" size="large" class="provider-button" @click="openProvider"
            >{{ text('打开安全支付页面', 'Open secure payment page') }}<Icon icon="ep:top-right"
          /></el-button>
        </template>
        <template v-else>
          <div
            class="state-symbol"
            :class="{
              success: entryState === 'complete',
              muted: ['canceled', 'refunded'].includes(entryState)
            }"
            ><Icon :icon="stateIcon" :class="{ 'is-loading': entryState === 'loading' }"
          /></div>
          <h2>{{ stateTitle }}</h2>
          <p class="action-description">{{ stateDescription }}</p>
          <el-button
            v-if="['entry_missing', 'image_failed'].includes(entryState)"
            type="primary"
            size="large"
            :loading="refreshing"
            @click="manualRefresh"
            >{{ text('重新获取支付入口', 'Recover payment entry') }}</el-button
          >
        </template>
        <p v-if="exhausted" class="refresh-note" role="status">{{
          text(
            '自动查询已暂停，请手动刷新。订单会保留在历史记录中。',
            'Automatic checks are paused. Refresh manually; your order remains in history.'
          )
        }}</p>
        <p v-if="error" class="payment-error" role="alert">{{ error }}</p>
        <p class="cashier-footnote">{{
          text(
            '新购积分自成功支付起有效期为六个自然月。支付确认与积分入账分别显示。',
            'New credits are valid for six calendar months after payment. Payment and credit allocation are confirmed separately.'
          )
        }}</p>
      </section>
    </div>
    <template #footer>
      <div class="cashier-footer"
        ><span
          ><Icon icon="ep:lock" />{{
            text('安全支付 · 订单可随时查询', 'Secure payment · Track your order anytime')
          }}</span
        ><div
          ><el-button :loading="refreshing" @click="manualRefresh">{{
            text('刷新状态', 'Refresh status')
          }}</el-button
          ><el-button @click="emit('update:modelValue', false)">{{
            text('返回订单记录', 'Back to orders')
          }}</el-button></div
        ></div
      >
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import '@/styles/clipforge-theme.css'
import { useLocaleStore } from '@/store/modules/locale'
import { PaymentApi } from '@/api/tk/payment'
import type { PaymentOrder } from '@/api/tk/payment'
import {
  money,
  expiryMillis,
  safeProviderUrl,
  paymentEntryState,
  mergePaymentOrder,
  shouldPollOrder,
  isPaidAndCredited
} from '@/utils/tenantOnboarding'
import { paymentStatusText, creditStatusText } from './statusText'

const props = defineProps<{ modelValue: boolean; order?: PaymentOrder }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; 'update:order': [PaymentOrder] }>()
const locale = useLocaleStore()
const lang = computed(() => (locale.getCurrentLocale.lang === 'en' ? 'en-US' : 'zh-CN'))
const text = (zh: string, en: string) => (lang.value === 'en-US' ? en : zh)
const statusLabel = (status: string) => paymentStatusText(status, lang.value === 'en-US')
const creditLabel = (status: string) => creditStatusText(status, lang.value === 'en-US')
const qrUrl = computed(() => safeProviderUrl(props.order?.qrImageUrl, 'qr'))
const redirectUrl = computed(() => safeProviderUrl(props.order?.redirectUrl, 'redirect'))
const credited = computed(() => props.order && isPaidAndCredited(props.order))
const failedImage = ref('')
const imageKey = computed(() => (props.order?.orderNo || '') + ':' + qrUrl.value)
const entryState = computed(() =>
  props.order
    ? paymentEntryState(props.order, {
        refreshing: refreshing.value,
        imageFailed: failedImage.value === imageKey.value
      })
    : 'loading'
)
const markImageFailed = () => {
  failedImage.value = imageKey.value
}
const stateTitle = computed(
  () =>
    (
      ({
        loading: text('正在获取支付入口', 'Loading payment entry'),
        entry_missing: text('支付入口暂未就绪', 'Payment entry is not ready'),
        image_failed: text('二维码加载失败', 'QR code could not load'),
        credit_pending: text('已支付，积分确认中', 'Paid — allocating credits'),
        complete: text('支付完成，积分已到账', 'Payment complete, credits allocated'),
        canceled: text('订单已取消', 'Order canceled'),
        refund_pending: text('退款处理中', 'Refund in progress'),
        refunded: text('订单已退款', 'Order refunded'),
        review: text('订单正在核查', 'Order under review'),
        quoted: text('请先确认支付金额', 'Confirm the amount first')
      }) as Record<string, string>
    )[entryState.value] || text('正在核实订单状态', 'Verifying order status')
)
const stateDescription = computed(
  () =>
    (
      ({
        loading: text('正在查询当前订单，请稍候。', 'Checking this order. Please wait.'),
        entry_missing: text(
          '渠道尚未返回可用的付款入口。请刷新原订单，不要重复下单。',
          'The provider has not returned a payment entry. Refresh this order rather than creating another.'
        ),
        image_failed: text(
          '请检查网络后重新获取二维码，将继续使用当前订单。',
          'Check your connection and reload the QR code for this order.'
        ),
        credit_pending: text(
          '支付渠道已确认付款，服务器正在处理积分入账。请勿重复支付。',
          'Payment is confirmed. The server is allocating your credits. Do not pay again.'
        ),
        complete: text(
          '积分已加入你的账户，可返回工作台继续创作。',
          'Credits are in your account. Return to your workspace to keep creating.'
        ),
        canceled: text(
          '此订单已关闭，付款入口已移除。如需充值，请返回选择套餐。',
          'This order is closed and its payment entry has been removed. Return to choose a package.'
        ),
        refund_pending: text(
          '正在核实退款与积分状态，结果以服务器确认为准。',
          'The server is confirming the refund and credit status.'
        ),
        refunded: text(
          '此订单已退款。积分处理结果请以左侧状态为准。',
          'This order has been refunded. See the credit status on the left.'
        ),
        review: text(
          '当前订单需要人工核查，请保留订单号并联系支持。',
          'This order needs review. Keep the order number and contact support.'
        ),
        quoted: text(
          '返回充值页确认实际币种和金额后，再继续付款。',
          'Return to the recharge page and confirm the charge currency and amount.'
        )
      }) as Record<string, string>
    )[entryState.value] ||
    text('请刷新原订单或联系支持。', 'Refresh this order or contact support.')
)
const stateIcon = computed(() =>
  entryState.value === 'loading'
    ? 'ep:loading'
    : entryState.value === 'complete'
      ? 'ep:circle-check-filled'
      : ['entry_missing', 'image_failed', 'review'].includes(entryState.value)
        ? 'ep:warning'
        : entryState.value === 'credit_pending'
          ? 'ep:clock'
          : 'ep:document-checked'
)
const refreshing = ref(false)
const exhausted = ref(false)
const error = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let attempts = 0
let generation = 0
let refreshingGeneration: number | undefined
const stop = () => {
  generation++
  refreshing.value = false
  if (timer) clearTimeout(timer)
  timer = undefined
}
const schedule = () => {
  if (!props.modelValue || !props.order || !shouldPollOrder(props.order)) return
  if (attempts >= 12) {
    exhausted.value = true
    return
  }
  timer = setTimeout(() => refresh(), Math.min(15000, 2000 * 1.5 ** attempts))
}
const refresh = async () => {
  if (!props.order || refreshingGeneration === generation || !props.modelValue) return
  const orderNo = props.order.orderNo
  const token = generation
  refreshingGeneration = token
  refreshing.value = true
  attempts++
  try {
    const result = await PaymentApi.get(orderNo)
    if (token === generation && props.order?.orderNo === orderNo) {
      emit('update:order', mergePaymentOrder(props.order, result))
      error.value = ''
    }
  } catch {
    if (token === generation)
      error.value = text(
        '暂时无法查询，请稍后刷新原订单。',
        'Unable to check now. Refresh this order later.'
      )
  } finally {
    if (refreshingGeneration === token) {
      refreshingGeneration = undefined
      refreshing.value = false
    }
    if (token === generation) schedule()
  }
}
const manualRefresh = () => {
  failedImage.value = ''
  stop()
  attempts = 0
  exhausted.value = false
  void refresh()
}
const openProvider = () => {
  if (entryState.value === 'redirect' && redirectUrl.value)
    window.open(redirectUrl.value, '_blank', 'noopener,noreferrer')
}
watch(
  [() => props.modelValue, () => props.order?.orderNo],
  () => {
    stop()
    attempts = 0
    exhausted.value = false
    error.value = ''
    failedImage.value = ''
    if (props.modelValue && props.order) void refresh()
  },
  { immediate: true }
)
onBeforeUnmount(stop)
</script>
<style scoped>
.cashier-title {
  display: flex;
  font-size: 14px;
  color: var(--cf-muted);
  gap: 14px;
  align-items: center;
}

.cashier-brand {
  padding-right: 14px;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.6px;
  color: var(--cf-ink);
  border-right: 1px solid var(--cf-line);
}

.cashier-subtitle {
  margin: 7px 0 0;
  font-size: 12px;
  color: var(--cf-muted);
}

.cashier-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  min-height: 480px;
}

.cashier-summary {
  padding: 30px 28px 22px;
  background: var(--cf-surface);
  border-right: 1px solid var(--cf-line);
}

.summary-eyebrow {
  margin: 0;
  font-size: 13px;
  color: var(--cf-muted);
}

.payment-amount {
  margin: 7px 0 12px;
  font-size: 42px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -1.5px;
  color: var(--cf-ink);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.credits-pill {
  display: inline-flex;
  padding: 5px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #195fae;
  background: #e7f0ff;
  border: 1px solid #d4e5ff;
  border-radius: 6px;
}

.summary-details {
  margin: 26px 0;
}

.summary-details > div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  margin: 13px 0;
  font-size: 12px;
}

.summary-details dt {
  color: var(--cf-muted);
  flex-shrink: 0;
}

.summary-details dd {
  margin: 0;
  color: #354f71;
  text-align: right;
  overflow-wrap: anywhere;
}

.cashier-steps {
  display: flex;
  padding: 0;
  margin: 24px 0;
  list-style: none;
  justify-content: space-between;
  gap: 8px;
}

.cashier-steps li {
  display: flex;
  font-size: 11px;
  color: #8291a8;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.cashier-steps li > span {
  display: grid;
  width: 25px;
  height: 25px;
  font-size: 12px;
  background: white;
  border: 1px solid #cfdbeb;
  border-radius: 50%;
  place-items: center;
}

.cashier-steps .done {
  color: #376da6;
}

.cashier-steps .done > span {
  color: white;
  background: #1677ff;
  border-color: #1677ff;
}

.cashier-steps .current {
  font-weight: 600;
  color: #1677ff;
}

.cashier-steps .current > span {
  color: #1677ff;
  border-color: #1677ff;
  box-shadow: 0 0 0 4px #e7f0ff;
}

.order-reference {
  padding-top: 18px;
  font-size: 11px;
  color: var(--cf-muted);
  border-top: 1px solid var(--cf-line);
}

.order-number {
  margin-top: 5px;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 10px;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

.summary-note {
  display: flex;
  margin: 14px 0 0;
  font-size: 10px;
  color: #7a8da6;
  gap: 6px;
  align-items: center;
}

.cashier-action {
  display: flex;
  padding: 26px 30px 20px;
  text-align: center;
  flex-direction: column;
  align-items: center;
}

.channel-heading {
  display: flex;
  font-size: 14px;
  font-weight: 600;
  color: #2b4667;
  align-items: center;
  gap: 9px;
}

.channel-badge {
  display: grid;
  width: 27px;
  height: 27px;
  font-size: 16px;
  font-weight: 700;
  color: white;
  background: #1677ff;
  border-radius: 7px;
  place-items: center;
}

.channel-badge.wechat {
  background: #07a963;
}

.cashier-action h2 {
  margin: 21px 0 7px;
  font-size: 20px;
  font-weight: 650;
  line-height: 1.4;
  color: var(--cf-ink);
}

.action-description {
  max-width: 320px;
  margin: 0 0 18px;
  font-size: 12px;
  line-height: 1.9;
  color: var(--cf-muted);
}

.payment-qr {
  width: 244px;
  height: 244px;
  padding: 12px;
  margin: 0 0 15px;
  background: white;
  border: 1px solid var(--cf-line);
  border-radius: 12px;
  box-shadow: 0 6px 20px #1e5ca10a;
}

.payment-qr img {
  display: block;
  width: 218px;
  height: 218px;
  max-width: 100%;
  object-fit: contain;
  background: white;
}

.awaiting-label {
  display: flex;
  font-size: 11px;
  color: #5b7392;
  align-items: center;
  gap: 7px;
}

.awaiting-label > span {
  width: 6px;
  height: 6px;
  background: #1677ff;
  border-radius: 50%;
}

.state-symbol {
  display: grid;
  width: 66px;
  height: 66px;
  margin: 38px 0 2px;
  font-size: 30px;
  color: #1677ff;
  background: #edf5ff;
  border-radius: 50%;
  place-items: center;
}

.state-symbol.success {
  color: #18a567;
  background: #eaf8f1;
}

.state-symbol.muted {
  color: #7a8da6;
  background: #f1f5fa;
}

.provider-button {
  min-width: 220px;
  margin-top: 6px;
  gap: 10px;
}

.cashier-footnote {
  max-width: 320px;
  padding-top: 16px;
  margin: 22px 0 0;
  font-size: 10px;
  line-height: 1.8;
  color: #8291a8;
  border-top: 1px solid #edf2f8;
}

.refresh-note {
  margin: 15px 0 0;
  font-size: 11px;
  color: #6b7f99;
}

.payment-error {
  margin: 14px 0 0;
  font-size: 12px;
  color: #d04a4a;
}

.cashier-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
}

.cashier-footer > span {
  display: flex;
  font-size: 10px;
  color: #8291a8;
  gap: 6px;
  align-items: center;
}

@media (width <= 700px) {
  .cashier-grid {
    grid-template-columns: 1fr;
  }

  .cashier-summary {
    border-right: 0;
    border-bottom: 1px solid var(--cf-line);
  }

  .cashier-footer {
    flex-wrap: wrap;
  }
}
</style>
