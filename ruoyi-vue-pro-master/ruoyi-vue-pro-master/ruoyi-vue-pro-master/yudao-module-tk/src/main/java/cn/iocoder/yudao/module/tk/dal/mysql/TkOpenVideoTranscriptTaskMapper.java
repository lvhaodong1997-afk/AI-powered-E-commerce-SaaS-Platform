package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkOpenVideoTranscriptTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TkOpenVideoTranscriptTaskMapper extends BaseMapperX<TkOpenVideoTranscriptTaskDO> {

    @Update("UPDATE tk_open_video_transcript_task SET audio_url = NULL, updater = 'tk-cleanup', "
            + "update_time = NOW() WHERE id = #{id} AND audio_url = #{audioUrl} AND deleted = 0")
    int clearAudioUrlIfMatches(@Param("id") Long id, @Param("audioUrl") String audioUrl);
}
