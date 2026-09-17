# Meta 数据能力核验（2026-09-17）

使用现有线上授权及发布记录执行只读请求，Graph API v25.0；未发布新内容、未改授权配置。凭据仅在服务器内存中使用。

| 对象 | 已验证结果 |
| --- | --- |
| Instagram 专业账号 | followers_count、follows_count、media_count 均支持；当前分别为 18、178、0 |
| Instagram 现有成功记录 | 作品 GET 与 Insights 返回 code=100/subcode=33，无法确定是删除、不可见或权限问题；当前账号 media 列表为空。不得显示为零播放，也不能声称作品数据已现场验收 |
| Facebook Page | followers_count、fan_count 均成功返回有效数值 0 |
| Facebook Reels | video_insights 的 blue_reels_play_count 返回 lifetime 数值 0 |
| Facebook 视频互动 | likes/comments 的 summary.total_count 可用；视频对象本身的 reactions、shares 字段不存在，不能混用 Post 字段 |
| Facebook 可选指标 | post_video_social_actions、post_video_likes_by_reaction_type 返回空对象；不能据此编造具体互动数 |
| Facebook 旧指标 | total_video_views、total_video_views_unique 返回空数据；post_video_views 返回上游错误；不能直接用旧字段替代当前 Reels 播放指标 |
| Facebook Post 数据 | 由 Video.post_id 解析 Post 后查询互动，现有授权返回 code=10，要求额外权限；首期明确显示权限不足，不为此更换授权配置 |
| Facebook 触达候选 | post_video_reach、blue_reels_reach 返回无效指标；total_video_impressions_unique 返回空数据，需显示不可用/尚无数据 |

实施应逐项保留可用性、平台来源和获取时间。某个可选字段失败，不应导致所有账号/作品字段清零，也不能更改已发布成功状态。
