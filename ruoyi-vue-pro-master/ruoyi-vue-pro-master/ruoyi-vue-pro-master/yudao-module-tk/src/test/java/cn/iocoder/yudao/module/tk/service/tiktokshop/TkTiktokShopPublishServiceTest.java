package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopPublishVO.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopPublishServiceTest {
    TkTiktokShopAuthService auth=mock(TkTiktokShopAuthService.class);TkDataScopeService scope=mock(TkDataScopeService.class);
    TkTiktokShopProductService products=mock(TkTiktokShopProductService.class);TkTiktokShopMediaService media=mock(TkTiktokShopMediaService.class);
    TkTiktokShopPublishTaskMapper tasks=mock(TkTiktokShopPublishTaskMapper.class);TkTiktokShopPublishAttemptMapper attempts=mock(TkTiktokShopPublishAttemptMapper.class);
    PlatformTransactionManager tx=mock(PlatformTransactionManager.class);ObjectMapper json=new ObjectMapper();
    TkTiktokShopPublishService service(){TenantContextHolder.setTenantId(1L);when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L,1L,"MEMBER",2L));when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());return new TkTiktokShopPublishService(auth,scope,products,media,tasks,attempts,json,tx);}
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void confirmationRequiresExactImmutableVersionHashAndOnlyCreatesOneAttempt(){
        TkTiktokShopPublishService s=service();TkTiktokShopPublishTaskDO t=new TkTiktokShopPublishTaskDO();t.setId(3L);t.setTenantId(1L);t.setCompanyId(2L);t.setAccountId(4L);t.setCreator("7");t.setVersion(1);t.setRequestHash("hash");t.setStage("AWAITING_CONFIRMATION");t.setPrecheckResult("{\"passed\":true}");t.setProductSnapshot("{}");t.setFileSize(10L);
        when(tasks.findTask(3L,1L)).thenReturn(t);when(auth.requireReadable(4L)).thenReturn(new TkTiktokShopAccountDO());
        ConfirmRequest c=new ConfirmRequest();c.setConfirm(true);c.setVersion(2);c.setRequestHash("hash");assertThrows(RuntimeException.class,()->s.confirm(3L,c));verifyNoInteractions(attempts);
        c.setVersion(1);when(tasks.confirm(eq(3L),eq(1L),eq(1),eq("hash"),eq(7L),any())).thenAnswer(i->{t.setStage("SUBMITTING");return 1;});
        s.confirm(3L,c);s.confirm(3L,c);verify(attempts,times(1)).insert(any(TkTiktokShopPublishAttemptDO.class));
    }
    @Test void validationRejectsUnsafeTitlesAndRequiresUploadedAiChoice(){
        CreateRequest c=new CreateRequest();c.setAccountId(1L);c.setMediaId(2L);c.setTitle("caption");c.setProductId("p");c.setProductSource("SHOWCASE");c.setProductLinkTitle("Tea!");c.setIdempotencyKey("key");c.setSelectionProof("proof");c.setIsAiGenerated(false);
        assertThrows(IllegalArgumentException.class,()->TkTiktokShopPublishService.validate(c));c.setProductLinkTitle("Tea 123");TkTiktokShopPublishService.validate(c);
        c.setIsAiGenerated(null);assertThrows(IllegalArgumentException.class,()->TkTiktokShopPublishService.validate(c));c.setGenerationTaskId(3L);c.setMediaId(null);TkTiktokShopPublishService.validate(c);
        c.setTitle(new String(new char[2201]).replace('\0','x'));assertThrows(IllegalArgumentException.class,()->TkTiktokShopPublishService.validate(c));
    }
    @Test void requestHashIncludesMediaAndAiDisclosure(){CreateRequest c=new CreateRequest();c.setTitle("caption");assertNotEquals(TkTiktokShopPublishService.requestHash(c,"a",json),TkTiktokShopPublishService.requestHash(c,"b",json));c.setIsAiGenerated(false);String first=TkTiktokShopPublishService.requestHash(c,"a",json);c.setIsAiGenerated(true);assertNotEquals(first,TkTiktokShopPublishService.requestHash(c,"a",json));}
    @Test void duplicateCreateReturnsExactTaskAndRejectsChangedInputAndConcurrentWinner(){
        TkTiktokShopPublishService s=service();CreateRequest c=new CreateRequest();c.setAccountId(1L);c.setMediaId(2L);c.setTitle("caption");c.setProductId("p");c.setProductLinkTitle("Tea");c.setIdempotencyKey("key");c.setSelectionProof("proof");c.setIsAiGenerated(false);
        TkTiktokShopAccountDO account=new TkTiktokShopAccountDO();account.setId(1L);account.setTenantId(1L);account.setCompanyId(2L);when(auth.requireReadable(1L)).thenReturn(account);
        when(products.verifySelection(any(),anyString(),anyString(),anyString(),anyString())).thenReturn(new TkTiktokShopProductService.Selection());
        TkTiktokShopMediaService.Snapshot snapshot=new TkTiktokShopMediaService.Snapshot(java.nio.file.Paths.get("private.mp4"),"fingerprint",10,"video/mp4");when(media.snapshot(any(),isNull(),eq(2L))).thenReturn(snapshot);
        TkTiktokShopPublishTaskDO existing=new TkTiktokShopPublishTaskDO();existing.setId(99L);existing.setRequestHash(TkTiktokShopPublishService.requestHash(c,"fingerprint",json));when(tasks.findKey(1L,"7","key")).thenReturn(existing);
        assertEquals(99L,s.create(c).getId());verify(tasks,never()).insert(any(TkTiktokShopPublishTaskDO.class));verify(media).discard(snapshot);
        c.setTitle("changed");assertThrows(org.springframework.web.server.ResponseStatusException.class,()->s.create(c));c.setTitle("caption");
        when(tasks.findKey(1L,"7","key")).thenReturn(null,existing);when(tasks.insert(any(TkTiktokShopPublishTaskDO.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("collision"));assertEquals(99L,s.create(c).getId());
    }
    @Test void ambiguousInsertMustNotDeletePotentiallyCommittedSnapshot(){
        TkTiktokShopPublishService s=service();CreateRequest c=new CreateRequest();c.setAccountId(1L);c.setMediaId(2L);c.setTitle("caption");c.setProductId("p");c.setProductLinkTitle("Tea");c.setIdempotencyKey("key");c.setSelectionProof("proof");c.setIsAiGenerated(false);
        TkTiktokShopAccountDO a=new TkTiktokShopAccountDO();a.setId(1L);a.setCompanyId(2L);when(auth.requireReadable(1L)).thenReturn(a);when(products.verifySelection(any(),anyString(),anyString(),anyString(),anyString())).thenReturn(new TkTiktokShopProductService.Selection());
        TkTiktokShopMediaService.Snapshot snapshot=new TkTiktokShopMediaService.Snapshot(java.nio.file.Paths.get("private.mp4"),"hash",10,"video/mp4");when(media.snapshot(any(),isNull(),eq(2L))).thenReturn(snapshot);when(tasks.insert(any(TkTiktokShopPublishTaskDO.class))).thenThrow(new IllegalStateException("ambiguous database connection"));assertThrows(IllegalStateException.class,()->s.create(c));verify(media,never()).discard(snapshot);
    }
}
