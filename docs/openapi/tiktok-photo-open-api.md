# 图文发布 OpenAPI

基地址：`https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok`。沿用现有 HMAC 签名、client 隔离、media/publish 权限、IP 白名单和限流。所有 API 请求由服务端签名；OSS 表单直接使用返回的受限凭证，不附加 API 签名。视频接口与默认值保持兼容，省略 mediaType 表示 VIDEO。

## 上传与转换

1. `POST /media/uploads`：`{"mediaType":"PHOTO","fileName":"product.heic","fileSize":718114,"contentType":"image/heic","sha256":"原文件64位SHA256"}`。SHA256 可省略；指定后 complete 必须传相同值。支持 JPEG/WebP/PNG/GIF/HEIC/HEIF，单张 1 字节至 20MiB，每篇 1–35 张。MIME 与扩展名匹配，最终仍校验真实字节和解码结果。
2. 按 `data.uploadMode` 上传原文件。OSS：向 `uploadUrl` 提交 multipart，先填下表字段，最后填 file。LOCAL：使用返回的 chunkSize/totalChunks，逐片 PUT `/media/uploads/{uploadId}/chunks/{index}`，Content-Type 为 application/octet-stream，签名覆盖实际片段字节。PHOTO 分片为 1MiB，避免超过签名请求体上限。
3. `POST /media/uploads/{uploadId}/complete`：`{"fileSize":718114,"sha256":"同一个原文件SHA256"}`。返回 PROCESSING；后台持久转换，重复 complete 不重复启动。FAILED 在会话有效期内可再次 complete 重试；重试可以省略 SHA，若传入仍须与已绑定值一致。服务端继续校验原文件字节及对象身份，不能替换为另一张图片。
4. `GET /media/uploads/{uploadId}`：每隔 2–5 秒查询，READY 后才读取 mediaId；FAILED 查看 failReason；CANCELLED/EXPIRED 不能发布。客户端轮询应设置总时限，超时后保留 uploadId 继续查询，不重复创建上传。
5. `DELETE /media/uploads/{uploadId}` 可取消未 READY 的会话。24 小时默认重试窗口到期后清理原图；完成后的 JPEG 保留供发布和引用保护。会话有效期以 expireTime 为准。

| API 返回字段 | OSS multipart 字段 | 规则 |
| --- | --- | --- |
| objectKey | key | 原样填入，禁止自行拼 key |
| fields.policy | policy | 原样填入 |
| fields.ossAccessKeyId | OSSAccessKeyId | 大小写一致 |
| fields.signature | Signature | 原样填入 |
| fields.xOssObjectAcl | x-oss-object-acl | PHOTO 固定 private，是表单字段 |
| fields.contentType | Content-Type | 原文件 MIME，是表单字段 |
| fields.xOssMetaSha256 | x-oss-meta-sha256 | 返回非空时必须填入 |
| 原文件字节 | file | 放在表单最后 |

原图使用 private Object ACL，匿名读取应为 403。不要把原图地址直接用于发布。服务端输出经过校验的 JPEG：校正方向、sRGB、白色透明底，GIF 取第一显示画面，动画 WebP/HEIF 只保留静态主图；最长边≤1920、短边≤1080且不放大。最终 JPEG ≤20MiB。原文件哈希和重编码结果哈希分别保存，不混用。非支持格式、坏图、超像素、超时会返回 FAILED。网页共用此转换服务，不要求浏览器具备 HEIC 解码能力。

## 创建发布任务

`POST /publish/tasks`，必须带 `Idempotency-Key`：

```json
{
  "mediaType": "PHOTO",
  "connectionIds": ["conn_xxx"],
  "photoMediaIds": ["media_first", "media_second"],
  "photoCoverIndex": 0,
  "postMode": "DIRECT_POST",
  "privacyLevel": "SELF_ONLY",
  "title": "商品展示",
  "caption": "商品细节与使用场景",
  "allowComment": true,
  "autoAddMusic": true,
  "aigcContent": false
}
```

photoMediaIds 为当前 client 的 READY 图片 ID，有序保存；photoCoverIndex 从 0 起，默认 0。不能同时传视频 mediaId。图片 ID 与后台网页数值 ID 不通用。标题≤90、描述≤4000个 UTF-16 代码单元，emoji 通常占两个；VIDEO 仍为标题≤512、描述≤2200。图文不支持启用 allowDuet/allowStitch。

DIRECT_POST 需 video.publish 授权，执行前查询 creator-info 并严格检查 privacyLevel。autoAddMusic 仅此模式适用，表示 TikTok 自动配乐，不支持通过该字段上传自定义音频。UPLOAD_TO_INBOX 需 video.upload，映射 TikTok MEDIA_UPLOAD，用户还需在 TikTok 完成发布；不查询直发 creator-info，不能启用 autoAddMusic。privacyLevel 字段为兼容现有请求校验仍需提交，但不会传给 TikTok 收件箱初始化。

定时直接发布使用已有 scheduledAt/regionCode 规则，仅单账号；不能定时发送收件箱。幂等摘要包含图片顺序、封面、音乐和其它参数。重试同一请求使用相同 key；改变顺序或参数须使用新 key，已存在 key 参数不一致会冲突。

第一版不接收任意远程图片 URL。所有图片经过直拉预检后，复用一次初始化的 journal/fence；已获得 publishId 或初始化结果不明时只核查原结果，不重新发帖。

## 查询、回调与重试

GET `/publish/tasks/{taskId}` 返回 mediaType、photoMediaIds、photoCount、photoCoverIndex、autoAddMusic、聚合状态与调度信息；GET 同路径 `/details` 返回账号状态、publishId、阶段和错误。沿用已有 reschedule/cancel/retry 路径及允许状态。

已有签名回调保留 eventId 和重试去重规则，业务 payload 新增 mediaType 与 photoCount；VIDEO 的 photoCount 为 0。接收方应容忍新增字段并按 eventId 去重。

图文按 TikTok 平台发布状态确认成功，不以视频 Display API 的链接/播放指标为完成条件。metrics 接口对 PHOTO 返回 UNAVAILABLE；这不表示发布失败。发布链接可能为空，调用方以任务和账号明细状态为准。

常见业务错误：MEDIA_NOT_READY（继续查询）、PHOTO_MEDIA_TYPE_INVALID（使用转换后的图片）、MEDIA_NOT_FOUND（不存在或非当前 client）、PHOTO_COVER_INVALID、PHOTO_TITLE_TOO_LONG、PHOTO_CAPTION_TOO_LONG、PHOTO_SCOPE_REQUIRED、PHOTO_MUSIC_MODE_UNSUPPORTED。转换失败原因在上传状态 failReason；重试前处理原文件或授权问题，禁止无界重试。

存储：素材库仍在 tk-material-factory，不迁移。原图私有暂存，最终发布 JPEG 在 tk-video-create，经 TikTok 已验证域名直拉；任务关联覆盖全部图片的清理保护。
