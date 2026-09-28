package com.shop.payment.psp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class PspClientConfig {

    @Bean
    RestClient pspRestClient(@Value("${psp.base-url}") String baseUrl,
                             @Value("${psp.connect-timeout:PT1S}") Duration connectTimeout,
                             @Value("${psp.read-timeout:PT3S}") Duration readTimeout) {
        // Hard timeouts are the first line of defence; never call a PSP without them.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeout.toMillis());
        factory.setReadTimeout((int) readTimeout.toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
