package cn.iocoder.yudao.module.tk.service.bgm;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkBgmAssetMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class TkBgmAssetServiceImpl implements TkBgmAssetService {

    private static final long MAX_BGM_FILE_SIZE = 20L * 1024 * 1024;
    private static final List<String> ALLOWED_AUDIO_EXTENSIONS = Arrays.asList("mp3", "wav", "m4a");

    @Resource
    private TkBgmAssetMapper bgmAssetMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private FileApi fileApi;

    @Override
    public List<TkBgmAssetDO> getAvailableList() {
        return bgmAssetMapper.selectAvailableList(dataScopeService.getCurrentScope());
    }

    @Override
    public List<TkBgmAssetDO> getSystemList() {
        return bgmAssetMapper.selectSystemAvailableList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadUserBgm(String name, String style, MultipartFile file) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (!scope.hasTenantScope()) {
            throw new IllegalArgumentException("当前账号缺少租户范围，不能上传 BGM");
        }
        validateUpload(file);
        String originalFilename = StrUtil.blankToDefault(file.getOriginalFilename(), "bgm.mp3");
        String extension = FileUtil.extName(originalFilename).toLowerCase(Locale.ROOT);
        String fileUrl;
        try {
            String directory = buildUserBgmDirectory(scope);
            fileUrl = fileApi.createFile(file.getBytes(), originalFilename, directory, file.getContentType());
        } catch (Exception ex) {
            throw new IllegalStateException("上传 BGM 文件失败：" + ex.getMessage(), ex);
        }
        TkBgmAssetDO asset = TkBgmAssetDO.builder()
                .companyId(scope.getCompanyId())
                .name(StrUtil.blankToDefault(name, FileUtil.mainName(originalFilename)))
                .sourceType("USER")
                .style(StrUtil.blankToDefault(style, "LIGHT"))
                .fileUrl(fileUrl)
                .format(extension)
                .status(1)
                .build();
        asset.setTenantId(scope.getTenantId());
        bgmAssetMapper.insert(asset);
        return asset.getId();
    }

    @Override
    public void deleteUserBgm(Long id) {
        TkBgmAssetDO asset = validateReadable(id);
        if (!"USER".equals(asset.getSourceType())) {
            throw new IllegalArgumentException("系统 BGM 不能删除");
        }
        asset.setStatus(0);
        bgmAssetMapper.updateById(asset);
    }

    @Override
    public TkBgmAssetDO validateReadable(Long id) {
        if (id == null) {
            return null;
        }
        TkBgmAssetDO asset = bgmAssetMapper.selectById(id);
        if (asset == null || asset.getStatus() == null || asset.getStatus() != 1) {
            throw new IllegalArgumentException("BGM 不存在或已停用");
        }
        if (!"SYSTEM".equals(asset.getSourceType())) {
            dataScopeService.validateReadable(asset.getTenantId(), asset.getCompanyId(), null);
        }
        return asset;
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择 BGM 音频文件");
        }
        if (file.getSize() > MAX_BGM_FILE_SIZE) {
            throw new IllegalArgumentException("BGM 文件不能超过 20MB");
        }
        String extension = FileUtil.extName(StrUtil.blankToDefault(file.getOriginalFilename(), "")).toLowerCase(Locale.ROOT);
        if (!ALLOWED_AUDIO_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("BGM 仅支持 mp3、wav、m4a 格式");
        }
    }

    private String buildUserBgmDirectory(TkUserScope scope) {
        return scope.getCompanyId() == null
                ? StrUtil.format("bgm/user/{}/tenant", scope.getTenantId())
                : StrUtil.format("bgm/user/{}/{}", scope.getTenantId(), scope.getCompanyId());
    }

}
