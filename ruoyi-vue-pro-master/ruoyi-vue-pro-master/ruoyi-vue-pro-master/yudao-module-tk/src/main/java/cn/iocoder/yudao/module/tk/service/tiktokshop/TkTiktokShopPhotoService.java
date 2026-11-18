package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopPhotoMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TkTiktokShopPhotoService {
    private static final long MAX_BYTES=10L*1024*1024;
    private static final String POST="/affiliate_creator/202610/photos";
    private final TkTiktokShopAuthService auth;
    private final TkDataScopeService scope;
    private final TkTiktokShopTokenService tokens;
    private final TkTiktokShopProductService products;
    private final TkTiktokShopApiClient api;
    private final TkTiktokShopPhotoMapper photos;
    private final TkTiktokShopProperties properties;
    private final TransactionTemplate transaction;

    public TkTiktokShopPhotoService(TkTiktokShopAuthService auth,TkDataScopeService scope,TkTiktokShopTokenService tokens,
            TkTiktokShopProductService products,TkTiktokShopApiClient api,TkTiktokShopPhotoMapper photos,
            TkTiktokShopProperties properties,PlatformTransactionManager tx) {
        this.auth=auth;this.scope=scope;this.tokens=tokens;this.products=products;this.api=api;this.photos=photos;this.properties=properties;
        transaction=new TransactionTemplate(tx);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PhotoResponse publish(PhotoRequest request,List<MultipartFile> files) {
        if(!properties.isPublishingConfigured())throw new IllegalStateException("Shop publishing is not configured");
        if(!Boolean.TRUE.equals(request.confirm) || request.isAiGenerated==null || request.accountId==null || request.idempotencyKey==null
                || !request.idempotencyKey.matches("[a-zA-Z0-9-]{16,64}") || request.title==null || request.title.length()>5000
                || request.linkTitle==null || request.linkTitle.trim().isEmpty() || request.linkTitle.length()>29
                || request.productId==null || !request.productId.matches("[A-Za-z0-9_-]{1,128}") || files==null || files.isEmpty() || files.size()>9)
            throw new IllegalArgumentException("Invalid shoppable photo confirmation");
        TkTiktokShopAccountDO account=auth.requireWritable(request.accountId);
        String user=scope.getCurrentScope().getUserIdString();
        List<String> types=files.stream().map(TkTiktokShopPhotoService::imageType).collect(Collectors.toList());
        String hash=hash(request,files);
        TkTiktokShopPhotoDO found=existing(account,user,request.idempotencyKey);
        if(found!=null)return check(found,hash);
        TkTiktokShopProductService.Selection selection=products.verifySelection(account,user,request.productId,request.productSource,request.selectionProof);
        products.revalidate(account,selection);
        TkTiktokShopTokenService.AccessToken token=tokens.getAccessToken(account.getTenantId(),account.getCompanyId(),account.getId());
        if(!Objects.equals(selection.getAuthVersion(),token.getVersion()))throw new IllegalArgumentException("Shop authorization changed; select the product again");
        TkTiktokShopPhotoDO row=new TkTiktokShopPhotoDO();
        row.setTenantId(account.getTenantId());row.setCompanyId(account.getCompanyId());row.setAccountId(account.getId());row.setCreator(user);row.setUpdater(user);
        row.setIdempotencyKey(request.idempotencyKey);row.setRequestHash(hash);row.setTitle(request.title);row.setProductId(request.productId);row.setStage("UPLOADING");
        try {transaction.execute(status -> photos.insert(row));}
        catch(DuplicateKeyException e) {
            found=existing(account,user,request.idempotencyKey);if(found==null)throw e;return check(found,hash);
        }
        try {
            List<Map<String,String>> images=new ArrayList<>();
            for(int i=0;i<files.size();i++) {
                TkTiktokShopApiClient.Result result=api.uploadPhoto(token.getValue(),body(files.get(i),types.get(i)),types.get(i));
                String uri=result.getData().path("photo_file").path("photo_uri").asText("");
                if(uri.isEmpty() || uri.length()>4096)throw new TkTiktokShopApiException("INVALID_RESPONSE",result.getRequestId());
                images.add(Collections.singletonMap("photo_file_uris",uri));
            }
            Map<String,Object> links=new LinkedHashMap<>();links.put("post_type","1");
            Map<String,String> link=new LinkedHashMap<>();link.put("product_id",request.productId);link.put("link_title",request.linkTitle);
            links.put("links",Collections.singletonList(link));
            Map<String,Object> payload=new LinkedHashMap<>();payload.put("photos_info",images);payload.put("link_info",links);
            payload.put("title",request.title);payload.put("is_ai_generated",request.isAiGenerated);
            // The committed SENDING marker must exist before the irreversible request.
            transition(row,"SENDING");
            TkTiktokShopApiClient.Result result=api.post(POST,token.getValue(),payload);
            row.setRequestId(result.getRequestId());
            String id=result.getData().path("photo").path("photo_post_id").asText("");
            if(!id.matches("[A-Za-z0-9_-]{1,128}"))throw new TkTiktokShopApiException("INVALID_RESPONSE",result.getRequestId());
            row.setPhotoId(id);transition(row,"SUBMITTED");
        } catch(TkTiktokShopApiException e) {
            row.setFailCode(e.getCode());row.setRequestId(e.getRequestId());
            transition(row,"UPLOADING".equals(row.getStage()) || row.getFailCode().matches("[1-9][0-9]*")?"FAILED":"UNKNOWN");
            if(e.isReauthorizationRequired())tokens.reportRejectedToken(account.getTenantId(),account.getCompanyId(),account.getId(),token.getVersion(),e);
        }
        return new PhotoResponse(row);
    }

    public PageResult<PhotoResponse> history(Long accountId,int pageNo) {
        TkTiktokShopAccountDO account=auth.requireReadable(accountId);
        PageParam page=new PageParam();page.setPageNo(Math.max(1,Math.min(10000,pageNo)));page.setPageSize(20);
        PageResult<TkTiktokShopPhotoDO> result=photos.selectPage(page,owned(account,scope.getCurrentScope().getUserIdString()).orderByDesc(TkTiktokShopPhotoDO::getId));
        return new PageResult<>(result.getList().stream().map(PhotoResponse::new).collect(Collectors.toList()),result.getTotal());
    }

    public PhotoResponse status(Long accountId,Long id) {
        TkTiktokShopAccountDO account=auth.requireReadable(accountId);
        TkTiktokShopPhotoDO row=photos.selectOne(owned(account,scope.getCurrentScope().getUserIdString()).eq(TkTiktokShopPhotoDO::getId,id));
        if(row==null)throw new IllegalArgumentException("Shop photo unavailable or access denied");
        if("SUBMITTED".equals(row.getStage()) && row.getPhotoId()!=null && row.getPhotoId().matches("[A-Za-z0-9_-]{1,128}")){
            TkTiktokShopTokenService.AccessToken token=tokens.getAccessToken(account.getTenantId(),account.getCompanyId(),accountId);
            try {
                TkTiktokShopApiClient.Result result=api.get("/affiliate_creator/202511/photos/"+row.getPhotoId()+"/status",token.getValue(),Collections.emptyMap());
                JsonNode photo=result.getData().path("photo");
                if(!row.getPhotoId().equals(photo.path("photo_post_id").asText()) || !photo.path("post_status").isTextual())
                    throw new TkTiktokShopApiException("INVALID_RESPONSE",result.getRequestId());
                String postStatus=photo.path("post_status").asText();
                if(postStatus.length()>64 || postStatus.trim().isEmpty())throw new TkTiktokShopApiException("INVALID_RESPONSE",result.getRequestId());
                photos.update(null,new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TkTiktokShopPhotoDO>()
                        .eq(TkTiktokShopPhotoDO::getId,id).eq(TkTiktokShopPhotoDO::getTenantId,account.getTenantId())
                        .eq(TkTiktokShopPhotoDO::getPostStatus,row.getPostStatus()==null?"":row.getPostStatus()).set(TkTiktokShopPhotoDO::getPostStatus,postStatus));
                row.setPostStatus(postStatus);
            } catch(TkTiktokShopApiException e) {
                if(e.isReauthorizationRequired())tokens.reportRejectedToken(account.getTenantId(),account.getCompanyId(),accountId,token.getVersion(),e);
                throw e;
            }
        }
        return new PhotoResponse(row);
    }

    private void transition(TkTiktokShopPhotoDO row,String next) {
        if(!Integer.valueOf(1).equals(transaction.execute(tx->photos.transition(row,row.getStage(),next))))
            throw new IllegalStateException("Shop photo state changed");
        row.setStage(next);
    }

    private TkTiktokShopPhotoDO existing(TkTiktokShopAccountDO a,String user,String key) {
        return photos.selectOne(owned(a,user).eq(TkTiktokShopPhotoDO::getIdempotencyKey,key));
    }

    private static LambdaQueryWrapperX<TkTiktokShopPhotoDO> owned(TkTiktokShopAccountDO a,String user) {
        return new LambdaQueryWrapperX<TkTiktokShopPhotoDO>().eq(TkTiktokShopPhotoDO::getTenantId,a.getTenantId())
                .eq(TkTiktokShopPhotoDO::getCompanyId,a.getCompanyId()).eq(TkTiktokShopPhotoDO::getAccountId,a.getId())
                .eq(TkTiktokShopPhotoDO::getCreator,user);
    }

    private static PhotoResponse check(TkTiktokShopPhotoDO row,String hash) {
        if(!hash.equals(row.getRequestHash()))throw new IllegalArgumentException("Shop photo confirmation key has different input");
        return new PhotoResponse(row);
    }

    static String imageType(MultipartFile file) {
        if(file==null || file.getSize()<1 || file.getSize()>MAX_BYTES)throw new IllegalArgumentException("Image must be at most 10 MiB");
        try(InputStream bytes=file.getInputStream(); MemoryCacheImageInputStream input=new MemoryCacheImageInputStream(bytes)) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException();
            ImageReader reader=readers.next();
            try {
                String name=reader.getFormatName();
                String type="JPEG".equalsIgnoreCase(name)?"image/jpeg":"PNG".equalsIgnoreCase(name)?"image/png":null;
                if(type==null || !type.equalsIgnoreCase(file.getContentType()))throw new IOException();
                reader.setInput(input,true,true);
                long w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1 || h<1 || w*16<h*9 || h*16<w*9 || w*h>40_000_000)throw new IOException();
                return type;
            } finally {reader.dispose();}
        } catch(IOException | RuntimeException e) {throw new IllegalArgumentException("Choose a valid JPEG or PNG with a 9:16 to 16:9 aspect ratio");}
    }

    private static String hash(PhotoRequest request,List<MultipartFile> files) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(String field:Arrays.asList(request.title,request.linkTitle,request.productId,request.productSource,String.valueOf(request.isAiGenerated))) {
                byte[] data=String.valueOf(field).getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());digest.update(data);
            }
            byte[] buffer=new byte[8192];
            for(MultipartFile file:files)try(InputStream input=file.getInputStream()) {
                digest.update(java.nio.ByteBuffer.allocate(8).putLong(file.getSize()).array());
                int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
        } catch(Exception e) {throw new IllegalArgumentException("Could not read Shop photo input");}
    }

    private static RequestBody body(MultipartFile file,String type) {
        return new RequestBody() {
            @Override public MediaType contentType(){return MediaType.parse(type);}
            @Override public long contentLength(){return file.getSize();}
            @Override public void writeTo(BufferedSink sink) throws IOException {try(okio.Source input=Okio.source(file.getInputStream())){sink.writeAll(input);}}
        };
    }

    @Getter @Setter public static final class PhotoRequest {
        private Long accountId;
        private String idempotencyKey,title,productId,productSource,selectionProof,linkTitle;
        private Boolean confirm,isAiGenerated;
    }

    @Getter public static final class PhotoResponse {
        private final Long id;
        private final String title,productId,stage,photoId,postStatus,failCode,requestId;
        private final LocalDateTime createTime;
        PhotoResponse(TkTiktokShopPhotoDO p) {
            id=p.getId();title=p.getTitle();productId=p.getProductId();stage=p.getStage();photoId=p.getPhotoId();postStatus=p.getPostStatus();
            failCode=p.getFailCode();requestId=p.getRequestId();createTime=p.getCreateTime();
        }
    }
}
