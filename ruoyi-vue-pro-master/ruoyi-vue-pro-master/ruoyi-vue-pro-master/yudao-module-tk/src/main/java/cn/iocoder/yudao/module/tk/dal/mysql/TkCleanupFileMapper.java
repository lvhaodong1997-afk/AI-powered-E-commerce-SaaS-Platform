package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkCleanupFileMapper extends BaseMapperX<FileDO> {

    @SelectProvider(type = CleanupSql.class, method = "expiredGenerationFiles")
    @Options(timeout = 15)
    List<FileDO> selectExpiredGenerationTaskCandidates(@Param("deadline") LocalDateTime deadline,
                                                      @Param("limit") int limit);

    class CleanupSql {
        public String expiredGenerationFiles() {
            String marker = "LOCATE('/generation-tasks/',infra_file.path)";
            String start = "(" + marker + "+LENGTH('/generation-tasks/'))";
            String end = "LOCATE('/',infra_file.path," + start + ")";
            String id = "SUBSTRING(infra_file.path," + start + "," + end + "-" + start + ")";
            // Guard conversion and retain ambiguous paths; cleanup must never guess which task owns a file.
            String numericId = "CASE WHEN " + id + " REGEXP '^[1-9][0-9]{0,18}$' THEN CAST("
                    + id + " AS DECIMAL(20,0)) ELSE NULL END";
            return "<script>SELECT /*+ MAX_EXECUTION_TIME(15000) */ id,config_id,name,path,url,type,size,"
                    + "creator,create_time,updater,update_time,deleted FROM infra_file WHERE deleted=0"
                    + " AND create_time &lt; #{deadline} AND path LIKE 'tk/%' AND " + marker + ">0"
                    + " AND LOCATE('/generation-tasks/',infra_file.path," + marker + "+1)=0"
                    + " AND " + end + ">" + start + " AND " + numericId + " &lt;= 9223372036854775807"
                    + " AND NOT EXISTS (SELECT 1 FROM tk_generation_task refg WHERE refg.id=" + numericId
                    + " AND LOCATE(CONCAT('/generation-tasks/',refg.id,'/'),CONCAT('/',infra_file.path))>0"
                    + " AND EXISTS (" + TkPublishReferenceSql.generationReferences("refg") + "))"
                    + " ORDER BY id ASC LIMIT #{limit}</script>";
        }
    }

    default List<FileDO> selectExpiredGenerationOpeningCandidates(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapper<FileDO>()
                .lt(FileDO::getCreateTime, deadline)
                .likeRight(FileDO::getPath, "tk/")
                .like(FileDO::getPath, "/generation-openings/")
                .orderByAsc(FileDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    default List<FileDO> selectExpiredReferencePreviewCandidates(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapper<FileDO>()
                .lt(FileDO::getCreateTime, deadline)
                .and(wrapper -> wrapper
                        .likeRight(FileDO::getPath, "tk/reference-videos/")
                        .or()
                        .likeRight(FileDO::getPath, "tk/reference-covers/"))
                .orderByAsc(FileDO::getId)
                .last("LIMIT " + limit));
    }

    default List<FileDO> selectExpiredTranscriptAudioCandidates(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapper<FileDO>()
                .lt(FileDO::getCreateTime, deadline)
                .likeRight(FileDO::getPath, "tk/open-video-transcripts/")
                .like(FileDO::getPath, "transcript-audio-")
                .like(FileDO::getPath, ".wav")
                .orderByAsc(FileDO::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }
}
