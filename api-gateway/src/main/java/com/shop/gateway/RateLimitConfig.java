package com.shop.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Optional;

/** Rate-limit key: the authenticated user (JWT sub); falls back to the client IP if there is no principal. */
@Configuration
public class RateLimitConfig {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(a -> a.getAddress().getHostAddress())
                        .orElse("anonymous")));
    }
}
