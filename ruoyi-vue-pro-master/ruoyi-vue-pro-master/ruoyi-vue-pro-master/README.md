# TK 自动混剪 SaaS 产品

本项目是在现有管理后台底座上收敛后的 TK 自动混剪 SaaS。当前真实保留的业务边界只有两部分：

- 系统管理：用户、角色、菜单、部门、岗位、字典、通知、日志、租户、社交应用、文件等后台支撑能力。
- TK素材工厂：首页、素材库、生成记录。

已从项目中移除或隐藏上游模板的大量非保留业务模块。不要再按上游全量模板的模块说明理解当前项目。

## 目录

| 路径 | 说明 |
| --- | --- |
| `yudao-server` | Spring Boot 启动模块 |
| `yudao-module-system` | 系统管理模块 |
| `yudao-module-infra` | 文件、日志、接口错误等基础支撑 |
| `yudao-module-tk` | TK素材工厂业务模块 |
| `yudao-framework` | 保留模块需要的通用框架能力 |
| `yudao-ui/yudao-ui-admin-vue3` | Vue3 + Element Plus 管理后台 |
| `worker` | TK 生成任务 Worker 骨架 |
| `docs` | 当前项目接入说明 |

## 后端

依赖 JDK 8、Maven、MySQL、Redis。后端入口：

```powershell
mvn -pl yudao-server -am -DskipTests compile
mvn -pl yudao-server -am spring-boot:run
```

TK 数据库脚本：

```sql
source yudao-module-tk/src/main/resources/sql/tk_mysql.sql;
source yudao-module-tk/src/main/resources/sql/tk_cleanup_removed_menus_mysql.sql;
```

`tk_mysql.sql` 创建 TK 数据表和菜单；`tk_cleanup_removed_menus_mysql.sql` 用于隐藏上游初始化数据中不在保留范围内的菜单。

## 前端

进入前端目录后使用 Corepack 的 pnpm：

```powershell
cd yudao-ui/yudao-ui-admin-vue3
corepack pnpm install --registry=https://registry.npmmirror.com
corepack pnpm dev
```

前端当前只保留系统管理入口，以及 TK素材工厂下的：

- 首页：`src/views/tk/dashboard/index.vue`
- 素材库：`src/views/tk/material-library/index.vue`
- 生成记录：`src/views/tk/generation/index.vue`

## Worker

Worker 目前是本地 HTTP 骨架，用于承接后续 FFmpeg、TTS、脚本生成和状态回写。

```powershell
cd worker
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8090
```

## 当前约束

- TK 素材工厂后端保留公司级数据隔离字段和表结构，但没有保留独立“公司管理”菜单页。
- 文件上传能力保留在 infra 模块中，用于头像、素材上传和生成任务开头视频上传。
- WebSocket 只保留系统通知的本地推送，不保留外部 MQ sender。
