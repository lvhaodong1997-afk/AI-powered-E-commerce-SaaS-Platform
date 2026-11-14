package cn.iocoder.yudao.module.tk.service.media;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkPhotoConversionWorkerTest {
    @TempDir Path dir;
    @Test void staleCancelledWorkerDeletesOnlyItsOwnOutputAndNeverMarksFailed() throws Exception {
        TkOpenTiktokMediaMapper media=mock(TkOpenTiktokMediaMapper.class);
        TkUploadSessionMapper sessions=mock(TkUploadSessionMapper.class);
        TkOssObjectStorageService oss=mock(TkOssObjectStorageService.class);
        TkPhotoNormalizationService codec=mock(TkPhotoNormalizationService.class);
        TkPhotoPrivateStorage files=new TkPhotoPrivateStorage();ReflectionTestUtils.setField(files,"rootDir",dir.toString());
        TkPhotoConversionWorker worker=new TkPhotoConversionWorker(media,sessions,oss,codec,files,mock(TkPhotoConversionStore.class));
        String upload="upload_12345678901234567890123456789012",token="12345678901234567890123456789012";
        Path source=files.session(upload).resolve("original.bin");Files.createDirectories(source.getParent());Files.write(source,new byte[]{1,2,3});
        TkOpenTiktokMediaDO m=TkOpenTiktokMediaDO.builder().id(7L).clientId("client_a").uploadId(upload).mediaType("PHOTO")
                .sourceLocalPath(source.toString()).sourceFileSize(3L).sourceSha256(TkPhotoNormalizationService.sha256(source))
                .expireTime(LocalDateTime.now().plusHours(1)).build();
        when(oss.publishStorage()).thenReturn(oss);when(oss.unsignedUrl(anyString())).thenAnswer(i->"https://publish/"+i.getArgument(0));
        when(codec.normalize(any(),any())).thenAnswer(i->{Files.write((Path)i.getArgument(1),new byte[]{4,5});return new TkPhotoNormalizationService.Result(10,5,2,"finalhash");});
        when(media.completePhoto(any(),eq(token))).thenReturn(0);
        worker.processOpen(m,token);
        verify(media).completePhoto(argThat(x->"PHOTO".equals(x.getMediaType()) && "image/jpeg".equals(x.getContentType()) && "finalhash".equals(x.getSha256())),eq(token));
        verify(oss).deleteObject("tk/open-api/client_a/photo-final/"+upload+"-"+token+".jpg");
        verify(media,never()).failPhoto(anyLong(),anyString(),anyString()); assertTrue(Files.exists(source));
        worker.stop();
    }

    @Test void originalHashMismatchNeverCreatesReadyOrUploads() throws Exception {
        TkOpenTiktokMediaMapper media=mock(TkOpenTiktokMediaMapper.class);TkUploadSessionMapper sessions=mock(TkUploadSessionMapper.class);
        TkOssObjectStorageService oss=mock(TkOssObjectStorageService.class);when(oss.publishStorage()).thenReturn(oss);
        TkPhotoNormalizationService codec=mock(TkPhotoNormalizationService.class);
        TkPhotoPrivateStorage files=new TkPhotoPrivateStorage();ReflectionTestUtils.setField(files,"rootDir",dir.toString());
        TkPhotoConversionWorker worker=new TkPhotoConversionWorker(media,sessions,oss,codec,files,mock(TkPhotoConversionStore.class));
        String upload="upload_12345678901234567890123456789012";Path source=files.session(upload).resolve("original.bin");
        Files.createDirectories(source.getParent());Files.write(source,new byte[]{1,2,3});
        TkOpenTiktokMediaDO m=TkOpenTiktokMediaDO.builder().id(9L).clientId("c").uploadId(upload).sourceLocalPath(source.toString()).sourceFileSize(3L).sourceSha256("wrong").build();
        worker.processOpen(m,"12345678901234567890123456789012");
        verify(media,never()).completePhoto(any(),anyString());verify(media).failPhoto(eq(9L),anyString(),anyString());
        verifyNoInteractions(codec);verify(oss,never()).uploadFile(anyString(),any(),anyLong(),anyString());worker.stop();
    }
    @Test void changedOriginalEtagIsRejectedBeforeDecode() {
        TkOpenTiktokMediaMapper media=mock(TkOpenTiktokMediaMapper.class);TkUploadSessionMapper sessions=mock(TkUploadSessionMapper.class);
        TkOssObjectStorageService oss=mock(TkOssObjectStorageService.class);when(oss.publishStorage()).thenReturn(oss);
        TkPhotoNormalizationService codec=mock(TkPhotoNormalizationService.class);TkPhotoPrivateStorage files=new TkPhotoPrivateStorage();
        ReflectionTestUtils.setField(files,"rootDir",dir.toString());
        TkPhotoConversionWorker worker=new TkPhotoConversionWorker(media,sessions,oss,codec,files,mock(TkPhotoConversionStore.class));
        String source="tk/open-api/c/photo-original/u.png";
        when(oss.hasPrivateObjectAcl(source)).thenReturn(true);
        when(oss.headObject(source)).thenReturn(new cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient.ObjectMetadata(3,null,"new-etag","v2"));
        TkOpenTiktokMediaDO m=TkOpenTiktokMediaDO.builder().id(8L).clientId("c").uploadId("upload_12345678901234567890123456789012")
                .sourceObjectKey(source).sourceFileSize(3L).sourceEtag("old-etag").sourceVersionId("v1").build();
        worker.processOpen(m,"12345678901234567890123456789012");
        verifyNoInteractions(codec);verify(media,never()).completePhoto(any(),anyString());verify(media).failPhoto(eq(8L),anyString(),anyString());
        verify(oss,never()).downloadToFile(anyString(),any(),any(),anyLong());worker.stop();
    }
}
