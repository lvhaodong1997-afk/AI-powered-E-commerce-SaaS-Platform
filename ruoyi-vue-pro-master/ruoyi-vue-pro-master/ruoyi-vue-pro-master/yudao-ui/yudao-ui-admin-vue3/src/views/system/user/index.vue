<template>

  <el-row :gutter="20">
    <el-col :span="24">
      <!-- 搜索 -->
      <ContentWrap>
        <el-form
          class="-mb-15px"
          :model="queryParams"
          ref="queryFormRef"
          :inline="true"
          label-width="68px"
        >
          <el-form-item :label="copy.username" prop="username">
            <el-input
              v-model="queryParams.username"
              :placeholder="copy.usernamePlaceholder"
              clearable
              @keyup.enter="handleQuery"
              class="!w-240px"
            />
          </el-form-item>
          <el-form-item :label="copy.mobile" prop="mobile">
            <el-input
              v-model="queryParams.mobile"
              :placeholder="copy.mobilePlaceholder"
              clearable
              @keyup.enter="handleQuery"
              class="!w-240px"
            />
          </el-form-item>
          <el-form-item :label="copy.status" prop="status">
            <el-select
              v-model="queryParams.status"
              :placeholder="copy.statusPlaceholder"
              clearable
              class="!w-240px"
            >
              <el-option
                v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
                :key="dict.value"
                :label="translateStatusLabel(dict.label)"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="copy.tenant" prop="tenantId">
            <el-select
              v-model="queryParams.tenantId"
              :placeholder="copy.tenantPlaceholder"
              clearable
              filterable
              class="!w-240px"
            >
              <el-option
                v-for="tenant in tenantList"
                :key="tenant.id"
                :label="tenant.name"
                :value="tenant.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="copy.createTime" prop="createTime">
            <el-date-picker
              v-model="queryParams.createTime"
              value-format="YYYY-MM-DD HH:mm:ss"
              type="datetimerange"
              :start-placeholder="copy.startDate"
              :end-placeholder="copy.endDate"
              class="!w-240px"
            />
          </el-form-item>
          <el-form-item>
            <el-button @click="handleQuery"><Icon icon="ep:search" />{{ t('common.query') }}</el-button>
            <el-button @click="resetQuery"><Icon icon="ep:refresh" />{{ t('common.reset') }}</el-button>
            <el-button
              type="primary"
              plain
              @click="openForm('create')"
              v-hasPermi="['system:user:create']"
            >
              <Icon icon="ep:plus" /> {{ t('action.create') }}
            </el-button>
            <el-button
              type="warning"
              plain
              @click="handleImport"
              v-hasPermi="['system:user:import']"
            >
              <Icon icon="ep:upload" /> {{ t('action.import') }}
            </el-button>
            <el-button
              type="success"
              plain
              @click="handleExport"
              :loading="exportLoading"
              v-hasPermi="['system:user:export']"
            >
              <Icon icon="ep:download" />{{ t('action.export') }}
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="checkedIds.length === 0"
              @click="handleDeleteBatch"
              v-hasPermi="['system:user:delete']"
            >
              <Icon icon="ep:delete" />{{ copy.batchDelete }}
            </el-button>
          </el-form-item>
        </el-form>
      </ContentWrap>
      <ContentWrap>
        <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
          <el-table-column type="selection" width="55" />
          <el-table-column :label="copy.userId" align="center" key="id" prop="id" />
          <el-table-column
            :label="copy.username"
            align="center"
            prop="username"
            :show-overflow-tooltip="true"
          />
          <el-table-column
            :label="copy.nickname"
            align="center"
            prop="nickname"
            :show-overflow-tooltip="true"
          />
          <el-table-column :label="copy.mobile" align="center" prop="mobile" width="120" />
          <el-table-column :label="copy.status" key="status">
            <template #default="scope">
              <el-switch
                v-model="scope.row.status"
                :active-value="0"
                :inactive-value="1"
                @change="handleStatusChange(scope.row)"
                :disabled="!checkPermi(['system:user:update'])"
              />
            </template>
          </el-table-column>
          <el-table-column
            :label="copy.createTime"
            align="center"
            prop="createTime"
            :formatter="dateFormatter"
            width="180"
          />
          <el-table-column :label="copy.operation" align="center" width="160">
            <template #default="scope">
              <div class="flex items-center justify-center">
                <el-button
                  type="primary"
                  link
                  @click="openForm('update', scope.row.id)"
                  v-hasPermi="['system:user:update']"
                >
                  <Icon icon="ep:edit" />{{ t('action.edit') }}
                </el-button>
                <el-dropdown
                  @command="(command) => handleCommand(command, scope.row)"
                  v-hasPermi="[
                    'system:user:delete',
                    'system:user:update-password',
                    'system:permission:assign-user-role'
                  ]"
                >
                  <el-button type="primary" link><Icon icon="ep:d-arrow-right" /> {{ t('action.more') }}</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        command="handleDelete"
                        v-if="checkPermi(['system:user:delete'])"
                      >
                        <Icon icon="ep:delete" />{{ t('action.delete') }}
                      </el-dropdown-item>
                      <el-dropdown-item
                        command="handleResetPwd"
                        v-if="checkPermi(['system:user:update-password'])"
                      >
                        <Icon icon="ep:key" />{{ copy.resetPassword }}
                      </el-dropdown-item>
                      <el-dropdown-item
                        command="handleRole"
                        v-if="checkPermi(['system:permission:assign-user-role'])"
                      >
                        <Icon icon="ep:circle-check" />{{ copy.assignRole }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="total"
          v-model:page="queryParams.pageNo"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </ContentWrap>
    </el-col>
  </el-row>

  <!-- 添加或修改用户对话框 -->
  <UserForm ref="formRef" @success="getList" />
  <!-- 用户导入对话框 -->
  <UserImportForm ref="importFormRef" @success="getList" />
  <!-- 分配角色 -->
  <UserAssignRoleForm ref="assignRoleFormRef" @success="getList" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { checkPermi } from '@/utils/permission'
import { dateFormatter } from '@/utils/formatTime'
import download from '@/utils/download'
import { CommonStatusEnum } from '@/utils/constants'
import * as UserApi from '@/api/system/user'
import * as TenantApi from '@/api/system/tenant'
import UserForm from './UserForm.vue'
import UserImportForm from './UserImportForm.vue'
import UserAssignRoleForm from './UserAssignRoleForm.vue'
import { useLocaleStore } from '@/store/modules/locale'

defineOptions({ name: 'SystemUser' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化
const localeStore = useLocaleStore()
const currentLocale = computed(() => localeStore.getCurrentLocale.lang)

const pageCopy = {
  en: {
    username: 'Username',
    usernamePlaceholder: 'Please enter username',
    mobile: 'Phone Number',
    mobilePlaceholder: 'Please enter phone number',
    status: 'Status',
    statusPlaceholder: 'Please select user status',
    tenant: 'Tenant',
    tenantPlaceholder: 'Please select tenant',
    createTime: 'Created Time',
    startDate: 'Start date',
    endDate: 'End date',
    batchDelete: 'Batch Delete',
    userId: 'User ID',
    nickname: 'Nickname',
    operation: 'Actions',
    resetPassword: 'Reset Password',
    assignRole: 'Assign Roles',
    enable: 'enable',
    disable: 'disable',
    statusConfirm: (action: string, username: string) =>
      `Are you sure you want to ${action} user "${username}"?`,
    newPasswordPrompt: (username: string) => `Please enter the new password for "${username}"`,
    resetPasswordSuccess: (password: string) => `Password changed successfully. New password: ${password}`,
    exportFileName: 'User Data.xls',
    statusMap: {
      开启: 'Enabled',
      启用: 'Enabled',
      正常: 'Enabled',
      关闭: 'Disabled',
      禁用: 'Disabled',
      停用: 'Disabled'
    }
  },
  zh: {
    username: '用户名称',
    usernamePlaceholder: '请输入用户名称',
    mobile: '手机号码',
    mobilePlaceholder: '请输入手机号码',
    status: '状态',
    statusPlaceholder: '请选择用户状态',
    tenant: '所属租户',
    tenantPlaceholder: '请选择所属租户',
    createTime: '创建时间',
    startDate: '开始日期',
    endDate: '结束日期',
    batchDelete: '批量删除',
    userId: '用户编号',
    nickname: '用户昵称',
    operation: '操作',
    resetPassword: '重置密码',
    assignRole: '分配角色',
    enable: '启用',
    disable: '停用',
    statusConfirm: (action: string, username: string) => `确认要"${action}""${username}"用户吗?`,
    newPasswordPrompt: (username: string) => `请输入"${username}"的新密码`,
    resetPasswordSuccess: (password: string) => `修改成功，新密码是：${password}`,
    exportFileName: '用户数据.xls',
    statusMap: {}
  }
}

const copy = computed(() => (currentLocale.value === 'en' ? pageCopy.en : pageCopy.zh))

const translateStatusLabel = (label: string) => copy.value.statusMap[label] || label

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  username: undefined,
  mobile: undefined,
  status: undefined,
  tenantId: undefined,
  createTime: []
})
const queryFormRef = ref() // 搜索的表单
const tenantList = ref([] as TenantApi.TenantVO[])

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await UserApi.getUserPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

/** 用户导入 */
const importFormRef = ref()
const handleImport = () => {
  importFormRef.value.open()
}

/** 修改用户状态 */
const handleStatusChange = async (row: UserApi.UserVO) => {
  try {
    // 修改状态的二次确认
    const text = row.status === CommonStatusEnum.ENABLE ? copy.value.enable : copy.value.disable
    await message.confirm(copy.value.statusConfirm(text, row.username))
    // 发起修改状态
    await UserApi.updateUserStatus(row.id, row.status)
    // 刷新列表
    await getList()
  } catch {
    // 取消后，进行恢复按钮
    row.status =
      row.status === CommonStatusEnum.ENABLE ? CommonStatusEnum.DISABLE : CommonStatusEnum.ENABLE
  }
}

/** 导出按钮操作 */
const exportLoading = ref(false)
const handleExport = async () => {
  try {
    // 导出的二次确认
    await message.exportConfirm()
    // 发起导出
    exportLoading.value = true
    const data = await UserApi.exportUser(queryParams)
    download.excel(data, copy.value.exportFileName)
  } catch {
  } finally {
    exportLoading.value = false
  }
}

/** 操作分发 */
const handleCommand = (command: string, row: UserApi.UserVO) => {
  switch (command) {
    case 'handleDelete':
      handleDelete(row.id)
      break
    case 'handleResetPwd':
      handleResetPwd(row)
      break
    case 'handleRole':
      handleRole(row)
      break
    default:
      break
  }
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await UserApi.deleteUser(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 批量删除按钮操作 */
const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows: UserApi.UserVO[]) => {
  checkedIds.value = rows.map((row) => row.id)
}

const handleDeleteBatch = async () => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起批量删除
    await UserApi.deleteUserList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 重置密码 */
const handleResetPwd = async (row: UserApi.UserVO) => {
  try {
    // 重置的二次确认
    const result = await message.prompt(copy.value.newPasswordPrompt(row.username), t('common.reminder'))
    const password = result.value
    // 发起重置
    await UserApi.resetUserPassword(row.id, password)
    message.success(copy.value.resetPasswordSuccess(password))
  } catch {}
}

/** 分配角色 */
const assignRoleFormRef = ref()
const handleRole = (row: UserApi.UserVO) => {
  assignRoleFormRef.value.open(row)
}

/** 初始化 */
onMounted(() => {
  getList()
  TenantApi.getTenantList().then((data) => {
    tenantList.value = data
  })
})
</script>
