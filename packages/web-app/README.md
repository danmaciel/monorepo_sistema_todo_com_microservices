# 🌐 Web App - Frontend React

Interface React + Vite + TailwindCSS para gerenciamento de tarefas. Este projeto é uma demonstração técnica local, não uma implantação de produção.

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
| `VITE_API_URL` | `http://localhost:8080` | URL base da API no modo legado |
| `VITE_SESSION_AUTH` | `false` | Habilita somente em execução HTTPS opt-in a autenticação de sessão com cookie de refresh e CSRF |

## 📱 Funcionalidades

### Autenticação
- Registro de novos usuários
- Login com username/senha
- Modo padrão: access JWT Bearer persistido em `localStorage` (limitação de segurança documentada)
- Modo HTTPS opt-in: JWT em memória, refresh em cookie HttpOnly/Secure/SameSite, CSRF e coordenação Web Locks entre abas. Consulte [guia de execução](../../docs/BROWSER_SESSION_LOCAL_DEMO.md)
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