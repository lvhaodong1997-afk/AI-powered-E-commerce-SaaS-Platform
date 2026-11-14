package cn.iocoder.yudao.module.system.service.tenant;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationReqVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PhoneVerificationClientTest {
    @Test void signingBindsPathAndExactBody() {
        String secret="12345678901234567890123456789012";
        String a=PhoneVerificationClient.sign(secret,"1234567890","0123456789abcdef0123456789abcdef","POST","/internal/phone-verification/send","{}");
        assertTrue(a.matches("[a-f0-9]{64}"));
        assertNotEquals(a,PhoneVerificationClient.sign(secret,"1234567890","0123456789abcdef0123456789abcdef","POST","/internal/phone-verification/consume","{}"));
        assertNotEquals(a,PhoneVerificationClient.sign(secret,"1234567890","0123456789abcdef0123456789abcdef","POST","/internal/phone-verification/send","{ }"));
    }
    @Test void providerVerificationMustMatchTkAndRequestBinding() throws Exception {
        TenantRegistrationReqVO req=new TenantRegistrationReqVO(); req.setMobile("13800138000"); req.setRequestId("request1");
        ObjectMapper mapper=new ObjectMapper();
        String body="{\"verified\":true,\"appId\":\"clipforge\",\"purpose\":\"REGISTER\",\"mobile\":\"13800138000\",\"registrationRequestId\":\"request1\"}";
        PhoneVerificationClient.validateBinding(mapper.readTree(body),req);
        assertThrows(RuntimeException.class, () -> PhoneVerificationClient.validateBinding(mapper.readTree(body.replace("clipforge","jiying")),req));
        assertThrows(RuntimeException.class, () -> PhoneVerificationClient.validateBinding(mapper.readTree(body.replace("request1","request2")),req));
    }
    @Test void configurationFailsClosedWithoutDedicatedSecretOrHttps() {
        TenantRegistrationProperties p=new TenantRegistrationProperties();
        assertFalse(p.tenantAvailable()); p.setEnabled(true); p.setDefaultPackageId(5L); p.setValidityDays(30);
        assertTrue(p.tenantAvailable()); assertFalse(p.smsAvailable());
        p.setPhoneBaseUrl("https://sms.example.com"); p.setPhoneSecret("12345678901234567890123456789012"); assertTrue(p.smsAvailable());
        p.setPhoneBaseUrl("http://sms.example.com"); assertFalse(p.smsAvailable());
    }
    @Test void exactCalendarYearsAreAvailableWithoutDayConfiguration() {
        TenantRegistrationProperties p=new TenantRegistrationProperties();p.setEnabled(true);p.setDefaultPackageId(111L);p.setValidityDays(0);p.setValidityYears(10);
        assertTrue(p.tenantAvailable());p.setValidityYears(101);assertFalse(p.tenantAvailable());
    }

    @Test void recoveredSentChallengeAllowsZeroCooldownAndExpiredOriginal() throws Exception {
        ObjectMapper json=new ObjectMapper();
        for(int remaining:new int[]{240,1,0}) {
            String body="{\"smsRequestId\":\"challenge-original\",\"deliveryStatus\":\"SENT\",\"expiresInSeconds\":"+remaining+",\"resendAfterSeconds\":0}";
            cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationSmsRespVO result=PhoneVerificationClient.parseSendResponse(json.readTree(body));
            assertEquals(remaining,result.getExpiresInSeconds());assertEquals(0,result.getResendAfterSeconds());
        }
    }
    @Test void malformedAndUnknownChallengeFieldsFailClosed() throws Exception {
        ObjectMapper json=new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode valid=json.createObjectNode().put("smsRequestId","challenge-original").put("deliveryStatus","SENT").put("expiresInSeconds",200).put("resendAfterSeconds",30);
        for(String field:new String[]{"expiresInSeconds","resendAfterSeconds"}) {
            for(com.fasterxml.jackson.databind.JsonNode invalid:new com.fasterxml.jackson.databind.JsonNode[]{json.nullNode(),json.getNodeFactory().numberNode(-1),json.getNodeFactory().numberNode(0.5),json.getNodeFactory().textNode("30"),json.getNodeFactory().numberNode(301)}) {
                com.fasterxml.jackson.databind.node.ObjectNode body=valid.deepCopy();body.set(field,invalid);
                assertThrows(RuntimeException.class,()->PhoneVerificationClient.parseSendResponse(body));
            }
            com.fasterxml.jackson.databind.node.ObjectNode missing=valid.deepCopy();missing.remove(field);
            assertThrows(RuntimeException.class,()->PhoneVerificationClient.parseSendResponse(missing));
        }
        for(String state:new String[]{"SENT","PENDING","UNKNOWN"}) {
            com.fasterxml.jackson.databind.node.ObjectNode body=valid.deepCopy().put("deliveryStatus",state);
            assertEquals(state,PhoneVerificationClient.parseSendResponse(body).getDeliveryStatus());
        }
        assertThrows(RuntimeException.class,()->PhoneVerificationClient.parseSendResponse(valid.deepCopy().put("deliveryStatus","FAILED")));
    }
}
