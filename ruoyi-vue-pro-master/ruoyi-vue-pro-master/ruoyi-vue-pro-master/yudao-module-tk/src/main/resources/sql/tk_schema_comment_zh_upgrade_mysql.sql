SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS tk_apply_schema_comment_upgrade;

DELIMITER $$

CREATE PROCEDURE tk_apply_schema_comment_upgrade()
BEGIN
  DECLARE v_table_name VARCHAR(64);
  DECLARE v_table_comment VARCHAR(255);
  DECLARE v_column_name VARCHAR(64);
  DECLARE v_column_type VARCHAR(255);
  DECLARE v_is_nullable VARCHAR(3);
  DECLARE v_column_default TEXT;
  DECLARE v_extra VARCHAR(255);
  DECLARE v_character_set_name VARCHAR(64);
  DECLARE v_collation_name VARCHAR(64);
  DECLARE v_target_comment VARCHAR(1024);

  BEGIN
    DECLARE v_done BOOLEAN DEFAULT FALSE;
    DECLARE table_cursor CURSOR FOR
      SELECT table_name,
             CASE table_name
               WHEN 'tk_open_api_client' THEN 'TK 开放 API 客户端'
               WHEN 'tk_open_tiktok_auth_session' THEN 'TK 开放 API TikTok 授权会话'
               WHEN 'tk_open_tiktok_connection' THEN 'TK 开放 API TikTok 账号连接'
               WHEN 'tk_open_tiktok_media' THEN 'TK 开放 API 媒体'
               WHEN 'tk_open_tiktok_publish_task' THEN 'TK 开放 API TikTok 发布任务'
               WHEN 'tk_open_tiktok_publish_detail' THEN 'TK 开放 API TikTok 发布明细'
               WHEN 'tk_open_api_idempotency' THEN 'TK 开放 API 幂等记录'
               WHEN 'tk_open_api_event' THEN 'TK 开放 API 回调事件'
               WHEN 'tk_open_api_request_log' THEN 'TK 开放 API 请求日志'
               WHEN 'tk_tiktok_account' THEN 'TK TikTok 账号'
               WHEN 'tk_tiktok_account_group' THEN 'TK TikTok 账号分组'
               WHEN 'tk_tiktok_account_group_rel' THEN 'TK TikTok 账号分组关系'
               WHEN 'tk_tiktok_auth_session' THEN 'TK TikTok 授权会话'
               WHEN 'tk_tiktok_publish_task' THEN 'TK TikTok 发布任务'
               WHEN 'tk_tiktok_publish_detail' THEN 'TK TikTok 发布明细'
               WHEN 'tk_tiktok_publish_media' THEN 'TK TikTok 用户发布视频'
               WHEN 'tk_generation_task' THEN 'TK 智能生成任务'
               WHEN 'tk_generation_route' THEN 'TK 生成路由'
               WHEN 'tk_generation_route_history' THEN 'TK 生成路由历史'
               WHEN 'tk_generation_batch' THEN 'TK 生成批次'
               WHEN 'tk_generation_step_log' THEN 'TK 生成步骤日志'
               WHEN 'tk_voice_profile' THEN 'TK 租户自定义音色'
             END AS table_comment
        FROM information_schema.tables
       WHERE table_schema = DATABASE()
         AND table_name IN (
           'tk_open_api_client',
           'tk_open_tiktok_auth_session',
           'tk_open_tiktok_connection',
           'tk_open_tiktok_media',
           'tk_open_tiktok_publish_task',
           'tk_open_tiktok_publish_detail',
           'tk_open_api_idempotency',
           'tk_open_api_event',
           'tk_open_api_request_log',
           'tk_tiktok_account',
           'tk_tiktok_account_group',
           'tk_tiktok_account_group_rel',
           'tk_tiktok_auth_session',
           'tk_tiktok_publish_task',
           'tk_tiktok_publish_detail',
           'tk_tiktok_publish_media',
           'tk_generation_task',
           'tk_generation_route',
           'tk_generation_route_history',
           'tk_generation_batch',
           'tk_generation_step_log',
           'tk_voice_profile'
         )
         AND (
           COALESCE(table_comment, '') = ''
           OR (
             CHAR_LENGTH(COALESCE(table_comment, '')) = LENGTH(COALESCE(table_comment, ''))
             AND COALESCE(table_comment, '') <> ''
           )
           OR table_comment REGEXP '璐|缂|鍙|閰|鎺|濉|鏁|绉|鍒|浠|鍏|绱|闂'
         );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    OPEN table_cursor;
    table_loop: LOOP
      FETCH table_cursor INTO v_table_name, v_table_comment;
      IF v_done THEN
        LEAVE table_loop;
      END IF;

      SET @tk_schema_comment_sql = CONCAT(
        'ALTER TABLE `', v_table_name, '` COMMENT = ', QUOTE(v_table_comment)
      );
      PREPARE tk_schema_comment_stmt FROM @tk_schema_comment_sql;
      EXECUTE tk_schema_comment_stmt;
      DEALLOCATE PREPARE tk_schema_comment_stmt;
    END LOOP;
    CLOSE table_cursor;
  END;

  BEGIN
    DECLARE v_done BOOLEAN DEFAULT FALSE;
    DECLARE v_current_table_name VARCHAR(64);
    DECLARE v_alter_sql LONGTEXT;
    DECLARE v_column_clause LONGTEXT;
    DECLARE v_first_column BOOLEAN DEFAULT TRUE;
    DECLARE column_cursor CURSOR FOR
      SELECT table_name,
             column_name,
             column_type,
             is_nullable,
             column_default,
             extra,
             character_set_name,
             collation_name,
             target_comment
        FROM (
          SELECT c.table_name,
                 c.column_name,
                 c.column_type,
                 c.is_nullable,
                 c.column_default,
                 c.extra,
                 c.character_set_name,
                 c.collation_name,
                 c.column_comment,
                 CASE
                   WHEN c.table_name = 'tk_generation_task' AND c.column_name = 'title'
                     THEN '任务标题'
                   WHEN c.table_name IN ('tk_open_tiktok_publish_task', 'tk_tiktok_publish_task')
                        AND c.column_name = 'title'
                     THEN '发布标题'
                   WHEN c.table_name = 'tk_generation_task' AND c.column_name = 'source_url'
                     THEN 'TikTok 对标链接'
                   WHEN c.table_name = 'tk_generation_batch' AND c.column_name = 'source_url'
                     THEN '对标视频链接'
                   WHEN c.table_name = 'tk_generation_step_log' AND c.column_name = 'task_id'
                     THEN '生成任务编号'
                   WHEN c.table_name = 'tk_open_tiktok_publish_detail' AND c.column_name = 'task_id'
                     THEN '开放 API 发布任务编号'
                   WHEN c.table_name IN ('tk_open_tiktok_publish_task', 'tk_open_tiktok_publish_detail')
                        AND c.column_name = 'client_id'
                     THEN '开放 API 客户端编号'
                   WHEN c.table_name = 'tk_tiktok_publish_media' AND c.column_name = 'file_url'
                     THEN '视频文件地址'
                   WHEN c.table_name = 'tk_tiktok_publish_media' AND c.column_name = 'status'
                     THEN '视频状态'
                   WHEN c.table_name = 'tk_generation_task' AND c.column_name = 'tts_provider'
                     THEN 'TTS 服务提供方'
                   WHEN c.table_name IN ('tk_generation_task', 'tk_voice_profile')
                        AND c.column_name = 'mimo_voice_mode'
                     THEN 'MiMo 音色模式'
                   WHEN c.table_name IN ('tk_generation_task', 'tk_voice_profile')
                        AND c.column_name = 'mimo_voice_code'
                     THEN 'MiMo 预置音色编码'
                   WHEN c.table_name IN ('tk_generation_task', 'tk_voice_profile')
                        AND c.column_name IN ('mimo_voice_prompt')
                     THEN 'MiMo 音色设计提示词'
                   WHEN c.table_name = 'tk_generation_task' AND c.column_name = 'mimo_voice_sample_url'
                     THEN 'MiMo 音色克隆样本地址'
                   WHEN c.table_name = 'tk_voice_profile' AND c.column_name = 'mimo_sample_url'
                     THEN 'MiMo 音色克隆样本地址'
                   WHEN c.table_name = 'tk_voice_profile' AND c.column_name = 'sample_file_url'
                     THEN '授权参考音频'
                   WHEN c.column_name = 'id' THEN '主键编号'
                   WHEN c.column_name = 'tenant_id' THEN '租户编号'
                   WHEN c.column_name = 'company_id' THEN '公司编号'
                   WHEN c.column_name = 'creator' THEN '创建者'
                   WHEN c.column_name = 'create_time' THEN '创建时间'
                   WHEN c.column_name = 'updater' THEN '更新者'
                   WHEN c.column_name = 'update_time' THEN '更新时间'
                   WHEN c.column_name = 'deleted' THEN '是否删除'
                   WHEN c.column_name = 'status' THEN '状态'
                   WHEN c.column_name = 'name' THEN '名称'
                   WHEN c.column_name = 'remark' THEN '备注'
                   WHEN c.column_name = 'fail_reason' THEN '失败原因'
                   WHEN c.column_name = 'fail_code' THEN '失败错误码'
                   WHEN c.column_name = 'retry_count' THEN '重试次数'
                   WHEN c.column_name = 'last_sync_time' THEN '最近同步时间'
                   WHEN c.column_name = 'last_auth_time' THEN '最近授权时间'
                   WHEN c.column_name = 'last_publish_time' THEN '最近发布时间'
                   WHEN c.column_name = 'expire_time' THEN '过期时间'
                   WHEN c.column_name = 'company_id' THEN '公司编号'
                   WHEN c.column_name = 'library_id' THEN '素材库编号'
                   WHEN c.column_name = 'account_id' THEN 'TikTok 账号编号'
                   WHEN c.column_name = 'account_name' THEN '账号名称'
                   WHEN c.column_name = 'account_display_name' THEN '账号展示名称'
                   WHEN c.column_name = 'group_id' THEN '账号分组编号'
                   WHEN c.column_name = 'open_id' THEN 'TikTok 开放账号编号'
                   WHEN c.column_name = 'external_account_id' THEN '外部账号编号'
                   WHEN c.column_name = 'client_id' THEN '开放 API 客户端编号'
                   WHEN c.column_name = 'client_name' THEN '客户端名称'
                   WHEN c.column_name = 'client_secret_cipher' THEN '客户端密钥密文'
                   WHEN c.column_name = 'callback_secret_cipher' THEN '回调密钥密文'
                   WHEN c.column_name = 'auth_callback_url' THEN '授权回调地址'
                   WHEN c.column_name = 'publish_callback_url' THEN '发布回调地址'
                   WHEN c.column_name = 'allowed_ips' THEN '允许的 IP 地址'
                   WHEN c.column_name = 'permissions' THEN '权限范围'
                   WHEN c.column_name = 'rate_limit_per_minute' THEN '每分钟限流次数'
                   WHEN c.column_name = 'daily_quota' THEN '每日配额'
                   WHEN c.column_name = 'auth_session_id' THEN '授权会话编号'
                   WHEN c.column_name = 'client_state' THEN '客户端状态参数'
                   WHEN c.column_name = 'auth_mode' THEN '授权模式'
                   WHEN c.column_name = 'oauth_state' THEN 'OAuth 状态参数'
                   WHEN c.column_name = 'state' THEN '授权状态参数'
                   WHEN c.column_name = 'client_ticket' THEN '客户端票据'
                   WHEN c.column_name = 'qrcode_token' THEN '二维码令牌'
                   WHEN c.column_name = 'qrcode_url' THEN '二维码地址'
                   WHEN c.column_name = 'authorize_url' THEN '授权地址'
                   WHEN c.column_name = 'connection_id' THEN '账号连接编号'
                   WHEN c.column_name = 'display_name' THEN '账号展示名称'
                   WHEN c.column_name = 'username' THEN '账号用户名'
                   WHEN c.column_name = 'avatar_url' THEN '头像地址'
                   WHEN c.column_name = 'scopes' THEN '授权范围'
                   WHEN c.column_name = 'access_token_cipher' THEN '访问令牌密文'
                   WHEN c.column_name = 'refresh_token_cipher' THEN '刷新令牌密文'
                   WHEN c.column_name = 'access_token_expire_time' THEN '访问令牌过期时间'
                   WHEN c.column_name = 'refresh_token_expire_time' THEN '刷新令牌过期时间'
                   WHEN c.column_name = 'token_status' THEN '令牌状态'
                   WHEN c.column_name = 'auth_status' THEN '授权状态'
                   WHEN c.column_name = 'upload_id' THEN '上传编号'
                   WHEN c.column_name = 'media_id' THEN '媒体编号'
                   WHEN c.column_name = 'detail_id' THEN '发布明细编号'
                   WHEN c.column_name = 'upload_mode' THEN '上传模式'
                   WHEN c.column_name = 'file_name' THEN '文件名'
                   WHEN c.column_name = 'file_size' THEN '文件大小（字节）'
                   WHEN c.column_name = 'mime_type' THEN 'MIME 类型'
                   WHEN c.column_name = 'content_type' THEN '内容类型'
                   WHEN c.column_name = 'sha256' THEN '文件 SHA-256'
                   WHEN c.column_name = 'object_key' THEN '对象存储键'
                   WHEN c.column_name = 'file_url' THEN '文件地址'
                   WHEN c.column_name = 'uploaded_size' THEN '已上传大小（字节）'
                   WHEN c.column_name = 'uploaded_chunks' THEN '已上传分片 JSON'
                   WHEN c.column_name = 'cover_url' THEN '封面地址'
                   WHEN c.column_name = 'cover_timestamp_ms' THEN '封面时间点（毫秒）'
                   WHEN c.column_name = 'completed_time' THEN '完成时间'
                   WHEN c.column_name = 'task_id' THEN '任务编号'
                   WHEN c.column_name = 'publish_task_id' THEN '发布任务编号'
                   WHEN c.column_name = 'generation_task_id' THEN '生成任务编号'
                   WHEN c.column_name = 'uploaded_video_id' THEN '用户上传视频编号'
                   WHEN c.column_name = 'external_request_id' THEN '外部请求编号'
                   WHEN c.column_name = 'caption' THEN '发布文案'
                   WHEN c.column_name = 'video_url' THEN '视频地址'
                   WHEN c.column_name = 'publish_id' THEN 'TikTok 发布编号'
                   WHEN c.column_name = 'publish_url' THEN '发布链接'
                   WHEN c.column_name = 'tiktok_status' THEN 'TikTok 平台状态'
                   WHEN c.table_name = 'tk_voice_profile' AND c.column_name = 'source_type'
                     THEN '音色来源类型'
                   WHEN c.column_name = 'source_type' THEN '视频来源'
                   WHEN c.column_name = 'post_mode' THEN '发布模式'
                   WHEN c.column_name = 'privacy_level' THEN '隐私级别'
                   WHEN c.column_name = 'allow_comment' THEN '是否允许评论'
                   WHEN c.column_name = 'allow_duet' THEN '是否允许合拍'
                   WHEN c.column_name = 'allow_stitch' THEN '是否允许拼接'
                   WHEN c.column_name = 'commercial_content' THEN '是否为商业内容'
                   WHEN c.column_name = 'brand_content' THEN '是否为品牌内容'
                   WHEN c.column_name = 'aigc_content' THEN '是否为 AIGC 内容'
                   WHEN c.column_name = 'account_count' THEN '账号数量'
                   WHEN c.column_name = 'success_count' THEN '成功数量'
                   WHEN c.column_name = 'failed_count' THEN '失败数量'
                   WHEN c.column_name = 'pending_count' THEN '待处理数量'
                   WHEN c.column_name = 'event_id' THEN '事件编号'
                   WHEN c.column_name = 'event_type' THEN '事件类型'
                   WHEN c.column_name = 'resource_type' THEN '资源类型'
                   WHEN c.column_name = 'resource_id' THEN '资源编号'
                   WHEN c.column_name = 'callback_url' THEN '回调地址'
                   WHEN c.column_name = 'payload_json' THEN '事件载荷 JSON'
                   WHEN c.column_name = 'attempt_count' THEN '投递尝试次数'
                   WHEN c.column_name = 'next_retry_time' THEN '下次重试时间'
                   WHEN c.column_name = 'last_http_status' THEN '最近 HTTP 状态码'
                   WHEN c.column_name = 'last_error' THEN '最近错误信息'
                   WHEN c.column_name = 'delivered_time' THEN '投递完成时间'
                   WHEN c.column_name = 'idempotency_key' THEN '幂等键'
                   WHEN c.column_name = 'request_hash' THEN '请求哈希'
                   WHEN c.column_name = 'request_id' THEN '请求编号'
                   WHEN c.column_name = 'http_method' THEN 'HTTP 方法'
                   WHEN c.column_name = 'request_target' THEN '请求目标'
                   WHEN c.column_name = 'http_status' THEN 'HTTP 状态码'
                   WHEN c.column_name = 'error_code' THEN '错误码'
                   WHEN c.column_name = 'duration_ms' THEN '耗时毫秒'
                   WHEN c.column_name = 'client_ip' THEN '客户端 IP'
                   WHEN c.column_name = 'request_date' THEN '请求日期'
                   WHEN c.column_name = 'route_id' THEN '生成路由编号'
                   WHEN c.column_name = 'route_code' THEN '生成路由编码'
                   WHEN c.column_name = 'route_name' THEN '生成路由名称'
                   WHEN c.column_name = 'route_config' THEN '生成路由配置 JSON'
                   WHEN c.column_name = 'route_version' THEN '路由版本'
                   WHEN c.column_name = 'traffic_weight' THEN '流量权重'
                   WHEN c.column_name = 'ab_group' THEN 'A/B 分组'
                   WHEN c.column_name = 'enabled' THEN '是否启用'
                   WHEN c.column_name = 'change_reason' THEN '变更原因'
                   WHEN c.column_name = 'material_purpose' THEN '素材用途'
                   WHEN c.column_name = 'product_category_code' THEN '商品类别编码'
                   WHEN c.column_name = 'batch_no' THEN '批次号'
                   WHEN c.column_name = 'script_count' THEN '文案数量'
                   WHEN c.column_name = 'videos_per_script' THEN '每条文案视频数'
                   WHEN c.column_name = 'expected_video_count' THEN '预计视频数量'
                   WHEN c.column_name = 'created_task_count' THEN '已创建任务数量'
                   WHEN c.column_name = 'success_task_count' THEN '成功任务数量'
                   WHEN c.column_name = 'failed_task_count' THEN '失败任务数量'
                   WHEN c.column_name = 'running_task_count' THEN '运行中任务数量'
                   WHEN c.column_name = 'progress_percent' THEN '进度百分比'
                   WHEN c.column_name = 'fail_summary' THEN '失败摘要'
                   WHEN c.column_name = 'step_code' THEN '步骤编码'
                   WHEN c.column_name = 'step_name' THEN '步骤名称'
                   WHEN c.column_name = 'batch_id' THEN '生成批次编号'
                   WHEN c.column_name = 'start_time' THEN '开始时间'
                   WHEN c.column_name = 'end_time' THEN '结束时间'
                   WHEN c.column_name = 'duration_millis' THEN '耗时毫秒'
                   WHEN c.column_name = 'worker_id' THEN '执行节点'
                   WHEN c.column_name = 'voice_code' THEN '供应商音色编码'
                   WHEN c.column_name = 'voice_profile_id' THEN '音色档案编号'
                   WHEN c.column_name = 'tts_provider' THEN 'TTS 服务提供方'
                   WHEN c.column_name = 'source_type' THEN '来源类型'
                   WHEN c.column_name = 'mimo_voice_mode' THEN 'MiMo 音色模式'
                   WHEN c.column_name = 'mimo_voice_code' THEN 'MiMo 预置音色编码'
                   WHEN c.column_name = 'mimo_voice_prompt' THEN 'MiMo 音色设计提示词'
                   WHEN c.column_name IN ('mimo_voice_sample_url', 'mimo_sample_url')
                     THEN 'MiMo 音色克隆样本地址'
                   WHEN c.column_name = 'tags' THEN '标签'
                   WHEN c.column_name = 'sort' THEN '排序号'
                   WHEN c.column_name = 'provider' THEN '服务提供方'
                   WHEN c.column_name = 'model' THEN '模型名称'
                   WHEN c.column_name = 'provider_request_id' THEN '供应商请求编号'
                   WHEN c.column_name = 'language' THEN '语言'
                   WHEN c.column_name = 'enabled' THEN '是否启用'
                   WHEN c.column_name = 'consent_confirmed' THEN '是否确认授权'
                   WHEN c.column_name = 'consent_operator' THEN '授权确认操作人'
                   WHEN c.column_name = 'consent_time' THEN '授权确认时间'
                   WHEN c.column_name = 'error_message' THEN '错误信息'
                   WHEN c.column_name = 'preview_file_url' THEN '试听音频地址'
                   WHEN c.column_name = 'last_used_time' THEN '最后使用时间'
                   ELSE NULL
                 END AS target_comment
            FROM information_schema.columns c
           WHERE c.table_schema = DATABASE()
             AND c.table_name IN (
               'tk_open_api_client',
               'tk_open_tiktok_auth_session',
               'tk_open_tiktok_connection',
               'tk_open_tiktok_media',
               'tk_open_tiktok_publish_task',
               'tk_open_tiktok_publish_detail',
               'tk_open_api_idempotency',
               'tk_open_api_event',
               'tk_open_api_request_log',
               'tk_tiktok_account',
               'tk_tiktok_account_group',
               'tk_tiktok_account_group_rel',
               'tk_tiktok_auth_session',
               'tk_tiktok_publish_task',
               'tk_tiktok_publish_detail',
               'tk_tiktok_publish_media',
               'tk_generation_task',
               'tk_generation_route',
               'tk_generation_route_history',
               'tk_generation_batch',
               'tk_generation_step_log',
               'tk_voice_profile'
             )
        ) comment_columns
       WHERE target_comment IS NOT NULL
          AND (
            COALESCE(column_comment, '') = ''
           OR (
             CHAR_LENGTH(COALESCE(column_comment, '')) = LENGTH(COALESCE(column_comment, ''))
             AND COALESCE(column_comment, '') <> ''
           )
            OR column_comment REGEXP '璐|缂|鍙|閰|鎺|濉|鏁|绉|鍒|浠|鍏|绱|闂'
          )
        ORDER BY table_name, column_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    SET v_current_table_name = NULL;
    SET v_alter_sql = '';
    SET v_first_column = TRUE;

    OPEN column_cursor;
    column_loop: LOOP
      FETCH column_cursor INTO v_table_name, v_column_name, v_column_type,
        v_is_nullable, v_column_default, v_extra, v_character_set_name,
        v_collation_name, v_target_comment;
      IF v_done THEN
        IF v_current_table_name IS NOT NULL THEN
          SET @tk_schema_comment_sql = v_alter_sql;
          PREPARE tk_schema_comment_stmt FROM @tk_schema_comment_sql;
          EXECUTE tk_schema_comment_stmt;
          DEALLOCATE PREPARE tk_schema_comment_stmt;
        END IF;
        LEAVE column_loop;
      END IF;

      IF v_current_table_name IS NULL OR v_current_table_name <> v_table_name THEN
        IF v_current_table_name IS NOT NULL THEN
          SET @tk_schema_comment_sql = v_alter_sql;
          PREPARE tk_schema_comment_stmt FROM @tk_schema_comment_sql;
          EXECUTE tk_schema_comment_stmt;
          DEALLOCATE PREPARE tk_schema_comment_stmt;
        END IF;
        SET v_current_table_name = v_table_name;
        SET v_alter_sql = CONCAT('ALTER TABLE `', v_table_name, '` ');
        SET v_first_column = TRUE;
      END IF;

      SET @tk_default_clause = CASE
        WHEN v_column_default IS NULL THEN ''
        WHEN UPPER(v_column_default) LIKE 'CURRENT_TIMESTAMP%'
          THEN CONCAT(' DEFAULT ', v_column_default)
        WHEN LOWER(v_column_type) LIKE 'bit(%'
          THEN CONCAT(' DEFAULT b''', REPLACE(REPLACE(REPLACE(v_column_default, 'b', ''), '''', ''), ' ', ''), '''')
        WHEN LOWER(v_column_type) REGEXP '^(tinyint|smallint|mediumint|int|bigint|decimal|float|double)'
          THEN CONCAT(' DEFAULT ', v_column_default)
        ELSE CONCAT(' DEFAULT ', QUOTE(v_column_default))
      END;

      SET @tk_extra_clause = '';
      IF LOCATE('auto_increment', LOWER(COALESCE(v_extra, ''))) > 0 THEN
        SET @tk_extra_clause = CONCAT(@tk_extra_clause, ' AUTO_INCREMENT');
      END IF;
      IF LOCATE('on update', LOWER(COALESCE(v_extra, ''))) > 0 THEN
        SET @tk_extra_clause = CONCAT(
          @tk_extra_clause,
          ' ',
          SUBSTRING(v_extra, LOCATE('on update', LOWER(v_extra)))
        );
      END IF;

      SET @tk_charset_clause = IF(
        v_character_set_name IS NULL,
        '',
        CONCAT(' CHARACTER SET ', v_character_set_name, ' COLLATE ', v_collation_name)
      );

      SET v_column_clause = CONCAT(
        'MODIFY COLUMN `', v_column_name, '` ', v_column_type,
        @tk_charset_clause,
        IF(v_is_nullable = 'NO', ' NOT NULL', ' NULL'),
        @tk_default_clause,
        @tk_extra_clause,
        ' COMMENT ', QUOTE(v_target_comment)
      );
      SET v_alter_sql = CONCAT(
        v_alter_sql,
        IF(v_first_column, '', ', '),
        v_column_clause
      );
      SET v_first_column = FALSE;
    END LOOP;
    CLOSE column_cursor;
  END;
END$$

DELIMITER ;

CALL tk_apply_schema_comment_upgrade();
DROP PROCEDURE IF EXISTS tk_apply_schema_comment_upgrade;
