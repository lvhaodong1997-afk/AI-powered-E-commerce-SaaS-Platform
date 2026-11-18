package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopPhotoMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.scope.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopPhotoTest {
    @Test void checksActualFileTypeAndAspectRatio() throws Exception {
        assertEquals("image/png", TkTiktokShopPhotoService.imageType(png(16,9,"image/png")));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokShopPhotoService.imageType(png(17,9,"image/png")));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokShopPhotoService.imageType(png(16,9,"image/jpeg")));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokShopPhotoService.imageType(new MockMultipartFile("files","x.png","image/png",new byte[]{1})));
    }

    @Test void ambiguousSubmissionIsRecordedAndSameRequestIsNotSentAgain() throws Exception {
        TkTiktokShopAuthService auth=mock(TkTiktokShopAuthService.class);
        TkDataScopeService scope=mock(TkDataScopeService.class);
        TkTiktokShopTokenService tokens=mock(TkTiktokShopTokenService.class);
        TkTiktokShopProductService products=mock(TkTiktokShopProductService.class);
        TkTiktokShopApiClient api=mock(TkTiktokShopApiClient.class);
        TkTiktokShopPhotoMapper mapper=mock(TkTiktokShopPhotoMapper.class);
        TkTiktokShopProperties properties=mock(TkTiktokShopProperties.class);
        PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(properties.isPublishingConfigured()).thenReturn(true);
        TkTiktokShopAccountDO account=new TkTiktokShopAccountDO();account.setId(3L);account.setTenantId(1L);account.setCompanyId(2L);
        when(auth.requireWritable(3L)).thenReturn(account);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L,1L,"MEMBER",2L));
        TkTiktokShopProductService.Selection selection=new TkTiktokShopProductService.Selection();selection.setAuthVersion(4L);
        when(products.verifySelection(account,"7","product","SHOWCASE","proof")).thenReturn(selection);
        when(tokens.getAccessToken(1L,2L,3L)).thenReturn(new TkTiktokShopTokenService.AccessToken("token",4L));
        when(api.uploadPhoto(eq("token"),any(),eq("image/png"))).thenReturn(new TkTiktokShopApiClient.Result(new ObjectMapper().readTree("{\"photo_file\":{\"photo_uri\":\"uri\"}}"),null));
        AtomicReference<TkTiktokShopPhotoDO> saved=new AtomicReference<>();
        when(mapper.selectOne(any())).thenAnswer(call->saved.get());
        when(mapper.insert(any(TkTiktokShopPhotoDO.class))).thenAnswer(call->{TkTiktokShopPhotoDO row=call.getArgument(0);row.setId(99L);saved.set(row);return 1;});
        when(mapper.transition(any(),anyString(),anyString())).thenReturn(1);
        when(api.post(eq("/affiliate_creator/202610/photos"),eq("token"),any())).thenAnswer(call->{
            assertEquals("SENDING",saved.get().getStage());
            verify(tx,times(2)).commit(any());
            throw new TkTiktokShopApiException("TRANSPORT_ERROR",null);
        });
        TkTiktokShopPhotoService service=new TkTiktokShopPhotoService(auth,scope,tokens,products,api,mapper,properties,tx);
        TkTiktokShopPhotoService.PhotoRequest request=new TkTiktokShopPhotoService.PhotoRequest();
        request.setAccountId(3L);request.setTitle("Caption");request.setLinkTitle("Anchor");request.setProductId("product");request.setProductSource("SHOWCASE");
        request.setSelectionProof("proof");request.setIsAiGenerated(true);request.setIdempotencyKey("same-request-123");
        MockMultipartFile file=png(16,9,"image/png");
        assertThrows(IllegalArgumentException.class,()->service.publish(request,Collections.singletonList(file)));
        verifyNoInteractions(api);request.setConfirm(true);
        assertEquals("UNKNOWN",service.publish(request,Collections.singletonList(file)).getStage());
        assertEquals(99L,service.publish(request,Collections.singletonList(file)).getId());
        request.setTitle("Different caption");
        assertThrows(IllegalArgumentException.class,()->service.publish(request,Collections.singletonList(file)));
        verify(api,times(1)).post(eq("/affiliate_creator/202610/photos"),eq("token"),any());
        verify(api,times(1)).uploadPhoto(eq("token"),any(),eq("image/png"));
    }

    private MockMultipartFile png(int width,int height,String mime) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",bytes);
        return new MockMultipartFile("files","x.png",mime,bytes.toByteArray());
    }
}
