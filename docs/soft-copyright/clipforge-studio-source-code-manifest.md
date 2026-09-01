# ClipForge Studio 软著源代码提交清单

## 提交口径

- 软件名称：ClipForge Studio 短视频智能混剪与发布管理软件
- 版本号：V1.0
- 建议提交量：约 3,000 行
- 排版要求：每页 50 行，约 60 页
- 程序总量参考：当前 TK 后端、前端和 Worker 代码约 40,000 行
- 建议提交方式：选择能完整体现核心功能的连续代码片段，避免提交无关框架代码和生成文件

## 推荐代码范围

### 后端控制层

1. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/material/TkMaterialLibraryController.java`
2. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/material/TkMaterialVideoController.java`
3. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/reference/TkReferenceAnalysisController.java`
4. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/TkGenerationTaskController.java`
5. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/TkGenerationBatchController.java`
6. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/TkTiktokAuthController.java`
7. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/TkTiktokPublishController.java`

### 后端核心服务

1. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/material/TkMaterialLibraryServiceImpl.java`
2. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/material/TkMaterialVideoServiceImpl.java`
3. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisServiceImpl.java`
4. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java`
5. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationBatchServiceImpl.java`
6. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkGenerationPipelineService.java`
7. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkVideoRenderService.java`
8. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/voice/TkVoiceProfileServiceImpl.java`
9. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokAuthServiceImpl.java`
10. `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImpl.java`

### 前端核心页面

1. `yudao-ui/yudao-ui-admin-vue3/src/views/tk/material-library/index.vue`
2. `yudao-ui/yudao-ui-admin-vue3/src/views/tk/generation/index.vue`
3. `yudao-ui/yudao-ui-admin-vue3/src/views/tk/generation-batch/index.vue`
4. `yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/index.vue`
5. `yudao-ui/yudao-ui-admin-vue3/src/views/tk/data-dashboard/index.vue`
6. `yudao-ui/yudao-ui-admin-vue3/src/views/tk/voice/components/VoiceProfileDialog.vue`

## 排版与脱敏检查

- 每页固定 50 行，页眉标注软件名称和版本号；
- 代码按真实文件路径和顺序整理，不拼接无关代码；
- 删除或替换 API Key、Secret、Token、密码、真实域名和服务器 IP；
- 删除测试账号、客户信息、手机号、邮箱和真实文件地址；
- 不提交 `.runtime`、`node_modules`、`target`、`dist`、日志和生成媒体；
- 保留类名、方法名、业务注释和模块结构，确保源代码与操作手册功能一致；
- 提交前检查代码页数、行数、软件名称、版本号和页码连续性。

## 功能对应关系

| 软著功能 | 代码模块 |
| --- | --- |
| 素材库管理 | material controller/service、material-library/index.vue |
| AI 对标分析 | reference controller/service、AI client |
| 混剪生成 | generation controller/service、generation pipeline |
| 视频渲染 | clip planner、subtitle、video render service |
| AI 配音和音色 | voice service、VoiceProfileDialog.vue |
| 批量任务 | generation batch controller/service、generation-batch/index.vue |
| TikTok 发布 | TikTok auth/publish controller/service、video-publish-center/index.vue |
| 数据看板 | dashboard service、data-dashboard/index.vue |

