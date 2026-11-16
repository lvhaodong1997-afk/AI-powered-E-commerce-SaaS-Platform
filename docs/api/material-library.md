# 素材库管理与视频上传

更新日期：2026-11-16。本文整理已有后台接口，不新增业务端点。产品基地址示例为 `https://clipforgestudio.fnn.net.cn`，统一请求前缀 `/admin-api`。先按 [快速开始](quickstart.md) 查询租户、登录并保存 `TK_BASE_URL`、`TK_TENANT_ID`、`TK_ACCESS_TOKEN`；素材接口使用账号 Token + tenant-id，TikTok HMAC 凭证不可用于这些接口。

## 1. 权限、范围与调用顺序

| 能力 | 菜单权限 |
| --- | --- |
| 素材库查询 / 新建 / 编辑 / 删除 | `tk:material-library:query` / `tk:material-library:create` / `tk:material-library:update` / `tk:material-library:delete` |
| 素材视频查询 / 上传、用途和分段编辑 / 删除 | `tk:material-video:query` / `tk:material-video:upload` / `tk:material-video:delete` |
| 上传会话创建、状态、分片、完成、取消 | `tk:material-video:upload` |
| 后续出片 | `tk:generation:query`、`tk:generation:create`，另需素材库查询权限 |

菜单权限之外仍执行现有租户与资源访问规则。`companyId` 是可选筛选或创建目标公司；当前不能将该字段视为独立的公司隔离保证。库创建时租户来自当前会话；库更新保留原公司。不要在请求中自行指定 `tenantId`。

顺序：创建素材库 → 保存返回的 `libraryId` → 上传文件或创建上传会话 → 保存返回的 `videoId` → 查询到 `AVAILABLE` → 使用这个 `libraryId` 创建自动出片任务 → 轮询任务 → 下载。新库没有可用视频时不能直接出片；上传成功也不代表解析已完成。

业务响应统一为 `{"code":0,"data":...,"msg":""}`。先判断 HTTP 和 `code`，再取 `data`。ID 使用响应中的真实值；int64 调用方宜保留十进制文本，避免 JavaScript Number 精度丢失。不要根据 HTTP 200 推断业务成功。

## 2. 素材库 CRUD

以下路径均省略 `/admin-api`。

| 方法 | 路径 | 输入 | 成功 data |
| --- | --- | --- | --- |
| POST | `/tk/material-library/create` | JSON 保存对象 | 新建库 ID（整数） |
| PUT | `/tk/material-library/update` | JSON 保存对象，含已有 `id` | `true` |
| GET | `/tk/material-library/get?id=...` | `id` | 库对象 |
| GET | `/tk/material-library/page` | 分页和筛选 | `list,total` |
| DELETE | `/tk/material-library/delete?id=...` | `id` | `true`，仅允许空库 |

保存对象：`id`（更新时提供）、`companyId`（创建可选）、`name`（非空必填）、`category,scene,materialPurpose,tags,description,coverUrl`（字符串）、`defaulted`（布尔）、`status`（整数，0 启用，1 停用）。`tags` 为文本字符串。创建默认 `defaulted=false,status=0`；`materialPurpose` 使用 `ECOMMERCE` 或 `LEAD_GENERATION`，省略按服务端默认规范化。更新不是 JSON Patch：先 GET 原对象，再提交 `id`、`name` 及需要保留的可写字段，不能假设省略字段一律保持。

```bash
curl -sS "$TK_BASE_URL/admin-api/tk/material-library/create" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  -H 'Content-Type: application/json' \
  --data '{"name":"API素材测试_唯一名称","materialPurpose":"ECOMMERCE","description":"API测试专用","defaulted":false,"status":0}'
export TK_LIBRARY_ID='<成功响应data中的库ID>'
curl -sS --get "$TK_BASE_URL/admin-api/tk/material-library/get" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  --data-urlencode "id=$TK_LIBRARY_ID"
```

库分页支持 `pageNo`（默认1）、`pageSize`（默认10，最大200）、`companyId,name,category,materialPurpose,status`。`data.list` 每项含 `id,tenantId,companyId,name,category,scene,materialPurpose,tags,description,coverUrl,previewVideoUrl,videoCount,totalSize,defaulted,status,createTime`。`totalSize` 为字节，`createTime` 默认 Unix 毫秒。`videoCount` 不是已就绪计数，应查询视频状态。删除非空库会失败；先明确检查内容，不能自动清空已有生产库。

## 3. 单文件 multipart 上传

`POST /tk/material-video/upload`，`multipart/form-data`：`libraryId`（必填）、`file`（必填文件）、`tags,usagePhase,segmentType`（可选文本）。允许扩展名 `mp4,mov,webm`，文件须非空且为有效视频容器；该入口固定上限 100 MiB（104857600 字节），网关或框架可能设置更低限制。不要手动设置 multipart 的 Content-Type/boundary。

```bash
curl -sS "$TK_BASE_URL/admin-api/tk/material-video/upload" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  -F "libraryId=$TK_LIBRARY_ID" -F 'file=@./sample.mp4;type=video/mp4' \
  -F 'tags=API测试' -F 'usagePhase=GENERAL' -F 'segmentType=GENERAL'
export TK_VIDEO_ID='<成功响应data中的视频ID>'
```

成功 `data` 是视频 ID，不是对象。后台异步解析视频元数据与封面；必须继续第5节轮询。

## 4. 上传会话：OSS 与本地分片

前缀为 `/tk/upload/material-video`。创建、查询、完成、取消接口均需要平台 Token + tenant-id；**只有发往返回的 OSS uploadUrl 的文件直传不带这些凭证**。

| 方法 | 路径 | 输入 | 成功 data |
| --- | --- | --- | --- |
| POST | `/session/create` | JSON `libraryId,fileName,fileSize,contentType?` | 上传会话对象 |
| GET | `/session/{uploadId}` | 创建响应的上传 ID | 状态对象 |
| POST | `/chunk` | multipart `uploadId,chunkIndex,chunk` | `true`（仅本地） |
| POST | `/session/complete` | JSON 完成对象 | 视频 ID |
| DELETE | `/session/{uploadId}` | 上传 ID | `true` |

`fileSize` 是原文件精确字节数，须大于0；`fileName` 非空、扩展名为 `mp4,mov,webm`；`contentType` 可用 `video/mp4`。会话大小由环境配置，配置类默认 `maxFileSizeBytes=1000000000`（十进制1 GB）、`chunkSizeBytes=1048576`（1 MiB）、`sessionExpireHours=24`。只有配置值为 null 或非正数时，服务才回退为最大100 MiB、分片8 MiB；这与单文件上传固定100 MiB的限制不同。调用方必须使用响应的 `chunkSize,totalChunks`，不可硬编码，也不可将配置默认值视为目标环境实测值。服务端按当前部署配置选择上传方式；客户端根据创建响应 `uploadMode` 分支，不能自行强制 OSS 或本地。

```json
{"libraryId":123,"fileName":"sample.mp4","fileSize":1048576,"contentType":"video/mp4"}
```

### 4.1 OSS（uploadMode=oss）

创建响应含 `uploadId,uploadMode,uploadUrl,publicUrl,objectKey,accessKeyId,policy,signature,successActionStatus,expiration,uploadedSize,uploadedChunks`；这些是本次上传返回的临时表单信息，不能写入共享日志或导出的环境文件。不要在代码内配置长期 OSS Secret。

向原样返回的 `uploadUrl` 发送 POST multipart：

| 表单名 | 值 |
| --- | --- |
| `key` | `objectKey` |
| `OSSAccessKeyId` | `accessKeyId` |
| `policy` | `policy` |
| `signature` | `signature` |
| `success_action_status` | `successActionStatus`（当前为字符串200） |
| `file` | 原始视频文件，放在表单最后 |

OSS 响应按 HTTP 状态判断，不是平台 `CommonResult` JSON。不要给该请求附加 `Authorization: Bearer` 或 `tenant-id`。临时策略过期后重新创建会话；浏览器直传需要目标环境 OSS CORS 允许产品来源。凭证与目标地址仅使用该次可信 API 的响应。

OSS 文件传输成功后调用平台 `/session/complete`，例如：

```json
{
  "uploadId":"创建返回的uploadId",
  "libraryId":123,
  "fileName":"sample.mp4",
  "fileSize":1048576,
  "contentType":"video/mp4",
  "objectKey":"创建返回的objectKey",
  "fileUrl":"创建返回的publicUrl",
  "tags":"API测试",
  "usagePhase":"GENERAL",
  "segmentType":"GENERAL"
}
```

`libraryId,fileName,fileSize,objectKey` 虽未全部加 VO 必填注解，OSS 服务实际依赖它们，必须携带并保持与会话一致。服务端根据 objectKey 生成实际文件 URL，`fileUrl` 不能用于导入任意外部 URL；还会 HEAD 校验对象存在和字节大小。完成成功返回视频 ID 后进入解析。

### 4.2 本地分片（uploadMode 非 oss）

创建响应含 `uploadId,chunkSize,totalChunks,uploadedSize,uploadedChunks`，当前本地实现返回 `uploadMode=local`；兼容旧版本时可能省略该字段，需先确认目标环境契约。将文件按 `chunkSize` 切为二进制分片，`chunkIndex` 从 **0** 到 `totalChunks-1`，最后一片可短于 chunkSize。向 `/chunk` 发送 multipart 字段 `uploadId,chunkIndex,chunk`，`chunk` 是该片文件内容，不是整文件或路径文本。不要使用 TikTok 上传接口的 application/octet-stream 契约。

断点续传先 GET 会话，读取 `uploadedChunks` 跳过已有片；`uploadedSize` 为累计字节数。状态响应还有 `uploadId,chunkSize,totalChunks,fileSize,status`。所有片完成后发送 JSON：

```json
{"uploadId":"创建返回的uploadId","tags":"API测试","usagePhase":"GENERAL","segmentType":"GENERAL"}
```

本地完成所需库与文件信息来自会话 manifest。完成会检查分片完整性、合并大小和视频容器，返回视频 ID。会话完成、过期或取消后不要继续补片。会话状态与视频解析状态是两件事；完成上传必须转而轮询视频详情。

取消只针对未完成的上传会话，不等于删除已创建的素材视频。成功返回不承诺即时删除所有远端孤立对象，按平台清理策略处理。

## 5. 查询视频和等待 AVAILABLE

```bash
curl -sS --get "$TK_BASE_URL/admin-api/tk/material-video/get" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  --data-urlencode "id=$TK_VIDEO_ID"
curl -sS --get "$TK_BASE_URL/admin-api/tk/material-video/page" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  --data-urlencode "libraryId=$TK_LIBRARY_ID" --data-urlencode 'status=AVAILABLE' \
  --data-urlencode 'pageNo=1' --data-urlencode 'pageSize=20'
```

分页支持 `pageNo,pageSize,companyId,libraryId,fileName,status,usagePhase,segmentType`，页数和上限同素材库分页。返回 `list,total`。详情对象：`id,tenantId,companyId,libraryId,fileName,fileUrl,coverUrl,duration,size,resolution,format,tags,usagePhase,segmentType,status,failReason,createTime`。`duration` 为向上取整的秒数，`size` 为字节，`resolution` 如 `1080x1920`；解析前这些字段可能为空，时间默认 Unix 毫秒。

每3–5秒查一次，设置总超时：`UPLOADING/PARSING` 等待，`AVAILABLE` 才可用；`FAILED` 读取 `failReason` 后停止。上传请求成功或库 videoCount>0 都不足以判断可用。超时保留资源 ID，后续续查，不要盲目重新上传/完成。创建、完成、multipart 上传没有调用方幂等键，响应丢失需先检查会话和库中实际素材，避免重复。

## 6. 编辑用途、分段与统计

`PUT /tk/material-video/usage-phase`：`{"ids":[123],"usagePhase":"GENERAL"}`。

`PUT /tk/material-video/segment-type`（兼容别名 `/segment-type/update`）：`{"ids":[123],"segmentType":"S3_REVEAL"}`。

`ids` 必须为非空 ID 数组，每项非 null；请使用当前可写的视频真实 ID。`usagePhase` 为 `ATTENTION,PRODUCT_SHOW,RESULT_EFFECT,GENERAL`。`segmentType` 为 `S1_HOOK,S2_PAIN,S3_REVEAL,S4_DEMO,S5_PROOF,S6_DETAIL,S7_LIFESTYLE,GENERAL`。空白或未知枚举在服务内回退 `GENERAL`，调用方仍应传有效枚举，避免拼写错误静默回退。更新接口要求用途/分段文本非空。

更新 segmentType 会同步 usagePhase：S1/S2 → ATTENTION；S5 → RESULT_EFFECT；其余（包括 GENERAL）→ PRODUCT_SHOW。用途更新只改变用途。批量更新逐项处理，失败不保证前面已处理项回滚，应逐项查询确认。

`GET /tk/material-video/segment-summary?libraryId=...` 返回 `[{"segmentType":"GENERAL","count":1},...]`，统计的是可用视频，不能替代所有素材总数。当前不提供任意视频字段通用更新接口；fileName/tags/fileUrl 没有对应编辑端点，不要自造 `/update` 请求。

## 7. 删除与清理

`DELETE /tk/material-video/delete?id=...` 删除单条；`DELETE /tk/material-video/delete-batch` 带 JSON `{"ids":[123,456]}` 批量删除。成功 data 为 true；会删除视频及封面文件并更新库统计。先停用相关自动流程并确认无正在使用的任务，仅清理明确属于本次测试的资源。不要将删除放进自动轮询或上传失败重试逻辑；跨存储的文件删除和数据库更新不保证整体原子性，失败后需核对实际状态。

素材库只能在视频已清空后删除。Postman 里的删除/取消与其他素材写请求全部默认跳过，必须每次单独开启一次写入开关；它不会自动清理已有业务资源。

## 8. 交给自动出片

当至少一个视频已 AVAILABLE 且现有生成预检通过，把已保存的 libraryId 传给 `POST /admin-api/tk/automation/v1/tasks`。继续按 [自动出片 API](tk-automation-v1.md) 查询音色、当前账号自有 BGM、积分，提交完整口播，保存 taskId 并轮询到 SUCCESS 后获取下载地址。出片会沿用积分规则；创建响应丢失不要重试创建。素材用途/分段可维护，但当前 automation v1 仍使用素材库全池混剪，不提供按标签选片参数。

## 9. 工具与验证

导入 [Postman 集合](tk-automation.postman_collection.json) 与 [环境模板](tk-automation.postman_environment.json)，先完成00–02登录，再展开“素材库管理与视频上传”。所有素材写操作需要本机环境 `allowMaterialWrite=true`，请求前立即恢复 false；收费出片仍独立使用 `allowPaidCreate`。普通文件选择 `materialFile`；本地分片需先自行切片并选择 `materialChunkFile`，配置真实 `chunkIndex`；`materialFileName/materialFileSize` 必须与文件一致。Postman 文件变量为本机路径占位，不上传到共享环境，不自动切片、不自动轮询、不自动删除。OSS 请求只在 materialUploadMode=oss 时可发送，本地 chunk 请求拒绝 oss 模式。

可下载 [素材库 smoke 脚本](examples/material-library-smoke.mjs) 及阅读 [示例说明第7节](examples/README.md#7-素材库-smoke)。设置 `MATERIAL_API_BASE_URL`、`MATERIAL_API_TOKEN`、`MATERIAL_API_TENANT_ID` 后，运行 `node material-library-smoke.mjs` 只读查询；需要真实上传时显式使用 `--allow-write --file ./sample.mp4 --output ./material-evidence`。该脚本没有 `--help` 参数。真实环境验证需具备对应权限、测试视频、解析服务和目标存储配置；打包检查通过只说明交付文件完整，不能替代真实上传/解析验证。认证、403、业务错误和重试策略参阅 [调用规则](calling-rules.md)。机器字段见 [OpenAPI](tk-platform-openapi.json)。
