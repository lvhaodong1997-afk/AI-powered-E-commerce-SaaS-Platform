package cn.iocoder.yudao.module.tk.service.open.x;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXReturnTicketDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXReturnTicketMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TkOpenXTicketService {
    public static final String PURPOSE_AUTH_RESULT = "AUTH_RESULT";
    private final TkOpenXReturnTicketMapper tickets;
    private final TkSocialTokenCipher cipher;
    private final SecureRandom random = new SecureRandom();

    @Transactional(rollbackFor = Exception.class)
    public String issueOrReuse(String clientId, String authSessionId, String purpose, LocalDateTime expireTime) {
        TkOpenXReturnTicketDO existing = tickets.selectIssuedBySession(clientId, authSessionId, purpose);
        if (existing != null) {
            if (existing.getExpireTime() != null && existing.getExpireTime().isBefore(LocalDateTime.now())) {
                existing.setStatus("EXPIRED");
                existing.setTicketCipher(null);
                tickets.updateById(existing);
            } else {
                return cipher.decrypt(existing.getTicketCipher(), context(existing));
            }
        }
        String raw = secret();
        TkOpenXReturnTicketDO row = TkOpenXReturnTicketDO.builder()
                .ticketHash(TkSocialAuthService.hash(raw))
                .clientId(clientId)
                .authSessionId(authSessionId)
                .purpose(purpose)
                .status("ISSUED")
                .expireTime(expireTime)
                .build();
        tickets.insert(row);
        row.setTicketCipher(cipher.encrypt(raw, context(row)));
        tickets.updateById(row);
        return raw;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenXReturnTicketDO redeem(String clientId, String rawTicket, String purpose) {
        if (rawTicket == null || !rawTicket.matches("[A-Za-z0-9_-]{43}")) {
            throw TkOpenApiException.badRequest("TICKET_INVALID", "ticket is invalid");
        }
        TkOpenXReturnTicketDO row = tickets.selectByTicketHashForUpdate(TkSocialAuthService.hash(rawTicket));
        if (row == null || !clientId.equals(row.getClientId()) || !purpose.equals(row.getPurpose())) {
            throw TkOpenApiException.notFound("TICKET_NOT_FOUND", "ticket does not exist");
        }
        if (!"ISSUED".equals(row.getStatus())
                || row.getExpireTime() == null
                || row.getExpireTime().isBefore(LocalDateTime.now())) {
            throw TkOpenApiException.badRequest("TICKET_USED", "ticket is no longer valid");
        }
        try {
            String stored = cipher.decrypt(row.getTicketCipher(), context(row));
            if (!rawTicket.equals(stored)) {
                throw TkOpenApiException.notFound("TICKET_NOT_FOUND", "ticket does not exist");
            }
        } catch (TkOpenApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw TkOpenApiException.notFound("TICKET_NOT_FOUND", "ticket does not exist");
        }
        row.setStatus("CONSUMED");
        row.setConsumedTime(LocalDateTime.now());
        row.setTicketCipher(null);
        tickets.updateById(row);
        return row;
    }

    private static String context(TkOpenXReturnTicketDO row) {
        return "open-x:ticket:" + row.getClientId() + ":" + row.getAuthSessionId() + ":" + row.getPurpose() + ":" + row.getId();
    }

    private String secret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}