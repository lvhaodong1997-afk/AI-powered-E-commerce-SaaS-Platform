package cn.iocoder.yudao.module.tk.dal.mysql;

/** Only fixed identifiers are passed here. Runtime URLs are always bound parameters. */
public final class TkPublishReferenceSql {
    private TkPublishReferenceSql() { }

    public static String clean(String value) {
        return "(CASE WHEN LOCATE('?'," + value + ")>0 THEN LEFT(" + value
                + ",LOCATE('?'," + value + ")-1) ELSE " + value + " END)";
    }

    /** Match complete JSON string values, including optional read signatures, never URL substrings. */
    public static String photo(String json, String url) {
        return "(LOCATE(CONCAT('\"'," + clean(url) + ",'\"')," + json
                + ")>0 OR LOCATE(CONCAT('\"'," + clean(url) + ",'?')," + json + ")>0)";
    }

    public static String taskActive(String t) {
        return t + ".status IN ('SCHEDULED','PENDING','PROCESSING','PARTIAL_SUCCESS','RECOVERY_REQUIRED')";
    }

    public static String detailActive(String d, boolean admin) {
        return "(" + d + ".status IN ('SCHEDULED','PENDING','PROCESSING','RECOVERY_REQUIRED') OR "
                + d + ".tiktok_status IN ('UPLOAD_PENDING','PREPARING_MEDIA','PROCESSING_DOWNLOAD','PROCESSING_UPLOAD','RECOVERY_REQUIRED')"
                + (admin ? " OR " + d + ".init_phase IN ('INIT_IN_FLIGHT','RECOVERY_REQUIRED')" : "") + ")";
    }

    public static String adminTaskActive(String t) {
        return "(" + taskActive(t) + " OR EXISTS (SELECT 1 FROM tk_tiktok_publish_detail refd"
                + " WHERE refd.deleted=0 AND refd.publish_task_id=" + t + ".id AND " + detailActive("refd", true) + "))";
    }

    public static String openTaskActive(String t) {
        return "(" + taskActive(t) + " OR EXISTS (SELECT 1 FROM tk_open_tiktok_publish_detail refd"
                + " WHERE refd.deleted=0 AND refd.client_id=" + t + ".client_id AND refd.task_id="
                + t + ".task_id AND " + detailActive("refd", false) + "))";
    }

    public static String mediaReferences(String m) {
        return "SELECT 1 FROM tk_tiktok_publish_task reft WHERE reft.deleted=0 AND (reft.uploaded_video_id="
                + m + ".id OR (reft.media_type='PHOTO' AND " + photo("reft.photo_urls", m + ".file_url")
                + ")) AND " + adminTaskActive("reft")
                + " UNION ALL SELECT 1 FROM tk_tiktok_publish_detail refd WHERE refd.deleted=0 AND refd.uploaded_video_id="
                + m + ".id AND " + detailActive("refd", true);
    }

    public static String generationReferences(String g) {
        return "SELECT 1 FROM tk_tiktok_publish_task reft WHERE reft.deleted=0 AND (reft.generation_task_id="
                + g + ".id OR " + clean("reft.video_url") + "=" + clean(g + ".output_url") + ") AND " + adminTaskActive("reft")
                + " UNION ALL SELECT 1 FROM tk_tiktok_publish_detail refd WHERE refd.deleted=0 AND refd.generation_task_id="
                + g + ".id AND " + detailActive("refd", true)
                + " UNION ALL SELECT 1 FROM tk_open_tiktok_media refm JOIN tk_open_tiktok_publish_task refo"
                + " ON refo.client_id=refm.client_id AND refo.media_id=refm.media_id AND refo.deleted=0"
                + " WHERE refm.deleted=0 AND " + clean("refm.file_url") + "=" + clean(g + ".output_url")
                + " AND " + openTaskActive("refo");
    }
}
