package cn.iocoder.yudao.module.tk.service.tiktokshop;

import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared single-transmission transport for Shop JSON calls and binary uploads. */
public final class TkTiktokShopHttpTransport {
    private TkTiktokShopHttpTransport() {}

    /**
     * Create an independent client with at most one network exchange per logical call.
     * Upload callers may use newBuilder() to set finite upload timeouts, preserving both
     * interceptors and disabled retries/redirects. They must enforce fixed official hosts
     * and sanitize failures without exposing request URLs, credentials or exception causes.
     */
    public static OkHttpClient createClient() { return createClient(new OkHttpClient()); }

    /** Package-only transport seam; runtime configuration cannot inject interceptors. */
    static OkHttpClient createClient(OkHttpClient supplied) {
        OkHttpClient.Builder builder=supplied.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS);
        // A shared application's interceptors/authenticators must never observe Shop secrets.
        builder.interceptors().clear();builder.networkInterceptors().clear();
        builder.authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE);
        // Application interceptors run once per Call; network interceptors run per exchange.
        builder.addInterceptor(chain->chain.proceed(chain.request().newBuilder()
                .tag(Transmission.class,new Transmission()).build()));
        builder.addNetworkInterceptor(chain->{
            Transmission transmission=chain.request().tag(Transmission.class);
            if(transmission==null || !transmission.sent.compareAndSet(false,true))
                throw new IOException("Shop request retransmission blocked");
            // This check also blocks response-driven 503/421 follow-ups for bodyless GETs.
            return chain.proceed(chain.request());
        });
        return builder.build();
    }

    private static final class Transmission {
        private final AtomicBoolean sent=new AtomicBoolean();
    }
}
