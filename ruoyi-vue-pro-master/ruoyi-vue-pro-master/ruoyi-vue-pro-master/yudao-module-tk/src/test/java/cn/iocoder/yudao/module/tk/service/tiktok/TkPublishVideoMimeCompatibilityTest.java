package cn.iocoder.yudao.module.tk.service.tiktok;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.service.upload.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkPublishVideoMimeCompatibilityTest {
 @TempDir Path root;
 @Test void directAndLegacyChunksNormalizeGenericAndParameterizedMime() {
  for(String[] sample:new String[][]{{"a.mp4","application/octet-stream","video/mp4"},{"a.mp4","video/mp4; codecs=avc1","video/mp4"},{"a.mov","application/octet-stream","video/quicktime"},{"a.webm","video/webm; codecs=vp9","video/webm"}}) {
   TkGenerationProperties p=new TkGenerationProperties();p.getUpload().setRootDir(root.toString());
   TkDataScopeService scope=mock(TkDataScopeService.class);when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L,1L,"USER",2L));when(scope.getWritableCompanyId(null)).thenReturn(2L);
   TkOssObjectStorageService storage=mock(TkOssObjectStorageService.class),target=mock(TkOssObjectStorageService.class);when(storage.isPublishEnabled()).thenReturn(true);when(storage.publishStorage()).thenReturn(target);when(target.unsignedUrl(anyString())).thenAnswer(c->"https://tkpublish.fnn.net.cn/"+c.getArgument(0));
   TkTiktokPublishMediaMapper mapper=mock(TkTiktokPublishMediaMapper.class);
   TkTiktokPublishMediaServiceImpl direct=new TkTiktokPublishMediaServiceImpl();ReflectionTestUtils.setField(direct,"storageService",new TkLocalUploadStorageService(p));ReflectionTestUtils.setField(direct,"mediaMapper",mapper);ReflectionTestUtils.setField(direct,"dataScopeService",scope);ReflectionTestUtils.setField(direct,"ossObjectStorageService",storage);
   assertEquals(sample[2],direct.uploadVideo(new MockMultipartFile("file",sample[0],sample[1],new byte[]{1,2,3})).getMimeType());
   TkTiktokPublishMediaUploadServiceImpl chunks=new TkTiktokPublishMediaUploadServiceImpl();ReflectionTestUtils.setField(chunks,"storageService",new TkLocalUploadStorageService(p));ReflectionTestUtils.setField(chunks,"mediaMapper",mapper);ReflectionTestUtils.setField(chunks,"dataScopeService",scope);ReflectionTestUtils.setField(chunks,"generationProperties",p);
   byte[] body=sample[0].endsWith("webm")?new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3}:"ftypmoov".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
   String id=chunks.createSession(sample[0],(long)body.length,sample[1]).getUploadId();chunks.uploadChunk(id,0,new MockMultipartFile("chunk",body));
   ReflectionTestUtils.setField(chunks,"ossObjectStorageService",storage);
   assertEquals(sample[2],chunks.complete(id,null).getMimeType());
   verify(target,times(2)).uploadFile(anyString(),any(Path.class),anyLong(),eq(sample[2]));
   assertThrows(RuntimeException.class,()->direct.uploadVideo(new MockMultipartFile("file","bad.exe",sample[1],body)));
  }
 }
}
