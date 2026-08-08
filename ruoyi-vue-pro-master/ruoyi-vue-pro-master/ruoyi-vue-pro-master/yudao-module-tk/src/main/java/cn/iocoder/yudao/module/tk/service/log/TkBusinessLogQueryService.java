package cn.iocoder.yudao.module.tk.service.log;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.log.vo.TkBusinessLogPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBusinessLogDO;

public interface TkBusinessLogQueryService {

    PageResult<TkBusinessLogDO> getBusinessLogPage(TkBusinessLogPageReqVO pageReqVO);

}
