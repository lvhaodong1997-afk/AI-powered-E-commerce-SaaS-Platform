# 百炼视频理解路由接入设计

## 目标与边界

在现有对标视频分析功能中新增显式的 `DASHSCOPE_VIDEO` 分析路由。未传 `analysisProvider` 或传入 `GEMINI` 时，完整保留当前 Gemini 关键帧分析行为。不得用百炼自动替换或兜底现有分析，不改变积分冻结、异步队列、业务日志、结果落库、文案生成及混剪流程。

## 请求与前端

`TkReferenceAnalyzeReqVO` 新增可选字段 `analysisProvider`，支持：

- `GEMINI`：现有默认路由。
- `DASHSCOPE_VIDEO`：百炼完整视频理解路由。

前端对标分析区域增加分析引擎选择，默认选中“现有分析”。只有用户主动选择“百炼视频理解”时才提交 `DASHSCOPE_VIDEO`。

## 后端路由

新增统一分析客户端接口，由路由器根据 provider 选择实现：

```java
public interface TkReferenceAiAnalysisClient {
    String getProvider();
    String analyze(TkReferenceAnalysisContext context);
}
```

Gemini 实现复用现有 `TkGeminiClient.generateText(prompt, images)`，百炼实现调用视频理解接口。两个实现必须输出项目现有提示词约定的 JSON，继续由 `TkReferenceAnalysisServiceImpl` 的现有解析、校验和落库逻辑处理。

缓存复用条件必须加入 `analysisProvider`，防止同一视频的 Gemini 与百炼结果互相命中。分析记录保存实际 provider 和 model，便于审计与问题定位。

## 百炼调用

使用华北 2（北京）OpenAI 兼容接口：

```text
POST https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions
Authorization: Bearer {DASHSCOPE_API_KEY}
Content-Type: application/json
```

请求主体：

```json
{
  "model": "qwen3.7-plus",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "video_url",
          "video_url": { "url": "https://example.com/video.mp4" },
          "fps": 2
        },
        {
          "type": "text",
          "text": "项目现有结构化视频分析提示词"
        }
      ]
    }
  ],
  "enable_thinking": false,
  "temperature": 0.2
}
```

配置项归属现有 `DASHSCOPE` provider：

| Key | 默认值 | 必填 |
| --- | --- | --- |
| `api-key` | 环境变量 `DASHSCOPE_API_KEY` | 是 |
| `workspace-id` | 无 | 是 |
| `video-model` | `qwen3.7-plus` | 否 |
| `video-fps` | `2` | 否 |
| `video-timeout-seconds` | `300` | 否 |
| `video-enable-thinking` | `false` | 否 |
| `video-temperature` | `0.2` | 否 |
| `video-analysis-prompt` | 复用现有分析输出结构 | 否 |

## 视频地址要求

优先将系统已解析并可公网访问的视频 URL 传给百炼。URL 响应必须包含正确的 `Content-Length` 和视频 `Content-Type`。如果原始平台链接不是直接视频地址，继续复用现有 `TkReferenceVideoContentService` 获取实际视频地址；不得把 TikTok 页面 URL 直接作为 `video_url.url`。

官方参数约束：`fps` 范围为 `0.1` 至 `10`，默认 `2.0`；支持 MP4、AVI、MKV、MOV、FLV、WMV 等格式；百炼视觉模型不理解视频音轨；非流式调用约 300 秒可能超时。实现需要记录 provider、model、request-id 和错误摘要，不记录 API Key。

## 数据变更

`tk_reference_analysis` 增加：

```sql
analysis_provider varchar(32) NOT NULL DEFAULT 'GEMINI'
analysis_model varchar(64) NULL
```

历史数据按 `GEMINI` 处理。查询最新结果、复用运行中任务和成功缓存时都加入 provider 条件。

## 错误与兼容

- `DASHSCOPE_VIDEO` 缺少 API Key 或 WorkspaceId 时，只将该分析任务标记失败并按现有规则退款。
- 百炼超时、限流、下载视频失败或返回非法 JSON 时，不自动切换 Gemini。
- 默认路由不新增任何外部调用，不改变现有 Gemini 请求参数和结果。
- 百炼响应仍通过现有结果解析器校验，字段缺失按当前分析失败路径处理。

## 验证范围

- 路由测试：未传 provider 与 `GEMINI` 均调用现有客户端；`DASHSCOPE_VIDEO` 只调用百炼客户端。
- 请求测试：验证 endpoint、Authorization、model、`video_url.url`、`fps`、提示词和 `enable_thinking`。
- 缓存测试：相同视频不同 provider 不互相复用。
- 失败测试：缺少配置、百炼超时和非法 JSON 保持现有失败及退款语义。
- 前端针对性检查：默认选中现有分析，显式选择后才提交 `DASHSCOPE_VIDEO`。

不运行与本次路由无关的全仓库测试。
