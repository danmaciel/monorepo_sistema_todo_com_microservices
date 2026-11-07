# Demonstração local — sessão web com refresh rotativo

**Situação:** modo opt-in para desenvolvimento, não habilitado no frontend Docker padrão. O login legado e o frontend convencional continuam disponíveis sem configuração adicional.

## O que funciona neste modo

- Login em `POST /api/users/session/login` autentica por senha e entrega o access JWT no corpo, cookie de refresh `HttpOnly; Secure; SameSite=Strict` e `X-CSRF-Token` na resposta.
- O React mantém o access JWT e o CSRF **somente em memória**. Não grava tokens no `localStorage`.
- No carregamento/recarregamento da página, o React chama `POST /api/users/session/csrf`. O User Service só retorna o CSRF se houver cookie válido, não consumido, não revogado e pertencente a conta habilitada. Depois o React renova o access JWT.
- Requisições do React usam o proxy **mesma origem** `/api` para o Gateway, evitando habilitar CORS credenciado. Ao receber 401 de uma chamada de API, o frontend faz uma tentativa de refresh e repete a chamada uma única vez. Requisições concorrentes da mesma aba compartilham a renovação.
- O botão de sair envia `POST /api/users/logout` e limpa a sessão local. Uma falha de rede pode impedir a revogação no servidor, mesmo que o frontend limpe seus dados.

## Execução: backend Docker + frontend Vite HTTPS

Pré-requisitos: Docker Compose, Node e npm compatíveis com o projeto, `mkcert` (com CA local instalada no navegador). Não gere nem versione certificados reais dentro do repositório. **Nunca adicione chaves privadas, JWT_SECRET ou CSRF key ao Git.**

1. Gere credenciais descartáveis em seu shell e configure o ambiente do backend, **sem colocar os valores no histórico do Git**:

   ```bash
   export POSTGRES_PASSWORD="$(openssl rand -hex 24)"
   export JWT_SECRET="$(openssl rand -hex 48)"
   export AUTH_REFRESH_HTTP_ENABLED=true
   export AUTH_REFRESH_ALLOWED_ORIGINS=https://localhost:3000
   export AUTH_REFRESH_CSRF_KEY_BASE64="$(openssl rand -base64 48)"
   docker compose up --build -d postgres zipkin eureka user-service todo-service gateway
   ```

2. Crie um diretório temporário **fora do repositório** para os certificados de desenvolvimento:

   ```bash
   mkdir -p "$HOME/.config/todo-local-tls"
   mkcert -install
   mkcert -cert-file "$HOME/.config/todo-local-tls/localhost.pem" \
          -key-file "$HOME/.config/todo-local-tls/localhost-key.pem" localhost 127.0.0.1
   ```

3. Em outro terminal:

   ```bash
   cd packages/web-app
   npm ci
   export VITE_SESSION_AUTH=true
   export VITE_API_URL=http://localhost:8080
   export DEV_HTTPS_CERT="$HOME/.config/todo-local-tls/localhost.pem"
   export DEV_HTTPS_KEY="$HOME/.config/todo-local-tls/localhost-key.pem"
   npm run dev
   ```

4. Abra **`https://localhost:3000`**, não a versão HTTP. Cadastre uma conta, faça login, crie uma tarefa, recarregue a página e verifique que a sessão volta. Em DevTools, confira que o refresh cookie é `HttpOnly` e que o armazenamento local **não contém o access JWT da sessão nova**. Faça logout e tente renovar a sessão novamente.

Ao mudar entre os modos, limpe no navegador os dados antigos do aplicativo (`auth-storage` legado), especialmente se existirem JWTs de experimentos anteriores.

**Observação de topologia:** o frontend Vite HTTPS encaminha `/api` para o Gateway HTTP local via proxy do processo de desenvolvimento. O cookie Secure é criado para a origem HTTPS vista pelo navegador. **Não exponha o Gateway HTTP nem use este proxy como configuração de produção.**

## Limitações conhecidas

- **Não temos ainda um teste de navegador automatizado.** O CI executa testes unitários, build do React e testes HTTP reais via Gateway usando Python; não valida o navegador com certificados, regras de cookie ou múltiplas abas.
- A coordenação de refresh é apenas **por aba**. Duas abas ou uma resposta perdida após COMMIT ainda podem provocar replay e revogação da família; será necessário definir a política de recuperação.
- Access JWT continua com **24 horas de validade** e não é imediatamente revogado por logout. Todo Service continua validando JWT até expiração. Para uma sessão web, recomenda-se reduzir o TTL após a migração do cliente.
- A funcionalidade opt-in está separada do frontend Docker padrão, que permanece no modo antigo. Para mostrar o novo fluxo use a configuração Vite acima.
- Se o proxy HTTPS, o certificado local confiável ou as configurações explícitas de origem não estiverem corretos, o fluxo deve falhar de forma visível, **sem desabilitar Secure ou as verificações CSRF/Origin**.
- O cadastro continua usando a API legada para criar a conta e, no novo modo, realiza login de sessão em seguida. A resposta JWT legada do cadastro é descartada pelo frontend.
