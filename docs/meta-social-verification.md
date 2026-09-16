# Meta 视频发布本地验证

2026-09-15，产品源代码本地验证；未部署、未迁移真实数据库、未真实授权或发帖。

## 验证结果

- 后端：`TkSocial*Test,TkTiktokPublishServiceImplTest,TkTiktokAuthServiceImplTest,TkTiktokTokenCipherTest`，120 项通过，0 失败、0 错误、0 跳过。包含 91 项 Meta 测试及 29 项已有 TikTok 回归。
- MyBatis/H2：13 项真实持久化测试，使用生产增量脚本中的表定义，覆盖字段映射、唯一键、租约竞争、旧检查点、加锁后重试、主任务计数及事务回滚。H2 测试不替代真实 MySQL 上的迁移演练及菜单授权验收。
- 真实 FFmpeg/ffprobe：2 项通过，含标准视频探测及系统现有 44.1kHz / 192kbps 音频转换到独立发布副本；原视频内容保持不变。
- 前端：`node --test tests/meta-social-publish.test.cjs tests/tiktok-upload-request.test.cjs`，24 项通过；`pnpm ts:check` 通过。
- 源码格式：限定变更路径的差异检查通过，新源码/SQL 无行末空格。
- 独立复审：五项原审查问题全部关闭，本地规格及代码质量审查通过，无新增 Critical / Important 问题；33 个新增源码/SQL 文件与提供的 diff 一致。

## 后端复现命令

从产品源代码根目录执行，Maven 路径指向仓库根目录下的 bundled toolchain：

```powershell
& '../../../.runtime/apache-maven-3.9.10/bin/mvn.cmd' -q -pl yudao-module-tk -am '-Dtest=TkSocial*Test,TkTiktokPublishServiceImplTest,TkTiktokAuthServiceImplTest,TkTiktokTokenCipherTest' '-DargLine=-Dnet.bytebuddy.experimental=true' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Java 21 需要上述 ByteBuddy 参数；现有 Mockito 动态 agent 警告不影响测试结果。

## 审查修复

1. 发布副本兼容已有成片音频，不改生成器及原成片。
2. 已有远端 ID 的 UNKNOWN 使用独立 GET 对账，绝不再次发布；缺少远端 ID 仍需人工核验。
3. 领取任务后、手动重试加锁后均重新读取最新进度，防止旧快照重复推进。
4. 清理未引用的生成快照及过期上传，媒体锁与任务引用保护正在使用的文件。
5. 页面初始读取失败仍可恢复状态轮询，停用/权限不足时不查询新表。

真实浏览器授权、Meta 权限审核、外网存储拉取及双平台最终发布仍需使用已配置应用和账号验收，不能以模拟平台测试视为已真实发布。
