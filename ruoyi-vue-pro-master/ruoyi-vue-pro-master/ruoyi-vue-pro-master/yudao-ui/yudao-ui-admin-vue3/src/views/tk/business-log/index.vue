<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      label-width="80px"
      class="-mb-15px"
    >
      <el-form-item label="流水号" prop="businessTraceId">
        <el-input
          v-model="queryParams.businessTraceId"
          placeholder="请输入业务流水号"
          clearable
          class="!w-260px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="业务类型" prop="bizType">
        <el-input
          v-model="queryParams.bizType"
          placeholder="请输入业务类型"
          clearable
          class="!w-180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="业务ID" prop="bizId">
        <el-input-number v-model="queryParams.bizId" :min="1" controls-position="right" class="!w-160px" />
      </el-form-item>
      <el-form-item label="级别" prop="level">
        <el-select v-model="queryParams.level" placeholder="请选择级别" clearable class="!w-140px">
          <el-option label="信息" value="INFO" />
          <el-option label="警告" value="WARN" />
          <el-option label="错误" value="ERROR" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-140px">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="处理中" value="PROCESSING" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作人" prop="operatorId">
        <el-input-number v-model="queryParams.operatorId" :min="1" controls-position="right" class="!w-160px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="流水号" prop="businessTraceId" min-width="220" show-overflow-tooltip />
      <el-table-column label="业务" min-width="180">
        <template #default="scope">
          <div class="log-main">{{ scope.row.bizType || '-' }}</div>
          <div class="log-sub">ID: {{ scope.row.bizId || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="动作" prop="action" min-width="160" show-overflow-tooltip />
      <el-table-column label="级别" width="100">
        <template #default="scope">
          <el-tag :type="levelTagType(scope.row.level)">{{ levelLabel(scope.row.level) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作人" prop="operatorId" width="110" />
      <el-table-column label="消息" prop="message" min-width="260" show-overflow-tooltip />
      <el-table-column label="详情" prop="detailJson" min-width="260" show-overflow-tooltip />
      <el-table-column label="创建时间" prop="createTime" width="180" />
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { TkBusinessLogApi } from '@/api/tk/businessLog'
import type { TkBusinessLogVO } from '@/api/tk/businessLog'

defineOptions({ name: 'TkBusinessLog' })

const loading = ref(false)
const list = ref<TkBusinessLogVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  businessTraceId: undefined,
  bizType: undefined,
  bizId: undefined,
  level: undefined,
  action: undefined,
  status: undefined,
  operatorId: undefined
})

const levelLabel = (level?: string) => {
  const map: Record<string, string> = { INFO: '信息', WARN: '警告', ERROR: '错误' }
  return level ? map[level] || level : '-'
}

const levelTagType = (level?: string) => {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}

const statusLabel = (status?: string) => {
  const map: Record<string, string> = { SUCCESS: '成功', FAILED: '失败', PROCESSING: '处理中' }
  return status ? map[status] || status : '-'
}

const statusTagType = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'warning'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await TkBusinessLogApi.getPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

onMounted(getList)
</script>

<style scoped>
.log-main {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.log-sub {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
