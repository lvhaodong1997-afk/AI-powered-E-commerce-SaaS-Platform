package cn.iocoder.yudao.module.tk.service.media;

import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokMediaVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient.ObjectMetadata;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.*;

class TkPhotoUploadLifecycleTest {
    @TempDir Path dir;
    TkOpenTiktokMediaMapper mapper=mock(TkOpenTiktokMediaMapper.class);
    TkOssObjectStorageService oss=mock(TkOssObjectStorageService.class);
    TkGenerationProperties props=new TkGenerationProperties();
    TkPhotoPrivateStorage files=new TkPhotoPrivateStorage();
    TkOpenPhotoMediaService service;
    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(files,"rootDir",dir.toString());
        when(oss.publishStorage()).thenReturn(oss); when(oss.isConfigured()).thenReturn(true);
        props.getUpload().getOss().setEnabled(true); props.getUpload().getOss().setBucket("bucket");
        props.getUpload().getOss().setEndpoint("oss-cn-beijing.aliyuncs.com");
        props.getUpload().getOss().setAccessKeyId("id"); props.getUpload().getOss().setAccessKeySecret("secret");
        props.getUpload().getOss().setPublicBaseUrl("https://bucket.oss-cn-beijing.aliyuncs.com");
        service=new TkOpenPhotoMediaService(mapper,props,oss,files);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_a","A","media"),"request");
    }
    @AfterEach void clear(){TkOpenApiContext.clear();}
    @Test void createKeepsOriginalSeparateAndPrivate() {
        TkOpenTiktokMediaVO.UploadSessionResp response=service.create("image.PNG",12L,"image/png",null);
        assertEquals("PHOTO",response.getMediaType()); assertEquals("private",response.getFields().getXOssObjectAcl());
        ArgumentCaptor<TkOpenTiktokMediaDO> capture=ArgumentCaptor.forClass(TkOpenTiktokMediaDO.class);
        verify(mapper).insert(capture.capture()); TkOpenTiktokMediaDO m=capture.getValue();
        assertEquals("UPLOADING",m.getStatus()); assertEquals(12L,m.getSourceFileSize());
        assertEquals("image/png",m.getContentType(),"initial row must satisfy existing NOT NULL content_type");
        assertNull(m.getFileUrl()); assertNull(m.getMediaId()); assertNull(m.getObjectKey());
        assertTrue(m.getSourceObjectKey().startsWith("tk/open-api/client_a/photo-original/"));
    }
    @Test void completeBindsObjectIdentityAndQueuesWithoutReady() {
        TkOpenTiktokMediaDO m=photo();
        when(oss.headObject(m.getSourceObjectKey())).thenReturn(new ObjectMetadata(12,null,"\"etag\"","version","image/png"));
        when(oss.hasPrivateObjectAcl(m.getSourceObjectKey())).thenReturn(true);
        TkOpenTiktokMediaVO.MediaResp response=service.complete(m,12L,null);
        assertEquals("PROCESSING",response.getStatus()); assertNull(response.getMediaId());
        assertEquals("\"etag\"",m.getSourceEtag()); assertEquals("version",m.getSourceVersionId());
        verify(mapper).updateById(m);
    }
    @Test void rejectsPublicOriginalEvenIfAllMetadataMatches() {
        TkOpenTiktokMediaDO m=photo(); when(oss.headObject(m.getSourceObjectKey())).thenReturn(new ObjectMetadata(12,null,"e",null,"image/png"));
        assertThrows(TkOpenApiException.class,()->service.complete(m,12L,null)); verify(mapper,never()).updateById(any(TkOpenTiktokMediaDO.class));
    }
    @Test void duplicateCompleteDoesNotRequeueAndCancelInvalidatesClaim() {
        TkOpenTiktokMediaDO m=photo().setStatus("PROCESSING").setConversionClaimToken("worker");
        assertEquals("PROCESSING",service.complete(m,12L,null).getStatus()); verifyNoInteractions(oss,mapper);
        service.cancel(m); verify(mapper).cancelPhoto(m.getId());
    }
    @Test void rejectsOversizeUnsupportedOrMimeMismatch() {
        assertThrows(TkOpenApiException.class,()->service.create("photo.png",21L*1024*1024,"image/png",null));
        assertThrows(TkOpenApiException.class,()->service.create("photo.svg",12L,"image/svg+xml",null));
        assertThrows(TkOpenApiException.class,()->service.create("photo.png",12L,"image/gif",null));
        verify(mapper,never()).insert(any(TkOpenTiktokMediaDO.class));
    }
    @Test void privatePathsRejectTraversalAndKeepOutOfPublicUploads() {
        assertThrows(IllegalArgumentException.class,()->files.session("../bad"));
        assertTrue(files.session("upload_12345678901234567890123456789012").startsWith(dir));
        assertThrows(IllegalArgumentException.class,()->files.requireOwned(dir.getParent().resolve("outside.bin").toString()));
    }
    @Test void failedLocalRetryMayOmitPreviouslyComputedShaButCannotReplaceOriginal() throws Exception {
        String id="upload_12345678901234567890123456789012";
        Path source=files.createSession(id).resolve("original.bin");Files.write(source,new byte[12]);
        String hash=TkPhotoNormalizationService.sha256(source);
        TkOpenTiktokMediaDO m=photo().setUploadMode("LOCAL").setStatus("FAILED").setSourceLocalPath(source.toString()).setSourceSha256(hash);
        assertEquals("PROCESSING",service.complete(m,12L,null).getStatus());
        m.setStatus("FAILED");Files.write(source,new byte[]{1,2,3,4,5,6,7,8,9,0,1,2});
        assertThrows(TkOpenApiException.class,()->service.complete(m,12L,null));
    }
    private TkOpenTiktokMediaDO photo(){ return TkOpenTiktokMediaDO.builder().id(1L).clientId("client_a").uploadId("upload_12345678901234567890123456789012")
            .mediaType("PHOTO").status("UPLOADING").uploadMode("OSS").sourceFileName("image.png").sourceFileSize(12L)
            .sourceContentType("image/png").sourceObjectKey("tk/open-api/client_a/photo-original/u.png").expireTime(LocalDateTime.now().plusHours(1)).build(); }
}
