<template>
  <el-tooltip v-if="visible" placement="bottom" :content="tooltipText">
    <div class="tk-credit-balance" :class="{ warning: balance.lowBalance }">
      <Icon icon="ep:coin" />
      <span>{{ creditLabel }} {{ balance.remainingCredits ?? 0 }}</span>
    </div>
  </el-tooltip>
</template>

<script setup lang="ts">
import { TkCreditApi, TkCreditBalanceVO } from '@/api/tk/credit'
import { tkText } from '@/utils/tkI18n'

defineOptions({ name: 'TkCreditBalance' })

const visible = ref(false)
const balance = ref<TkCreditBalanceVO>({})

const tooltipText = computed(() => {
  const total = balance.value.totalCredits ?? 0
  const remaining = balance.value.remainingCredits ?? 0
  if (balance.value.lowBalance) {
    return tkText(
      `积分余额不足 ${balance.value.warningThreshold ?? 100}，请联系客服充值。总额度 ${total}，剩余 ${remaining}`,
      `Credit balance is below ${balance.value.warningThreshold ?? 100}. Contact support to recharge. Total ${total}, remaining ${remaining}`
    )
  }
  return tkText(
    `租户总额度 ${total}，剩余 ${remaining}`,
    `Tenant quota ${total}, remaining ${remaining}`
  )
})

const creditLabel = computed(() => tkText('积分', 'Credits'))

const loadBalance = async () => {
  try {
    balance.value = await TkCreditApi.getBalance()
    visible.value = true
  } catch (e) {
    visible.value = false
  }
}

onMounted(loadBalance)
</script>

<style scoped>
.tk-credit-balance {
  display: inline-flex;
  height: 32px;
  padding: 0 10px;
  margin-right: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  align-items: center;
  gap: 6px;
}

.tk-credit-balance.warning {
  color: #b45309;
  background: #fffbeb;
  border-color: #facc15;
}
</style>
