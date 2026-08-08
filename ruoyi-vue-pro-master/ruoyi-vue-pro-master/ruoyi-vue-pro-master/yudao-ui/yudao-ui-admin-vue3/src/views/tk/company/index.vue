<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      label-width="80px"
      class="-mb-15px"
    >
      <el-form-item label="公司名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入公司名称"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-160px">
          <el-option label="启用" :value="0" />
          <el-option label="禁用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm()" v-hasPermi="['tk:company:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增公司
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="公司名称" prop="name" min-width="180" />
      <el-table-column label="联系人" prop="contactName" width="140" />
      <el-table-column label="联系电话" prop="contactPhone" width="160" />
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">
            {{ scope.row.status === 0 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="180" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openForm(scope.row)" v-hasPermi="['tk:company:update']">
            编辑
          </el-button>
          <el-button link type="danger" @click="handleDelete(scope.row.id)" v-hasPermi="['tk:company:delete']">
            删除
          </el-button>
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

  <Dialog v-model="dialogVisible" :title="formData.id ? '编辑公司' : '新增公司'">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="90px">
      <el-form-item label="公司名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入公司名称" maxlength="128" />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input v-model="formData.contactName" placeholder="请输入联系人" maxlength="64" />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" maxlength="32" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio :label="0">启用</el-radio>
          <el-radio :label="1">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确定</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { TkCompanyApi } from '@/api/tk/videoPublishCenter'
import type { TkCompanyVO } from '@/api/tk/videoPublishCenter'
import type { FormRules } from 'element-plus'

defineOptions({ name: 'TkCompany' })

const message = useMessage()
const loading = ref(false)
const list = ref<TkCompanyVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  status: undefined
})

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const formData = ref<TkCompanyVO>({
  name: '',
  status: 0,
  contactName: '',
  contactPhone: ''
})
const formRules = reactive<FormRules>({
  name: [{ required: true, message: '公司名称不能为空', trigger: 'blur' }]
})

const getList = async () => {
  loading.value = true
  try {
    const data = await TkCompanyApi.getPage(queryParams)
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

const openForm = (row?: TkCompanyVO) => {
  formData.value = row
    ? { ...row }
    : {
        name: '',
        status: 0,
        contactName: '',
        contactPhone: ''
      }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const submitForm = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return
  formLoading.value = true
  try {
    if (formData.value.id) {
      await TkCompanyApi.update(formData.value)
      message.success('更新成功')
    } else {
      await TkCompanyApi.create(formData.value)
      message.success('创建成功')
    }
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

const handleDelete = async (id: number) => {
  await message.delConfirm()
  await TkCompanyApi.delete(id)
  message.success('删除成功')
  await getList()
}

onMounted(getList)
</script>
