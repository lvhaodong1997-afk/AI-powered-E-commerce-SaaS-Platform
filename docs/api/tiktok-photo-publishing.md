# TikTok 图文发布接入

管理后台「视频发布中心 → 上传素材发布 → 发布类型：图文」可选择 1–35 张 JPEG / WebP 图片，预览、调整顺序、移除及选择封面。浏览器根据 EXIF 方向解码，统一转为 JPEG，按原比例缩放至最长边 1920、短边 1080 以内；单张原图不超过 20MB。支持直接发布、发送到 TikTok 收件箱、单账号定时直接发布及可选自动配乐。

## 部署顺序

1. 在目标业务数据库执行 `yudao-module-tk/src/main/resources/sql/tk_tiktok_photo_publish_upgrade_mysql.sql`，再更新后端和前端。脚本可重复执行，新增字段并允许图文任务的 `video_url` 为空、扩展描述存储；历史视频默认为 `VIDEO`。
2. 配置现有 `tk.generation.upload.oss` 对应的 OSS 上传参数（沿用项目当前配置绑定），确保图片能经已验证域名公开读取。
3. 在 TikTok API 配置中检查 `verified-pull-domain`、`pull-from-url-enabled=true`、`pull-account-allowlist`、`pull-base-url`、`pull-origin-host`。图文必须走 URL 拉取；域名或 URL 前缀必须在 TikTok 应用中验证。无法公开读取、重定向、格式或大小不符时，任务明确失败，不回退到视频上传。
4. 账号直接发布需授权 `video.publish`，发送收件箱需 `video.upload`。收件箱模式提交成功后，用户仍需在 TikTok 内完成编辑和发布。

升级脚本需在目标业务数据库执行；服务和页面验证通过后，还需使用真实账号验证发帖结果。

## 管理端接口

- `POST /tk/tiktok-publish/media/photo/upload`：单张 JPEG multipart `file`，返回现有素材结构（`id`、`fileUrl` 等）。浏览器负责 WebP 转 JPEG；后端独立验证真实 JPEG 内容、1080p 分辨率和 20MB 大小，存入租户与公司目录下的 OSS。接口不接受任意远程图片 URL。
- `POST /tk/tiktok-publish/creator-info`：`{accountIds, groupIds}`，读取有权限账号的实时隐私选项和评论限制。图文直接发布时页面取共同选项，要求用户选择；执行前再校验当前权限。
- `POST /tk/tiktok-publish/create`：复用原发布接口，图文传 `mediaType: "PHOTO"`、有序 `uploadedPhotoIds: number[]`、`photoCoverIndex`（从 0 起）、`autoAddMusic` 及现有标题/描述/账号/隐私/标识/定时字段。不得同时传视频来源或 `photoUrls`。省略媒体类型时仍按视频处理。

图片 ID 逐张校验可写权限，所有图片和账号必须属于同一租户及公司。后台将经过授权的图片地址保存为任务快照，重试及定时发布沿用相同顺序和封面。向 TikTok 调用 `/v2/post/publish/content/init/` 时，直接发布使用 `DIRECT_POST`，收件箱使用 `MEDIA_UPLOAD`，AIGC 标记位于请求顶层。

任务复用现有初始化并发保护、账号令牌刷新、请求预算、状态查询及重试流程。初始化超时或结果不明时进入待核查状态，禁止自动重新发帖。收件箱模式不查询仅适用于直发权限的 creator-info；TikTok App 版本过低等明确拒绝初始化的错误保留失败原因，修复后可重试。图文以 TikTok 发布状态确认成功，不调用 Display API 视频详情来反复等待图文链接；详情页显示「图文以发布状态为准」。

本次范围为管理后台发布链路；独立 Open API 的视频上传/发布协议维持现状。

参考：[Photo API](https://developers.tiktok.com/docs/en/content-posting-api-reference-photo-post)、[Media Transfer Guide](https://developers.tiktok.com/docs/en/content-posting-api-media-transfer-guide)。
