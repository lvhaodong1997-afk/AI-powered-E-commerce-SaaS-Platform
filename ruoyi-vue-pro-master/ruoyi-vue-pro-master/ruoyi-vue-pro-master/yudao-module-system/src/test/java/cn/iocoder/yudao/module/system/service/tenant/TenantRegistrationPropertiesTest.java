package cn.iocoder.yudao.module.system.service.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantRegistrationPropertiesTest {
    @Test void newRegistrationsDefaultToFiveMemberSeats() {
        assertEquals(5, new TenantRegistrationProperties().getMemberLimit());
    }

    @Test void explicitMemberSeatLimitOverridesDefault() {
        TenantRegistrationProperties properties = new TenantRegistrationProperties();
        properties.setMemberLimit(8);
        assertEquals(8, properties.getMemberLimit());
    }
}
