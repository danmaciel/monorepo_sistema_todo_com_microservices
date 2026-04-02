# 🔍 Eureka Server

Servidor de Service Discovery usando Netflix Eureka.

## 📋 Descrição

O Eureka Server é responsável por:
- Registrar todos os microserviços da aplicação
- Permitir que serviços descubram outros serviços
- Balanceamento de carga do lado do cliente
- Health checks automáticos

## 🚀 Como Executar

### Local (sem Docker)
```bash
cd packages/eureka-server
mvn spring-boot:run
```

### Docker
```bash
docker build -f packages/eureka-server/Dockerfile -t todo-eureka packages/eureka-server
docker run -p 8761:8761 todo-eureka
```

## 🌐 Dashboard

Após iniciar, acesse o Dashboard do Eureka:
- **URL**: http://localhost:8761

## ⚙️ Configuração

### application.yml
```yaml
server:
  port: 8761

eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false
    fetch-registry: false
```

## 🔗 Endpoints dos Serviços Registrados

| Serviço | URL Esperada |
|---------|--------------|
| User Service | http://localhost:8081/actuator/health |
| Todo Service | http://localhost:8082/actuator/health |
| API Gateway | http://localhost:8080/actuator/health |
