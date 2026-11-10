# Sistema de gerenciamento de tarefas — microsserviços Java e React

Projeto de demonstração técnica com React, Spring Boot, API Gateway, PostgreSQL, testes automatizados e tracing distribuído. O objetivo é estudar separação de responsabilidades, autenticação, persistência e integração entre serviços; **não se trata de uma implantação de produção**.

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENTE (Browser)                        │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (:8080)                        │
│                   (Spring Cloud Gateway)                        │
└────┬───────────────────────┬───────────────────────┬────────────┘
     │                       │                       │
     ▼                       ▼                       ▼
┌─────────────┐     ┌─────────────┐         ┌─────────────┐
│ User Service│     │ Todo Service│         │   Eureka    │
│   (:8081)   │     │   (:8082)   │         │  (:8761)    │
└──────┬──────┘     └──────┬──────┘         └─────────────┘
       │                   │
       ▼                   ▼
┌─────────────┐     ┌─────────────┐
│ PostgreSQL  │     │ PostgreSQL  │
│   (:5432)   │     │   (:5432)   │
└─────────────┘     └─────────────┘
```

## 🛠️ Stack Tecnológica

| Serviço | Tecnologia | Porta |
|---------|------------|-------|
| **Frontend** | React + Vite + TailwindCSS | 3000 |
| **API Gateway** | Spring Cloud Gateway | 8080 |
| **User Service** | Spring Boot + JPA | 8081 |
| **Todo Service** | Spring Boot + JPA | 8082 |
| **Service Discovery** | Eureka Server | 8761 |
| **Banco de Dados** | PostgreSQL 15 | 5432 |
| **Redis (disponível no Compose; ainda sem integração funcional)** | Redis 7 | 6379 |

## 📁 Estrutura do Projeto

```
todo-monorepo/
├── docker-compose.yml
├── package.json
│
├── packages/
│   ├── eureka-server/     # Service Discovery
│   ├── api-gateway/       # API Gateway
│   ├── user-service/      # Gerenciamento de Usuários
│   ├── todo-service/      # Gerenciamento de Todos
│   └── web-app/           # Frontend React
│
└── scripts/
```

## 🛡️ Configuração segura

Antes de iniciar com Docker Compose, copie `.env.example` para `.env` e preencha `POSTGRES_PASSWORD` e `JWT_SECRET` com valores fortes (não versione o `.env`). Para executar os serviços Java fora do Docker, exporte `DB_PASSWORD` e `JWT_SECRET` no ambiente. Credenciais de demonstração não são apropriadas para produção.

**Importante:** os serviços Java precisam compartilhar o segredo JWT. No ambiente Docker o Compose fornece essas variáveis; no ambiente local o segredo e a senha não são mais embutidos nos arquivos `application.yml`.

## 🚀 Como Executar

### Pré-requisitos
- Docker e Docker Compose
- Java 17+
- Node.js 18+
- Maven 3.9+

### 1. Script de Desenvolvimento Local (Recomendado)

O script `scripts/dev-local.sh` permite subir/parar serviços individualmente com interface interativa:

```bash
# Execute o script
./scripts/dev-local.sh

# Opções disponíveis:
#   1-5)  Subir serviços individualmente
#   6-10) Parar serviços individualmente
#   d1-d3) Subir com DEBUG (JDWP)
#   11) Subir TODOS (Eureka → User → Todo → Gateway)
#   12) Parar TODOS
#   13) Ver Status
#   14) Ver Logs
#   d) Iniciar Docker (Banco de Dados)
#   d0) Parar Docker (Banco de Dados)
#   15) Sair
```

> **Nota:** Antes de subir os serviços Java, certifique-se de que o Docker (banco de dados) está rodando (`d`).

### 2. Subir todos os serviços via Docker Compose
```bash
# Development (sobe todos os containers)
npm run dev

# Em background
npm run dev:detached

# Apenas os serviços backend (sem frontend)
npm run dev:services
```

> **Nota:** Esses comandos usam Docker Compose por baixo, subindo todos os serviços como containers Docker.

### 3. Desenvolvimento Local do Frontend
```bash
# Navegue até o diretório
cd packages/web-app

# Instale as dependências
npm install

# Execute em modo desenvolvimento
npm run dev
```

### 4. Visualizar logs
```bash
# Todos os logs
npm run logs

# Apenas serviços backend
npm run logs:services

# Apenas frontend
npm run logs:web
```

### 5. Parar os serviços
```bash
npm run stop

# Também remove volumes
npm run clean
```

## 🔌 API Endpoints

### User Service (:8081)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/users/register` | Registrar novo usuário |
| POST | `/api/users/login` | Login de usuário |
| GET | `/api/users/{id}` | Consultar **apenas o próprio perfil** (HTTP 403 para outro usuário) |
| GET | `/api/users/me` | Buscar usuário autenticado |

### Todo Service (:8082)
| Método | Endpoint | Descrição |
|--------|----------|-----------|
| GET | `/api/todos` | Listar todos do usuário |
| POST | `/api/todos` | Criar novo todo |
| GET | `/api/todos/{id}` | Buscar todo por ID |
| PUT | `/api/todos/{id}` | Atualizar todo |
| DELETE | `/api/todos/{id}` | Deletar todo |
| PATCH | `/api/todos/{id}/complete` | Marcar como completo |
| PATCH | `/api/todos/{id}/uncomplete` | Desmarcar como completo |

### API Gateway (:8080)
```
/api/users/**   → user-service
/api/todos/**   → todo-service
```

## 📝 Modelos de Dados

### User
```json
{
  "id": "uuid",
  "username": "string",
  "email": "string",
  "password": "string",
  "createdAt": "timestamp"
}
```

### Todo
```json
{
  "id": "uuid",
  "title": "string",
  "description": "string",
  "completed": "boolean",
  "userId": "uuid",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

## 🔐 Autenticação

- JWT (JSON Web Tokens)
- **Modo padrão:** JWT Bearer no cabeçalho `Authorization`, com expiração de 24 horas e persistência do frontend legado em `localStorage`.
- **Modo opcional de demonstração HTTPS:** login de sessão, refresh rotativo persistido por fingerprint SHA-256 no PostgreSQL, cookie `HttpOnly; Secure; SameSite=Strict`, validação de Origin/CSRF, access JWT em memória e coordenação de refresh/logout entre abas com Web Locks.
- O modo opcional é **desabilitado por padrão**; consulte [passo a passo e limites](docs/BROWSER_SESSION_LOCAL_DEMO.md). O User Service permite configurar o TTL com `JWT_ACCESS_EXPIRATION_MS` (24 horas por padrão). Não reduzir o prazo no cliente legado sem migração de sessão.
- Logout revoga refresh no servidor quando confirmado, mas não invalida imediatamente access JWTs emitidos; ver [decisões de segurança](docs/JWT_SECURITY.md).

## 🐳 Docker

### Build das imagens
```bash
# Eureka
docker build -f packages/eureka-server/Dockerfile packages/eureka-server

# Gateway
docker build -f packages/api-gateway/Dockerfile packages/api-gateway

# User Service
docker build -f packages/user-service/Dockerfile packages/user-service

# Todo Service
docker build -f packages/todo-service/Dockerfile packages/todo-service

# Web App
docker build -f packages/web-app/Dockerfile packages/web-app
```

## 📦 Monorepo Commands (Workspaces)

```bash
# Instalar todas as dependências
npm install

# Build de todos os workspaces
npm run build

# Executar comando em workspace específico
npm run dev --workspace=web-app
```

## 🧪 Qualidade, decisões e limites

A pipeline [Services CI](.github/workflows/ci.yml) executa verificações dos serviços Java e do frontend. Além dos testes unitários de autenticação e propriedade de tarefas, a pipeline [Docker Compose E2E](.github/workflows/e2e.yml) já testa o Gateway, PostgreSQL e serviços reais: cadastro, login, criação e edição de tarefas, isolamento entre usuários e exclusão. Ela também verifica traces exportados ao Zipkin.

O gateway utiliza rotas estáticas; o Eureka registra os serviços, mas não é usado como balanceador dinâmico pelas rotas atuais. O PostgreSQL é compartilhado entre serviços, e o Redis do Compose ainda não é usado como cache pela aplicação. Essas escolhas e os próximos passos estão explicados em [Arquitetura e decisões técnicas](docs/ARCHITECTURE.md).

## 🔎 Tracing distribuído (Docker)

O Gateway, User Service e Todo Service exportam spans OpenTelemetry para um Zipkin local, acessível em `http://localhost:9411` após iniciar o Compose. A pipeline E2E confirma o recebimento de spans dos três serviços. Isso comprova emissão de traces, mas não substitui testes de carga, falha ou observabilidade de produção. Consulte [guia de tracing e limitações](docs/TRACING.md).

## 📐 Documentação técnica

A implementação e os limites de segurança estão registrados nos documentos abaixo. Eles descrevem o comportamento existente, os testes que o sustentam e as decisões ainda pendentes:

- [Arquitetura e limitações](docs/ARCHITECTURE.md)
- [Contrato do banco e plano de migração segura](docs/DATABASE_SCHEMA_MIGRATION_PLAN.md)
- [Validação JWT e autorização](docs/JWT_SECURITY.md)
- [Modelo de sessão e material de refresh token](docs/REFRESH_TOKEN_DESIGN.md)
- [Persistência PostgreSQL, Flyway e concorrência](docs/REFRESH_TOKEN_PERSISTENCE.md)
- [ADR-0001: sessões web e acesso a perfis](docs/ADR-0001-web-session-and-profile-visibility.md)
- [ADR-0002: alternativas para revogação distribuída](docs/ADR-0002-distributed-token-revocation.md)
- [ADR-0003: cookie de refresh e proteção CSRF](docs/ADR-0003-refresh-cookie-transport-and-csrf.md)
- [Demonstração HTTPS de sessão web e testes com múltiplas abas](docs/BROWSER_SESSION_LOCAL_DEMO.md)

**Estado verificado:** o fluxo HTTP opcional de login/refresh/logout, a rotação atômica no PostgreSQL, os testes de replay e os cenários HTTPS com Chromium estão implementados. O modo padrão mantém JWT de 24 horas. **Ainda não estão resolvidas** a recuperação de resposta perdida após commit, a revogação imediata de access JWT no Todo Service, a migração de clientes legados e a validação para implantação real. O teste de duas abas não substitui testes de carga, falhas ou compatibilidade em outros navegadores.

## 📚 Documentação Adicional

- [Eureka Server](./packages/eureka-server/README.md)
- [API Gateway](./packages/api-gateway/README.md)
- [User Service](./packages/user-service/README.md)
- [Todo Service](./packages/todo-service/README.md)
- [Web App](./packages/web-app/README.md)

## 📄 Licença

MIT