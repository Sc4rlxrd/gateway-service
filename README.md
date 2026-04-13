# 🌐 Gateway Service

> Ponto de entrada único do **BookCommerce** — responsável por rotear requisições, validar tokens JWT e propagar identidade do usuário autenticado para os microsserviços internos.

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-6DB33F?style=for-the-badge&logo=springboot)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring_Cloud_Gateway-2023.0.1-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)

---

## 📋 Sumário

- [Sobre](#-sobre)
- [Responsabilidades](#-responsabilidades)
- [Arquitetura Interna](#-arquitetura-interna)
- [Roteamento](#-roteamento)
- [Fluxo de Autenticação](#-fluxo-de-autenticação)
- [Propagação de Identidade](#-propagação-de-identidade)
- [Segurança](#-segurança)
- [Tecnologias](#-tecnologias)
- [Como Rodar](#-como-rodar)
- [Visão Geral da Arquitetura BookCommerce](#-visão-geral-da-arquitetura-bookcommerce)

---

## 📖 Sobre

O **Gateway Service** é o **ponto de entrada único** de toda a plataforma BookCommerce. Ele recebe todas as requisições externas, valida o JWT localmente e as encaminha para o microsserviço correto — injetando os dados do usuário autenticado via headers HTTP para que os serviços internos não precisem se preocupar com autenticação.

- **Porta:** `8080`
- **Paradigma:** Reativo (WebFlux + Spring Cloud Gateway)

---

## 🧠 Responsabilidades

- ✅ Rotear requisições para os microsserviços corretos
- ✅ Validar JWT localmente via `JwtUtils` (sem chamada ao `identity-service`)
- ✅ Bloquear requisições sem token ou com token inválido (`401 Unauthorized`)
- ✅ Injetar `X-User-Email` e `X-User-Roles` nos headers das requisições roteadas
- ✅ Permitir acesso público às rotas de autenticação (`/auth/**`)

---

## 🏗️ Arquitetura Interna

### 📁 Estrutura de Pacotes

```
src/main/java/com/scarlxrd/gateway_service/
│
├── GatewayServiceApplication.java     # Entry point
├── AuthenticationFilter.java          # Filtro JWT aplicado nas rotas protegidas
├── GatewaySecurityConfig.java         # Configuração WebFlux Security
└── JwtUtils.java                      # Validação e decodificação do JWT
```

> O gateway é intencionalmente enxuto — toda a lógica de negócio de autenticação vive no `identity-service`. O gateway apenas valida a assinatura do token e propaga a identidade.

---

## 🗺️ Roteamento

Todas as rotas são configuradas via `application.yaml`:

| Rota         | Destino                        | Filtro JWT       |
|--------------|--------------------------------|------------------|
| `/auth/**`   | `identity-service` (:8084)     | ❌ Público       |
| `/books/**`  | `catalog-service` (:8081)      | ✅ Obrigatório   |
| `/orders/**` | `order-service` (:8082)        | ✅ Obrigatório   |
| `/payments/**` | `payment-service` (:8083)    | ✅ Obrigatório   |

---

## 🔄 Fluxo de Autenticação

```
Cliente
  │
  ▼
Gateway (:8080)
  │
  ├── /auth/** ──────────────────────────────► identity-service (:8084)
  │                                            (sem validação JWT)
  │
  └── /books, /orders, /payments
        │
        ▼
   AuthenticationFilter
        │
        ├── Sem header Authorization?      → 401 Unauthorized
        ├── Header não começa com Bearer?  → 401 Unauthorized
        ├── Token inválido ou expirado?    → 401 Unauthorized
        │
        └── Token válido ✅
              │
              ▼
        Injeta headers:
        X-User-Email: user@email.com
        X-User-Roles: USER,ADMIN
              │
              ▼
        Microsserviço de destino
```

---

## 📨 Propagação de Identidade

Após validação bem-sucedida do JWT, o `AuthenticationFilter` **modifica a requisição** antes de encaminhá-la, adicionando:

| Header          | Valor                        | Exemplo                        |
|-----------------|------------------------------|--------------------------------|
| `X-User-Email`  | Subject do JWT               | `user@email.com`               |
| `X-User-Roles`  | Claims `roles` do JWT        | `USER` ou `USER,ADMIN`         |

Os microsserviços internos confiam nesses headers — **nunca devem ser expostos diretamente à internet**, apenas via gateway.

---

## 🔐 Segurança

### Validação JWT

O `JwtUtils` valida o token localmente usando a mesma chave secreta compartilhada com o `identity-service`:

```yaml
api:
  security:
    token:
      secret: ${JWT_SECRET:dd2e2add61a0cce0eb2bfb9c2d812515}
```

- Algoritmo: **HMAC256**
- Biblioteca: **Auth0 JWT** (`com.auth0:java-jwt:4.5.0`)
- Token inválido ou expirado retorna `null` → `401 Unauthorized`

### WebFlux Security

Configurado via `GatewaySecurityConfig`:

- CSRF desabilitado (API stateless)
- `/auth/**` liberado publicamente
- Demais rotas permitem qualquer acesso no nível do Security — a proteção real é feita pelo `AuthenticationFilter` no nível do Gateway

> 💡 Essa abordagem delega a proteção efetiva ao `AuthenticationFilter`, mantendo o `SecurityConfig` simples e sem acoplamento com a lógica de rotas.

---

## ⚙️ Tecnologias

| Tecnologia                | Finalidade                                    |
|---------------------------|-----------------------------------------------|
| Java 21                   | Linguagem principal                           |
| Spring Boot 3.2.5         | Framework base                                |
| Spring Cloud Gateway      | Roteamento e filtros reativos                 |
| Spring Security (WebFlux) | Configuração de segurança reativa             |
| Auth0 JWT 4.5.0           | Validação e decodificação de tokens JWT       |
| Project Reactor           | Programação reativa (Mono/Flux)               |
| Spring Boot Logging       | Logs de debug do gateway                      |

---

## 🐳 Como Rodar

### Pré-requisitos

- Java 21+
- Maven
- Todos os microsserviços dependentes rodando:

| Serviço             | Porta  |
|---------------------|--------|
| `identity-service`  | `8084` |
| `catalog-service`   | `8081` |
| `order-service`     | `8082` |
| `payment-service`   | `8083` |

### Executar o serviço

```bash
./mvnw spring-boot:run
```

### Variáveis de ambiente

| Variável     | Descrição                                                         |
|--------------|-------------------------------------------------------------------|
| `JWT_SECRET` | Chave secreta HMAC256 — deve ser **idêntica** à do `identity-service` |

> ⚠️ O `JWT_SECRET` deve ser exatamente o mesmo nos dois serviços. Se divergirem, todos os tokens serão rejeitados pelo gateway.

---

## 🏛️ Visão Geral da Arquitetura BookCommerce

```
                        ┌─────────────────────────────┐
                        │         Cliente              │
                        └──────────────┬──────────────┘
                                       │ HTTP
                                       ▼
                        ┌─────────────────────────────┐
                        │      Gateway Service         │
                        │          :8080               │
                        │  (AuthenticationFilter +     │
                        │      JWT Validation)         │
                        └──┬──────┬──────┬──────┬─────┘
                           │      │      │      │
               /auth/**    │  /books/**  │  /payments/**
                           │      │  /orders/** │
                           ▼      ▼      ▼      ▼
                  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
                  │identity│ │catalog │ │ order  │ │payment │
                  │service │ │service │ │service │ │service │
                  │ :8084  │ │ :8081  │ │ :8082  │ │ :8083  │
                  └────────┘ └────────┘ └───┬────┘ └───▲────┘
                                            │  RabbitMQ │
                                            └───────────┘
```

---

> Parte da arquitetura de microserviços do **BookCommerce** — ponto de entrada único para todos os clientes externos.