# 🚪 API Gateway

Gateway da API usando Spring Cloud Gateway.

## 📋 Descrição

O API Gateway é responsável por:
- Roteamento de requisições para os microserviços corretos
- CORS (Cross-Origin Resource Sharing)
- Rate limiting
- Autenticação centralizada (JWT)
- Logs de requisições

## 🔀 Rotas Configuradas

| Path | Serviço | Descrição |
|------|---------|-----------|
| `/api/users/**` | user-service (:8081) | Rotas de usuário |
| `/api/todos/**` | todo-service (:8082) | Rotas de tarefas |

## 🚀 Como Executar

### Local (sem Docker)
```bash
cd packages/api-gateway
mvn spring-boot:run
```

### Docker
```bash
docker build -f packages/api-gateway/Dockerfile -t todo-gateway packages/api-gateway
docker run -p 8080:8080 todo-gateway
```

## 🌐 Endpoints

### Health Check
```bash
GET http://localhost:8080/actuator/health
```

### Gateway Routes
```bash
GET http://localhost:8080/actuator/gateway/routes
```

## 🔒 CORS

CORS está configurado para permitir todas as origens em desenvolvimento.
Para produção, configure as origens específicas no `application.yml`.