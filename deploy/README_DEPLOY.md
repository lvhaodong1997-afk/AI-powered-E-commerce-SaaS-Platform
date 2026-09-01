# TK 自动混剪 SaaS 部署说明

本目录是部署模板，实际发布包位于 `.codex-build/tk-auto-mix-release-*`。

## 服务器依赖

- Linux x86_64
- JDK 8 或 17
- Python 3.10+
- MySQL 8.x
- Redis 6+
- Nginx
- FFmpeg / FFprobe

## 目录约定

```bash
/opt/tk-auto-mix/
  backend/yudao-server.jar
  frontend/
  worker/
  sql/
  logs/
```

## 数据库初始化

先创建库：

```sql
CREATE DATABASE IF NOT EXISTS tk_auto_mix DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后按 `sql/` 内文件名顺序导入 TK 业务脚本。若服务器还没有若依/芋道基础系统表，需要先导入基础系统管理初始化脚本；当前仓库只包含 TK 业务 SQL 和测试用基础表脚本。

## 后端启动

推荐通过 systemd 管理：

```bash
sudo cp systemd/tk-yudao.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now tk-yudao
```

### 当前生产路径的后端开机自启

当前服务器生产目录为 `/data/Tk/current`，使用 `systemd/tk-yudao-prod.service`，不要直接套用上面的 `/opt/tk-auto-mix` 开发模板：

```bash
sudo cp systemd/tk-yudao-prod.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now tk-yudao-prod
sudo systemctl status tk-yudao-prod --no-pager
sudo journalctl -u tk-yudao-prod -n 100 --no-pager
```

如果后端当前由 `nohup` 手动运行，切换前先停止旧进程并确认 `127.0.0.1:48080` 已释放，再执行上述命令，避免端口冲突。切换后用 `systemctl is-enabled tk-yudao-prod`、`systemctl is-active tk-yudao-prod`、后端 HTTP 和 Nginx HTTP 检查服务状态。

生产环境需要在 service 中覆盖这些参数：

- MySQL 地址、用户名、密码、数据库名
- Redis 地址和密码
- `GEMINI_API_KEY`
- `DASHSCOPE_API_KEY`
- `FFMPEG_PATH`
- `FFPROBE_PATH`

## Worker 启动

```bash
cd /opt/tk-auto-mix/worker
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
sudo cp ../systemd/tk-worker.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now tk-worker
```

## Nginx

```bash
sudo cp nginx/tk-auto-mix.conf /etc/nginx/conf.d/
sudo nginx -t
sudo systemctl reload nginx
```

把模板中的 `server_name` 改成实际域名或公网 IP。
