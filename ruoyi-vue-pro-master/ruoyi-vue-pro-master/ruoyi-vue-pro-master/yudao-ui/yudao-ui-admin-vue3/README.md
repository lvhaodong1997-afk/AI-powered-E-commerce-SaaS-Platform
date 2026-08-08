# TK 自动混剪 SaaS 管理后台

这是当前项目保留的 Vue3 管理后台。展示范围与菜单边界保持一致：

- 系统管理整组保留。
- TK素材工厂只保留首页、素材库、生成记录。

旧模板中的非保留业务页面和依赖已经清理，不属于当前后台范围。

## 启动

```powershell
corepack pnpm install --registry=https://registry.npmmirror.com
corepack pnpm dev
```

## 常用命令

```powershell
corepack pnpm ts:check
corepack pnpm build:local
```

## 关键目录

| 路径 | 说明 |
| --- | --- |
| `src/views/tk/dashboard/index.vue` | TK素材工厂首页 |
| `src/views/tk/material-library/index.vue` | 素材库 |
| `src/views/tk/generation/index.vue` | 生成记录 |
| `src/api/tk` | TK素材工厂接口封装 |
| `src/views/system` | 系统管理页面 |

## 技术栈

- Vue 3
- Vite
- TypeScript
- Element Plus
- Pinia
- Vue Router
- UnoCSS
