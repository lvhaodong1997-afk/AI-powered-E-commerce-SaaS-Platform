package cn.iocoder.yudao.module.tk.service.log;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.log.vo.TkBusinessLogPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBusinessLogDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkBusinessLogMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

@Service
@Validated
public class TkBusinessLogQueryServiceImpl implements TkBusinessLogQueryService {

    @Resource
    private TkBusinessLogMapper businessLogMapper;
    @Resource
    private TkDataScopeService dataScopeService;

    @Override
    public PageResult<TkBusinessLogDO> getBusinessLogPage(TkBusinessLogPageReqVO pageReqVO) {
        return businessLogMapper.selectPage(pageReqVO, dataScopeService.getCurrentScope());
    }

}
