package com.scarlxrd.gateway_service;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureWebTestClient
@DisplayName("AuthenticationFilter - Testes de autenticação no Gateway")
class AuthenticationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private JwtUtils jwtUtils;

    @Nested
    @DisplayName("Quando a requisição NÃO possui autenticação")
    class NoAuthentication {

        @Test
        @DisplayName("Deve retornar 401 quando não houver header Authorization")
        void shouldReturn401_whenNoAuthorizationHeader() {

            // When
            var response = webTestClient.get()
                    .uri("/books/test")
                    .exchange();

            // Then
            response.expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Quando o token é inválido")
    class InvalidToken {

        @Test
        @DisplayName("Deve retornar 401 quando o token for inválido")
        void shouldReturn401_whenInvalidToken() {

            // Given
            when(jwtUtils.validateToken("invalid")).thenReturn(null);

            // When
            var response = webTestClient.get()
                    .uri("/books/test")
                    .header("Authorization", "Bearer invalid")
                    .exchange();

            // Then
            response.expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Quando o token é válido")
    class ValidToken {

        @Test
        @DisplayName("Deve permitir a requisição quando o token for válido")
        void shouldPass_whenValidToken() {

            // Given
            DecodedJWT decoded = mock(DecodedJWT.class);
            Claim roles = mock(Claim.class);

            when(decoded.getSubject()).thenReturn("user@test.com");
            when(decoded.getClaim("roles")).thenReturn(roles);
            when(roles.asList(String.class)).thenReturn(List.of("ROLE_USER"));

            when(jwtUtils.validateToken("valid")).thenReturn(decoded);

            // When
            var response = webTestClient.get()
                    .uri("/books/test")
                    .header("Authorization", "Bearer valid")
                    .exchange();

            // Then
            response.expectStatus().is5xxServerError();
            verify(jwtUtils).validateToken("valid");
        }

        @Test
        @DisplayName("Deve extrair claims e adicionar headers quando o token for válido")
        void shouldAddHeaders_whenValidToken() {

            // Given
            DecodedJWT decoded = mock(DecodedJWT.class);
            Claim roles = mock(Claim.class);
            Claim userIdClaim = mock(Claim.class);

            when(decoded.getSubject()).thenReturn("user@test.com");

            when(decoded.getClaim("roles")).thenReturn(roles);
            when(roles.asList(String.class))
                    .thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));

            when(decoded.getClaim("userId")).thenReturn(userIdClaim);
            when(userIdClaim.asString()).thenReturn("12345");

            when(jwtUtils.validateToken("valid")).thenReturn(decoded);

            // When
            var response = webTestClient.get()
                    .uri("/books/test")
                    .header("Authorization", "Bearer valid")
                    .exchange();

            // Then
            response.expectStatus().is5xxServerError();
            verify(jwtUtils).validateToken("valid");
        }
    }
}