<template>
  <ContentWrap class="material-workbench">
    <div class="workbench-hero">
      <div>
        <div class="workbench-kicker">{{ getMaterialPurposeLabel(activeMaterialPurpose) }}</div>
        <h2>素材运营工作台</h2>
        <p>{{ activePurposeDescription }}</p>
      </div>
      <div class="workbench-actions">
        <el-tabs v-model="activeMaterialPurpose" class="material-purpose-tabs" @tab-change="handleMaterialPurposeChange">
          <el-tab-pane
            v-for="item in materialPurposeOptions"
            :key="item.value"
            :label="localizeUiText(item.label)"
            :name="item.value"
          />
        </el-tabs>
        <div class="hero-buttons">
          <el-button type="primary" plain @click="openLibraryForm()" v-hasPermi="['tk:material-library:create']">
            <Icon icon="ep:plus" class="mr-5px" /> 新建素材库
          </el-button>
          <el-button type="success" plain @click="openUploadForm()" v-hasPermi="['tk:material-video:upload']">
            <Icon icon="ep:upload" class="mr-5px" /> 上传视频
          </el-button>
        </div>
      </div>
    </div>

    <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="70px" class="workbench-filters">
      <el-form-item label="素材库" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入素材库名称" clearable class="!w-220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-input v-model="queryParams.category" placeholder="请输入分类" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-150px">
          <el-option label="启用" :value="0" />
          <el-option label="禁用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>

    <div class="ops-metrics">
      <div class="ops-metric">
        <span>素材库数量</span>
        <strong>{{ total }}</strong>
      </div>
      <div class="ops-metric">
        <span>当前素材总数</span>
        <strong>{{ currentLibraryVideoCount }}</strong>
      </div>
      <div class="ops-metric">
        <span>{{ completenessMetricTitle }}</span>
        <strong>{{ completedKeySegmentCount }} / {{ keySegmentTotal }}</strong>
      </div>
      <div class="ops-metric" :class="{ warning: missingKeySegmentCount > 0 }">
        <span>缺失分组</span>
        <strong>{{ missingKeySegmentCount }}</strong>
      </div>
    </div>

    <div class="workbench-grid">
      <aside class="library-panel" v-loading="loading">
        <div class="panel-head">
          <div>
            <h3>素材库</h3>
            <p>点击左侧素材库后，在右侧维护分组和视频</p>
          </div>
          <el-tag>{{ total }} 个</el-tag>
        </div>
        <div v-if="list.length" class="library-card-list">
          <button
            v-for="item in list"
            :key="item.id"
            type="button"
            class="library-card"
            :class="{ active: selectedLibrary?.id === item.id }"
            @click="handleSelectLibrary(item)"
          >
            <div class="library-card__main">
              <Icon icon="ep:folder" />
              <div>
                <strong>{{ item.name }}</strong>
                <span>{{ displayText(item.description, '暂无描述') }}</span>
              </div>
            </div>
            <div class="library-card__meta">
              <span>{{ displayText(item.category) }}</span>
              <span>{{ displayText(item.scene) }}</span>
            </div>
            <div class="library-card__footer">
              <el-tag :type="item.materialPurpose === MATERIAL_PURPOSE_LEAD_GENERATION ? 'warning' : 'success'" size="small">
                {{ getMaterialPurposeLabel(item.materialPurpose) }}
              </el-tag>
              <span>{{ item.videoCount || 0 }} 条</span>
              <span>{{ formatSize(item.totalSize) }}</span>
              <el-tag :type="item.status === 0 ? 'success' : 'info'" size="small">
                {{ item.status === 0 ? '启用' : '禁用' }}
              </el-tag>
            </div>
            <div class="library-card__health">
              <span>{{ getLibraryHealthText(item) }}</span>
              <el-progress
                :percentage="getLibraryHealthPercent(item)"
                :stroke-width="6"
                :show-text="false"
                :status="getLibraryHealthPercent(item) >= 100 ? 'success' : undefined"
              />
            </div>
          </button>
        </div>
        <div v-else class="empty-workbench compact">
          <Icon icon="ep:folder-add" />
          <strong>暂无素材库</strong>
          <span>新建素材库后即可上传视频</span>
          <el-button type="primary" plain @click="openLibraryForm()" v-hasPermi="['tk:material-library:create']">
            新建素材库
          </el-button>
        </div>
        <el-pagination
          v-if="total > 0"
          v-model:current-page="queryParams.pageNo"
          :page-size="queryParams.pageSize"
          :total="total"
          :pager-count="3"
          small
          background
          layout="total, prev, pager, next"
          class="library-pagination"
          @current-change="getList"
        />
      </aside>

      <section v-if="selectedLibrary" class="library-detail">
        <div class="detail-head">
          <div>
            <div class="detail-title-row">
              <h3>{{ selectedLibrary.name }}</h3>
              <el-tag :type="selectedLibrary.status === 0 ? 'success' : 'info'">
                {{ selectedLibrary.status === 0 ? '启用' : '禁用' }}
              </el-tag>
            </div>
            <p>
              {{ displayText(selectedLibrary.category) }} / {{ displayText(selectedLibrary.scene) }}
              <span v-if="selectedLibrary.tags"> / {{ selectedLibrary.tags }}</span>
            </p>
          </div>
          <div class="detail-actions">
            <el-button plain @click="openLibraryForm(selectedLibrary)" v-hasPermi="['tk:material-library:update']">编辑素材库</el-button>
            <el-button type="danger" plain @click="handleDeleteLibrary(selectedLibrary.id as number)" v-hasPermi="['tk:material-library:delete']">删除素材库</el-button>
            <el-button type="primary" @click="openUploadForm(selectedLibrary)" v-hasPermi="['tk:material-video:upload']">
              <Icon icon="ep:upload" class="mr-5px" /> 上传到当前库
            </el-button>
          </div>
        </div>

        <div class="detail-summary">
          <div>
            <span>视频数</span>
            <strong>{{ currentLibraryVideoCount }}</strong>
          </div>
          <div>
            <span>容量</span>
            <strong>{{ formatSize(selectedLibrary.totalSize) }}</strong>
          </div>
          <div>
            <span>素材类型</span>
            <strong>{{ getMaterialPurposeLabel(selectedLibrary.materialPurpose) }}</strong>
          </div>
        </div>

        <div class="segment-health">
          <button
            v-for="item in segmentSummary"
            :key="item.value"
            type="button"
            class="segment-health__item"
            :class="{ empty: item.count === 0, key: item.keySegment, active: videoQuery.segmentType === item.value }"
            @click="handleSegmentCardClick(item.value)"
          >
            <span>{{ getSegmentOptionShortLabel(item) }}</span>
            <strong>{{ getSegmentTypeName(item) }}</strong>
            <em>{{ item.count }}</em>
          </button>
        </div>

        <el-alert
          v-if="missingKeySegments.length"
          class="segment-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="missingKeySegmentsTitle"
          :description="keySegmentRuleDescription"
        />

        <div class="section-head">
          <div>
            <h3>{{ materialVideosTitle }}</h3>
            <p>
              {{
                videoQuery.segmentType
                  ? `当前筛选：${getSegmentTypeLabel(videoQuery.segmentType)}；完整度按当前素材库全部可用视频统计`
                  : '展示当前素材库全部素材；完整度按当前素材库全部可用视频统计'
              }}
            </p>
          </div>
          <div class="video-actions">
            <div class="segment-control">
              <span>筛选</span>
              <el-select
                v-model="videoQuery.segmentType"
                placeholder="全部用途"
                clearable
                class="!w-150px"
                @change="handleVideoQuery"
              >
                <el-option
                  v-for="item in segmentTypeOptions"
                  :key="item.value"
                  :label="getSegmentOptionLabel(item)"
                  :value="item.value"
                />
              </el-select>
            </div>
            <el-button type="primary" plain @click="openUploadForm(selectedLibrary)" v-hasPermi="['tk:material-video:upload']">
              <Icon icon="ep:upload" class="mr-5px" /> 上传
            </el-button>
          </div>
        </div>

        <div v-if="selectedVideoIds.length" class="selected-action-bar">
          <span>已选 {{ selectedVideoIds.length }} 条素材</span>
          <el-select
            v-model="batchSegmentType"
            class="!w-170px"
            :disabled="batchSegmentUpdating"
            v-hasPermi="['tk:material-video:upload']"
          >
            <el-option
              v-for="item in segmentTypeOptions"
              :key="item.value"
              :label="getSegmentOptionLabel(item)"
              :value="item.value"
            />
          </el-select>
          <el-button
            type="primary"
            plain
            :loading="batchSegmentUpdating"
            @click="handleBatchSegmentType"
            v-hasPermi="['tk:material-video:upload']"
          >
            修改已选用途
          </el-button>
          <el-button
            type="danger"
            plain
            :loading="batchDeleting"
            @click="handleBatchDeleteVideos"
            v-hasPermi="['tk:material-video:delete']"
          >
            批量删除
          </el-button>
        </div>

        <el-table v-loading="videoLoading" :data="videoList" stripe empty-text="当前素材库暂无视频" @selection-change="handleVideoSelectionChange">
      <el-table-column type="selection" width="48" />
      <el-table-column label="封面" width="96">
        <template #default="scope">
          <button
            class="video-cover"
            :class="{ empty: !scope.row.coverUrl && !scope.row.fileUrl, clickable: !!scope.row.fileUrl }"
            type="button"
            :disabled="!scope.row.fileUrl"
            @click="openVideoPreview(scope.row)"
          >
            <img
              v-if="scope.row.coverUrl && !brokenVideoCovers[coverErrorKey(scope.row)]"
              :src="scope.row.coverUrl"
              :alt="scope.row.fileName"
              @error="markVideoCoverBroken(scope.row)"
            />
            <span v-else class="video-cover__fallback">
              <Icon icon="ep:video-camera" />
            </span>
          </button>
        </template>
      </el-table-column>
      <el-table-column label="文件名" prop="fileName" min-width="220" />
      <el-table-column label="格式" prop="format" width="90" />
      <el-table-column label="时长" width="90">
        <template #default="scope">{{ formatDuration(scope.row.duration) }}</template>
      </el-table-column>
      <el-table-column label="分辨率" width="110">
        <template #default="scope">{{ displayText(scope.row.resolution) }}</template>
      </el-table-column>
      <el-table-column label="大小" width="110">
        <template #default="scope">{{ formatSize(scope.row.size) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="scope">
          <el-tag :type="getVideoStatusType(scope.row.status)">
            {{ getVideoStatusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="用途" width="180">
        <template #default="scope">
          <el-select
            :model-value="scope.row.segmentType"
            size="small"
            class="!w-145px"
            :disabled="isSegmentUpdating(scope.row.id)"
            @change="(value) => handleRowSegmentTypeChange(scope.row, value as SegmentTypeValue)"
            v-hasPermi="['tk:material-video:upload']"
          >
            <el-option
              v-for="item in segmentTypeOptions"
              :key="item.value"
              :label="getSegmentOptionLabel(item)"
              :value="item.value"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="220" show-overflow-tooltip>
        <template #default="scope">{{ displayText(scope.row.failReason) }}</template>
      </el-table-column>
      <el-table-column label="标签" min-width="160">
        <template #default="scope">{{ displayText(scope.row.tags) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            :disabled="!scope.row.fileUrl"
            @click="openVideoPreview(scope.row)"
          >
            预览
          </el-button>
          <el-button link type="danger" @click="handleDeleteVideo(scope.row.id)" v-hasPermi="['tk:material-video:delete']">删除</el-button>
        </template>
      </el-table-column>
        </el-table>
        <Pagination :total="videoTotal" v-model:page="videoQuery.pageNo" v-model:limit="videoQuery.pageSize" @pagination="getVideoList" />
      </section>
      <section v-else class="library-detail empty-workbench">
        <Icon icon="ep:collection" />
        <strong>请选择一个素材库</strong>
        <span>左侧选择素材库后，可以查看 S1-S8 完整度、上传素材、预览视频并批量调整用途。</span>
        <el-button type="primary" plain @click="openLibraryForm()" v-hasPermi="['tk:material-library:create']">
          新建素材库
        </el-button>
      </section>
    </div>
  </ContentWrap>

  <el-dialog :title="libraryForm.id ? '编辑素材库' : '新建素材库'" v-model="libraryDialogVisible" width="620px">
    <el-form ref="libraryFormRef" :model="libraryForm" :rules="libraryRules" label-width="110px">
      <el-form-item label="素材库名称" prop="name">
        <el-input v-model="libraryForm.name" placeholder="请输入素材库名称" maxlength="128" />
      </el-form-item>
      <el-form-item label="素材类型" prop="materialPurpose">
        <el-radio-group v-model="libraryForm.materialPurpose">
          <el-radio-button
            v-for="item in materialPurposeOptions"
            :key="item.value"
            :label="item.value"
          >
            {{ localizeUiText(item.label) }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="产品分类" prop="category">
        <el-input v-model="libraryForm.category" placeholder="如：美妆护肤、3C数码" />
      </el-form-item>
      <el-form-item label="使用场景" prop="scene">
        <el-input v-model="libraryForm.scene" placeholder="如：带货混剪、开箱测评、场景展示" />
      </el-form-item>
      <el-form-item label="标签" prop="tags">
        <el-input v-model="libraryForm.tags" placeholder="多个标签用逗号分隔" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="libraryForm.description" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="默认库" prop="defaulted">
        <el-switch v-model="libraryForm.defaulted" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="libraryDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submitLibraryForm" :loading="formLoading">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog
    title="批量上传素材视频"
    v-model="uploadDialogVisible"
    width="760px"
    :close-on-click-modal="!uploadLoading"
    :close-on-press-escape="!uploadLoading"
    :before-close="handleUploadDialogClose"
  >
    <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="110px">
      <el-form-item label="素材库" prop="libraryId">
        <el-select v-model="uploadForm.libraryId" placeholder="请选择素材库" class="!w-full">
          <el-option v-for="item in librariesWithId" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="标签" prop="tags">
        <el-input v-model="uploadForm.tags" placeholder="多个标签用逗号分隔" />
      </el-form-item>
      <el-form-item label="素材用途" prop="segmentType">
        <el-select v-model="uploadForm.segmentType" class="!w-full" placeholder="请选择素材用途">
          <el-option
            v-for="item in segmentTypeOptions"
            :key="item.value"
            :label="getSegmentOptionLabel(item)"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="视频文件" prop="file">
        <div class="upload-source-actions">
          <el-button plain :disabled="uploadLoading" @click="triggerFolderPicker">
            <Icon icon="ep:folder-opened" />
            选择文件夹
          </el-button>
          <span>文件夹上传会递归识别视频，单次最多 200 个；普通批量选择仍最多 10 个。</span>
        </div>
        <input
          ref="folderInputRef"
          class="folder-input"
          type="file"
          multiple
          webkitdirectory
          accept=".mp4,.mov,.webm"
          @change="handleFolderFileChange"
        />
        <el-upload
          drag
          multiple
          :auto-upload="false"
          :limit="MAX_BATCH_UPLOAD_COUNT"
          :file-list="uploadFileList"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :on-exceed="handleFileExceed"
          accept=".mp4,.mov,.webm"
        >
          <Icon icon="ep:upload-filled" class="upload-icon" />
          <div>拖拽视频到此处，或点击批量选择文件</div>
          <template #tip>
            <div class="el-upload__tip">支持 mp4、mov、webm，单文件最大 100MB，单次最多 10 个</div>
          </template>
        </el-upload>
        <div v-if="folderUploadSummary" class="folder-upload-summary">
          <Icon icon="ep:info-filled" />
          <span>
            文件夹识别到 {{ folderUploadSummary.validCount }} 个视频，已加入 {{ folderUploadSummary.addedCount }} 个
            <template v-if="folderUploadSummary.ignoredCount">，忽略非视频 {{ folderUploadSummary.ignoredCount }} 个</template>
            <template v-if="folderUploadSummary.oversizedCount">，超过 100MB {{ folderUploadSummary.oversizedCount }} 个</template>
            <template v-if="folderUploadSummary.limitedCount">，超过上限未加入 {{ folderUploadSummary.limitedCount }} 个</template>
          </span>
        </div>
      </el-form-item>
      <el-form-item v-if="uploadQueue.length" label="上传队列">
        <div class="upload-progress-panel">
          <div class="upload-progress-panel__stats">
            <div>
              <span>文件总数</span>
              <strong>{{ uploadQueue.length }}</strong>
            </div>
            <div>
              <span>已完成</span>
              <strong>{{ uploadSuccessCount }}</strong>
            </div>
            <div>
              <span>失败</span>
              <strong>{{ uploadFailedCount }}</strong>
            </div>
          </div>
          <el-progress :percentage="uploadOverallProgress" :status="uploadFailedCount ? 'exception' : uploadSuccessCount === uploadQueue.length ? 'success' : undefined" />
        </div>
        <div class="upload-queue">
          <div v-for="item in uploadQueue" :key="item.uid" class="upload-queue__item">
            <div class="upload-queue__meta">
              <div>
                <strong>{{ item.name }}</strong>
                <span>{{ formatSize(item.size) }}</span>
              </div>
              <el-tag :type="getUploadStatusType(item.status)" size="small">
                {{ getUploadStatusLabel(item.status) }}
              </el-tag>
            </div>
            <el-progress :percentage="item.progress" :status="item.status === 'failed' ? 'exception' : item.status === 'success' ? 'success' : undefined" />
            <div v-if="item.error" class="upload-queue__error">{{ item.error }}</div>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleUploadDialogClose()" :disabled="uploadLoading">取消</el-button>
      <el-button v-if="hasFailedUpload" type="warning" plain @click="retryFailedUploads" :loading="uploadLoading">
        重试失败文件
      </el-button>
      <el-button type="primary" @click="submitUploadForm" :loading="uploadLoading">
        {{ uploadLoading ? uploadSummaryText : '开始批量上传' }}
      </el-button>
    </template>
  </el-dialog>

  <el-drawer
    v-model="previewDialogVisible"
    title="素材预览"
    size="520px"
    class="material-video-preview-drawer"
    destroy-on-close
    @closed="previewVideo = undefined"
  >
    <div v-if="previewVideo" class="video-preview">
      <video
        class="video-preview__player"
        :src="previewVideo.fileUrl"
        :poster="previewVideo.coverUrl"
        controls
        autoplay
        preload="metadata"
        playsinline
      ></video>
      <div class="video-preview__meta">
        <div>
          <span>文件名</span>
          <strong>{{ previewVideo.fileName }}</strong>
        </div>
        <div>
          <span>状态</span>
          <el-tag :type="getVideoStatusType(previewVideo.status)">
            {{ getVideoStatusLabel(previewVideo.status) }}
          </el-tag>
        </div>
        <div>
          <span>时长</span>
          <strong>{{ formatDuration(previewVideo.duration) }}</strong>
        </div>
        <div>
          <span>分辨率</span>
          <strong>{{ displayText(previewVideo.resolution) }}</strong>
        </div>
        <div>
          <span>大小</span>
          <strong>{{ formatSize(previewVideo.size) }}</strong>
        </div>
        <div>
          <span>格式</span>
          <strong>{{ displayText(previewVideo.format) }}</strong>
        </div>
        <div>
          <span>素材用途</span>
          <el-select
            :model-value="previewVideo.segmentType"
            class="!w-full"
            :disabled="isSegmentUpdating(previewVideo.id)"
            @change="(value) => handlePreviewSegmentTypeChange(value as SegmentTypeValue)"
            v-hasPermi="['tk:material-video:upload']"
          >
            <el-option
              v-for="item in segmentTypeOptions"
              :key="item.value"
              :label="getSegmentOptionLabel(item)"
              :value="item.value"
            />
          </el-select>
        </div>
        <div>
          <span>失败原因</span>
          <strong>{{ displayText(previewVideo.failReason) }}</strong>
        </div>
        <div>
          <span>标签</span>
          <strong>{{ displayText(previewVideo.tags) }}</strong>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import {
  MATERIAL_VIDEO_UPLOAD_TIMEOUT,
  TkMaterialApi,
  TkMaterialLibraryVO,
  TkMaterialVideoSegmentSummaryVO,
  TkMaterialVideoVO
} from '@/api/tk/material'
import type { UploadFile, UploadUserFile } from 'element-plus'
import axios from 'axios'
import type { AxiosProgressEvent } from 'axios'
import { useLocaleStore } from '@/store/modules/locale'
import { translateUiText } from '@/utils/tkI18n'

defineOptions({ name: 'TkMaterialLibrary' })

const message = useMessage()
const route = useRoute()
const localeStore = useLocaleStore()
const isEnglishLocale = computed(() => localeStore.getCurrentLocale.lang === 'en')
const localizeUiText = (text: string) => (isEnglishLocale.value ? translateUiText(text) : text)
const MATERIAL_PURPOSE_ECOMMERCE = 'ECOMMERCE'
const MATERIAL_PURPOSE_LEAD_GENERATION = 'LEAD_GENERATION'
type MaterialPurpose = typeof MATERIAL_PURPOSE_ECOMMERCE | typeof MATERIAL_PURPOSE_LEAD_GENERATION
const materialPurposeOptions = [
  { label: '电商素材库', value: MATERIAL_PURPOSE_ECOMMERCE },
  { label: '引流素材库', value: MATERIAL_PURPOSE_LEAD_GENERATION }
] as const
const activeMaterialPurpose = ref<MaterialPurpose>(MATERIAL_PURPOSE_ECOMMERCE)
const normalizeMaterialPurpose = (value?: string): MaterialPurpose =>
  value === MATERIAL_PURPOSE_LEAD_GENERATION ? MATERIAL_PURPOSE_LEAD_GENERATION : MATERIAL_PURPOSE_ECOMMERCE
const getMaterialPurposeLabel = (value?: string) =>
  localizeUiText(
    materialPurposeOptions.find((item) => item.value === normalizeMaterialPurpose(value))?.label ||
      '电商素材库'
  )
const activePurposeDescription = computed(() =>
  activeMaterialPurpose.value === MATERIAL_PURPOSE_LEAD_GENERATION
    ? '围绕 S1-S8 检查引流素材完整度，快速发现缺失分组并维护视频用途。'
    : '集中管理电商混剪素材库，按分类、场景和用途维护可用视频。'
)
const loading = ref(false)
const list = ref<TkMaterialLibraryVO[]>([])
const librariesWithId = computed(() => list.value.filter((item) => item.id !== undefined) as Array<TkMaterialLibraryVO & { id: number }>)
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  category: undefined,
  materialPurpose: activeMaterialPurpose.value,
  status: undefined
})

const videoLoading = ref(false)
const videoList = ref<TkMaterialVideoVO[]>([])
const videoTotal = ref(0)
const brokenVideoCovers = reactive<Record<string, boolean>>({})
const previewDialogVisible = ref(false)
const previewVideo = ref<TkMaterialVideoVO>()
const getVideoStatusLabel = (status: string) => {
  const labelMap: Record<string, string> = {
    UPLOADING: '上传中',
    PARSING: '解析中',
    AVAILABLE: '成功',
    FAILED: '失败'
  }
  return labelMap[status] || status
}
const getVideoStatusType = (status: string) => {
  const typeMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    UPLOADING: 'info',
    PARSING: 'warning',
    AVAILABLE: 'success',
    FAILED: 'danger'
  }
  return typeMap[status] || 'info'
}
const usagePhaseOptions = [
  { label: '吸引注意', value: 'ATTENTION', type: 'warning' },
  { label: '产品展示', value: 'PRODUCT_SHOW', type: 'success' },
  { label: '使用效果', value: 'RESULT_EFFECT', type: 'danger' },
  { label: '通用素材', value: 'GENERAL', type: 'info' }
] as const
const segmentTypeOptions = [
  { label: 'S1 黄金3秒', shortLabel: 'S1', value: 'S1_HOOK', type: 'warning', keySegment: true },
  { label: 'S2 痛点场景', shortLabel: 'S2', value: 'S2_PAIN', type: 'danger', keySegment: false },
  { label: 'S3 产品亮相', shortLabel: 'S3', value: 'S3_REVEAL', type: 'success', keySegment: true },
  { label: 'S4 使用演示', shortLabel: 'S4', value: 'S4_DEMO', type: 'success', keySegment: true },
  { label: 'S5 效果证明', shortLabel: 'S5', value: 'S5_PROOF', type: 'danger', keySegment: true },
  { label: 'S6 细节特写', shortLabel: 'S6', value: 'S6_DETAIL', type: 'info', keySegment: false },
  { label: 'S7 场景融入', shortLabel: 'S7', value: 'S7_LIFESTYLE', type: 'warning', keySegment: false },
  { label: '通用素材', shortLabel: '通用', value: 'GENERAL', type: 'info', keySegment: false }
] as const
type UsagePhaseValue = (typeof usagePhaseOptions)[number]['value']
type SegmentTypeValue = (typeof segmentTypeOptions)[number]['value']
type SegmentDisplayOption = {
  label: string
  shortLabel: string
  value: SegmentTypeValue
}
const leadGenerationSegmentLabels: Partial<Record<SegmentTypeValue, { label: string; shortLabel: string }>> = {
  S1_HOOK: { label: 'S1 黄金开场', shortLabel: 'S1' },
  S2_PAIN: { label: 'S2 背景交代', shortLabel: 'S2' },
  S3_REVEAL: { label: 'S3 场景进入', shortLabel: 'S3' },
  S4_DEMO: { label: 'S4 过程展示', shortLabel: 'S4' },
  S5_PROOF: { label: 'S5 结果证明', shortLabel: 'S5' },
  S6_DETAIL: { label: 'S6 案例验证', shortLabel: 'S6' },
  S7_LIFESTYLE: { label: 'S7 工具展示', shortLabel: 'S7' },
  GENERAL: { label: 'S8 转化引导', shortLabel: 'S8' }
}
const normalizeUsagePhase = (value?: string): UsagePhaseValue => {
  const option = usagePhaseOptions.find((item) => item.value === value)
  return option?.value || 'GENERAL'
}
const normalizeSegmentType = (value?: string): SegmentTypeValue => {
  const option = segmentTypeOptions.find((item) => item.value === value)
  return option?.value || 'GENERAL'
}
const routeQueryText = (value: unknown) => {
  if (Array.isArray(value)) {
    return value[0] ? String(value[0]) : ''
  }
  return value ? String(value) : ''
}
const getSegmentTypeLabel = (value?: string) => {
  const normalized = normalizeSegmentType(value)
  const option = segmentTypeOptions.find((item) => item.value === normalized)
  return option ? getSegmentOptionLabel(option) : localizeUiText('通用素材')
}
const getSegmentOptionLabel = (item: SegmentDisplayOption) => {
  const leadLabel = leadGenerationSegmentLabels[item.value]
  return localizeUiText(
    activeMaterialPurpose.value === MATERIAL_PURPOSE_LEAD_GENERATION && leadLabel
      ? leadLabel.label
      : item.label
  )
}
const getSegmentOptionShortLabel = (item: SegmentDisplayOption) => {
  const leadLabel = leadGenerationSegmentLabels[item.value]
  return localizeUiText(
    activeMaterialPurpose.value === MATERIAL_PURPOSE_LEAD_GENERATION && leadLabel
      ? leadLabel.shortLabel
      : item.shortLabel
  )
}
const getSegmentTypeName = (item: SegmentDisplayOption) =>
  getSegmentOptionLabel(item).startsWith(`${getSegmentOptionShortLabel(item)} `)
    ? getSegmentOptionLabel(item).slice(getSegmentOptionShortLabel(item).length + 1)
    : getSegmentOptionLabel(item)
const selectedLibrary = ref<TkMaterialLibraryVO>()
const materialVideosTitle = computed(() =>
  selectedLibrary.value
    ? `${localizeUiText('素材视频')} · ${selectedLibrary.value.name}`
    : localizeUiText('素材视频')
)
const videoQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  libraryId: undefined,
  usagePhase: undefined as string | undefined,
  segmentType: undefined as string | undefined
})
const videoSelection = ref<TkMaterialVideoVO[]>([])
const selectedVideoIds = computed(() => videoSelection.value.map((item) => item.id).filter(Boolean))
const batchSegmentType = ref<SegmentTypeValue>('S4_DEMO')
const batchSegmentUpdating = ref(false)
const batchDeleting = ref(false)
const rowSegmentUpdatingIds = ref<number[]>([])
const segmentSummaryCounts = ref<Record<string, number>>({})
const isLeadGenerationLibrary = computed(
  () => normalizeMaterialPurpose(selectedLibrary.value?.materialPurpose || activeMaterialPurpose.value) === MATERIAL_PURPOSE_LEAD_GENERATION
)
const isRequiredSegment = (segmentType: SegmentTypeValue) =>
  isLeadGenerationLibrary.value
    ? segmentTypeOptions.some((item) => item.value === segmentType)
    : ['S1_HOOK', 'S3_REVEAL', 'S4_DEMO', 'S5_PROOF'].includes(segmentType)
const segmentSummary = computed(() => {
  return segmentTypeOptions.map((item) => ({
    ...item,
    keySegment: isRequiredSegment(item.value),
    count: segmentSummaryCounts.value[item.value] || 0
  }))
})
const missingKeySegments = computed(() =>
  segmentSummary.value.filter((item) => item.keySegment && item.count === 0)
)
const keySegmentTotal = computed(() => segmentSummary.value.filter((item) => item.keySegment).length)
const completedKeySegmentCount = computed(
  () => segmentSummary.value.filter((item) => item.keySegment && item.count > 0).length
)
const missingKeySegmentCount = computed(() => missingKeySegments.value.length)
const currentLibraryVideoCount = computed(() => selectedLibrary.value?.videoCount ?? videoTotal.value)
const missingKeySegmentLabels = computed(() =>
  missingKeySegments.value.map((item) => getSegmentOptionLabel(item)).join(isEnglishLocale.value ? ', ' : '、')
)
const missingKeySegmentsTitle = computed(() =>
  isEnglishLocale.value
    ? `${isLeadGenerationLibrary.value ? 'Missing S1-S8 segments' : 'Missing key uses'}: ${missingKeySegmentLabels.value}. Generation will be blocked by missing uses. Complete or relabel materials before generation.`
    : isLeadGenerationLibrary.value
      ? `S1-S8 缺少：${missingKeySegmentLabels.value}。生成会按缺失分段阻断，请补齐或重新标记素材用途。`
      : `关键用途缺少：${missingKeySegmentLabels.value}。生成会按缺失用途阻断，请补齐或重新标记素材用途。`
)
const keySegmentRuleDescription = computed(() =>
  isEnglishLocale.value
    ? isLeadGenerationLibrary.value
      ? 'Lead-gen videos require S1-S8 materials.'
      : 'E-commerce videos mainly require S1, S3, S4, and S5 materials. Other groups are supplementary.'
    : isLeadGenerationLibrary.value
      ? '引流视频要求 S1-S8 素材都已补齐。'
      : '电商视频主要依赖 S1、S3、S4、S5 关键素材，其他分组作为补充素材。'
)
const completenessMetricTitle = computed(() =>
  isLeadGenerationLibrary.value ? localizeUiText('S1-S8 完整度') : localizeUiText('关键完整度')
)
const selectedLibraryHealthPercent = computed(() =>
  keySegmentTotal.value ? Math.round((completedKeySegmentCount.value / keySegmentTotal.value) * 100) : 0
)
const getLibraryHealthPercent = (library: TkMaterialLibraryVO) =>
  selectedLibrary.value?.id === library.id ? selectedLibraryHealthPercent.value : 0
const getLibraryHealthText = (library: TkMaterialLibraryVO) => {
  if (selectedLibrary.value?.id !== library.id) {
    return isEnglishLocale.value ? 'Select to inspect completeness' : '选择后查看完整度'
  }
  return isEnglishLocale.value
    ? isLeadGenerationLibrary.value
      ? `S1-S8 completeness ${completedKeySegmentCount.value}/${keySegmentTotal.value}`
      : `Key completeness ${completedKeySegmentCount.value}/${keySegmentTotal.value}`
    : isLeadGenerationLibrary.value
      ? `S1-S8 完整度 ${completedKeySegmentCount.value}/${keySegmentTotal.value}`
      : `关键用途 ${completedKeySegmentCount.value}/${keySegmentTotal.value}`
}

const placeholderPattern = /^\?+$/
const cleanText = (value?: string) => {
  const text = typeof value === 'string' ? value.trim() : ''
  return text && !placeholderPattern.test(text) && !text.includes('??') ? text : ''
}
const displayText = (value?: string, fallback = '-') => cleanText(value) || fallback

const normalizeLibrary = (item: TkMaterialLibraryVO): TkMaterialLibraryVO => ({
  ...item,
  category: cleanText(item.category),
  scene: cleanText(item.scene),
  materialPurpose: normalizeMaterialPurpose(item.materialPurpose),
  tags: cleanText(item.tags),
  description: cleanText(item.description)
})

const normalizeVideo = (item: TkMaterialVideoVO): TkMaterialVideoVO => ({
  ...item,
  resolution: cleanText(item.resolution),
  tags: cleanText(item.tags),
  failReason: cleanText(item.failReason),
  usagePhase: normalizeUsagePhase(item.usagePhase),
  segmentType: normalizeSegmentType(item.segmentType)
})

const coverErrorKey = (video: TkMaterialVideoVO) => `${video.id}:${video.coverUrl || ''}`

const syncBrokenVideoCoverState = (videos: TkMaterialVideoVO[]) => {
  const activeKeys = new Set(videos.filter((video) => video.coverUrl).map(coverErrorKey))
  Object.keys(brokenVideoCovers).forEach((key) => {
    if (!activeKeys.has(key)) {
      delete brokenVideoCovers[key]
    }
  })
}

const markVideoCoverBroken = (video: TkMaterialVideoVO) => {
  if (video.coverUrl) {
    brokenVideoCovers[coverErrorKey(video)] = true
  }
}

const openVideoPreview = (video: TkMaterialVideoVO) => {
  if (!video.fileUrl) {
    message.warning('当前视频暂无可预览地址')
    return
  }
  previewVideo.value = video
  previewDialogVisible.value = true
}

const handlePreviewSegmentTypeChange = async (segmentType: SegmentTypeValue) => {
  if (!previewVideo.value) return
  await handleRowSegmentTypeChange(previewVideo.value, segmentType)
}

const getList = async () => {
  loading.value = true
  try {
    queryParams.materialPurpose = activeMaterialPurpose.value
    const data = await TkMaterialApi.getLibraryPage(queryParams)
    list.value = data.list.map(normalizeLibrary)
    total.value = data.total
    if (!list.value.length) {
      selectedLibrary.value = undefined
      videoQuery.libraryId = undefined
      videoList.value = []
      videoTotal.value = 0
      segmentSummaryCounts.value = {}
      return
    }
    const current = selectedLibrary.value?.id
      ? list.value.find((item) => item.id === selectedLibrary.value?.id)
      : undefined
    if (current) {
      selectedLibrary.value = current
      videoQuery.libraryId = current.id as any
      await Promise.all([getVideoList(), getSegmentSummary()])
    } else {
      selectedLibrary.value = list.value[0]
      videoQuery.libraryId = list.value[0].id as any
      await Promise.all([getVideoList(), getSegmentSummary()])
    }
  } finally {
    loading.value = false
  }
}

const getVideoList = async () => {
  videoLoading.value = true
  try {
    const data = await TkMaterialApi.getVideoPage(videoQuery)
    const videos = data.list.map(normalizeVideo)
    videoList.value = videos
    videoTotal.value = data.total
    videoSelection.value = []
    syncBrokenVideoCoverState(videos)
  } finally {
    videoLoading.value = false
  }
}

const getSegmentSummary = async () => {
  if (!selectedLibrary.value?.id) {
    segmentSummaryCounts.value = {}
    return
  }
  const data = await TkMaterialApi.getSegmentSummary(selectedLibrary.value.id)
  segmentSummaryCounts.value = (data || []).reduce(
    (result: Record<string, number>, item: TkMaterialVideoSegmentSummaryVO) => {
      result[normalizeSegmentType(item.segmentType)] = Number(item.count) || 0
      return result
    },
    {}
  )
}

const handleVideoQuery = () => {
  videoQuery.pageNo = 1
  getVideoList()
}

const handleSegmentCardClick = (segmentType: SegmentTypeValue) => {
  videoQuery.segmentType = videoQuery.segmentType === segmentType ? undefined : segmentType
  handleVideoQuery()
}

const handleQuery = () => {
  queryParams.pageNo = 1
  queryParams.materialPurpose = activeMaterialPurpose.value
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.materialPurpose = activeMaterialPurpose.value
  handleQuery()
}

const handleMaterialPurposeChange = () => {
  queryParams.pageNo = 1
  queryParams.materialPurpose = activeMaterialPurpose.value
  selectedLibrary.value = undefined
  videoQuery.libraryId = undefined
  videoQuery.segmentType = undefined
  videoList.value = []
  videoTotal.value = 0
  segmentSummaryCounts.value = {}
  getList()
}

const handleSelectLibrary = async (row: TkMaterialLibraryVO) => {
  selectedLibrary.value = row
  videoQuery.libraryId = row.id as any
  videoQuery.pageNo = 1
  await Promise.all([getVideoList(), getSegmentSummary()])
}

const handleVideoSelectionChange = (selection: TkMaterialVideoVO[]) => {
  videoSelection.value = selection
}

const getRequestErrorText = (error: any) => {
  const status = error?.response?.status
  const responseMessage = error?.response?.data?.msg
  const messageText = error?.message
  if (status) {
    return `请求状态 ${status}${responseMessage ? `：${responseMessage}` : ''}`
  }
  return responseMessage || messageText || '请求失败'
}

const handleBatchSegmentType = async () => {
  if (!selectedVideoIds.value.length) {
    message.warning('请先选择素材视频')
    return
  }
  batchSegmentUpdating.value = true
  try {
    await TkMaterialApi.updateVideoSegmentType({
      ids: selectedVideoIds.value,
      segmentType: batchSegmentType.value
    })
    message.success(`已将 ${selectedVideoIds.value.length} 个素材改为 ${getSegmentTypeLabel(batchSegmentType.value)}`)
    videoSelection.value = []
    await Promise.all([getVideoList(), getSegmentSummary()])
  } catch (error) {
    message.error(`用途修改失败：${getRequestErrorText(error)}；素材ID：${selectedVideoIds.value.join(', ')}`)
  } finally {
    batchSegmentUpdating.value = false
  }
}

const handleBatchDeleteVideos = async () => {
  if (!selectedVideoIds.value.length) {
    message.warning('请先选择素材视频')
    return
  }
  await message.delConfirm(`确认删除选中的 ${selectedVideoIds.value.length} 条素材吗？`)
  batchDeleting.value = true
  try {
    await TkMaterialApi.deleteVideos(selectedVideoIds.value)
    message.success(`已删除 ${selectedVideoIds.value.length} 条素材`)
    videoSelection.value = []
    await Promise.all([getList(), getVideoList(), getSegmentSummary()])
  } catch (error) {
    message.error(`批量删除失败：${getRequestErrorText(error)}；素材ID：${selectedVideoIds.value.join(', ')}`)
  } finally {
    batchDeleting.value = false
  }
}

const isSegmentUpdating = (id: number) => rowSegmentUpdatingIds.value.includes(id)

const handleRowSegmentTypeChange = async (row: TkMaterialVideoVO, segmentType: SegmentTypeValue) => {
  if (row.segmentType === segmentType) {
    return
  }
  rowSegmentUpdatingIds.value.push(row.id)
  const previousSegmentType = row.segmentType
  row.segmentType = segmentType
  try {
    await TkMaterialApi.updateVideoSegmentType({
      ids: [row.id],
      segmentType
    })
    message.success(`已改为 ${getSegmentTypeLabel(segmentType)}`)
    await Promise.all([getVideoList(), getSegmentSummary()])
  } catch (error) {
    row.segmentType = previousSegmentType
    message.error(`用途修改失败：${getRequestErrorText(error)}；素材ID：${row.id}`)
  } finally {
    rowSegmentUpdatingIds.value = rowSegmentUpdatingIds.value.filter((id) => id !== row.id)
  }
}

const libraryDialogVisible = ref(false)
const formLoading = ref(false)
const libraryFormRef = ref()
const libraryForm = ref<TkMaterialLibraryVO>({
  name: '',
  materialPurpose: MATERIAL_PURPOSE_ECOMMERCE,
  category: '',
  scene: '',
  tags: '',
  description: '',
  defaulted: false,
  status: 0
})
const libraryRules = reactive({
  name: [{ required: true, message: '素材库名称不能为空', trigger: 'blur' }],
  materialPurpose: [{ required: true, message: '请选择素材类型', trigger: 'change' }]
})

const openLibraryForm = (row?: TkMaterialLibraryVO) => {
  libraryDialogVisible.value = true
  libraryForm.value = row
    ? { ...row }
    : {
        name: '',
        materialPurpose: activeMaterialPurpose.value,
        category: '',
        scene: '',
        tags: '',
        description: '',
        defaulted: false,
        status: 0
      }
}

const submitLibraryForm = async () => {
  await libraryFormRef.value.validate()
  formLoading.value = true
  try {
    if (libraryForm.value.id) {
      await TkMaterialApi.updateLibrary(libraryForm.value)
      message.success('更新成功')
    } else {
      await TkMaterialApi.createLibrary(libraryForm.value)
      message.success('创建成功')
    }
    libraryDialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

const handleDeleteLibrary = async (id: number) => {
  await message.delConfirm()
  await TkMaterialApi.deleteLibrary(id)
  message.success('删除成功')
  selectedLibrary.value = undefined
  await getList()
}

const uploadDialogVisible = ref(false)
const uploadLoading = ref(false)
const uploadFormRef = ref()
const folderInputRef = ref<HTMLInputElement>()
const uploadFileList = ref<UploadUserFile[]>([])
const MAX_BATCH_UPLOAD_COUNT = 10
const MAX_FOLDER_UPLOAD_COUNT = 200
const MAX_UPLOAD_FILE_SIZE = 100 * 1024 * 1024
const DEFAULT_UPLOAD_CHUNK_SIZE = 8 * 1024 * 1024
const UPLOAD_FILE_CONCURRENCY = 2
const UPLOAD_CHUNK_RETRY_COUNT = 2
const ALLOWED_UPLOAD_EXTENSIONS = ['mp4', 'mov', 'webm']
type UploadItemStatus = 'ready' | 'uploading' | 'merging' | 'success' | 'failed'
type UploadQueueItem = {
  uid: number
  name: string
  size: number
  file: File
  status: UploadItemStatus
  progress: number
  error?: string
}
type FolderUploadSummary = {
  validCount: number
  addedCount: number
  ignoredCount: number
  oversizedCount: number
  limitedCount: number
}
const uploadQueue = ref<UploadQueueItem[]>([])
const folderUploadSummary = ref<FolderUploadSummary>()
const uploadForm = ref<{
  libraryId?: number
  tags: string
  usagePhase: UsagePhaseValue
  segmentType: SegmentTypeValue
}>({
  libraryId: undefined,
  tags: '',
  usagePhase: 'GENERAL',
  segmentType: 'GENERAL'
})
const uploadRules = reactive({
  libraryId: [{ required: true, message: '请选择素材库', trigger: 'change' }],
  usagePhase: [{ required: true, message: '请选择素材用途', trigger: 'change' }],
  segmentType: [{ required: true, message: '请选择素材用途', trigger: 'change' }]
})

const openUploadForm = (row?: TkMaterialLibraryVO, segmentType: SegmentTypeValue = 'GENERAL') => {
  const targetLibrary = row || selectedLibrary.value || librariesWithId.value[0]
  uploadDialogVisible.value = true
  uploadForm.value = {
    libraryId: targetLibrary?.id,
    tags: '',
    usagePhase: 'GENERAL',
    segmentType
  }
  uploadFileList.value = []
  uploadQueue.value = []
  folderUploadSummary.value = undefined
  if (folderInputRef.value) {
    folderInputRef.value.value = ''
  }
}

const getUploadExtension = (fileName: string) => fileName.split('.').pop()?.toLowerCase() || ''

const isAllowedUploadFileName = (fileName: string) =>
  ALLOWED_UPLOAD_EXTENSIONS.includes(getUploadExtension(fileName))

const validateUploadFile = (file: UploadUserFile) => {
  const rawFile = file.raw
  if (!rawFile) return false
  if (!isAllowedUploadFileName(file.name)) {
    message.warning(`${file.name} 文件格式不支持，仅支持 mp4、mov、webm`)
    return false
  }
  if (rawFile.size > MAX_UPLOAD_FILE_SIZE) {
    message.warning(`${file.name} 超过 100MB，无法上传`)
    return false
  }
  return true
}

const syncUploadQueue = (files: UploadUserFile[]) => {
  let validFiles = files.filter(validateUploadFile)
  if (validFiles.length > MAX_BATCH_UPLOAD_COUNT) {
    validFiles = validFiles.slice(0, MAX_BATCH_UPLOAD_COUNT)
    message.warning(`单次最多选择 ${MAX_BATCH_UPLOAD_COUNT} 个视频文件，已保留前 ${MAX_BATCH_UPLOAD_COUNT} 个`)
  }
  uploadFileList.value = validFiles
  const currentMap = new Map(uploadQueue.value.map((item) => [item.uid, item]))
  uploadQueue.value = validFiles
    .filter((file) => file.raw)
    .map((file) => {
      const uid = Number(file.uid)
      const current = currentMap.get(uid)
      if (current && current.status !== 'success') {
        return current
      }
      return {
        uid,
        name: file.name,
        size: file.size || file.raw?.size || 0,
        file: file.raw as File,
        status: current?.status === 'success' ? 'success' : 'ready',
        progress: current?.status === 'success' ? 100 : 0,
        error: ''
      }
    })
  folderUploadSummary.value = undefined
}

const handleFileChange = (_file: UploadFile, files: UploadUserFile[]) => {
  syncUploadQueue(files)
}

const handleFileRemove = (_file: UploadFile, files: UploadUserFile[]) => {
  syncUploadQueue(files)
}

const handleFileExceed = () => {
  message.warning(`单次最多选择 ${MAX_BATCH_UPLOAD_COUNT} 个视频文件`)
}

const createFolderUploadItem = (file: File, uid: number): UploadQueueItem => {
  const relativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath
  return {
    uid,
    name: relativePath || file.name,
    size: file.size,
    file,
    status: 'ready',
    progress: 0,
    error: ''
  }
}

const appendFolderFilesToUploadQueue = (files: File[]) => {
  const ignoredCount = files.filter((file) => !isAllowedUploadFileName(file.name)).length
  const allowedFiles = files.filter((file) => isAllowedUploadFileName(file.name))
  const oversizedCount = allowedFiles.filter((file) => file.size > MAX_UPLOAD_FILE_SIZE).length
  const validFiles = allowedFiles.filter((file) => file.size <= MAX_UPLOAD_FILE_SIZE)
  const selectedFiles = validFiles.slice(0, MAX_FOLDER_UPLOAD_COUNT)
  const limitedCount = Math.max(0, validFiles.length - selectedFiles.length)
  const startedAt = Date.now()
  uploadFileList.value = []
  uploadQueue.value = selectedFiles.map((file, index) => createFolderUploadItem(file, startedAt + index))
  folderUploadSummary.value = {
    validCount: validFiles.length,
    addedCount: selectedFiles.length,
    ignoredCount,
    oversizedCount,
    limitedCount
  }
  if (limitedCount) {
    message.warning(`文件夹最多上传 ${MAX_FOLDER_UPLOAD_COUNT} 个视频，已保留前 ${MAX_FOLDER_UPLOAD_COUNT} 个`)
  }
}

const triggerFolderPicker = () => {
  if (uploadLoading.value) return
  folderInputRef.value?.click()
}

const handleFolderFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return
  appendFolderFilesToUploadQueue(files)
  input.value = ''
}

const getUploadStatusLabel = (status: UploadItemStatus) => {
  const labelMap: Record<UploadItemStatus, string> = {
    ready: '待上传',
    uploading: '上传中',
    merging: '合并中',
    success: '已完成',
    failed: '失败'
  }
  return labelMap[status]
}

const getUploadStatusType = (status: UploadItemStatus) => {
  const typeMap: Record<UploadItemStatus, 'success' | 'warning' | 'danger' | 'info'> = {
    ready: 'info',
    uploading: 'warning',
    merging: 'warning',
    success: 'success',
    failed: 'danger'
  }
  return typeMap[status]
}

const hasFailedUpload = computed(() => uploadQueue.value.some((item) => item.status === 'failed'))
const uploadSuccessCount = computed(() => uploadQueue.value.filter((item) => item.status === 'success').length)
const uploadFailedCount = computed(() => uploadQueue.value.filter((item) => item.status === 'failed').length)
const uploadOverallProgress = computed(() => {
  if (!uploadQueue.value.length) return 0
  const totalProgress = uploadQueue.value.reduce((sum, item) => sum + item.progress, 0)
  return Math.round(totalProgress / uploadQueue.value.length)
})
const uploadSummaryText = computed(() => {
  const done = uploadQueue.value.filter((item) => ['success', 'failed'].includes(item.status)).length
  return `上传中 ${done}/${uploadQueue.value.length}`
})

const runUploadQueue = async (targets: UploadQueueItem[]) => {
  const pending = [...targets]
  const workers = Array.from({ length: Math.min(UPLOAD_FILE_CONCURRENCY, pending.length) }, async () => {
    while (pending.length) {
      const item = pending.shift()
      if (item) {
        await uploadSingleFile(item)
      }
    }
  })
  await Promise.all(workers)
}

const isUploadTimeoutError = (error: any) => {
  const message = String(error?.message || error?.msg || '')
  return error?.code === 'ECONNABORTED' || message.toLowerCase().includes('timeout')
}

const uploadSingleFile = async (item: UploadQueueItem) => {
  item.status = 'uploading'
  item.progress = Math.max(item.progress, 1)
  item.error = ''
  let uploadId = ''
  try {
    const session = await TkMaterialApi.createMaterialVideoUploadSession({
      libraryId: uploadForm.value.libraryId as number,
      fileName: item.file.name || item.name,
      fileSize: item.file.size,
      contentType: item.file.type
    })
    uploadId = session.uploadId
    if (session.uploadMode === 'oss') {
      await uploadOssFileWithRetry(item, session)
      item.status = 'merging'
      item.progress = 99
      await TkMaterialApi.completeMaterialVideoUpload({
        uploadId,
        libraryId: uploadForm.value.libraryId as number,
        fileName: item.file.name || item.name,
        fileSize: item.file.size,
        contentType: item.file.type,
        objectKey: session.objectKey,
        fileUrl: session.publicUrl,
        tags: uploadForm.value.tags || '',
        usagePhase: uploadForm.value.usagePhase,
        segmentType: uploadForm.value.segmentType
      })
      item.status = 'success'
      item.progress = 100
      return
    }
    const chunkSize = session.chunkSize || DEFAULT_UPLOAD_CHUNK_SIZE
    const totalChunks = session.totalChunks || Math.ceil(item.file.size / chunkSize)
    const uploadedChunks = new Set(session.uploadedChunks || [])
    let completedBytes = session.uploadedSize || 0
    item.progress = Math.max(item.progress, Math.min(98, Math.round((completedBytes * 100) / item.file.size)))
    for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
      const start = chunkIndex * chunkSize
      const end = Math.min(start + chunkSize, item.file.size)
      if (uploadedChunks.has(chunkIndex)) continue
      const chunk = item.file.slice(start, end)
      await uploadChunkWithRetry(item, uploadId, chunkIndex, chunk, completedBytes)
      completedBytes += chunk.size
      item.progress = Math.min(98, Math.round((completedBytes * 100) / item.file.size))
    }
    item.status = 'merging'
    item.progress = 99
    await TkMaterialApi.completeMaterialVideoUpload({
      uploadId,
      tags: uploadForm.value.tags || '',
      usagePhase: uploadForm.value.usagePhase,
      segmentType: uploadForm.value.segmentType
    })
    item.status = 'success'
    item.progress = 100
  } catch (error: any) {
    if (uploadId) {
      TkMaterialApi.cancelMaterialVideoUpload(uploadId).catch(() => undefined)
    }
    item.status = 'failed'
    item.progress = item.progress || 0
    item.error = isUploadTimeoutError(error)
      ? '上传等待超时，服务端可能仍在保存或解析，请稍后刷新素材列表确认后再重试'
      : error?.msg || error?.message || '上传失败'
  }
}

const uploadOssFileWithRetry = async (item: UploadQueueItem, session: any) => {
  let lastError: any
  for (let attempt = 0; attempt <= UPLOAD_CHUNK_RETRY_COUNT; attempt++) {
    try {
      await uploadOssFile(item, session)
      return
    } catch (error) {
      lastError = error
      if (attempt < UPLOAD_CHUNK_RETRY_COUNT) {
        item.error = `上传到云存储失败，正在重试第 ${attempt + 1} 次`
        await sleep(1200 * (attempt + 1))
      }
    }
  }
  throw lastError
}

const uploadOssFile = async (item: UploadQueueItem, session: any) => {
  if (!session.uploadUrl || !session.objectKey || !session.policy || !session.signature || !session.accessKeyId) {
    throw new Error('OSS 上传会话信息不完整')
  }
  const formData = new FormData()
  formData.append('key', session.objectKey)
  formData.append('policy', session.policy)
  formData.append('OSSAccessKeyId', session.accessKeyId)
  formData.append('signature', session.signature)
  formData.append('success_action_status', session.successActionStatus || '200')
  if (item.file.type) {
    formData.append('Content-Type', item.file.type)
  }
  formData.append('file', item.file)
  await axios.post(session.uploadUrl, formData, {
    timeout: MATERIAL_VIDEO_UPLOAD_TIMEOUT,
    onUploadProgress: (event: AxiosProgressEvent) => {
      if (!event.total) return
      item.progress = Math.min(98, Math.round((event.loaded * 100) / event.total))
    }
  })
  item.progress = Math.max(item.progress, 98)
}

const uploadChunkWithRetry = async (
  item: UploadQueueItem,
  uploadId: string,
  chunkIndex: number,
  chunk: Blob,
  completedBytes: number
) => {
  let lastError: any
  for (let attempt = 0; attempt <= UPLOAD_CHUNK_RETRY_COUNT; attempt++) {
    try {
      const formData = new FormData()
      formData.append('uploadId', uploadId)
      formData.append('chunkIndex', String(chunkIndex))
      formData.append('chunk', chunk)
      await TkMaterialApi.uploadMaterialVideoChunk(formData, {
        onUploadProgress: (event: AxiosProgressEvent) => {
          if (!event.total) return
          const currentLoaded = Math.min(chunk.size, event.loaded)
          item.progress = Math.min(98, Math.round(((completedBytes + currentLoaded) * 100) / item.file.size))
        }
      })
      return
    } catch (error) {
      lastError = error
      if (attempt < UPLOAD_CHUNK_RETRY_COUNT) {
        await sleep(800 * (attempt + 1))
      }
    }
  }
  throw lastError
}

const sleep = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms))

const refreshAfterUpload = async () => {
  const libraryId = uploadForm.value.libraryId
  if (libraryId) {
    const library = list.value.find((item) => item.id === libraryId)
    if (library) {
      selectedLibrary.value = library
      videoQuery.libraryId = libraryId as any
      videoQuery.pageNo = 1
    }
  }
  await getList()
  await Promise.all([getVideoList(), getSegmentSummary()])
}

const handleUploadDialogClose = (done?: () => void) => {
  if (uploadLoading.value) {
    message.warning('文件上传中，请等待完成后再关闭')
    return
  }
  uploadDialogVisible.value = false
  done?.()
}

const submitUploadForm = async () => {
  await uploadFormRef.value.validate()
  const targets = uploadQueue.value.filter((item) => item.status !== 'success')
  if (!targets.length) {
    message.warning('请选择需要上传的视频文件')
    return
  }
  uploadLoading.value = true
  try {
    await runUploadQueue(targets)
    const successCount = uploadQueue.value.filter((item) => item.status === 'success').length
    const failedCount = uploadQueue.value.filter((item) => item.status === 'failed').length
    if (failedCount) {
      message.warning(`批量上传完成，成功 ${successCount} 个，失败 ${failedCount} 个`)
    } else {
      message.success(`批量上传成功 ${successCount} 个，正在解析视频信息`)
      uploadDialogVisible.value = false
    }
    await refreshAfterUpload()
  } finally {
    uploadLoading.value = false
  }
}

const retryFailedUploads = async () => {
  const failedItems = uploadQueue.value.filter((item) => item.status === 'failed')
  if (!failedItems.length) return
  uploadLoading.value = true
  try {
    failedItems.forEach((item) => {
      item.status = 'ready'
      item.progress = 0
      item.error = ''
    })
    await runUploadQueue(failedItems)
    const stillFailed = uploadQueue.value.filter((item) => item.status === 'failed').length
    if (stillFailed) {
      message.warning(`重试完成，仍有 ${stillFailed} 个文件失败`)
    } else {
      message.success('失败文件已全部上传成功，正在解析视频信息')
      uploadDialogVisible.value = false
    }
    await refreshAfterUpload()
  } finally {
    uploadLoading.value = false
  }
}

const handleDeleteVideo = async (id: number) => {
  await message.delConfirm()
  await TkMaterialApi.deleteVideo(id)
  message.success('删除成功')
  await getList()
  await Promise.all([getVideoList(), getSegmentSummary()])
}

const formatSize = (value?: number) => {
  if (!value) return '0 MB'
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB`
}

const formatDuration = (value?: number) => {
  if (!value || value <= 0) return '-'
  const minutes = Math.floor(value / 60)
  const seconds = value % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

let routePresetApplied = false
const applyRoutePreset = async () => {
  if (routePresetApplied) {
    return
  }
  const libraryId = Number(routeQueryText(route.query.libraryId))
  if (libraryId) {
    const targetLibrary = list.value.find((item) => item.id === libraryId)
    if (targetLibrary) {
      selectedLibrary.value = targetLibrary
      videoQuery.libraryId = targetLibrary.id as any
      videoQuery.pageNo = 1
      await Promise.all([getVideoList(), getSegmentSummary()])
    }
  }
  const segmentTypeText = routeQueryText(route.query.segmentType)
  if (segmentTypeText) {
    openUploadForm(selectedLibrary.value, normalizeSegmentType(segmentTypeText))
    routePresetApplied = true
  }
}

onMounted(async () => {
  const routeMaterialPurpose = routeQueryText(route.query.materialPurpose)
  if (routeMaterialPurpose) {
    activeMaterialPurpose.value = normalizeMaterialPurpose(routeMaterialPurpose)
    queryParams.materialPurpose = activeMaterialPurpose.value
  }
  await getList()
  await applyRoutePreset()
})
</script>

<style scoped>
.material-workbench {
  background: #f3f6fb;
}
.workbench-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 2px 0 14px;
  border-bottom: 1px solid #e5e7eb;
}
.workbench-kicker {
  margin-bottom: 8px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
}
.workbench-hero h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  line-height: 30px;
}
.workbench-hero p {
  margin: 6px 0 0;
  color: #667085;
}
.workbench-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.material-purpose-tabs {
  min-width: 260px;
}
.material-purpose-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.hero-buttons,
.video-actions,
.detail-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.workbench-filters {
  margin-top: 12px;
  padding: 10px 14px 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(15, 23, 42, .04);
}
.ops-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;
}
.ops-metric {
  min-height: 76px;
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 26px rgba(15, 23, 42, .05);
}
.ops-metric span {
  display: block;
  margin-bottom: 8px;
  color: #667085;
  font-size: 13px;
}
.ops-metric strong {
  color: #111827;
  font-size: 26px;
  line-height: 32px;
}
.ops-metric.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}
.ops-metric.warning strong {
  color: #c2410c;
}
.workbench-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
.library-panel,
.library-detail {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 16px 42px rgba(15, 23, 42, .06);
}
.library-panel {
  padding: 14px;
}
.panel-head,
.detail-head,
.section-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.panel-head h3,
.detail-head h3,
.section-head h3 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  line-height: 26px;
}
.panel-head p,
.detail-head p,
.section-head p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
}
.library-card-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}
.library-card {
  width: 100%;
  padding: 12px;
  text-align: left;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease, background .16s ease;
}
.library-card:hover,
.library-card.active {
  transform: translateY(-1px);
  border-color: #3b82f6;
  background: #f8fbff;
  box-shadow: 0 12px 30px rgba(37, 99, 235, .12);
}
.library-card__main {
  display: flex;
  gap: 10px;
  min-width: 0;
  align-items: flex-start;
}
.library-card__main :deep(.iconify) {
  margin-top: 2px;
  color: #2563eb;
  font-size: 22px;
}
.library-card__main strong,
.library-card__main span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.library-card__main strong {
  color: #111827;
  font-size: 14px;
}
.library-card__main span,
.library-card__meta,
.library-card__footer {
  color: #667085;
  font-size: 12px;
}
.library-card__meta {
  display: flex;
  gap: 8px;
  margin: 10px 0;
}
.library-card__footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.library-card__health {
  margin-top: 10px;
}
.library-card__health span {
  display: block;
  margin-bottom: 6px;
  color: #475467;
  font-size: 12px;
  font-weight: 600;
}
.library-pagination {
  display: flex;
  justify-content: center;
  margin-top: 14px;
  overflow: hidden;
}
.library-pagination :deep(.el-pagination__total) {
  margin-right: 8px;
  font-size: 12px;
}
.library-pagination :deep(.btn-prev),
.library-pagination :deep(.btn-next),
.library-pagination :deep(.el-pager li) {
  min-width: 28px;
}
.library-detail {
  padding: 16px;
}
.detail-title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.detail-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 14px 0;
}
.detail-summary div {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .8);
}
.detail-summary span {
  display: block;
  margin-bottom: 6px;
  color: #667085;
  font-size: 12px;
}
.detail-summary strong {
  color: #111827;
  font-size: 16px;
}
.segment-control {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #667085;
  font-size: 12px;
}
.segment-health {
  display: grid;
  grid-template-columns: repeat(8, minmax(64px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.segment-health__item {
  min-height: 82px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 9px 10px;
  background: #f8fafc;
  color: #475467;
  text-align: left;
  cursor: pointer;
  transition: transform .16s ease, border-color .16s ease, box-shadow .16s ease, background .16s ease;
}
.segment-health__item:hover {
  transform: translateY(-1px);
  border-color: #93c5fd;
  box-shadow: 0 10px 24px rgba(15, 23, 42, .08);
}
.segment-health__item span,
.segment-health__item strong,
.segment-health__item em {
  display: block;
}
.segment-health__item span {
  color: #2563eb;
  font-weight: 700;
}
.segment-health__item strong {
  margin-top: 4px;
  overflow: hidden;
  color: #111827;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.segment-health__item em {
  margin-top: 6px;
  color: #111827;
  font-size: 18px;
  font-style: normal;
  font-weight: 700;
}
.segment-health__item.active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 10px 24px rgba(37, 99, 235, .12);
}
.segment-health__item.key.empty {
  border-color: #fbbf24;
  background: #fffbeb;
}
.empty-workbench {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #667085;
  text-align: center;
}
.empty-workbench.compact {
  min-height: 260px;
}
.empty-workbench :deep(.iconify) {
  color: #93c5fd;
  font-size: 42px;
}
.empty-workbench strong {
  color: #111827;
  font-size: 16px;
}
.selected-action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
}
.selected-action-bar span {
  color: #1d4ed8;
  font-weight: 600;
}
.segment-alert {
  margin-bottom: 10px;
}
.video-cover {
  width: 58px;
  height: 78px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #eef2ff;
  color: #667085;
  display: flex;
  align-items: center;
  justify-content: center;
}
.video-cover.clickable {
  cursor: pointer;
}
.video-cover.clickable:hover {
  box-shadow: 0 0 0 2px rgba(109, 93, 252, 0.22);
}
.video-cover:disabled {
  cursor: default;
}
.video-cover img,
.video-cover video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.video-cover.empty {
  border: 1px dashed #cbd5e1;
}
.video-cover .iconify {
  font-size: 24px;
}
.video-preview {
  display: grid;
  gap: 16px;
}
.video-preview__player {
  width: 100%;
  max-height: 62vh;
  border-radius: 8px;
  background: #0f172a;
}
.video-preview__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.video-preview__meta div {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px 12px;
  background: #f8fafc;
}
.video-preview__meta span {
  display: block;
  margin-bottom: 5px;
  color: #667085;
  font-size: 12px;
}
.video-preview__meta strong {
  display: block;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-icon { font-size: 34px; color: #6d5dfc; }
.upload-source-actions {
  display: flex;
  width: 100%;
  margin-bottom: 10px;
  align-items: center;
  gap: 10px;
}
.upload-source-actions :deep(.iconify) {
  margin-right: 5px;
}
.upload-source-actions span {
  color: #667085;
  font-size: 12px;
  line-height: 1.4;
}
.folder-input {
  display: none;
}
.folder-upload-summary {
  display: flex;
  width: 100%;
  margin-top: 8px;
  padding: 8px 10px;
  color: #475467;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
}
.folder-upload-summary :deep(.iconify) {
  flex: 0 0 auto;
  margin-top: 1px;
  color: #6d5dfc;
}
.upload-progress-panel {
  width: 100%;
  margin-bottom: 10px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}
.upload-progress-panel__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 10px;
}
.upload-progress-panel__stats span {
  display: block;
  color: #475569;
  font-size: 12px;
}
.upload-progress-panel__stats strong {
  color: #0f172a;
  font-size: 18px;
}
.upload-queue {
  width: 100%;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px;
  background: #f8fafc;
}
.upload-queue__item {
  padding: 10px;
  border-radius: 6px;
  background: #fff;
}
.upload-queue__item + .upload-queue__item {
  margin-top: 8px;
}
.upload-queue__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}
.upload-queue__meta strong {
  display: block;
  max-width: 460px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f2937;
}
.upload-queue__meta span {
  color: #667085;
  font-size: 12px;
}
.upload-queue__error {
  margin-top: 4px;
  color: #dc2626;
  font-size: 12px;
}
@media (max-width: 768px) {
  .workbench-hero,
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .workbench-actions {
    align-items: stretch;
    width: 100%;
  }
  .ops-metrics,
  .workbench-grid,
  .detail-summary {
    grid-template-columns: 1fr;
  }
  .library-panel {
    order: 2;
  }
  .video-actions {
    justify-content: flex-start;
  }
  .segment-health {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
  .video-preview__meta {
    grid-template-columns: 1fr;
  }
}
</style>
