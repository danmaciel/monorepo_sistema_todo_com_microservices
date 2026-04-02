# 👤 User Service

Microserviço de gerenciamento de usuários.

## 📋 Descrição

Responsável por:
- Cadastro de novos usuários
- Autenticação (login/logout)
- Gerenciamento de perfil
- Geração de tokens JWT

## 🔐 Segurança

- Autenticação via JWT (JSON Web Tokens)
- Senhas criptografadas com BCrypt
- Validação de dados com Bean Validation

## 📝 API Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/users/register` | Registrar novo usuário |
| POST | `/api/users/login` | Login de usuário |
| GET | `/api/users/me` | Buscar usuário logado |
| GET | `/api/users/{id}` | Buscar usuário por ID |
| GET | `/api/users/exists/{id}` | Verificar se usuário existe |

## 📦 Modelos de Dados

### RegisterRequest
```json
{
  "username": "string (3-50 caracteres)",
  "email": "string (email válido)",
  "password": "string (6-100 caracteres)"
}
```

### LoginRequest
```json
{
  "username": "string",
  "password": "string"
}
```

### AuthResponse
```json
{
  "token": "string",
  "type": "Bearer",
  "user": {
    "id": "uuid",
    "username": "string",
    "email": "string",
    "createdAt": "datetime"
  }
}
```

## 🚀 Como Executar

### Local
```bash
cd packages/user-service
mvn spring-boot:run
```

### Docker
```bash
docker build -f packages/user-service/Dockerfile -t todo-user-service packages/user-service
docker run -p 8081:8081 todo-user-service
```
