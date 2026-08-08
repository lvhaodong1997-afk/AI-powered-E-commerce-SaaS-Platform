package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;

import java.util.Collection;
import java.util.List;

public interface TkTiktokAccountService {

    PageResult<TkTiktokAccountDO> getAccountPage(TkTiktokAccountPageReqVO reqVO);

    List<TkTiktokAccountDO> getEnabledAccounts();

    List<TkTiktokAccountDO> getReadableAccounts(Collection<Long> accountIds);

    void updateDefaultConfig(TkTiktokAccountDefaultConfigReqVO reqVO);

    void unbindAccount(Long id);

    void deleteAccount(Long id);

    TkTiktokAccountDO validateAccountReadable(Long id);

}
