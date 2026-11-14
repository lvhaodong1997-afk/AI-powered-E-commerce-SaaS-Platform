<template>
  <ContentWrap>
    <el-result
      v-if="!tenantAdmin"
      icon="warning"
      :title="text('仅租户管理员可查看邀请推荐', 'Tenant administrator access required')"
    />
    <section v-else v-loading="loading" class="invite-page">
      <header
        ><h1>{{ text('邀请推荐', 'Invite and refer') }}</h1
        ><p>{{
          text(
            '分享你的专属推荐码，让对方创建自己的工作空间。',
            'Share your referral code so others can create their own workspace.'
          )
        }}</p></header
      >
      <el-alert
        :closable="false"
        type="info"
        :title="
          text(
            '邀请码仅建立推荐关系。对方注册独立租户，不加入你的团队，不产生奖励或佣金。',
            'Invites only record a referral. Registrants create independent tenants, do not join your team, and receive no rewards or commissions.'
          )
        "
      />
      <el-alert v-if="error" class="mt-16px" :closable="false" type="error" :title="error" />
      <el-card v-if="invite" class="mt-24px" shadow="never">
        <div class="invite-code">{{ invite.inviteCode }}</div>
        <el-input
          :model-value="invite.inviteUrl"
          readonly
          :aria-label="text('邀请链接', 'Invite link')"
        />
        <div class="invite-actions">
          <el-button type="primary" @click="copy(invite.inviteUrl)">{{
            text('复制邀请链接', 'Copy invite link')
          }}</el-button>
          <el-button @click="copy(invite.inviteCode)">{{
            text('复制推荐码', 'Copy referral code')
          }}</el-button>
          <el-button @click="share">{{ text('分享', 'Share') }}</el-button>
        </div>
        <p
          >{{ text('已推荐注册租户', 'Referred tenants') }}：<strong>{{
            invite.invitedTenantCount
          }}</strong></p
        >
      </el-card>
      <h2>{{ text('推荐记录', 'Referral records') }}</h2>
      <el-table :data="records" stripe>
        <el-table-column
          prop="tenantName"
          :label="text('租户名称（已脱敏）', 'Tenant name (masked)')"
        />
        <el-table-column prop="registerTime" :label="text('注册时间', 'Registered at')" />
      </el-table>
      <Pagination
        v-model:page="pageNo"
        v-model:limit="pageSize"
        :total="total"
        @pagination="loadRecords"
      />
      <el-button class="mt-16px" @click="load">{{ text('刷新记录', 'Refresh records') }}</el-button>
    </section>
  </ContentWrap>
</template>
<script setup lang="ts">
import { isTenantAdministrator } from '@/utils/tenantOnboarding'
import { useLocaleStore } from '@/store/modules/locale'
import { useUserStore } from '@/store/modules/user'
import { getMyTenantInvite, getTenantInviteRecords } from '@/api/system/tenantInvite'
import type { MyTenantInvite, TenantInviteRecord } from '@/api/system/tenantInvite'
defineOptions({ name: 'TkInvite' })
const locale = useLocaleStore()
const text = (zh: string, en: string) => (locale.getCurrentLocale.lang === 'en' ? en : zh)
const tenantAdmin = computed(() => isTenantAdministrator(useUserStore().getUser.tkUserLevel))
const message = useMessage()
const invite = ref<MyTenantInvite>()
const records = ref<TenantInviteRecord[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const error = ref('')
const loadRecords = async () => {
  if (!tenantAdmin.value) return
  const result = await getTenantInviteRecords({ pageNo: pageNo.value, pageSize: pageSize.value })
  records.value = result.list
  total.value = result.total
}
const load = async () => {
  if (!tenantAdmin.value) return
  loading.value = true
  error.value = ''
  try {
    const [result] = await Promise.all([getMyTenantInvite(), loadRecords()])
    invite.value = result
  } catch {
    error.value = text(
      '暂时无法加载推荐信息，请刷新。',
      'Unable to load referral details. Please refresh.'
    )
  } finally {
    loading.value = false
  }
}
const copy = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value)
    message.success(text('已复制', 'Copied'))
  } catch {
    message.warning(
      text(
        '复制失败，请在输入框中手动复制链接。',
        'Copy failed. Select and copy the link manually.'
      )
    )
  }
}
const share = async () => {
  if (!invite.value) return
  try {
    const url = new URL(invite.value.inviteUrl)
    if (url.protocol !== 'https:' || url.username || url.password) {
      await copy(invite.value.inviteUrl)
      return
    }
    if (navigator.share) await navigator.share({ title: 'ClipForge Studio', url: url.href })
    else await copy(url.href)
  } catch {
    /* Dismissing the native share sheet does not change referral data. */
  }
}
onMounted(load)
</script>
<style scoped>
.invite-page {
  max-width: 900px;
  margin: 0 auto;
}

.invite-page h1 {
  margin: 0 0 12px;
  font-size: 24px;
}

.invite-page header p {
  margin-bottom: 24px;
  color: var(--el-text-color-secondary);
}

.invite-code {
  margin: 0 0 20px;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.08em;
  overflow-wrap: anywhere;
}

.invite-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.invite-actions .el-button + .el-button {
  margin-left: 0;
}

.invite-page h2 {
  margin-top: 32px;
  font-size: 18px;
}

@media (width <= 480px) {
  .invite-actions .el-button {
    width: 100%;
  }
}
</style>
