package com.mobai.alert.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.TimeUnit;

@Configuration
public class RestTemplateConfig {

    @Value("${monitoring.proxy.enabled:true}")
    private boolean proxyEnabled;

    @Value("${monitoring.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${monitoring.proxy.port:7890}")
    private int proxyPort;

    @Value("${market.ws.ping-interval-seconds:20}")
    private long webSocketPingIntervalSeconds;

    @Bean
    public RestTemplate restTemplate(ClientHttpRequestFactory factory) {
        return new RestTemplate(factory);
    }

    @Bean
    public OkHttpClient okHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(webSocketPingIntervalSeconds, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        Proxy proxy = buildProxy();
        if (proxy != null) {
            builder.proxy(proxy);
        }
        return builder.build();
    }

    @Bean
    public ClientHttpRequestFactory simpleClientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);
        factory.setReadTimeout(30000);
        Proxy proxy = buildProxy();
        if (proxy != null) {
            factory.setProxy(proxy);
        }
        return factory;
    }

    private Proxy buildProxy() {
        if (!proxyEnabled || proxyHost == null || proxyHost.isBlank()) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }
}
