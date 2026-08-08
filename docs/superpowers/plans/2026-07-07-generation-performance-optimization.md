# Generation Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize generation records table performance, polling load, backend generation APIs, and video/file delivery for TK素材工厂.

**Architecture:** Keep the existing Vue3 + Element Plus admin UI and Spring Boot backend. Add a slim list endpoint, a batch status endpoint, detail-on-demand loading, database indexes for high-frequency filters, and file delivery rules that keep large video traffic out of JSON APIs.

**Tech Stack:** Vue 3, TypeScript, Element Plus, Vite, Spring Boot, MyBatis Plus, MySQL, nginx/static file service.

## Global Constraints

- Do not change the existing route paths unless explicitly approved.
- Keep `/tk/generation/page` backward compatible until the new frontend endpoint is deployed.
- Do not return large fields in list APIs: `scriptText`, `clipPlan`, `segmentTimeline`, subtitle layout URLs, and long prompt fields must be detail-only.
- Poll only running tasks visible on the current page.
- Large video files must not be proxied through Java JSON APIs.
- Use repo-bundled tools where possible: `.runtime\npm-global\node_modules\.bin\pnpm.cmd` and `.runtime\apache-maven-3.9.10\bin\mvn.cmd`.

---

## File Structure

- Modify `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/generation/index.vue`
  - Render a slim table.
  - Move heavy fields into a detail drawer.
  - Poll batch status only for visible running tasks.

- Modify `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/generation/index.ts`
  - Add `TkGenerationTaskSummaryVO`.
  - Add `TkGenerationTaskStatusVO`.
  - Add `getGenerationSummaryPage`.
  - Add `getGenerationStatusBatch`.

- Create `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskSummaryRespVO.java`
  - Backend summary response for table rows.

- Create `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskStatusRespVO.java`
  - Backend response for polling status.

- Modify `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/TkGenerationTaskController.java`
  - Add `/page-summary`.
  - Add `/status-batch`.

- Modify `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskService.java`
  - Add summary page and status batch service methods.

- Modify `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java`
  - Implement data-scope-safe summary and status reads.

- Modify `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkGenerationTaskMapper.java`
  - Add selected-column queries for summary page and status batch.

- Create `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_generation_performance_indexes_20260707.sql`
  - Add indexes for tenant/company/status/time filters.

- Modify deployment nginx config under `deploy/` if present for current production config.
  - Add cache, Range, and static video rules for uploads/exports paths.

---

### Task 1: Slim Generation Records Table

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/generation/index.vue`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/generation/index.ts`

**Interfaces:**
- Consumes: `TkGenerationApi.getGenerationSummaryPage(params)`
- Produces: a table using `TkGenerationTaskSummaryVO[]`, with detail loaded via existing `TkGenerationApi.getGeneration(id)`

- [ ] **Step 1: Add frontend summary/status interfaces**

Add to `src/api/tk/generation/index.ts`:

```ts
export interface TkGenerationTaskSummaryVO {
  id?: number
  tenantId?: number
  companyId?: number
  sourceUrl?: string
  libraryId?: number
  openingVideoName?: string
  referenceDuration?: number
  targetDuration?: number
  status?: string
  progress?: number
  outputUrl?: string
  failReason?: string
  failCode?: string
  currentStep?: string
  retryCount?: number
  workerId?: string
  heartbeatTime?: string
  title?: string
  createTime?: string
}

export interface TkGenerationTaskStatusVO {
  id: number
  status?: string
  progress?: number
  outputUrl?: string
  failReason?: string
  failCode?: string
  currentStep?: string
  heartbeatTime?: string
  stepStartedAt?: string
  stepFinishedAt?: string
}
```

- [ ] **Step 2: Add frontend API methods**

Add to `TkGenerationApi` in `src/api/tk/generation/index.ts`:

```ts
getGenerationSummaryPage: async (params: any) => {
  return await request.get({ url: '/tk/generation/page-summary', params })
},
getGenerationStatusBatch: async (ids: number[]): Promise<TkGenerationTaskStatusVO[]> => {
  return await request.get({
    url: '/tk/generation/status-batch',
    params: { ids: ids.join(',') }
  })
}
```

- [ ] **Step 3: Replace generation list type**

In `src/views/tk/generation/index.vue`, change:

```ts
const generationList = ref<TkGenerationTaskVO[]>([])
```

to:

```ts
const generationList = ref<TkGenerationTaskSummaryVO[]>([])
const generationDetailVisible = ref(false)
const generationDetailLoading = ref(false)
const selectedGenerationDetail = ref<TkGenerationTaskVO>()
```

Add the import:

```ts
import type { TkGenerationTaskSummaryVO, TkGenerationTaskStatusVO } from '@/api/tk/generation'
```

- [ ] **Step 4: Load summary page instead of full page**

Change `getGenerationList` to call:

```ts
const data = await TkGenerationApi.getGenerationSummaryPage(generationQuery)
generationList.value = data.list
generationTotal.value = data.total
```

- [ ] **Step 5: Move heavy columns into detail drawer**

Remove these columns from the default generation table:

```vue
<el-table-column label="AI文案" prop="scriptText" min-width="220" show-overflow-tooltip />
<el-table-column label="配音" prop="audioUrl" min-width="160" show-overflow-tooltip />
<el-table-column label="字幕" prop="subtitleUrl" min-width="160" show-overflow-tooltip />
```

Add a detail action:

```vue
<el-button link type="primary" @click="openGenerationDetail(scope.row)">详情</el-button>
```

Add a drawer after the table:

```vue
<el-drawer v-model="generationDetailVisible" title="生成任务详情" size="720px">
  <el-skeleton v-if="generationDetailLoading" :rows="8" animated />
  <div v-else-if="selectedGenerationDetail" class="generation-detail">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="任务标题">{{ selectedGenerationDetail.title || '-' }}</el-descriptions-item>
      <el-descriptions-item label="AI文案">{{ selectedGenerationDetail.scriptText || '-' }}</el-descriptions-item>
      <el-descriptions-item label="配音">{{ selectedGenerationDetail.audioUrl || '-' }}</el-descriptions-item>
      <el-descriptions-item label="字幕">{{ selectedGenerationDetail.subtitleUrl || '-' }}</el-descriptions-item>
      <el-descriptions-item label="当前步骤">{{ selectedGenerationDetail.currentStep || '-' }}</el-descriptions-item>
      <el-descriptions-item label="失败原因">{{ selectedGenerationDetail.failReason || '-' }}</el-descriptions-item>
    </el-descriptions>
  </div>
</el-drawer>
```

Add the method:

```ts
const openGenerationDetail = async (row: TkGenerationTaskSummaryVO) => {
  if (!row.id) return
  generationDetailVisible.value = true
  generationDetailLoading.value = true
  try {
    selectedGenerationDetail.value = await TkGenerationApi.getGeneration(row.id)
  } finally {
    generationDetailLoading.value = false
  }
}
```

- [ ] **Step 6: Verify frontend type checks**

Run:

```powershell
& 'C:\Users\lhd\Documents\TK自动混剪SaaS产品\.runtime\npm-global\node_modules\.bin\pnpm.cmd' ts:check
```

Expected after existing unrelated TS issues are fixed: no new errors from `src/views/tk/generation/index.vue` or `src/api/tk/generation/index.ts`.

---

### Task 2: Add Backend Summary Page Endpoint

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskSummaryRespVO.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/TkGenerationTaskController.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskService.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkGenerationTaskMapper.java`

**Interfaces:**
- Produces: `GET /admin-api/tk/generation/page-summary`
- Returns: `CommonResult<PageResult<TkGenerationTaskSummaryRespVO>>`

- [ ] **Step 1: Create summary VO**

```java
package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 智能生成任务摘要 Response VO")
@Data
public class TkGenerationTaskSummaryRespVO {

    @Schema(description = "生成任务编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "租户编号")
    private Long tenantId;

    @Schema(description = "公司编号")
    private Long companyId;

    @Schema(description = "TikTok 对标链接")
    private String sourceUrl;

    @Schema(description = "素材库编号")
    private Long libraryId;

    @Schema(description = "黄金三秒文件名")
    private String openingVideoName;

    @Schema(description = "对标视频时长秒")
    private Integer referenceDuration;

    @Schema(description = "目标成片时长秒")
    private Integer targetDuration;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "进度")
    private Integer progress;

    @Schema(description = "输出视频")
    private String outputUrl;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "失败错误码")
    private String failCode;

    @Schema(description = "当前执行步骤")
    private String currentStep;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "执行节点")
    private String workerId;

    @Schema(description = "执行心跳时间")
    private LocalDateTime heartbeatTime;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
```

- [ ] **Step 2: Add service method signatures**

In `TkGenerationTaskService.java` add:

```java
PageResult<TkGenerationTaskDO> getGenerationTaskSummaryPage(TkGenerationTaskPageReqVO pageReqVO);
```

- [ ] **Step 3: Add controller endpoint**

In `TkGenerationTaskController.java` add:

```java
@GetMapping("/page-summary")
@Operation(summary = "获得智能生成任务摘要分页")
@PreAuthorize("@ss.hasPermission('tk:generation:query')")
public CommonResult<PageResult<TkGenerationTaskSummaryRespVO>> getGenerationTaskSummaryPage(@Valid TkGenerationTaskPageReqVO pageReqVO) {
    PageResult<TkGenerationTaskDO> pageResult = generationTaskService.getGenerationTaskSummaryPage(pageReqVO);
    return success(BeanUtils.toBean(pageResult, TkGenerationTaskSummaryRespVO.class));
}
```

- [ ] **Step 4: Add mapper selected-column query**

In `TkGenerationTaskMapper.java`, add a method that matches existing mapper style and only selects summary columns:

```java
default PageResult<TkGenerationTaskDO> selectSummaryPage(TkGenerationTaskPageReqVO reqVO, TkUserScope scope) {
    return selectPage(reqVO, new LambdaQueryWrapperX<TkGenerationTaskDO>()
            .select(TkGenerationTaskDO::getId,
                    TkGenerationTaskDO::getTenantId,
                    TkGenerationTaskDO::getCompanyId,
                    TkGenerationTaskDO::getSourceUrl,
                    TkGenerationTaskDO::getLibraryId,
                    TkGenerationTaskDO::getOpeningVideoName,
                    TkGenerationTaskDO::getReferenceDuration,
                    TkGenerationTaskDO::getTargetDuration,
                    TkGenerationTaskDO::getStatus,
                    TkGenerationTaskDO::getProgress,
                    TkGenerationTaskDO::getOutputUrl,
                    TkGenerationTaskDO::getFailReason,
                    TkGenerationTaskDO::getFailCode,
                    TkGenerationTaskDO::getCurrentStep,
                    TkGenerationTaskDO::getRetryCount,
                    TkGenerationTaskDO::getWorkerId,
                    TkGenerationTaskDO::getHeartbeatTime,
                    TkGenerationTaskDO::getTitle,
                    TkGenerationTaskDO::getCreateTime)
            .eqIfPresent(TkGenerationTaskDO::getStatus, reqVO.getStatus())
            .likeIfPresent(TkGenerationTaskDO::getTitle, reqVO.getTitle())
            .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.getTenantId())
            .eqIfPresent(TkGenerationTaskDO::getCompanyId, scope.getCompanyId())
            .orderByDesc(TkGenerationTaskDO::getId));
}
```

If the existing mapper already has a helper for data scope, reuse that exact helper and only change the selected columns.

- [ ] **Step 5: Implement service method**

In `TkGenerationTaskServiceImpl.java` add:

```java
@Override
public PageResult<TkGenerationTaskDO> getGenerationTaskSummaryPage(TkGenerationTaskPageReqVO pageReqVO) {
    return taskMapper.selectSummaryPage(pageReqVO, dataScopeService.getCurrentScope());
}
```

- [ ] **Step 6: Verify backend compile for TK module**

Run:

```powershell
& 'C:\Users\lhd\Documents\TK自动混剪SaaS产品\.runtime\apache-maven-3.9.10\bin\mvn.cmd' -pl yudao-module-tk -am -DskipTests compile
```

Expected: build succeeds, or fails only on pre-existing unrelated repository errors.

---

### Task 3: Add Batch Status Polling Endpoint

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskStatusRespVO.java`
- Modify: `TkGenerationTaskController.java`
- Modify: `TkGenerationTaskService.java`
- Modify: `TkGenerationTaskServiceImpl.java`
- Modify: `TkGenerationTaskMapper.java`
- Modify: `src/views/tk/generation/index.vue`

**Interfaces:**
- Produces: `GET /admin-api/tk/generation/status-batch?ids=1,2,3`
- Returns: `CommonResult<List<TkGenerationTaskStatusRespVO>>`

- [ ] **Step 1: Create status VO**

```java
package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 智能生成任务状态 Response VO")
@Data
public class TkGenerationTaskStatusRespVO {

    @Schema(description = "生成任务编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "进度")
    private Integer progress;

    @Schema(description = "输出视频")
    private String outputUrl;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "失败错误码")
    private String failCode;

    @Schema(description = "当前执行步骤")
    private String currentStep;

    @Schema(description = "执行心跳时间")
    private LocalDateTime heartbeatTime;

    @Schema(description = "当前步骤开始时间")
    private LocalDateTime stepStartedAt;

    @Schema(description = "当前步骤结束时间")
    private LocalDateTime stepFinishedAt;
}
```

- [ ] **Step 2: Add service signature**

In `TkGenerationTaskService.java` add:

```java
List<TkGenerationTaskDO> getGenerationTaskStatusBatch(Collection<Long> ids);
```

- [ ] **Step 3: Add controller endpoint**

In `TkGenerationTaskController.java` add imports:

```java
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
```

Add endpoint:

```java
@GetMapping("/status-batch")
@Operation(summary = "批量获得智能生成任务状态")
@PreAuthorize("@ss.hasPermission('tk:generation:query')")
public CommonResult<List<TkGenerationTaskStatusRespVO>> getGenerationTaskStatusBatch(@RequestParam("ids") String ids) {
    List<Long> parsedIds = Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .map(Long::valueOf)
            .distinct()
            .limit(50)
            .collect(Collectors.toList());
    return success(BeanUtils.toBean(generationTaskService.getGenerationTaskStatusBatch(parsedIds), TkGenerationTaskStatusRespVO.class));
}
```

- [ ] **Step 4: Add mapper selected-column query**

In `TkGenerationTaskMapper.java` add:

```java
default List<TkGenerationTaskDO> selectStatusBatch(Collection<Long> ids, TkUserScope scope) {
    if (ids == null || ids.isEmpty()) {
        return java.util.Collections.emptyList();
    }
    return selectList(new LambdaQueryWrapperX<TkGenerationTaskDO>()
            .select(TkGenerationTaskDO::getId,
                    TkGenerationTaskDO::getStatus,
                    TkGenerationTaskDO::getProgress,
                    TkGenerationTaskDO::getOutputUrl,
                    TkGenerationTaskDO::getFailReason,
                    TkGenerationTaskDO::getFailCode,
                    TkGenerationTaskDO::getCurrentStep,
                    TkGenerationTaskDO::getHeartbeatTime,
                    TkGenerationTaskDO::getStepStartedAt,
                    TkGenerationTaskDO::getStepFinishedAt,
                    TkGenerationTaskDO::getTenantId,
                    TkGenerationTaskDO::getCompanyId,
                    TkGenerationTaskDO::getCreator)
            .in(TkGenerationTaskDO::getId, ids)
            .eqIfPresent(TkGenerationTaskDO::getTenantId, scope.getTenantId())
            .eqIfPresent(TkGenerationTaskDO::getCompanyId, scope.getCompanyId()));
}
```

- [ ] **Step 5: Implement service method**

In `TkGenerationTaskServiceImpl.java` add:

```java
@Override
public List<TkGenerationTaskDO> getGenerationTaskStatusBatch(Collection<Long> ids) {
    return taskMapper.selectStatusBatch(ids, dataScopeService.getCurrentScope());
}
```

- [ ] **Step 6: Replace full-page polling with status-batch polling**

In `src/views/tk/generation/index.vue`, change the polling callback from:

```ts
getGenerationList(true)
```

to:

```ts
refreshGenerationStatuses()
```

Add:

```ts
const refreshGenerationStatuses = async () => {
  if (generationPollingRequesting) return
  const ids = generationList.value
    .filter((item) => item.id && runningGenerationStatuses.has(item.status || ''))
    .map((item) => item.id as number)
  if (!ids.length) {
    clearGenerationPolling()
    return
  }
  generationPollingRequesting = true
  try {
    const statuses = await TkGenerationApi.getGenerationStatusBatch(ids)
    const statusMap = new Map(statuses.map((item: TkGenerationTaskStatusVO) => [item.id, item]))
    generationList.value = generationList.value.map((item) => {
      const status = item.id ? statusMap.get(item.id) : undefined
      return status ? { ...item, ...status } : item
    })
  } finally {
    generationPollingRequesting = false
    syncGenerationPolling()
  }
}
```

- [ ] **Step 7: Add polling backoff**

Replace fixed interval creation with:

```ts
const generationPollingStartedAt = ref<number>()

const nextPollingInterval = () => {
  const elapsed = generationPollingStartedAt.value ? Date.now() - generationPollingStartedAt.value : 0
  if (elapsed < 60_000) return 5000
  if (elapsed < 5 * 60_000) return 10000
  return 15000
}
```

In `syncGenerationPolling`, set:

```ts
if (!generationPollingStartedAt.value) generationPollingStartedAt.value = Date.now()
generationPollingTimer = window.setInterval(refreshGenerationStatuses, nextPollingInterval())
```

In `clearGenerationPolling`, also set:

```ts
generationPollingStartedAt.value = undefined
```

---

### Task 4: Add Database Indexes for List and Status Reads

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_generation_performance_indexes_20260707.sql`

**Interfaces:**
- Produces SQL migration for high-frequency generation task queries.

- [ ] **Step 1: Create migration**

```sql
SET @schema_name := DATABASE();

SET @idx_generation_tenant_company_status_time_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'tk_generation_task'
    AND index_name = 'idx_tk_generation_task_scope_status_time'
);

SET @idx_generation_tenant_company_status_time_sql := IF(
  @idx_generation_tenant_company_status_time_exists > 0,
  'SELECT 1',
  'ALTER TABLE `tk_generation_task` ADD KEY `idx_tk_generation_task_scope_status_time` (`tenant_id`, `company_id`, `status`, `create_time`, `id`)'
);

PREPARE stmt FROM @idx_generation_tenant_company_status_time_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_generation_tenant_company_time_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'tk_generation_task'
    AND index_name = 'idx_tk_generation_task_scope_time'
);

SET @idx_generation_tenant_company_time_sql := IF(
  @idx_generation_tenant_company_time_exists > 0,
  'SELECT 1',
  'ALTER TABLE `tk_generation_task` ADD KEY `idx_tk_generation_task_scope_time` (`tenant_id`, `company_id`, `create_time`, `id`)'
);

PREPARE stmt FROM @idx_generation_tenant_company_time_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

- [ ] **Step 2: Validate index usage**

Run against a staging database:

```sql
EXPLAIN SELECT id, status, progress, output_url, fail_reason, current_step, title, create_time
FROM tk_generation_task
WHERE deleted = b'0'
  AND tenant_id = 1
  AND company_id = 1
  AND status = 'RENDERING'
ORDER BY id DESC
LIMIT 10;
```

Expected: MySQL uses `idx_tk_generation_task_scope_status_time` or a more selective equivalent.

---

### Task 5: Video and File Delivery Optimization

**Files:**
- Modify: deployment nginx config under `deploy/` that serves `tkassetplant.fnn.net.cn`.
- Modify backend file URL generation only if current URLs point through Java API instead of static/nginx paths.

**Interfaces:**
- Produces: static delivery for `/uploads/tk/` and `/exports/`.
- Guarantees: Range requests and cache headers for generated video files.

- [ ] **Step 1: Add nginx static video rules**

Add to the server block that serves `tkassetplant.fnn.net.cn`:

```nginx
location /uploads/tk/ {
    alias /data/Tk/uploads/tk/;
    add_header Accept-Ranges bytes;
    add_header Cache-Control "public, max-age=604800, immutable";
    types {
        video/mp4 mp4;
        video/webm webm;
        video/quicktime mov;
        image/jpeg jpg jpeg;
        image/png png;
        image/webp webp;
    }
    try_files $uri =404;
}

location /exports/ {
    alias /data/Tk/exports/;
    add_header Accept-Ranges bytes;
    add_header Cache-Control "private, max-age=86400";
    types {
        video/mp4 mp4;
        video/webm webm;
    }
    try_files $uri =404;
}
```

- [ ] **Step 2: Keep Java APIs out of video streaming path**

Search:

```powershell
rg -n "ResponseEntity<.*(File|Resource|byte\\[\\])|InputStreamResource|video/mp4|/exports|/uploads/tk" ruoyi-vue-pro-master\ruoyi-vue-pro-master\ruoyi-vue-pro-master\yudao-module-tk\src\main
```

Expected: generation list/detail APIs return URLs, not video bytes.

- [ ] **Step 3: Verify Range support**

Run:

```powershell
curl.exe -I -H "Range: bytes=0-1023" "https://tkassetplant.fnn.net.cn/exports/demo-1.mp4"
```

Expected:

```text
HTTP/2 206
accept-ranges: bytes
content-range: bytes 0-1023/...
```

- [ ] **Step 4: Add frontend media loading rules**

In `src/views/tk/generation/index.vue`, keep video previews out of the table. In detail drawer, render video only when user opens the detail:

```vue
<video
  v-if="selectedGenerationDetail?.outputUrl"
  :src="selectedGenerationDetail.outputUrl"
  controls
  preload="metadata"
  playsinline
  class="generation-output-video"
/>
```

Add scoped CSS:

```css
.generation-output-video {
  width: 100%;
  max-height: 420px;
  background: #0f172a;
  border-radius: 8px;
}
```

---

### Task 6: Performance Verification Checklist

**Files:**
- No production code files.

**Interfaces:**
- Produces measurable acceptance criteria.

- [ ] **Step 1: Verify table payload reduction**

Open browser devtools network for `/tk/generation`.

Expected:

```text
/admin-api/tk/generation/page-summary response body is at least 50% smaller than /admin-api/tk/generation/page for the same page size.
```

- [ ] **Step 2: Verify polling reduction**

With one running task visible:

```text
Only /admin-api/tk/generation/status-batch is called every 5-15 seconds.
/admin-api/tk/generation/page-summary is not repeatedly called during polling.
```

- [ ] **Step 3: Verify table interaction**

Manual checks:

```text
1. Open /tk/generation.
2. Switch to 视频生成记录.
3. Confirm core rows render without horizontal overload at 1440px width.
4. Click 详情.
5. Confirm AI文案、配音、字幕、输出视频 appear in drawer.
6. Close drawer.
7. Confirm table remains responsive.
```

- [ ] **Step 4: Verify backend compile**

Run:

```powershell
& 'C:\Users\lhd\Documents\TK自动混剪SaaS产品\.runtime\apache-maven-3.9.10\bin\mvn.cmd' -pl yudao-module-tk -am -DskipTests compile
```

Expected: compile succeeds.

- [ ] **Step 5: Verify frontend checks**

Run:

```powershell
& 'C:\Users\lhd\Documents\TK自动混剪SaaS产品\.runtime\npm-global\node_modules\.bin\pnpm.cmd' ts:check
```

Expected: type check succeeds after pre-existing `src/utils/auth.ts` and `DeptTreeSelect.vue` errors are fixed.

## Self-Review

- Spec coverage: covers generation records table performance, polling/realtime state, backend API payload/query performance, and video/file delivery performance.
- Placeholder scan: no TBD items are left. The only conditional instruction is to reuse existing mapper data-scope helper if present, because the exact helper shape must be preserved from the current codebase.
- Type consistency: frontend `TkGenerationTaskSummaryVO` and backend `TkGenerationTaskSummaryRespVO` field names match. Frontend `TkGenerationTaskStatusVO` and backend `TkGenerationTaskStatusRespVO` field names match.

