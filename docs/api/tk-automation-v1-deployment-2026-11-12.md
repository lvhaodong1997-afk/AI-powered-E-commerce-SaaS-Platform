# 自动出片 API 部署验收记录

2026-11-12 11:21（北京时间）完成服务器后端版本切换，随后完成公网检查。本次部署由用户明确授权。

| 项目 | 结果 |
| --- | --- |
| 远端代码同步 | `origin/main`，基础提交 `25363c209a82f478b9d307f03018d5915a2252fc` |
| 发布内容 | 基础版本及本地后端改动，包含本次自动出片 API；保留已有严格发布功能及二维码登录修复 |
| 发布目录 | `/data/Tk/releases/release-automation-api-20261112-25363c20` |
| 上一版本 | `/data/Tk/releases/release-tiktok-qr-20261106-25363c2` |
| JAR SHA-256 | `b7a56dc2e5ce5f433e2d42d2f78176eb649280503dffb4e81df4b93ba44bf205` |
| 构建与回归 | Maven `BUILD SUCCESS`；381 项测试中 380 项通过、1 项跳过 |
| 已知测试限制 | 原有字幕英文换行断言失败已在接口文档记录，本次发布回归未选择该测试 |
| 服务 | `tk-yudao-prod` active，PID `2185315`，`NRestarts=0` |
| 数据库与配置 | 无数据库迁移；生产配置与上一版本一致 |
| 运行中任务 | 切换前无活动生成或上传；11 条既有 `RECOVERY_REQUIRED` 发布记录保留，无人工重提或改状态 |
| 版本兼容 | 二维码登录服务反编译指令与线上原版本一致（忽略常量池索引），既有严格发布标记保留 |
| 后端与 Nginx | 数据库查询接口成功；7 个新 API 均强制登录；原 BGM 接口鉴权正常 |
| 网页资源 | 保留线上已有网页，首页及 9 个入口资源检查通过 |
| 公网 HTTPS | 首页与入口脚本校验值与服务器一致；数据库接口成功；新 API 鉴权生效 |
| 启动错误 | 首次切换验收未发现启动 ERROR |
| 真实成片 | 未使用真实账号提交生成任务，未消耗生成积分进行验收 |

接口入口：`https://clipforgestudio.fnn.net.cn/admin-api/tk/automation/v1`。请求格式和权限见 [API 对接文档](tk-automation-v1.md)。

服务器验收记录：`/data/Tk/releases/release-automation-api-20261112-25363c20/verification-automation-api.json`。

回滚脚本已保存并通过 shell 语法检查；需要回滚时执行：

```bash
bash /data/Tk/releases/release-automation-api-20261112-25363c20/scripts/rollback-automation-api.sh
```

回滚会停止当前 TK 后端、恢复上一版本软链接并启动 TK 后端；数据库及其它服务器项目不变。本次未执行回滚。
