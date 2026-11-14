<template>
  <el-form
    v-show="visible"
    ref="formRef"
    :model="form"
    :rules="rules"
    class="login-form"
    label-position="top"
    size="large"
  >
    <h3>{{ text('创建独立租户', 'Create your workspace') }}</h3>
    <p class="registration-note">{{
      text(
        '注册后拥有独立账号与数据，初始付费积分为 0。邀请码仅用于推荐。',
        'Your tenant has independent accounts and data, with zero initial paid credits. An invite is optional and records a referral only.'
      )
    }}</p>
    <el-alert
      v-if="!config?.enabled || !config?.smsAvailable"
      :closable="false"
      type="warning"
      :title="
        text(
          '注册或短信服务暂未开放，请稍后重试。',
          'Registration or SMS verification is currently unavailable.'
        )
      "
    />
    <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :prop="field.key">
      <el-input
        v-model="form[field.key]"
        :type="field.key === 'password' ? 'password' : 'text'"
        :show-password="field.key === 'password'"
        :maxlength="field.max"
        :autocomplete="field.key === 'password' ? 'new-password' : 'off'"
      />
    </el-form-item>
    <el-form-item :label="text('手机号码', 'Mobile number')" prop="mobile">
      <el-input v-model="form.mobile" maxlength="11" inputmode="tel" autocomplete="tel" />
    </el-form-item>
    <el-form-item :label="text('短信验证码', 'SMS code')" prop="smsCode">
      <div class="sms-row">
        <el-input
          v-model="sms.code"
          maxlength="6"
          inputmode="numeric"
          autocomplete="one-time-code"
        />
        <el-button
          :loading="smsLoading"
          :disabled="!available || (remaining > 0 && sms.deliveryStatus !== 'PENDING')"
          @click="begin('sms')"
        >
          {{
            sms.deliveryStatus === 'PENDING'
              ? text('确认发送状态', 'Check delivery')
              : remaining > 0
                ? remaining + 's'
                : text('获取验证码', 'Send code')
          }}
        </el-button>
      </div>
    </el-form-item>
    <p v-if="sms.deliveryStatus" class="registration-note" role="status">
      {{
        sms.deliveryStatus === 'SENT'
          ? text('验证码已发送，5 分钟内有效。', 'Code sent. Valid for 5 minutes.')
          : sms.deliveryStatus === 'PENDING'
            ? text(
                '发送处理中，请确认原请求状态。',
                'Delivery is pending. Check the original request.'
              )
            : text(
                '发送结果暂不确定，请勿重复发送；如已收到，可输入验证码。',
                'Delivery is uncertain. Use the received code, or recover the original request before resending.'
              )
      }}
    </p>
    <el-form-item :label="text('邀请码（选填）', 'Invite code (optional)')" prop="inviteCode">
      <el-input
        v-model="form.inviteCode"
        maxlength="10"
        clearable
        :placeholder="text('无效时可清空后继续', 'Clear an invalid code to continue')"
      />
    </el-form-item>
    <p class="registration-note">{{
      text(
        '推荐关系不会加入对方团队，也不会产生奖励或佣金。',
        'A referral does not join another team or create rewards or commission.'
      )
    }}</p>
    <el-alert
      v-if="registrationUncertain"
      :closable="false"
      type="info"
      :title="
        text(
          '上次注册结果待确认。请优先重试原请求，避免重复创建。',
          'The previous registration needs confirmation. Retry the original request first.'
        )
      "
    />
    <el-button
      v-if="registrationUncertain"
      class="w-full mb-12px"
      :loading="registerLoading"
      @click="begin('retry')"
    >
      {{ text('重试原注册请求', 'Retry original registration') }}
    </el-button>
    <el-button
      class="w-full"
      type="primary"
      :loading="registerLoading"
      :disabled="!available"
      @click="begin('register')"
    >
      {{ text('验证并创建租户', 'Verify and create workspace') }}
    </el-button>
    <el-button class="w-full mt-12px" @click="handleBackLogin">{{
      text('已有账号，返回登录', 'Already registered? Sign in')
    }}</el-button>
    <Verify
      v-if="captchaEnabled"
      ref="verify"
      captchaType="blockPuzzle"
      :imgSize="{ width: '400px', height: '200px' }"
      mode="pop"
      @success="perform"
    />
  </el-form>
</template>
<script setup lang="ts">
import { useLocaleStore } from '@/store/modules/locale'
import { useUserStore } from '@/store/modules/user'
import { deleteUserCache } from '@/hooks/web/useCache'
import * as auth from '@/utils/auth'
import {
  changePhone,
  applySmsChallenge,
  prepareSmsSend,
  registrationAttempt
} from '@/utils/tenantOnboarding'
import {
  getTenantRegistrationConfig,
  sendTenantRegistrationSms,
  registerTenant
} from '@/api/login/tenantRegistration'
import type {
  TenantRegistrationConfig,
  TenantRegistrationBody
} from '@/api/login/tenantRegistration'
import { LoginStateEnum, useLoginState } from './useLogin'

defineOptions({ name: 'RegisterForm' })
const locale = useLocaleStore()
const text = (zh: string, en: string) => (locale.getCurrentLocale.lang === 'en' ? en : zh)
const { getLoginState, handleBackLogin } = useLoginState()
const visible = computed(() => getLoginState.value === LoginStateEnum.REGISTER)
const router = useRouter()
const route = useRoute()
const formRef = ref()
const verify = ref()
const captchaEnabled = import.meta.env.VITE_APP_CAPTCHA_ENABLE !== 'false'
const config = ref<TenantRegistrationConfig>()
const available = computed(() => config.value?.enabled && config.value?.smsAvailable)
const form = reactive({
  tenantName: '',
  username: '',
  nickname: '',
  password: '',
  mobile: '',
  inviteCode: ''
})
const sms = ref(changePhone(undefined, ''))
const now = ref(Date.now())
const remaining = computed(() => Math.max(0, Math.ceil((sms.value.resendAt - now.value) / 1000)))
const smsLoading = ref(false)
const registerLoading = ref(false)
const registrationUncertain = ref(false)
let attempt: ReturnType<typeof registrationAttempt> | undefined
let pendingAction: 'sms' | 'register' | 'retry' = 'register'
const fields = computed(() => [
  { key: 'tenantName' as const, label: text('租户名称', 'Workspace name'), max: 63 },
  { key: 'username' as const, label: text('登录账号', 'Username'), max: 30 },
  { key: 'nickname' as const, label: text('姓名 / 昵称', 'Name / nickname'), max: 30 },
  {
    key: 'password' as const,
    label: text('密码（4–16 位）', 'Password (4–16 characters)'),
    max: 16
  }
])
const requiredRule = () => ({
  required: true,
  message: text('请填写此项', 'This field is required'),
  trigger: 'blur'
})
const rules = computed(() => ({
  tenantName: [requiredRule()],
  username: [
    requiredRule(),
    {
      pattern: /^[A-Za-z0-9]{4,30}$/,
      message: text('请输入 4–30 位字母或数字', 'Use 4–30 letters or numbers'),
      trigger: 'blur'
    }
  ],
  nickname: [requiredRule()],
  password: [
    requiredRule(),
    { min: 4, max: 16, message: text('密码长度为 4–16 位', 'Use 4–16 characters'), trigger: 'blur' }
  ],
  mobile: [
    requiredRule(),
    {
      pattern: /^1[3-9][0-9]{9}$/,
      message: text('请输入中国大陆手机号', 'Enter a mainland China mobile number'),
      trigger: 'blur'
    }
  ],
  inviteCode: [
    {
      pattern: /^$|^[A-Za-z0-9]{10}$/,
      message: text(
        '邀请码为 10 位字母数字，或清空继续',
        'Use a 10-character code, or clear it to continue'
      ),
      trigger: 'blur'
    }
  ],
  smsCode: [
    {
      validator: (_rule: unknown, _value: unknown, done: (error?: Error) => void) =>
        done(
          /^[0-9]{6}$/.test(sms.value.code) &&
            sms.value.smsRequestId &&
            sms.value.deliveryStatus !== 'PENDING' &&
            sms.value.expiresAt > Date.now()
            ? undefined
            : new Error(text('请获取并输入有效验证码', 'Request and enter a valid code'))
        ),
      trigger: 'blur'
    }
  ]
}))
watch(
  () => form.mobile,
  (mobile) => {
    sms.value = changePhone(sms.value, mobile.trim())
  },
  { flush: 'sync' }
)
watch(
  () => route.query.inviteCode,
  (value) => {
    if (typeof value === 'string') form.inviteCode = value.slice(0, 10)
  },
  { immediate: true }
)
const begin = async (action: typeof pendingAction) => {
  if (smsLoading.value || registerLoading.value || !available.value) return
  try {
    if (action === 'sms') await formRef.value.validateField('mobile')
    else if (action === 'register') await formRef.value.validate()
    pendingAction = action
    if (captchaEnabled) verify.value?.show?.()
    else await perform({})
  } catch {
    /* Validation is displayed beside the field. */
  }
}
const perform = async (captcha: { captchaVerification?: string }) => {
  if (pendingAction === 'sms') {
    smsLoading.value = true
    sms.value = prepareSmsSend(sms.value)
    const sent = { ...sms.value }
    try {
      const challenge = await sendTenantRegistrationSms({
        mobile: sent.mobile,
        requestId: sent.requestId,
        captchaVerification: captcha.captchaVerification || ''
      })
      if (sms.value.requestId === sent.requestId)
        sms.value = applySmsChallenge(sms.value, sent.mobile, challenge)
    } catch {
      /* Keep the original request ID on uncertain network results. */
    } finally {
      smsLoading.value = false
    }
    return
  }
  registerLoading.value = true
  try {
    if (pendingAction !== 'retry')
      attempt = registrationAttempt(attempt, {
        ...form,
        tenantName: form.tenantName.trim(),
        inviteCode: form.inviteCode.trim().toUpperCase(),
        smsRequestId: sms.value.smsRequestId,
        smsCode: sms.value.code
      })
    if (!attempt) return
    const result = await registerTenant({
      ...attempt.payload,
      requestId: attempt.requestId,
      captchaVerification: captcha.captchaVerification || ''
    } as TenantRegistrationBody)
    if (!result.tenantId) throw new Error('Missing tenant context')
    deleteUserCache()
    useUserStore().resetState()
    auth.setTenantId(result.tenantId)
    auth.removeLoginForm()
    auth.setToken(result)
    registrationUncertain.value = false
    await router.replace('/tk/dashboard')
  } catch {
    registrationUncertain.value = true
  } finally {
    registerLoading.value = false
  }
}
let ticker: ReturnType<typeof setInterval> | undefined
onMounted(async () => {
  ticker = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  try {
    config.value = await getTenantRegistrationConfig()
  } catch {
    /* Fail closed. */
  }
})
onBeforeUnmount(() => {
  if (ticker) clearInterval(ticker)
})
</script>
<style scoped>
.registration-note {
  margin: 12px 0;
  font-size: 12px;
  line-height: 1.65;
  opacity: 0.8;
}

.sms-row {
  display: flex;
  width: 100%;
  gap: 8px;
}

.sms-row .el-input {
  min-width: 0;
}

.login-form > .el-button + .el-button {
  margin-left: 0;
}

@media (width <= 480px) {
  .sms-row {
    flex-wrap: wrap;
  }

  .sms-row .el-button {
    width: 100%;
  }
}
</style>
