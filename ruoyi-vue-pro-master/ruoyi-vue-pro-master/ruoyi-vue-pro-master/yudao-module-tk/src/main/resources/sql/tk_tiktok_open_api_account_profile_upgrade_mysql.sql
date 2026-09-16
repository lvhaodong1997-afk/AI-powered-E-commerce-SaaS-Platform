-- TikTok Open API account profile and statistics fields.
SET @tk_open_connection_bio_description_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'bio_description'
);
SET @tk_open_connection_bio_description_sql = IF(@tk_open_connection_bio_description_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `bio_description` varchar(1024) DEFAULT NULL COMMENT ''TikTok account bio description'' AFTER `avatar_url`',
    'SELECT ''tk_open_tiktok_connection.bio_description already exists''');
PREPARE stmt FROM @tk_open_connection_bio_description_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_open_connection_profile_link_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'profile_deep_link'
);
SET @tk_open_connection_profile_link_sql = IF(@tk_open_connection_profile_link_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `profile_deep_link` varchar(1024) DEFAULT NULL COMMENT ''TikTok account profile deep link'' AFTER `bio_description`',
    'SELECT ''tk_open_tiktok_connection.profile_deep_link already exists''');
PREPARE stmt FROM @tk_open_connection_profile_link_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_open_connection_verified_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'verified'
);
SET @tk_open_connection_verified_sql = IF(@tk_open_connection_verified_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `verified` bit(1) DEFAULT NULL COMMENT ''Whether the TikTok account is verified'' AFTER `profile_deep_link`',
    'SELECT ''tk_open_tiktok_connection.verified already exists''');
PREPARE stmt FROM @tk_open_connection_verified_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_open_connection_follower_count_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'follower_count'
);
SET @tk_open_connection_follower_count_sql = IF(@tk_open_connection_follower_count_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `follower_count` bigint DEFAULT NULL COMMENT ''TikTok follower count'' AFTER `verified`',
    'SELECT ''tk_open_tiktok_connection.follower_count already exists''');
PREPARE stmt FROM @tk_open_connection_follower_count_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_open_connection_following_count_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'following_count'
);
SET @tk_open_connection_following_count_sql = IF(@tk_open_connection_following_count_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `following_count` bigint DEFAULT NULL COMMENT ''TikTok following count'' AFTER `follower_count`',
    'SELECT ''tk_open_tiktok_connection.following_count already exists''');
PREPARE stmt FROM @tk_open_connection_following_count_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_open_connection_likes_count_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'likes_count'
);
SET @tk_open_connection_likes_count_sql = IF(@tk_open_connection_likes_count_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `likes_count` bigint DEFAULT NULL COMMENT ''TikTok accumulated likes count'' AFTER `following_count`',
    'SELECT ''tk_open_tiktok_connection.likes_count already exists''');
PREPARE stmt FROM @tk_open_connection_likes_count_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_open_connection_video_count_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'video_count'
);
SET @tk_open_connection_video_count_sql = IF(@tk_open_connection_video_count_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `video_count` bigint DEFAULT NULL COMMENT ''TikTok public video count'' AFTER `likes_count`',
    'SELECT ''tk_open_tiktok_connection.video_count already exists''');
PREPARE stmt FROM @tk_open_connection_video_count_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @tk_open_connection_stats_updated_at_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'tk_open_tiktok_connection'
      AND column_name = 'stats_updated_at'
);
SET @tk_open_connection_stats_updated_at_sql = IF(@tk_open_connection_stats_updated_at_exists = 0,
    'ALTER TABLE `tk_open_tiktok_connection` ADD COLUMN `stats_updated_at` datetime DEFAULT NULL COMMENT ''Time when TikTok profile statistics were refreshed'' AFTER `video_count`',
    'SELECT ''tk_open_tiktok_connection.stats_updated_at already exists''');
PREPARE stmt FROM @tk_open_connection_stats_updated_at_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
