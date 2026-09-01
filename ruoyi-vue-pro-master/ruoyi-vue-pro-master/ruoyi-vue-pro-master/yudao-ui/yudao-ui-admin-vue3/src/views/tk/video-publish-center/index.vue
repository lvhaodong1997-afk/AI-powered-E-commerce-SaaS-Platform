<template>
  <div class="publish-center">
    <div class="overview-grid">
      <ContentWrap v-for="item in overviewCards" :key="item.label">
        <div class="overview-card">
          <Icon :icon="item.icon" />
          <div>
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </ContentWrap>
    </div>

    <ContentWrap>
      <div class="toolbar">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="待发布视频" name="videos" />
          <el-tab-pane label="账号矩阵" name="accounts" />
          <el-tab-pane label="发布任务" name="tasks" />
          <el-tab-pane label="发布明细" name="details" />
        </el-tabs>
        <div class="toolbar-actions">
          <el-button @click="refreshAll"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
          <el-button type="primary" plain @click="startRedirectAuth" v-hasPermi="['tk:tiktok-account:authorize']">
            <Icon icon="ep:link" class="mr-5px" /> 官方授权
          </el-button>
          <el-button type="success" plain @click="startQrAuth" v-hasPermi="['tk:tiktok-account:authorize']">
            <Icon icon="ep:full-screen" class="mr-5px" /> 二维码授权
          </el-button>
          <el-button type="primary" @click="openUploadPublishDrawer" v-hasPermi="['tk:tiktok-publish:create']">
            <Icon icon="ep:upload" class="mr-5px" /> 上传视频发布
          </el-button>
        </div>
      </div>
    </ContentWrap>


    <ContentWrap v-if="activeTab === 'videos'">
      <el-form :model="videoQuery" ref="videoQueryFormRef" :inline="true" label-width="90px" class="-mb-15px">
        <el-form-item label="任务标题" prop="title">
          <el-input v-model="videoQuery.title" placeholder="任务标题" clearable class="!w-220px" @keyup.enter="getVideoList" />
        </el-form-item>
        <el-form-item>
          <el-button @click="handleVideoQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
          <el-button @click="resetVideoQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="videoLoading" :data="videoList" stripe>
        <el-table-column label="成片任务" min-width="240">
          <template #default="scope">
            <div class="main-title">{{ scope.row.title || 'Task #' + scope.row.id }}</div>
            <div class="trace-line">
              <span>流水号：{{ scope.row.businessTraceId || '-' }}</span>
              <el-button
                v-if="scope.row.businessTraceId"
                link
                type="primary"
                @click="copyBusinessTraceId(scope.row.businessTraceId)"
              >
                复制
              </el-button>
            </div>
            <div class="sub-text">{{ displayVideoFileName(scope.row.outputUrl) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="素材库ID" prop="libraryId" width="100" />
        <el-table-column label="目标时长" width="100">
          <template #default="scope">{{ scope.row.targetDuration || scope.row.referenceDuration || '-' }}s</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="scope">{{ formatTimestamp(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openPublishDrawer(scope.row)" v-hasPermi="['tk:tiktok-publish:create']">发布</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="videoTotal" v-model:page="videoQuery.pageNo" v-model:limit="videoQuery.pageSize" @pagination="getVideoList" />
    </ContentWrap>


    <ContentWrap v-if="activeTab === 'accounts'">
      <el-form :model="accountQuery" ref="accountQueryFormRef" :inline="true" label-width="90px" class="-mb-15px">
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="accountQuery.keyword" placeholder="名称 / 用户名 / 标签" clearable class="!w-240px" @keyup.enter="getAccountList" />
        </el-form-item>
        <el-form-item label="Token" prop="tokenStatus">
          <el-select v-model="accountQuery.tokenStatus" clearable placeholder="Token状态" class="!w-160px">
            <el-option label="有效" value="VALID" />
            <el-option label="自动刷新中" value="AUTO_REFRESH" />
            <el-option label="需重新授权" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleAccountQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
          <el-button @click="resetAccountQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
          <el-button type="primary" plain @click="openGroupForm()" v-hasPermi="['tk:tiktok-account-group:manage']">
            <Icon icon="ep:plus" class="mr-5px" /> 新建分组
          </el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="accountLoading" :data="accountList" stripe>
        <el-table-column label="账号" min-width="220">
          <template #default="scope">
            <div class="account-cell">
              <el-avatar :src="scope.row.avatarUrl" :size="34">
                {{ accountInitial(scope.row) }}
              </el-avatar>
              <div>
                <div class="main-title">{{ accountDisplayName(scope.row) }}</div>
                <div class="sub-text">{{ scope.row.username || scope.row.openId }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="授权" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.authStatus === 'AUTHORIZED' ? 'success' : 'info'">{{ authStatusLabel(scope.row.authStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Token" width="110">
          <template #default="scope">
            <el-tag :type="tokenStatusType(scope.row.tokenStatus)">{{ tokenStatusLabel(scope.row.tokenStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Access Token 到期" width="170">
          <template #default="scope">{{ formatTimestamp(scope.row.accessTokenExpireTime) }}</template>
        </el-table-column>
        <el-table-column label="默认隐私" prop="defaultPrivacyLevel" width="130" />
        <el-table-column label="默认开关" min-width="170">
          <template #default="scope">
            <div class="switch-tags">
              <el-tag size="small" :type="scope.row.allowComment ? 'success' : 'info'">评论</el-tag>
              <el-tag size="small" :type="scope.row.allowDuet ? 'success' : 'info'">合拍</el-tag>
              <el-tag size="small" :type="scope.row.allowStitch ? 'success' : 'info'">拼接</el-tag>
              <el-tag size="small" :type="scope.row.aigcContent ? 'warning' : 'info'">AIGC</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标签" prop="labels" min-width="150" show-overflow-tooltip />
        <el-table-column label="失败原因" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ failReasonLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openAccountConfig(scope.row)" v-hasPermi="['tk:tiktok-account:update']">配置</el-button>
            <el-button
              v-if="canUnbindAccount(scope.row)"
              link
              type="danger"
              @click="unbindAccount(scope.row)"
              v-hasPermi="['tk:tiktok-account:update']"
            >
              解绑
            </el-button>
            <el-button
              link
              type="danger"
              @click="deleteAccount(scope.row)"
              v-hasPermi="['tk:tiktok-account:update']"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="accountTotal" v-model:page="accountQuery.pageNo" v-model:limit="accountQuery.pageSize" @pagination="getAccountList" />

      <el-divider />
      <div class="section-head">
        <h3>账号分组</h3>
      </div>
      <el-table v-loading="groupLoading" :data="groupList" stripe>
        <el-table-column label="分组" min-width="180">
          <template #default="scope">
            <div class="main-title">{{ scope.row.name }}</div>
            <div class="sub-text">{{ scope.row.scene || '未设置场景' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="账号数" prop="accountCount" width="90" />
        <el-table-column label="标签" prop="labels" min-width="160" />
        <el-table-column label="备注" prop="remark" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openGroupForm(scope.row)" v-hasPermi="['tk:tiktok-account-group:manage']">编辑</el-button>
            <el-button link type="danger" @click="deleteGroup(scope.row.id)" v-hasPermi="['tk:tiktok-account-group:manage']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="groupTotal" v-model:page="groupQuery.pageNo" v-model:limit="groupQuery.pageSize" @pagination="getGroupList" />
    </ContentWrap>


    <ContentWrap v-if="activeTab === 'tasks'">
      <el-form :model="taskQuery" ref="taskQueryFormRef" :inline="true" label-width="90px" class="-mb-15px">
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="taskQuery.keyword" placeholder="标题 / 文案" clearable class="!w-240px" @keyup.enter="getTaskList" />
        </el-form-item>
        <el-form-item label="流水号" prop="businessTraceId">
          <el-input v-model="taskQuery.businessTraceId" placeholder="请输入业务流水号" clearable class="!w-260px" @keyup.enter="getTaskList" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="taskQuery.status" clearable placeholder="请选择状态" class="!w-180px">
            <el-option v-for="status in publishStatuses" :key="status" :label="publishStatusLabel(status)" :value="status" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleTaskQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
          <el-button @click="resetTaskQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="taskLoading" :data="taskList" stripe>
        <el-table-column label="发布任务" min-width="240">
          <template #default="scope">
            <div class="main-title">{{ scope.row.title }}</div>
            <div class="trace-line">
              <span>流水号：{{ scope.row.businessTraceId || '-' }}</span>
              <el-button
                v-if="scope.row.businessTraceId"
                link
                type="primary"
                @click="copyBusinessTraceId(scope.row.businessTraceId)"
              >
                复制
              </el-button>
            </div>
            <div class="sub-text">{{ displayVideoFileName(scope.row.videoUrl) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="模式" width="140">
          <template #default="scope">{{ postModeLabel(scope.row.postMode) }}</template>
        </el-table-column>
        <el-table-column label="账号" width="170">
          <template #default="scope">
            {{ scope.row.accountCount }} / 成功 {{ scope.row.successCount }} / 失败 {{ scope.row.failedCount }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="scope">
            <el-tag :type="publishStatusType(scope.row.status)">{{ publishStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ failReasonLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="scope">{{ formatTimestamp(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openTaskDetails(scope.row)">明细</el-button>
            <el-button link type="success" @click="syncTask(scope.row.id)">同步</el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="taskTotal" v-model:page="taskQuery.pageNo" v-model:limit="taskQuery.pageSize" @pagination="getTaskList" />
    </ContentWrap>


    <ContentWrap v-if="activeTab === 'details'">
      <el-form :model="detailQuery" ref="detailQueryFormRef" :inline="true" label-width="90px" class="-mb-15px">
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="detailQuery.keyword" placeholder="账号 / Publish ID" clearable class="!w-240px" @keyup.enter="getDetailList" />
        </el-form-item>
        <el-form-item label="流水号" prop="businessTraceId">
          <el-input v-model="detailQuery.businessTraceId" placeholder="请输入业务流水号" clearable class="!w-260px" @keyup.enter="getDetailList" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="detailQuery.status" clearable placeholder="请选择状态" class="!w-180px">
            <el-option v-for="status in publishStatuses" :key="status" :label="publishStatusLabel(status)" :value="status" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleDetailQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
          <el-button @click="resetDetailQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="detailLoading" :data="detailList" stripe>
        <el-table-column label="账号" prop="accountDisplayName" min-width="160" />
        <el-table-column label="流水号" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.businessTraceId || '-' }}</span>
            <el-button
              v-if="scope.row.businessTraceId"
              link
              type="primary"
              @click="copyBusinessTraceId(scope.row.businessTraceId)"
            >
              复制
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="任务ID" prop="publishTaskId" width="90" />
        <el-table-column label="模式" width="130">
          <template #default="scope">{{ postModeLabel(scope.row.postMode) }}</template>
        </el-table-column>
        <el-table-column label="TikTok状态" prop="tiktokStatus" width="150">
          <template #default="scope">{{ tiktokStatusLabel(scope.row.tiktokStatus) }}</template>
        </el-table-column>
        <el-table-column label="本地状态" width="120">
          <template #default="scope">
            <el-tag :type="publishStatusType(scope.row.status)">{{ publishStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Publish ID" prop="publishId" min-width="180" show-overflow-tooltip />
        <el-table-column label="发布链接" min-width="240" show-overflow-tooltip>
          <template #default="scope">
            <div v-if="scope.row.publishUrl" class="publish-url-cell">
              <el-link :href="scope.row.publishUrl" target="_blank" type="primary">
                {{ scope.row.publishUrl }}
              </el-link>
              <el-button link type="primary" @click="openPublishUrlDialog(scope.row)">编辑</el-button>
            </div>
            <el-button v-else link type="primary" @click="openPublishUrlDialog(scope.row)">
              登记链接
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="240" show-overflow-tooltip>
          <template #default="scope">{{ failReasonLabel(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="重试" prop="retryCount" width="80" />
        <el-table-column label="同步时间" width="170">
          <template #default="scope">{{ formatTimestamp(scope.row.lastSyncTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="warning"
              :loading="retryingDetailIds.has(scope.row.id)"
              :disabled="scope.row.status !== 'FAILED' || retryingDetailIds.has(scope.row.id)"
              @click="retryDetail(scope.row)"
              v-hasPermi="['tk:tiktok-publish:retry']"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination :total="detailTotal" v-model:page="detailQuery.pageNo" v-model:limit="detailQuery.pageSize" @pagination="getDetailList" />
    </ContentWrap>

    <el-dialog v-model="publishUrlDialogVisible" title="登记发布链接" width="560px">
      <el-form ref="publishUrlFormRef" :model="publishUrlForm" :rules="publishUrlRules" label-width="110px">
        <el-form-item label="生成任务ID">
          <el-input :model-value="publishUrlForm.generationTaskId" disabled />
        </el-form-item>
        <el-form-item label="发布明细ID">
          <el-input :model-value="publishUrlForm.publishDetailId || '-'" disabled />
        </el-form-item>
        <el-form-item label="发布链接" prop="publishUrl">
          <el-input v-model="publishUrlForm.publishUrl" placeholder="请输入 TikTok 发布链接" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishUrlDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishUrlSubmitting" @click="submitPublishUrl">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="publishDrawerVisible" title="发布到 TikTok" size="520px" @closed="resetPublishSource">
      <el-form ref="publishFormRef" :model="publishForm" :rules="publishRules" label-width="110px">
        <el-form-item label="视频来源">
          <el-segmented v-model="publishForm.sourceType" :options="[{ label: '系统成片', value: 'GENERATED' }, { label: '用户上传', value: 'UPLOADED' }]" />
        </el-form-item>
        <el-form-item v-if="publishForm.sourceType === 'UPLOADED'" label="上传视频">
          <input ref="publishFileInput" type="file" accept="video/mp4,video/quicktime,video/webm" class="hidden" @change="handlePublishFileChange" />
          <el-button type="primary" plain :loading="publishUploadLoading" @click="publishFileInput?.click()">
            <Icon icon="ep:upload" class="mr-5px" /> 选择视频
          </el-button>
          <span v-if="uploadedPublishMedia" class="sub-text">{{ uploadedPublishMedia.fileName }}</span>
          <el-progress v-if="publishUploadLoading || publishUploadPercent > 0" :percentage="publishUploadPercent" />
          <span v-if="publishUploadError" class="upload-error">{{ publishUploadError }}</span>
        </el-form-item>
        <el-form-item v-if="publishForm.sourceType === 'GENERATED'" label="成片任务">
          <div>
            <div class="main-title">{{ selectedVideo?.title || '-' }}</div>
            <div class="trace-line">
              <span>流水号：{{ selectedVideo?.businessTraceId || '-' }}</span>
              <el-button
                v-if="selectedVideo?.businessTraceId"
                link
                type="primary"
                @click="copyBusinessTraceId(selectedVideo.businessTraceId)"
              >
                复制
              </el-button>
            </div>
            <div class="sub-text">{{ displayVideoFileName(selectedVideo?.outputUrl) }}</div>
          </div>
        </el-form-item>
        <el-form-item label="发布标题" prop="title">
          <el-input v-model="publishForm.title" maxlength="255" />
        </el-form-item>
        <el-form-item label="发布文案" prop="caption">
          <el-input v-model="publishForm.caption" type="textarea" :rows="4" maxlength="2200" show-word-limit />
        </el-form-item>
        <el-form-item label="发布账号">
          <el-select v-model="publishForm.accountIds" multiple filterable clearable class="!w-full" placeholder="请选择账号">
            <el-option v-for="account in accountList" :key="account.id" :label="accountOptionLabel(account)" :value="account.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="账号分组">
          <el-select v-model="publishForm.groupIds" multiple filterable clearable class="!w-full" placeholder="请选择账号分组">
            <el-option v-for="group in groupOptions" :key="group.id" :label="groupOptionLabel(group)" :value="group.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布模式" prop="postMode">
          <el-segmented v-model="publishForm.postMode" :options="postModeOptions" />
        </el-form-item>
        <el-form-item label="隐私" prop="privacyLevel">
          <el-select v-model="publishForm.privacyLevel" clearable class="!w-full" placeholder="默认使用账号配置">
            <el-option label="Self only" value="SELF_ONLY" />
            <el-option label="好友可见" value="MUTUAL_FOLLOW_FRIENDS" />
            <el-option label="粉丝可见" value="FOLLOWER_OF_CREATOR" />
            <el-option label="公开" value="PUBLIC_TO_EVERYONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="互动">
          <div class="check-row">
            <el-checkbox v-model="publishForm.allowComment">允许评论</el-checkbox>
            <el-checkbox v-model="publishForm.allowDuet">允许合拍</el-checkbox>
            <el-checkbox v-model="publishForm.allowStitch">允许拼接</el-checkbox>
          </div>
        </el-form-item>
        <el-form-item label="内容标识">
          <div class="check-row">
            <el-checkbox v-model="publishForm.aigcContent">AIGC</el-checkbox>
            <el-checkbox v-model="publishForm.commercialContent">商业内容</el-checkbox>
            <el-checkbox v-model="publishForm.brandContent">品牌内容</el-checkbox>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishLoading" :disabled="publishUploadLoading" @click="submitPublish">开始发布</el-button>
      </template>
    </el-drawer>

    <el-dialog title="TikTok 二维码授权" v-model="qrDialogVisible" width="420px">
      <div class="qr-box">
        <Qrcode v-if="qrInfo.qrcodeUrl" :text="qrInfo.qrcodeUrl" :width="220" />
        <el-empty v-else description="二维码未生成" />
        <el-tag :type="qrStatusType(qrInfo.status)">{{ qrInfo.status || '-' }}</el-tag>
        <p v-if="qrInfo.failReason" class="danger-text">{{ failReasonLabel(qrInfo) }}</p>
      </div>
      <template #footer>
        <el-button @click="qrDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="pollQrStatus">刷新状态</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="accountForm.id ? '账号发布配置' : '账号发布配置'" v-model="accountDialogVisible" width="560px">
      <el-form :model="accountForm" label-width="110px">
        <el-form-item label="账号">
          <span>{{ accountDisplayName(accountForm as TkTiktokAccountVO) }}</span>
        </el-form-item>
        <el-form-item label="账号备注名">
          <el-input
            v-model="accountForm.displayName"
            maxlength="64"
            show-word-limit
            placeholder="例如：美国主账号 / 美妆账号A"
          />
        </el-form-item>
        <el-form-item label="默认隐私">
          <el-select v-model="accountForm.defaultPrivacyLevel" clearable class="!w-full">
            <el-option label="Self only" value="SELF_ONLY" />
            <el-option label="好友可见" value="MUTUAL_FOLLOW_FRIENDS" />
            <el-option label="粉丝可见" value="FOLLOWER_OF_CREATOR" />
            <el-option label="公开" value="PUBLIC_TO_EVERYONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认互动">
          <div class="check-row">
            <el-checkbox v-model="accountForm.allowComment">评论</el-checkbox>
            <el-checkbox v-model="accountForm.allowDuet">合拍</el-checkbox>
            <el-checkbox v-model="accountForm.allowStitch">拼接</el-checkbox>
          </div>
        </el-form-item>
        <el-form-item label="内容标识">
          <div class="check-row">
            <el-checkbox v-model="accountForm.aigcContent">AIGC</el-checkbox>
            <el-checkbox v-model="accountForm.commercialContent">商业内容</el-checkbox>
            <el-checkbox v-model="accountForm.brandContent">品牌内容</el-checkbox>
          </div>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="accountForm.labels" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="Status">
          <el-switch v-model="accountEnabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAccountConfig">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :title="groupForm.id ? '编辑账号分组' : '新建账号分组'" v-model="groupDialogVisible" width="620px">
      <el-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-width="110px">
        <el-form-item label="分组名称" prop="name">
          <el-input v-model="groupForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="使用场景" prop="scene">
          <el-input v-model="groupForm.scene" placeholder="如：美妆账号矩阵、北美测评号" />
        </el-form-item>
        <el-form-item label="账号" prop="accountIds">
          <el-select v-model="groupForm.accountIds" multiple filterable clearable class="!w-full">
            <el-option v-for="account in groupAccountOptions" :key="account.id" :label="accountOptionLabel(account)" :value="account.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" prop="labels">
          <el-input v-model="groupForm.labels" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="groupForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitGroupForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Qrcode } from '@/components/Qrcode'
import { TkGenerationApi } from '@/api/tk/generation'
import type { TkGenerationTaskVO } from '@/api/tk/generation'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import {
  TkTiktokAccountApi,
  TkTiktokAccountGroupApi,
  TkTiktokPublishApi,
  TkVideoPublishCenterApi
} from '@/api/tk/videoPublishCenter'
import { uploadTikTokMediaInChunks } from '@/utils/tiktokMediaUpload'
import { getTkUploadErrorMessage } from '@/utils/tkChunkUpload'
import { formatDate } from '@/utils/formatTime'
import type {
  TkTiktokAccountGroupVO,
  TkTiktokAccountVO,
  TkTiktokPublishDetailVO,
  TkTiktokPublishTaskVO
} from '@/api/tk/videoPublishCenter'

defineOptions({ name: 'TkVideoPublishCenter' })

const message = useMessage()
const { tt, tText } = useTkI18n()
const { currentRoute } = useRouter()
const TIKTOK_AUTH_MESSAGE_TYPE = 'TK_TIKTOK_AUTH_RESULT'
const MAX_TIKTOK_VIDEO_SIZE = 1_000_000_000

type TkTiktokAuthMessage = {
  source?: string
  type?: string
  success?: boolean
  message?: string
}

const activeTab = ref<'videos' | 'accounts' | 'tasks' | 'details'>('videos')
const overview = reactive({
  authorizedAccountCount: 0,
  pendingPublishCount: 0,
  failedPublishCount: 0,
  tokenAbnormalCount: 0
})

const overviewCards = computed(() => [
  { label: tt('publish.overview.authorizedAccounts'), value: overview.authorizedAccountCount, icon: 'ep:user-filled' },
  { label: tt('publish.overview.pendingPublishes'), value: overview.pendingPublishCount, icon: 'ep:video-play' },
  { label: tt('publish.overview.failedTasks'), value: overview.failedPublishCount, icon: 'ep:warning-filled' },
  { label: tt('publish.overview.tokenIssues'), value: overview.tokenAbnormalCount, icon: 'ep:circle-close-filled' }
])

const videoLoading = ref(false)
const videoList = ref<TkGenerationTaskVO[]>([])
const videoTotal = ref(0)
const videoQueryFormRef = ref()
const videoQuery = reactive({ pageNo: 1, pageSize: 10, title: undefined, status: 'SUCCESS' })

const accountLoading = ref(false)
const accountList = ref<TkTiktokAccountVO[]>([])
const accountTotal = ref(0)
const accountQueryFormRef = ref()
const accountQuery = reactive({ pageNo: 1, pageSize: 10, keyword: undefined, tokenStatus: undefined, authStatus: undefined })

const groupLoading = ref(false)
const groupList = ref<TkTiktokAccountGroupVO[]>([])
const groupOptions = computed(() => groupList.value.filter((group) => group.id !== undefined) as Array<TkTiktokAccountGroupVO & { id: number }>)
const groupAccountOptions = computed(() => accountList.value)
const groupTotal = ref(0)
const groupQuery = reactive({ pageNo: 1, pageSize: 10, keyword: undefined, status: undefined })

const taskLoading = ref(false)
const taskList = ref<TkTiktokPublishTaskVO[]>([])
const taskTotal = ref(0)
const taskQueryFormRef = ref()
const taskQuery = reactive({ pageNo: 1, pageSize: 10, keyword: undefined, businessTraceId: undefined, status: undefined })

const detailLoading = ref(false)
const detailList = ref<TkTiktokPublishDetailVO[]>([])
const detailTotal = ref(0)
const detailQueryFormRef = ref()
const detailQuery = reactive({ pageNo: 1, pageSize: 10, publishTaskId: undefined, keyword: undefined, businessTraceId: undefined, status: undefined })
const retryingDetailIds = ref(new Set<number>())
let publishStatusTimer: number | undefined

const publishUrlDialogVisible = ref(false)
const publishUrlSubmitting = ref(false)
const publishUrlFormRef = ref()
const publishUrlForm = reactive({
  generationTaskId: undefined as number | undefined,
  publishDetailId: undefined as number | undefined,
  publishUrl: ""
})
const publishUrlRules = {
  publishUrl: [{ required: true, message: "发布链接不能为空", trigger: "blur" }]
}

const publishDrawerVisible = ref(false)
const publishLoading = ref(false)
const publishFormRef = ref()
const selectedVideo = ref<TkGenerationTaskVO>()
const publishFileInput = ref<HTMLInputElement>()
const publishUploadLoading = ref(false)
const publishUploadPercent = ref(0)
const publishUploadError = ref('')
const uploadedPublishMedia = ref<{ id: number; fileName: string; fileUrl: string }>()
const publishForm = reactive({
  generationTaskId: undefined as number | undefined,
  uploadedVideoId: undefined as number | undefined,
  sourceType: 'GENERATED' as 'GENERATED' | 'UPLOADED',
  coverTimestampMs: 1000,
  accountIds: [] as number[],
  groupIds: [] as number[],
  title: '',
  caption: '',
  postMode: 'DIRECT_POST',
  privacyLevel: 'SELF_ONLY',
  allowComment: true,
  allowDuet: false,
  allowStitch: false,
  commercialContent: false,
  brandContent: false,
  aigcContent: true
})
const publishRules = {
  title: [{ required: true, message: '发布标题不能为空', trigger: 'blur' }],
  postMode: [{ required: true, message: '请选择发布模式', trigger: 'change' }]
}
const postModeOptions = [
  { label: '直接发布', value: 'DIRECT_POST' },
  { label: '发送草稿箱', value: 'UPLOAD_TO_INBOX' }
]

const qrDialogVisible = ref(false)
const qrInfo = reactive({ clientTicket: '', qrcodeUrl: '', status: '', failReason: '' })

const accountDialogVisible = ref(false)
const accountForm = reactive<Partial<TkTiktokAccountVO>>({})
const accountEnabled = computed({
  get: () => accountForm.status !== 1,
  set: (value: boolean) => (accountForm.status = value ? 0 : 1)
})

const groupDialogVisible = ref(false)
const groupFormRef = ref()
const groupForm = reactive<TkTiktokAccountGroupVO>({
  name: '',
  companyId: undefined,
  scene: '',
  labels: '',
  remark: '',
  status: 0,
  accountIds: []
})
const groupRules = {
  name: [{ required: true, message: '鍒嗙粍鍚嶇О涓嶈兘涓虹┖', trigger: 'blur' }]
}

const publishStatuses = ['PENDING', 'PROCESSING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED']
const redirectAuthWindow = ref<Window | null>(null)

const copyBusinessTraceId = async (businessTraceId?: string) => {
  if (!businessTraceId) return
  await navigator.clipboard.writeText(businessTraceId)
  message.success('业务流水号已复制')
}

const getOverview = async () => {
  const data = await TkVideoPublishCenterApi.getOverview()
  Object.assign(overview, data)
}

const getVideoList = async () => {
  videoLoading.value = true
  try {
    const data = await TkGenerationApi.getGenerationPage(videoQuery)
    videoList.value = (data.list || []).filter((item: TkGenerationTaskVO) => item.status === 'SUCCESS' && item.outputUrl)
    videoTotal.value = data.total
  } finally {
    videoLoading.value = false
  }
}

const getAccountList = async () => {
  accountLoading.value = true
  try {
    const data = await TkTiktokAccountApi.getPage(accountQuery)
    accountList.value = data.list
    accountTotal.value = data.total
  } finally {
    accountLoading.value = false
  }
}

const getGroupList = async () => {
  groupLoading.value = true
  try {
    const data = await TkTiktokAccountGroupApi.getPage(groupQuery)
    groupList.value = data.list
    groupTotal.value = data.total
  } finally {
    groupLoading.value = false
  }
}

const getTaskList = async () => {
  taskLoading.value = true
  try {
    const data = await TkTiktokPublishApi.getTaskPage(taskQuery)
    taskList.value = data.list
    taskTotal.value = data.total
  } finally {
    taskLoading.value = false
  }
}

const getDetailList = async () => {
  detailLoading.value = true
  try {
    const data = await TkTiktokPublishApi.getDetailPage(detailQuery)
    detailList.value = data.list
    detailTotal.value = data.total
  } finally {
    detailLoading.value = false
  }
}

const pollPublishStatus = async () => {
  await getOverview()
  if (activeTab.value === 'details') {
    await getDetailList()
  } else if (activeTab.value === 'tasks') {
    await getTaskList()
  }
}

const startPublishStatusPolling = () => {
  stopPublishStatusPolling()
  publishStatusTimer = window.setInterval(() => {
    pollPublishStatus().catch(() => undefined)
  }, 30_000)
}

const stopPublishStatusPolling = () => {
  if (publishStatusTimer) window.clearInterval(publishStatusTimer)
  publishStatusTimer = undefined
}

const handleTabChange = () => {
  if (activeTab.value === 'videos') getVideoList()
  if (activeTab.value === 'accounts') {
    getAccountList()
    getGroupList()
  }
  if (activeTab.value === 'tasks') getTaskList()
  if (activeTab.value === 'details') getDetailList()
}

const refreshAll = async () => {
  await getOverview()
  handleTabChange()
}

const handleRedirectAuthResult = async (event: MessageEvent) => {
  if (!redirectAuthWindow.value || event.source !== redirectAuthWindow.value) {
    return
  }
  const data = event.data as TkTiktokAuthMessage
  if (!data || data.source !== 'tk-tiktok-auth' || data.type !== TIKTOK_AUTH_MESSAGE_TYPE) {
    return
  }
  redirectAuthWindow.value = null
  if (data.success) {
    activeTab.value = 'accounts'
    message.success(data.message || 'TikTok 授权完成')
    await getOverview()
    await getAccountList()
    await getGroupList()
    return
  }
  message.warning(data.message || 'TikTok 授权失败，请重新发起授权')
}

const handleVideoQuery = () => {
  videoQuery.pageNo = 1
  getVideoList()
}
const resetVideoQuery = () => {
  videoQueryFormRef.value?.resetFields()
  handleVideoQuery()
}
const handleAccountQuery = () => {
  accountQuery.pageNo = 1
  getAccountList()
}
const resetAccountQuery = () => {
  accountQueryFormRef.value?.resetFields()
  handleAccountQuery()
}
const handleTaskQuery = () => {
  taskQuery.pageNo = 1
  getTaskList()
}
const resetTaskQuery = () => {
  taskQueryFormRef.value?.resetFields()
  handleTaskQuery()
}
const handleDetailQuery = () => {
  detailQuery.pageNo = 1
  getDetailList()
}
const resetDetailQuery = () => {
  detailQueryFormRef.value?.resetFields()
  detailQuery.publishTaskId = undefined
  handleDetailQuery()
}

const openPublishDrawer = (row: TkGenerationTaskVO) => {
  selectedVideo.value = row
  publishForm.sourceType = 'GENERATED'
  publishForm.uploadedVideoId = undefined
  publishForm.generationTaskId = row.id
  publishForm.title = row.title || `TikTok 成片任务 #${row.id}`
  publishForm.caption = row.scriptText || row.title || ''
  publishForm.accountIds = []
  publishForm.groupIds = []
  publishDrawerVisible.value = true
}

const openUploadPublishDrawer = () => {
  selectedVideo.value = undefined
  publishForm.sourceType = 'UPLOADED'
  publishForm.generationTaskId = undefined
  publishForm.uploadedVideoId = undefined
  publishForm.title = ''
  publishForm.caption = ''
  uploadedPublishMedia.value = undefined
  publishDrawerVisible.value = true
}

const handlePublishFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  if (file.size > MAX_TIKTOK_VIDEO_SIZE) {
    message.warning('视频文件不能超过 1GB')
    return
  }
  uploadedPublishMedia.value = undefined
  publishForm.uploadedVideoId = undefined
  publishUploadError.value = ''
  publishUploadLoading.value = true
  publishUploadPercent.value = 0
  try {
    const result = await uploadTikTokMediaInChunks(file, {
      onProgress: ({ percent }) => {
        publishUploadPercent.value = percent
      }
    })
    uploadedPublishMedia.value = {
      id: result.uploadedVideoId,
      fileName: file.name,
      fileUrl: ''
    }
    publishForm.uploadedVideoId = result.uploadedVideoId
    publishForm.title = file.name.replace(/\.[^.]+$/, '')
    message.success('视频上传完成')
  } catch (error: any) {
    uploadedPublishMedia.value = undefined
    publishForm.uploadedVideoId = undefined
    publishUploadError.value = getTkUploadErrorMessage(error)
    message.error(publishUploadError.value)
  } finally {
    publishUploadLoading.value = false
  }
}

const resetPublishSource = () => {
  publishUploadPercent.value = 0
  publishUploadError.value = ''
  if (publishFileInput.value) publishFileInput.value.value = ''
}

const submitPublish = async () => {
  await publishFormRef.value?.validate()
  if (!publishForm.accountIds.length && !publishForm.groupIds.length) {
    message.warning('请选择至少一个账号或账号分组')
    return
  }
  if (publishForm.sourceType === 'UPLOADED' && !publishForm.uploadedVideoId) {
    message.warning('请先上传视频')
    return
  }
  publishLoading.value = true
  try {
    await TkTiktokPublishApi.create(publishForm)
    message.success('Publish task created')
    publishDrawerVisible.value = false
    activeTab.value = 'tasks'
    await refreshAll()
  } finally {
    publishLoading.value = false
  }
}

const startRedirectAuth = async () => {
  const data = await TkTiktokAccountApi.authorizeByRedirect({})
  if (data.authorizeUrl) {
    const authWindow = window.open(data.authorizeUrl, '_blank')
    if (authWindow) {
      redirectAuthWindow.value = authWindow
      message.success('TikTok authorization page opened')
    } else {
      message.warning('Browser blocked the popup, please allow popups and try again')
    }
  } else {
    message.warning(data.failReason || 'TikTok authorization config is incomplete')
  }
}

const startQrAuth = async () => {
  const data = await TkTiktokAccountApi.authorizeByQrCode({})
  qrInfo.clientTicket = data.clientTicket
  qrInfo.qrcodeUrl = data.qrcodeUrl || ''
  qrInfo.status = data.status
  qrInfo.failReason = data.failReason
  qrDialogVisible.value = true
  startQrPolling()
}

const pollQrStatus = async () => {
  if (!qrInfo.clientTicket) return
  const data = await TkTiktokAccountApi.getQrCodeStatus(qrInfo.clientTicket)
  qrInfo.status = data.status
  qrInfo.failReason = data.failReason
  if (data.status === 'SUCCESS') {
    message.success('TikTok 授权完成')
    await getAccountList()
    stopQrPolling()
  }
}

let qrPollingTimer: number | undefined
const startQrPolling = () => {
  stopQrPolling()
  qrPollingTimer = window.setInterval(() => {
    if (qrInfo.status === 'SUCCESS' || qrInfo.status === 'FAILED' || qrInfo.status === 'CONFIG_REQUIRED') {
      stopQrPolling()
      return
    }
    pollQrStatus()
  }, 2500)
}
const stopQrPolling = () => {
  if (qrPollingTimer) window.clearInterval(qrPollingTimer)
  qrPollingTimer = undefined
}

const openAccountConfig = (row: TkTiktokAccountVO) => {
  Object.assign(accountForm, row)
  accountDialogVisible.value = true
}

const submitAccountConfig = async () => {
  await TkTiktokAccountApi.updateDefaultConfig(accountForm)
  message.success('Account config saved')
  accountDialogVisible.value = false
  await getAccountList()
}

const canUnbindAccount = (row: TkTiktokAccountVO) => {
  return row.authStatus === 'AUTHORIZED' || row.tokenStatus === 'VALID'
}

const unbindAccount = async (row: TkTiktokAccountVO) => {
  await message.confirm(
    `确认解绑 TikTok 账号「${accountDisplayName(row)}」？解绑后会清除授权 Token，并从账号分组中移除，但账号记录仍会保留。`,
    '解绑确认'
  )
  await TkTiktokAccountApi.unbind(row.id)
  message.success('账号已解绑')
  await getOverview()
  await getAccountList()
  await getGroupList()
}

const deleteAccount = async (row: TkTiktokAccountVO) => {
  await message.confirm(
    `确认删除 TikTok 账号记录「${accountDisplayName(row)}」？删除后账号列表不再展示该账号，已生成的发布历史不会删除。`,
    '删除确认'
  )
  await TkTiktokAccountApi.delete(row.id)
  message.success('账号记录已删除')
  await getOverview()
  await getAccountList()
  await getGroupList()
}

const openGroupForm = (row?: TkTiktokAccountGroupVO) => {
  Object.assign(groupForm, {
    id: row?.id,
    companyId: row?.companyId,
    name: row?.name || '',
    scene: row?.scene || '',
    labels: row?.labels || '',
    remark: row?.remark || '',
    status: row?.status ?? 0,
    accountIds: row?.accountIds || []
  })
  groupDialogVisible.value = true
}

const submitGroupForm = async () => {
  await groupFormRef.value?.validate()
  if (groupForm.id) {
    await TkTiktokAccountGroupApi.update(groupForm)
  } else {
    await TkTiktokAccountGroupApi.create(groupForm)
  }
  message.success('Group saved')
  groupDialogVisible.value = false
  await getGroupList()
}

const deleteGroup = async (id?: number) => {
  if (!id) return
  await message.delConfirm()
  await TkTiktokAccountGroupApi.delete(id)
  message.success('Group deleted')
  await getGroupList()
}

const openTaskDetails = (row: TkTiktokPublishTaskVO) => {
  detailQuery.publishTaskId = row.id as any
  activeTab.value = 'details'
  getDetailList()
}

const openPublishUrlDialog = (row: TkTiktokPublishDetailVO) => {
  publishUrlForm.generationTaskId = row.generationTaskId
  publishUrlForm.publishDetailId = row.id
  publishUrlForm.publishUrl = row.publishUrl || ''
  publishUrlDialogVisible.value = true
}

const submitPublishUrl = async () => {
  await publishUrlFormRef.value?.validate()
  if (!publishUrlForm.generationTaskId) return
  publishUrlSubmitting.value = true
  try {
    await TkTiktokPublishApi.registerPublishUrl({
      generationTaskId: publishUrlForm.generationTaskId,
      publishDetailId: publishUrlForm.publishDetailId,
      publishUrl: publishUrlForm.publishUrl
    })
    message.success('Publish link registered')
    publishUrlDialogVisible.value = false
    await refreshAll()
  } finally {
    publishUrlSubmitting.value = false
  }
}

const syncTask = async (id: number) => {
  await TkTiktokPublishApi.syncStatus(id)
  message.success('Status synced')
  await refreshAll()
}

const setRetryingDetail = (id: number, retrying: boolean) => {
  const ids = new Set(retryingDetailIds.value)
  retrying ? ids.add(id) : ids.delete(id)
  retryingDetailIds.value = ids
}

const applyRetryPendingState = (row: TkTiktokPublishDetailVO) => {
  row.status = 'PENDING'
  row.tiktokStatus = 'RETRY_PENDING'
  row.publishId = undefined
  row.failReason = undefined
  row.retryCount = (row.retryCount || 0) + 1
  row.lastSyncTime = formatDate(new Date())
}

const retryDetail = async (row: TkTiktokPublishDetailVO) => {
  if (!row.id || retryingDetailIds.value.has(row.id)) return
  setRetryingDetail(row.id, true)
  try {
    await TkTiktokPublishApi.retry(row.id)
    applyRetryPendingState(row)
    message.success('Retry submitted')
    await getOverview()
    getTaskList()
    window.setTimeout(() => {
      getDetailList()
    }, 1200)
  } finally {
    setRetryingDetail(row.id, false)
  }
}

const displayVideoFileName = (url?: string) => {
  if (!url) return '-'
  const pathWithoutQuery = url.split(/[?#]/, 1)[0]
  const fileName = pathWithoutQuery.split('/').filter(Boolean).pop()
  if (!fileName) return '-'
  try {
    return decodeURIComponent(fileName)
  } catch {
    return fileName
  }
}
const formatTimestamp = (value?: string | number) => {
  if (value === undefined || value === null || value === '') return '-'
  const parsedValue = typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value
  const normalizedValue = typeof parsedValue === 'number' && parsedValue < 1_000_000_000_000
    ? parsedValue * 1000
    : parsedValue
  return formatDate(normalizedValue) || '-'
}
const accountDisplayName = (account: Partial<TkTiktokAccountVO>) => account.displayName || account.username || account.openId || ('Account #' + account.id)
const accountInitial = (row: TkTiktokAccountVO) => accountDisplayName(row).slice(0, 1).toUpperCase()
const accountOptionLabel = (account: TkTiktokAccountVO) => accountDisplayName(account)
const groupOptionLabel = (group: TkTiktokAccountGroupVO) => group.name || ('Group #' + group.id)
const authStatusLabel = (status?: string) => ({ AUTHORIZED: 'Authorized', UNAUTHORIZED: 'Unauthorized' })[status || ''] || status || '-'
const tokenStatusLabel = (status?: string) => ({ VALID: '有效', AUTO_REFRESH: '自动刷新中', EXPIRED: '需重新授权' })[status || ''] || status || '-'
const tokenStatusType = (status?: string) => ({ VALID: 'success', AUTO_REFRESH: 'warning', EXPIRED: 'danger' }[status || ''] || 'info') as 'success' | 'warning' | 'danger' | 'info'
const postModeLabel = (mode?: string) => ({ DIRECT_POST: 'Direct post', UPLOAD_TO_INBOX: 'Upload to inbox', MANUAL_REGISTER: 'Manual register' })[mode || ''] || mode || '-'
const publishStatusLabel = (status?: string) => ({ PENDING: 'Pending', PROCESSING: 'Processing', SUCCESS: 'Success', PARTIAL_SUCCESS: 'Partial success', FAILED: 'Failed' })[status || ''] || status || '-'
const tiktokStatusLabel = (status?: string) => ({
  UPLOAD_PENDING: '上传待确认',
  PROCESSING: '平台处理中',
  PUBLISH_COMPLETE: '已发布',
  SEND_TO_USER_INBOX: '已提交草稿箱',
  FAILED: '失败'
})[status || ''] || status || '-'
const publishStatusType = (status?: string) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARTIAL_SUCCESS') return 'warning'
  return 'info'
}
const qrStatusType = (status?: string) => (status === 'SUCCESS' ? 'success' : status === 'FAILED' || status === 'CONFIG_REQUIRED' ? 'danger' : 'info')
const failReasonLabel = (row: { failReasonCode?: string; failReason?: string }) => {
  if (row.failReasonCode) {
    const key = 'publish.failure.' + row.failReasonCode
    const translated = tt(key)
    if (translated !== key) return translated
  }
  return tText(row.failReason) || '-'
}
const loadRouteGeneration = async () => {
  const generationTaskId = Number(currentRoute.value.query.generationTaskId)
  if (!generationTaskId) return
  const task = await TkGenerationApi.getGeneration(generationTaskId)
  if (task?.status === 'SUCCESS' && task?.outputUrl) {
    openPublishDrawer(task)
  } else {
    message.warning('该生成任务暂不可发布')
  }
}

onMounted(async () => {
  window.addEventListener('message', handleRedirectAuthResult)
  await Promise.all([getOverview(), getVideoList(), getAccountList(), getGroupList()])
  startPublishStatusPolling()
  await loadRouteGeneration()
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handleRedirectAuthResult)
  stopQrPolling()
  stopPublishStatusPolling()
})
</script>

<style scoped>
.publish-center {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 56px;
}

.overview-card .iconify {
  color: var(--el-color-primary);
  font-size: 24px;
}

.overview-card span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.overview-card strong {
  color: var(--el-text-color-primary);
  font-size: 24px;
  line-height: 1.2;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.toolbar-actions,
.check-row,
.switch-tags,
.account-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.account-cell {
  flex-wrap: nowrap;
}

.main-title {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.sub-text {
  margin-top: 3px;
  max-width: 520px;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-line {

.publish-url-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
}

.qr-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.danger-text {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 13px;
}

.upload-error {
  display: block;
  width: 100%;
  margin-top: 4px;
  color: var(--el-color-danger);
  font-size: 12px;
}

@media (max-width: 960px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
