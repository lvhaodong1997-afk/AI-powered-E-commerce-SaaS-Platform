package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationReqVO;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class TenantRegistrationValidationTest {
    @Test void workspaceNameAcceptsThirtyCharactersAndRejectsThirtyOneBeforeProvisioning() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            TenantRegistrationReqVO request = new TenantRegistrationReqVO();
            request.setTenantName(String.join("", Collections.nCopies(30, "租")));
            assertTrue(factory.getValidator().validateProperty(request, "tenantName").isEmpty());
            request.setTenantName(String.join("", Collections.nCopies(31, "租")));
            assertFalse(factory.getValidator().validateProperty(request, "tenantName").isEmpty());
        }
    }
}
