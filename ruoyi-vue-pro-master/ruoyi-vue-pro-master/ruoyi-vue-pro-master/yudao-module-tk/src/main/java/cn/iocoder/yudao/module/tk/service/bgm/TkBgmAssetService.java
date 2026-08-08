package cn.iocoder.yudao.module.tk.service.bgm;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TkBgmAssetService {

    List<TkBgmAssetDO> getAvailableList();

    List<TkBgmAssetDO> getSystemList();

    Long uploadUserBgm(String name, String style, MultipartFile file);

    void deleteUserBgm(Long id);

    TkBgmAssetDO validateReadable(Long id);

}
