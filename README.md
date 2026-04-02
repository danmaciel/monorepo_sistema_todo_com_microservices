# 📝 Todo Monorepo - Sistema de Todos com Microservices

Sistema de gerenciamento de tarefas (TODO) utilizando arquitetura de microserviços.

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
| **Cache** | Redis 7 | 6379 |

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

### 2. Subir todos os serviços (Backend)
```bash
# Development com Docker
npm run dev

# Em background
npm run dev:detached

# Apenas os serviços (sem frontend)
npm run dev:services
```

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
| GET | `/api/users/{id}` | Buscar usuário por ID |
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
- Token é enviado no header: `Authorization: Bearer <token>`

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

## 📚 Documentação Adicional

- [Eureka Server](./packages/eureka-server/README.md)
- [API Gateway](./packages/api-gateway/README.md)
- [User Service](./packages/user-service/README.md)
- [Todo Service](./packages/todo-service/README.md)
- [Web App](./packages/web-app/README.md)

## 📄 Licença

MIT