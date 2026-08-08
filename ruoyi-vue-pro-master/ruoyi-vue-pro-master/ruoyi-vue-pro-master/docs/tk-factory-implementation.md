# TK素材工厂接入说明

当前项目的展示边界为系统管理整组，加 TK素材工厂下的首页、素材库、生成记录三页。本说明只记录仍然保留的接入内容。

## 后端接入

- 业务模块：`yudao-module-tk`
- 根 POM 已加入：`<module>yudao-module-tk</module>`
- `yudao-server` 已加入 `yudao-module-tk` 依赖
- 接口统一挂在管理后台 API 下：
  - `/admin-api/tk/dashboard/summary`
  - `/admin-api/tk/material-library/create`
  - `/admin-api/tk/material-library/page`
  - `/admin-api/tk/material-library/get`
  - `/admin-api/tk/material-library/update`
  - `/admin-api/tk/material-library/delete`
  - `/admin-api/tk/material-video/upload`
  - `/admin-api/tk/material-video/page`
  - `/admin-api/tk/material-video/get`
  - `/admin-api/tk/material-video/delete`
  - `/admin-api/tk/generation/create`
  - `/admin-api/tk/generation/create-with-opening`
  - `/admin-api/tk/generation/page`
  - `/admin-api/tk/generation/get`

## 数据隔离

`system_users` 扩展两个 TK 字段：

- `tk_user_level`
  - `PLATFORM_ADMIN`：平台管理员，可查看全部 TK 数据
  - `COMPANY_ADMIN`：公司管理员，只能查看所属公司数据
  - `COMPANY_USER`：普通用户，只能查看所属公司数据
- `tk_company_id`
  - 当前用户所属公司 ID

TK 业务表包含 `tenant_id` 和 `company_id`。素材库、素材视频、生成任务的列表、详情、编辑、删除和任务创建都会在 Java 服务层校验数据范围。

说明：`tk_company` 表和 `company_id` 字段是 TK 数据隔离支撑，不代表保留了独立的“公司管理”页面。

## 数据库脚本

执行：

```sql
source yudao-module-tk/src/main/resources/sql/tk_mysql.sql;
source yudao-module-tk/src/main/resources/sql/tk_cleanup_removed_menus_mysql.sql;
```

脚本包含：

- `system_users` 的 TK 字段扩展
- `tk_company`
- `tk_material_library`
- `tk_material_video`
- `tk_generation_task`
- TK素材工厂菜单：首页、素材库、生成记录
- 素材库和生成任务示例数据

执行后，需要在系统管理的角色管理中给目标角色分配 `TK素材工厂` 菜单权限。

## 前端接入

保留的前端文件：

- `src/api/tk/dashboard/index.ts`
- `src/api/tk/material/index.ts`
- `src/api/tk/generation/index.ts`
- `src/views/tk/dashboard/index.vue`
- `src/views/tk/material-library/index.vue`
- `src/views/tk/generation/index.vue`

菜单组件路径：

- `tk/dashboard/index`
- `tk/material-library/index`
- `tk/generation/index`

## Worker

Python Worker 当前为本地 HTTP 骨架：

```powershell
cd worker
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --reload --port 8090
```

当前不保留独立日志采集、外部消息队列或对象存储部署栈。文件上传走现有 infra 文件能力，系统通知 WebSocket 只保留本地推送。
