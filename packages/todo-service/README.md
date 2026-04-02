# ✅ Todo Service

Microserviço de gerenciamento de tarefas (TODOs).

## 📋 Descrição

Responsável por:
- CRUD completo de tarefas
- Marcar tarefas como completas/incompletas
- Filtrar tarefas por status
- Estatísticas (total, ativas, completas)

## 📝 API Endpoints

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| POST | `/api/todos` | Criar nova tarefa |
| GET | `/api/todos` | Listar todas as tarefas |
| GET | `/api/todos?completed=true` | Listar tarefas completas |
| GET | `/api/todos?completed=false` | Listar tarefas ativas |
| GET | `/api/todos/{id}` | Buscar tarefa por ID |
| PUT | `/api/todos/{id}` | Atualizar tarefa |
| DELETE | `/api/todos/{id}` | Deletar tarefa |
| PATCH | `/api/todos/{id}/complete` | Marcar como completa |
| PATCH | `/api/todos/{id}/uncomplete` | Desmarcar como completa |

## 📦 Modelos de Dados

### CreateTodoRequest
```json
{
  "title": "string (obrigatório, 1-200 caracteres)",
  "description": "string (opcional, max 2000 caracteres)"
}
```

### UpdateTodoRequest
```json
{
  "title": "string (opcional)",
  "description": "string (opcional)",
  "completed": "boolean (opcional)"
}
```

### TodoResponse
```json
{
  "id": "uuid",
  "title": "string",
  "description": "string",
  "completed": "boolean",
  "userId": "uuid",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

### TodoListResponse
```json
{
  "todos": [TodoResponse],
  "total": "number",
  "active": "number",
  "completed": "number"
}
```

## 🔒 Segurança

- Todos os endpoints (exceto health) requerem autenticação JWT
- Usuários só podem ver/editar suas próprias tarefas
- Validação de dados com Bean Validation

## 🚀 Como Executar

### Local
```bash
cd packages/todo-service
mvn spring-boot:run
```

### Docker
```bash
docker build -f packages/todo-service/Dockerfile -t todo-todo-service packages/todo-service
docker run -p 8082:8082 todo-todo-service
```
