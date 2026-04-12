package com.scarlxrd.gateway_service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private JwtUtils jwtUtils;

    public AuthenticationFilter() {
        super(Config.class);
    }

    public static class Config {}

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            var request = exchange.getRequest();

            // Verifica se tem o Header Authorization
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Invalid Authorization Header", HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            // Valida o Token (Decode)
            var decoded = jwtUtils.validateToken(token);
            if (decoded == null) {
                return onError(exchange, "Invalid Token", HttpStatus.UNAUTHORIZED);
            }

            //  Passa info do usuário para os microserviços via Header
            var rolesList = decoded.getClaim("roles").asList(String.class);
            String rolesString = (rolesList != null) ? String.join(",", rolesList) : "";

            var modifiedRequest = request.mutate()
                    .header("X-User-Email", decoded.getSubject())
                    .header("X-User-Roles", rolesString) // Ex: "USER,ADMIN"
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        var response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }
}
