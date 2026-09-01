package cn.iocoder.yudao.module.tk.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    ErrorCode TK_MATERIAL_LIBRARY_NOT_EXISTS = new ErrorCode(1_060_000_001, "素材库不存在");
    ErrorCode TK_MATERIAL_LIBRARY_NOT_EMPTY = new ErrorCode(1_060_000_002, "素材库内已有视频，请先清空或迁移视频");
    ErrorCode TK_MATERIAL_VIDEO_NOT_EXISTS = new ErrorCode(1_060_000_003, "素材视频不存在");
    ErrorCode TK_GENERATION_TASK_NOT_EXISTS = new ErrorCode(1_060_000_004, "生成任务不存在");
    ErrorCode TK_FORBIDDEN_COMPANY_DATA = new ErrorCode(1_060_000_005, "无权访问其他公司的数据");
    ErrorCode TK_FORBIDDEN_WRITE_COMPANY_DATA = new ErrorCode(1_060_000_006, "无权写入其他公司的数据");
    ErrorCode TK_PLATFORM_COMPANY_REQUIRED = new ErrorCode(1_060_000_007, "一级用户创建数据时必须选择公司");
    ErrorCode TK_MATERIAL_LIBRARY_COMPANY_MISMATCH = new ErrorCode(1_060_000_008, "生成任务不能使用其他公司的素材库");
    ErrorCode TK_USER_SCOPE_NOT_CONFIGURED = new ErrorCode(1_060_000_009, "当前用户未配置 TK 用户级别或所属公司");
    ErrorCode TK_UPLOAD_FILE_EMPTY = new ErrorCode(1_060_000_010, "上传文件不能为空");
    ErrorCode TK_UPLOAD_FILE_TOO_LARGE = new ErrorCode(1_060_000_011, "视频文件超过当前上传大小限制");
    ErrorCode TK_UPLOAD_FILE_EXTENSION_INVALID = new ErrorCode(1_060_000_012, "仅支持 mp4、mov、webm 视频");
    ErrorCode TK_UPLOAD_FILE_INVALID = new ErrorCode(1_060_000_031, "视频文件不完整或无法识别，请重新导出或转码后再上传");
    ErrorCode TK_UPLOAD_SESSION_INVALID = new ErrorCode(1_060_000_049, "上传会话已失效，请重新选择文件上传");
    ErrorCode TK_TIKTOK_PUBLISH_MEDIA_TOO_LARGE = new ErrorCode(1_060_000_050, "TikTok 发布视频不能超过 1GB");
    ErrorCode TK_REFERENCE_SCRIPT_OPTION_NOT_EXISTS = new ErrorCode(1_060_000_013, "文案方案不存在");
    ErrorCode TK_REFERENCE_ANALYSIS_NOT_EXISTS = new ErrorCode(1_060_000_014, "对标分析不存在");
    ErrorCode TK_REFERENCE_BINDING_MISMATCH = new ErrorCode(1_060_000_015, "对标分析或文案方案不属于当前素材库");
    ErrorCode TK_REFERENCE_AI_ANALYSIS_FAILED = new ErrorCode(1_060_000_016, "AI 对标分析失败：{}");
    ErrorCode TK_TIKTOK_ACCOUNT_NOT_EXISTS = new ErrorCode(1_060_000_017, "TikTok 账号不存在");
    ErrorCode TK_TIKTOK_ACCOUNT_NOT_AUTHORIZED = new ErrorCode(1_060_000_018, "TikTok 账号未授权或授权已失效");
    ErrorCode TK_TIKTOK_ACCOUNT_GROUP_NOT_EXISTS = new ErrorCode(1_060_000_019, "TikTok 账号分组不存在");
    ErrorCode TK_TIKTOK_AUTH_SESSION_NOT_EXISTS = new ErrorCode(1_060_000_020, "TikTok 授权会话不存在或已过期");
    ErrorCode TK_TIKTOK_AUTH_STATE_INVALID = new ErrorCode(1_060_000_021, "TikTok 授权 state 校验失败");
    ErrorCode TK_TIKTOK_PUBLISH_TASK_NOT_EXISTS = new ErrorCode(1_060_000_022, "TikTok 发布任务不存在");
    ErrorCode TK_TIKTOK_PUBLISH_DETAIL_NOT_EXISTS = new ErrorCode(1_060_000_023, "TikTok 发布明细不存在");
    ErrorCode TK_TIKTOK_PUBLISH_VIDEO_REQUIRED = new ErrorCode(1_060_000_024, "请选择可发布的视频");
    ErrorCode TK_TIKTOK_PUBLISH_ACCOUNT_REQUIRED = new ErrorCode(1_060_000_025, "请选择至少一个 TikTok 账号或账号组");
    ErrorCode TK_TIKTOK_CONFIG_MISSING = new ErrorCode(1_060_000_026, "TikTok 配置缺失：{}");
    ErrorCode TK_COMPANY_NOT_EXISTS = new ErrorCode(1_060_000_027, "公司不存在");
    ErrorCode TK_COMPANY_DISABLED = new ErrorCode(1_060_000_028, "公司已禁用，请先启用后再操作");
    ErrorCode TK_CREDIT_NOT_ENOUGH = new ErrorCode(1_060_000_029, "租户积分余额不足，本次操作需要 {} 积分，请联系客服充值");
    ErrorCode TK_TIKTOK_PUBLISH_RETRY_STATUS_INVALID = new ErrorCode(1_060_000_030, "只有失败状态的发布明细允许重试");
    ErrorCode TK_GENERATION_RETRY_STATUS_INVALID = new ErrorCode(1_060_000_032, "只有失败状态的生成任务允许重试");
    ErrorCode TK_VOICE_NOT_EXISTS = new ErrorCode(1_060_000_033, "音色不存在");
    ErrorCode TK_VOICE_TENANT_REQUIRED = new ErrorCode(1_060_000_034, "必须进入明确的租户后才能管理自定义音色");
    ErrorCode TK_VOICE_CONSENT_REQUIRED = new ErrorCode(1_060_000_035, "请确认已获得说话人或权利人的明确授权");
    ErrorCode TK_VOICE_FILE_EMPTY = new ErrorCode(1_060_000_036, "参考音频不能为空");
    ErrorCode TK_VOICE_FILE_TOO_LARGE = new ErrorCode(1_060_000_037, "参考音频不能超过 20MB");
    ErrorCode TK_VOICE_FILE_INVALID = new ErrorCode(1_060_000_038, "参考素材仅支持 MP3、WAV、M4A、MP4、MOV、WebM");
    ErrorCode TK_VOICE_UPLOAD_FAILED = new ErrorCode(1_060_000_039, "参考音频上传失败：{}");
    ErrorCode TK_VOICE_NOT_READY = new ErrorCode(1_060_000_040, "音色尚未复刻完成或已停用");
    ErrorCode TK_VOICE_SELECTION_INVALID = new ErrorCode(1_060_000_041, "音色选项无效，请刷新后重新选择");
    ErrorCode TK_VOICE_VIDEO_FILE_TOO_LARGE = new ErrorCode(1_060_000_042, "视频文件不能超过 100MB");
    ErrorCode TK_VOICE_VIDEO_NO_AUDIO = new ErrorCode(1_060_000_043, "视频未检测到可用音轨");
    ErrorCode TK_VOICE_VIDEO_AUDIO_TOO_SHORT = new ErrorCode(1_060_000_044, "视频有效人声不足 10 秒，请上传单人连续说话 10-60 秒的视频");
    ErrorCode TK_VOICE_VIDEO_FFMPEG_FAILED = new ErrorCode(1_060_000_045, "视频音频提取失败：{}");

    ErrorCode TK_REFERENCE_ANALYSIS_PROVIDER_INVALID = new ErrorCode(1_060_000_046, "分析引擎无效");
    ErrorCode TK_DASHSCOPE_VIDEO_CONFIG_MISSING = new ErrorCode(1_060_000_047, "百炼视频理解配置缺失：{}");
    ErrorCode TK_DASHSCOPE_VIDEO_CALL_FAILED = new ErrorCode(1_060_000_048, "百炼视频理解调用失败：{}");

}
