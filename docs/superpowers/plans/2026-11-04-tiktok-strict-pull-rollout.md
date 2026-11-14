# TikTok 签名直拉修复与灰度验收

## 范围

修复 OSS 签名被移除、已有 publishId 的恢复记录被漏扫、旧执行回调覆盖新结果。
复用现有发布任务、执行记录、回调表和加密组件，不需要新增数据库表或字段。
本地实现和测试不包含部署、修改生产配置、再次发布历史视频或修复外部回调站点。

## 执行清单

- [x] 增加签名 GET 直拉预检，保留查询参数，校验签名有效期及已验证域名。
- [x] Range 206 使用 Content-Range 总大小；Range 被忽略时最多读取 1 KiB。
- [x] 预检关闭重定向，连接/读超时 5 秒，总期限 10 秒，成功缓存 20 秒。
- [x] 新策略 OSS_PULL_V2_STRICT 禁止降级为本地文件上传或复用本地缓存。
- [x] 临时预检错误通过既有执行记录持久化退避，30/60/120 秒后重试，最多三次。
- [x] 当前客户端通过系统上传登记的 OSS 对象在执行前重新签名，默认 86400 秒。
- [x] REMOTE 外部链接只复用既有授权，禁止通过相同域名推断对象所有权并重新签名。
- [x] 创建及改期均检查外部签名能否覆盖计划执行时间和至少 3600 秒拉取窗口。
- [x] 混合白名单批量请求明确拒绝，返回 PULL_ACCOUNT_POLICY_MIXED。
- [x] 恢复扫描覆盖已有 publishId 的 RECOVERY_REQUIRED，保留退避和租约限制。
- [x] 指标查询只同步状态及登记恢复需求，由后台恢复任务执行重传。
- [x] 回调发送前检查当前 publishId、终态、执行次数，保留原回调载荷结构。
- [x] 发布中心显示实际方式、回退、恢复信息、当前执行的终态通知状态及历史日期。

## 启用方式

完成正常代码部署后，通过现有 API Key 配置管理维护 TIKTOK provider 的配置：

| key | 灰度值 |
| --- | --- |
| pull-from-url-enabled | true |
| pull-strict-enabled | true |
| pull-account-allowlist | 首批测试账户的内部数值 ID，逗号分隔；验收后再扩大 |
| pull-origin-host | tk-video-create.oss-cn-beijing.aliyuncs.com |
| pull-base-url | https://tkpublish.fnn.net.cn |
| verified-pull-domain | tkpublish.fnn.net.cn |

pull-strict-enabled 缺省 false。新任务只有满足灰度条件才持久化新策略；
历史 LEGACY、FILE_UPLOAD、OSS_PULL_V1 任务保持原行为。关闭开关只影响后续任务创建，
已经保存为 STRICT 的任务保持严格模式，避免恢复过程中切换上传方式。

应用配置 tk.open-api.publish.pull-url-expire-seconds 控制受控对象签名有效期，
默认 86400，可设置为 3600–604800。签名地址只加密写入 pull_url_cipher，不输出到日志或诊断接口。

## 验收

1. 使用专用测试素材与账号：实际 upload_source=PULL_FROM_URL，不占用文件上传执行器，
   不生成完整视频的本地缓存，最终 publishId 为平台拉取会话。
2. 输入过期链接、403、404：显示明确错误，没有 FILE_UPLOAD 尝试。
3. 模拟 503/超时：预检退避持久化，重启后保留次数及执行时间。
4. 构造带 publishId 的 RECOVERY_REQUIRED：无需访问指标接口即可被后台核对。
5. 模拟初始化结果不明确、租约失效和重复扫描：不产生第二个初始化请求。
6. 改期到签名过期后被拒绝；系统登记的 OSS 素材在执行前重新签名。
7. 发布成功而通知返回 503：发布状态仍为 SUCCESS，通知状态为 RETRYING；
   旧执行的失败通知不再投递，事件审计仍保留。
8. 选取历史日期可以查看原记录；原取消记录不伪装成补发成功记录。

历史两条视频已成功发布，不作为再次发帖的验收素材。

## 运行验证

后端使用项目 Maven 的 -pl yudao-module-tk -am，选择发布、签名、缓存、恢复及回调测试。
本机采用 JDK 17；项目当前 Byte Buddy 版本与默认 Maven JDK 21 的 mock instrumentation 不兼容。
前端运行 tiktok-publish-diagnostics.test.cjs、页面 Vue/TypeScript 检查及 ts:check。
事务测试夹具补齐 main 已有 pull_url_cipher、next_poll_time、downloaded_bytes、transfer_policy 字段。

2026-11-04 本地验证结果：后端上述范围 246 项测试通过（失败 0、错误 0）；
前端诊断行为测试 3 项通过，ts:check 通过，git diff --check 通过。
尚未执行生产灰度验收；前端验证环境提示 Node 18 低于项目建议的 Node 20，类型检查正常完成。

## 外部依赖

HTTP 503 的接收端属于外部回调服务。发布端保留终态事件重试，接收端仍需恢复服务并按
X-TK-Event-Id 幂等处理；不能通过再次发视频解决通知失败。

## 参考

- [TikTok Media Transfer Guide](https://developers.tiktok.com/docs/en/content-posting-api-media-transfer-guide)
- [OSS URL 签名](https://www.alibabacloud.com/help/en/oss/developer-reference/add-signatures-to-urls)
- [OSS 预签名下载](https://www.alibabacloud.com/help/en/oss/user-guide/how-to-obtain-the-url-of-a-single-object-or-the-urls-of-multiple-objects)
