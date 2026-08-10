package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

import java.io.File;

public interface TkGenerationOutputStorageService {

    String uploadGeneratedAsset(TkGenerationTaskDO task, File source, String fileName, String contentType);

    String uploadGeneratedAsset(TkGenerationTaskDO task, byte[] content, String fileName, String contentType);

    String refreshGeneratedAssetReadUrl(TkGenerationTaskDO task, String outputUrl);

    String refreshGeneratedAssetReadUrl(TkGenerationTaskDO task, String outputUrl, String preferredDownloadFileName);
}
