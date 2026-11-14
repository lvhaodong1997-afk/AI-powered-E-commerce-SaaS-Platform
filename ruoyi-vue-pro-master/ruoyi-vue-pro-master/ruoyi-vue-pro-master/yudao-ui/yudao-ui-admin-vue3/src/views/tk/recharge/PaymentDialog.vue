<template>
  <el-dialog
    :model-value="modelValue"
    :title="text('支付订单', 'Payment order')"
    width="min(560px, 94vw)"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="stop"
  >
    <template v-if="order">
      <div class="order-number">{{ order.orderNo }}</div>
      <div class="payment-amount">{{ money(order.amountCents, order.currency, lang) }}</div>
      <p
        >{{ text('人民币基准金额', 'CNY base amount') }}：{{
          money(order.baseAmountCents, 'CNY', lang)
        }}
        · {{ order.points }} {{ text('积分', 'credits') }}</p
      >
      <p
        >{{ text('报价截止', 'Quote expires') }}：{{
          new Date(expiryMillis(order.expiresAt)).toLocaleString(lang)
        }}</p
      >
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="text('支付状态', 'Payment status')">{{
          statusLabel(order.status)
        }}</el-descriptions-item>
        <el-descriptions-item :label="text('积分状态', 'Credit status')">{{
          creditLabel(order.creditStatus)
        }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="credited"
        class="mt-16px"
        type="success"
        :closable="false"
        :title="text('支付与积分入账均已确认', 'Payment and credit allocation are confirmed')"
      />
      <el-alert
        v-else
        class="mt-16px"
        type="info"
        :closable="false"
        :title="
          text(
            '完成支付或返回本站后，仍需服务器确认支付和积分到账。',
            'After payment or returning here, the server must still confirm payment and credit allocation.'
          )
        "
      />
      <div v-if="qrUrl && order.status === 'payment_pending'" class="payment-qr">
        <img
          :src="qrUrl"
          :alt="text('支付二维码', 'Payment QR code')"
          referrerpolicy="no-referrer"
        />
      </div>
      <el-button
        v-if="redirectUrl && order.status === 'payment_pending'"
        class="w-full mt-16px"
        type="primary"
        @click="openProvider"
      >
        {{ text('打开安全支付页面', 'Open secure payment page') }}
      </el-button>
      <p v-if="exhausted" role="status">{{
        text(
          '自动查询已暂停，请手动刷新。订单会保留在历史记录中。',
          'Automatic checks are paused. Refresh manually; your order remains in history.'
        )
      }}</p>
      <p v-if="error" class="payment-error" role="alert">{{ error }}</p>
    </template>
    <template #footer>
      <el-button :loading="refreshing" @click="manualRefresh">{{
        text('刷新状态', 'Refresh status')
      }}</el-button>
      <el-button @click="emit('update:modelValue', false)">{{
        text('返回订单记录', 'Back to orders')
      }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { useLocaleStore } from '@/store/modules/locale'
import { PaymentApi } from '@/api/tk/payment'
import type { PaymentOrder } from '@/api/tk/payment'
import {
  money,
  expiryMillis,
  safeProviderUrl,
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
  stop()
  attempts = 0
  exhausted.value = false
  void refresh()
}
const openProvider = () => {
  if (redirectUrl.value) window.open(redirectUrl.value, '_blank', 'noopener,noreferrer')
}
watch(
  () => [props.modelValue, props.order?.orderNo],
  () => {
    stop()
    attempts = 0
    exhausted.value = false
    error.value = ''
    if (props.modelValue && props.order) void refresh()
  },
  { immediate: true }
)
onBeforeUnmount(stop)
</script>
<style scoped>
.order-number {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.payment-amount {
  margin-top: 16px;
  font-size: 32px;
  font-weight: 700;
}

.payment-qr {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.payment-qr img {
  width: 220px;
  max-width: 100%;
  background: #fff;
}

.payment-error {
  color: var(--el-color-danger);
}
</style>
