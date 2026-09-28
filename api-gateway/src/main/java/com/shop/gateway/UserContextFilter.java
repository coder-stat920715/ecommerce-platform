package com.shop.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Trust boundary: strips any client-supplied identity headers, then injects X-User-Id / X-Roles derived from the
 * VALIDATED token, plus a correlation id. Downstream services rely only on these headers.
 */
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-Correlation-Id"))
                .orElseGet(() -> UUID.randomUUID().toString());

        ServerHttpRequest.Builder request = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-Roles");
                    h.set("X-Correlation-Id", correlationId);
                });

        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(maybeToken -> {
                    maybeToken.ifPresent(token -> request.headers(h -> {
                        h.set("X-User-Id", token.getToken().getSubject());
                        h.set("X-Roles", token.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority).collect(Collectors.joining(",")));
                    }));
                    return chain.filter(exchange.mutate().request(request.build()).build());
                });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
