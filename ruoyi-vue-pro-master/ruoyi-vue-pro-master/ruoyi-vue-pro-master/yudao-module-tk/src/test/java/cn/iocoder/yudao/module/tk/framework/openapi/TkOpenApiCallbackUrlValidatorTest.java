package cn.iocoder.yudao.module.tk.framework.openapi;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkOpenApiCallbackUrlValidatorTest {

    @Test
    void shouldAcceptPublicHttpsCallback() {
        assertDoesNotThrow(() -> TkOpenApiCallbackUrlValidator.validate("https://partner.example.com/tk/callback"));
    }

    @Test
    void shouldRejectNonHttpsAndPrivateCallbacks() {
        assertThrows(IllegalArgumentException.class,
                () -> TkOpenApiCallbackUrlValidator.validate("http://partner.example.com/callback"));
        assertThrows(IllegalArgumentException.class,
                () -> TkOpenApiCallbackUrlValidator.validate("https://127.0.0.1/callback"));
        assertThrows(IllegalArgumentException.class,
                () -> TkOpenApiCallbackUrlValidator.validate("https://10.0.0.8/callback"));
    }

    @Test
    void shouldReturnEveryValidatedDestinationAddress() throws Exception {
        InetAddress first = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        InetAddress second = InetAddress.getByAddress(new byte[]{1, 1, 1, 1});

        InetAddress[] resolved = TkOpenApiCallbackUrlValidator.resolveAndValidate(
                "https://partner.example.com/callback", host -> new InetAddress[]{first, second});

        assertArrayEquals(new InetAddress[]{first, second}, resolved);
    }

    @Test
    void shouldRejectDestinationWhenAnyResolvedAddressIsPrivate() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
        InetAddress privateAddress = InetAddress.getByAddress(new byte[]{10, 0, 0, 8});

        assertThrows(IllegalArgumentException.class,
                () -> TkOpenApiCallbackUrlValidator.resolveAndValidate(
                        "https://partner.example.com/callback",
                        host -> new InetAddress[]{publicAddress, privateAddress}));
    }
}
