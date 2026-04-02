# 🌐 Web App - Frontend React

Aplicação frontend do sistema de Todos usando React + Vite + TailwindCSS.

## 📋 Descrição

Interface de usuário para gerenciamento de tarefas com:
- Autenticação (Login/Registro)
- CRUD completo de tarefas
- Filtros (Todas/Pendentes/Concluídas)
- Design responsivo com TailwindCSS
- Gerenciamento de estado com Zustand

## 🛠️ Stack

- **React 18** - Biblioteca de UI
- **Vite** - Bundler e dev server
- **TailwindCSS** - Framework de estilos
- **Zustand** - Gerenciamento de estado
- **React Router 6** - Roteamento
- **Axios** - Cliente HTTP
- **Lucide React** - Ícones

## 📁 Estrutura

```
src/
├── components/
│   ├── Layout.jsx
│   ├── TodoItem.jsx
│   ├── TodoForm.jsx
│   └── FilterTabs.jsx
├── pages/
│   ├── Login.jsx
│   ├── Register.jsx
│   └── TodoList.jsx
├── services/
│   └── api.js
├── store/
│   ├── authStore.js
│   └── todoStore.js
├── App.jsx
├── main.jsx
└── index.css
```

## 🚀 Como Executar

### Desenvolvimento
```bash
cd packages/web-app
npm install
npm run dev
```

### Build
```bash
npm run build
```

### Docker
```bash
docker build -f packages/web-app/Dockerfile -t todo-web packages/web-app
docker run -p 3000:3000 todo-web
```

## 🔧 Variáveis de Ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `VITE_API_URL` | `http://localhost:8080` | URL base da API |

## 📱 Funcionalidades

### Autenticação
- Registro de novos usuários
- Login com username/senha
- JWT token storage (localStorage)
- Logout automático em caso de token expirado

### Gerenciamento de Tarefas
- Criar nova tarefa (título + descrição)
- Editar tarefa existente
- Marcar como completa/incompleta
- Deletar tarefa
- Filtrar por status
- Estatísticas em tempo real

### Interface
- Design moderno e responsivo
- Animações suaves
- Feedback visual para ações
- Tratamento de erros