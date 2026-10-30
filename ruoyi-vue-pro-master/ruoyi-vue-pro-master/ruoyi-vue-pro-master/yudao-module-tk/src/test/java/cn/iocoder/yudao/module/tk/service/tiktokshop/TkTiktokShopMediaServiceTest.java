package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.module.tk.dal.dataobject.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.framework.config.*;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.service.upload.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopMediaServiceTest {
    @TempDir Path dir;
    TkGenerationTaskMapper generations=mock(TkGenerationTaskMapper.class);
    TkTiktokPublishMediaMapper media=mock(TkTiktokPublishMediaMapper.class);
    TkDataScopeService scope=mock(TkDataScopeService.class);
    TkLocalUploadStorageService local=mock(TkLocalUploadStorageService.class);
    TkOssObjectStorageService oss=mock(TkOssObjectStorageService.class);
    TkTiktokShopMediaService service(){when(local.getRootDir()).thenReturn(dir);return new TkTiktokShopMediaService(generations,media,scope,local,oss,new TkTiktokShopProperties(),new TkGenerationProperties(),new ObjectMapper());}
    TkTiktokShopAccountDO account(){TkTiktokShopAccountDO a=new TkTiktokShopAccountDO();a.setTenantId(1L);a.setCompanyId(2L);return a;}
    @Test void rejectsCrossTenantAndCompanyBeforeResolvingAnyMedia(){
        TkTiktokShopMediaService s=service();TkTiktokPublishMediaDO m=new TkTiktokPublishMediaDO();m.setTenantId(1L);m.setCompanyId(3L);m.setStatus("READY");when(media.selectById(7L)).thenReturn(m);
        assertThrows(IllegalArgumentException.class,()->s.snapshot(account(),null,7L));m.setCompanyId(2L);m.setTenantId(9L);
        assertThrows(IllegalArgumentException.class,()->s.snapshot(account(),null,7L));verifyNoInteractions(oss);
        assertThrows(IllegalArgumentException.class,()->s.snapshot(account(),1L,7L));
    }
    @Test void actualProbeSnapshotAndFingerprint()throws Exception{
        String ffmpeg;try{ffmpeg=TkFfmpegExecutableResolver.ffmpeg(null);}catch(Exception e){Assumptions.assumeTrue(false,"FFmpeg unavailable");return;}
        Path source=dir.resolve("source.mp4");Process p=new ProcessBuilder(ffmpeg,"-v","error","-f","lavfi","-i","color=c=blue:s=160x90:d=0.2","-c:v","libx264","-y",source.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();assertTrue(p.waitFor(20,TimeUnit.SECONDS));assertEquals(0,p.exitValue());
        TkTiktokShopMediaService s=service();TkTiktokPublishMediaDO m=new TkTiktokPublishMediaDO();m.setTenantId(1L);m.setCompanyId(2L);m.setCreator("3");m.setStatus("READY");m.setFileUrl("/uploads/source.mp4");when(media.selectById(7L)).thenReturn(m);when(local.resolveLocalPath(m.getFileUrl())).thenReturn(java.util.Optional.of(source));
        TkTiktokShopMediaService.Snapshot snap=s.snapshot(account(),null,7L);assertNotEquals(source,snap.getPath());assertEquals("video/mp4",snap.getMimeType());s.verify(snap.getPath().toString(),snap.getFingerprint(),snap.getSize());
        Files.write(snap.getPath(),new byte[]{1,2,3});assertThrows(IllegalArgumentException.class,()->s.verify(snap.getPath().toString(),snap.getFingerprint(),snap.getSize()));verify(scope).validateReadable(1L,2L,"3");
    }
    @Test void rejectsFakeMagicAndUnsafeAspect()throws Exception{
        TkTiktokShopMediaService s=service();Path fake=dir.resolve("fake.mp4");Files.write(fake,"not video".getBytes());assertThrows(IllegalArgumentException.class,()->s.probe(fake));
        assertThrows(IllegalArgumentException.class,()->s.validateProbe(new ObjectMapper().readTree("{\"format\":{\"format_name\":\"mov,mp4,m4a,3gp,3g2,mj2\",\"duration\":\"10\"},\"streams\":[{\"codec_type\":\"video\",\"width\":100,\"height\":1000}]}"),"video/mp4"));
    }
    @Test void oversizedSparseFileAndProbeOutputAreBounded()throws Exception{
        TkTiktokShopMediaService s=service();Path big=dir.resolve("oversize.mp4");try(java.io.RandomAccessFile file=new java.io.RandomAccessFile(big.toFile(),"rw")){file.setLength(500L*1024*1024+1);}assertThrows(IllegalArgumentException.class,()->s.probe(big));
        assertThrows(IllegalArgumentException.class,()->TkTiktokShopMediaService.readProbeOutput(new java.io.ByteArrayInputStream(new byte[65537])));
    }
}
