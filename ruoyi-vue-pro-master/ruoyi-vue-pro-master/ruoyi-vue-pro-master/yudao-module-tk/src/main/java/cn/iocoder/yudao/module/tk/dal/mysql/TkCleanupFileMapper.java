package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkCleanupFileMapper extends BaseMapperX<FileDO> {

    default List<FileDO> selectExpiredGenerationTaskCandidates(LocalDateTime deadline, int limit) {
        return selectList(new LambdaQueryWrapper<FileDO>()
                .lt(FileDO::getCreateTime, deadline)
                .likeRight(FileDO::getPath, "tk/")
                .like(FileDO::getPath, "/generation-tasks/")
                .orderByAsc(FileDO::getId)
                .last("LIMIT " + limit));
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
