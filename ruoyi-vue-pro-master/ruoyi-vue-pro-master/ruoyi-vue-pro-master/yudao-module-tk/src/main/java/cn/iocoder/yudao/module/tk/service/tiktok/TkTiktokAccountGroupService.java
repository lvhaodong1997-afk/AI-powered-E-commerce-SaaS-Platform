package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountGroupDO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface TkTiktokAccountGroupService {

    PageResult<TkTiktokAccountGroupRespVO> getGroupPage(TkTiktokAccountGroupPageReqVO reqVO);

    Long saveGroup(TkTiktokAccountGroupSaveReqVO reqVO);

    void deleteGroup(Long id);

    TkTiktokAccountGroupDO validateGroupReadable(Long id);

    Map<Long, List<Long>> getGroupAccountIdMap(Collection<Long> groupIds);

}
