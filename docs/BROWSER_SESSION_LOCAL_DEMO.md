# Demonstração local — sessão web com refresh rotativo

**Situação:** modo opt-in para desenvolvimento, não habilitado no frontend Docker padrão. O login legado e o frontend convencional continuam disponíveis sem configuração adicional.

## O que funciona neste modo

- Login em `POST /api/users/session/login` autentica por senha e entrega o access JWT no corpo, cookie de refresh `HttpOnly; Secure; SameSite=Strict` e `X-CSRF-Token` na resposta.
- O React mantém o access JWT e o CSRF **somente em memória**. Não grava tokens no `localStorage`.
- No carregamento/recarregamento da página, o React adquire um **Web Lock exclusivo entre abas da mesma origem**, chama `POST /api/users/session/csrf` e renova o access JWT ainda sob esse lock. O User Service só retorna o CSRF se houver cookie válido, não consumido, não revogado e pertencente a conta habilitada. O fluxo falha de forma restritiva quando Web Locks não está disponível.
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

- O CI também executa um teste automatizado com **Chromium e Playwright em HTTPS local**, usando certificado temporário e contexto de navegador isolado. Esse teste cobre login, cookie HttpOnly/Secure/SameSite, criação e recuperação de tarefa, renovação após recarregar, ausência de access JWT/CSRF no armazenamento do navegador e logout. **Não substitui validação em outros navegadores ou testes de múltiplas abas**.
- O refresh no modo opt-in usa **Web Locks** para serializar o par bootstrap-CSRF + rotação entre abas **na mesma origem e no mesmo perfil de navegador**. Requisições concorrentes dentro da mesma aba também compartilham uma Promise. Sem Web Locks, o refresh não é tentado. Isso não abrange múltiplos dispositivos/perfis, logout concorrente, travamentos e respostas perdidas após COMMIT; esses casos ainda exigem tratamento e testes.
- Access JWT continua com **24 horas de validade** e não é imediatamente revogado por logout. Todo Service continua validando JWT até expiração. Para uma sessão web, recomenda-se reduzir o TTL após a migração do cliente.
- A funcionalidade opt-in está separada do frontend Docker padrão, que permanece no modo antigo. Para mostrar o novo fluxo use a configuração Vite acima.
- Se o proxy HTTPS, o certificado local confiável ou as configurações explícitas de origem não estiverem corretos, o fluxo deve falhar de forma visível, **sem desabilitar Secure ou as verificações CSRF/Origin**.
- O cadastro continua usando a API legada para criar a conta e, no novo modo, realiza login de sessão em seguida. A resposta JWT legada do cadastro é descartada pelo frontend.

## Comportamento entre duas abas — baseline de regressão

O teste `scripts/e2e_two_tabs.py` cria duas páginas no **mesmo contexto do Chromium**, compartilhando o cookie HttpOnly mas não o estado JavaScript. A primeira aba faz login; a segunda carrega a aplicação, obtém seu CSRF e rotaciona a sessão. Quando a primeira tenta sair usando seu **CSRF antigo**, o servidor deve rejeitar a operação com **403** antes de revogar a família. A segunda aba deve conseguir renovar a sessão normalmente.

Esse teste demonstra que a validação CSRF falha de forma restritiva e que o logout da aba com estado antigo não encerra a sessão da outra aba. **Não demonstra coordenação de refresh simultâneo**, tolerância a resposta perdida, suporte a vários navegadores nem solução para logout global. Em particular, a aba antiga apresenta logout local mesmo quando o servidor recusa a revogação. Essas limitações continuam abertas na [Issue #40](https://github.com/danmaciel/monorepo_sistema_todo_com_microservices/issues/40).

O E2E roda apenas com credenciais temporárias, certificado HTTPS descartável e perfil isolado; nunca imprime valores de cookie, JWT ou CSRF. Não execute este cenário contra contas reais.

## Web Locks — incremento de coordenação do refresh

O módulo `packages/web-app/src/utils/refreshLock.js` usa `navigator.locks.request` com lock exclusivo `todo-browser-refresh`. O cliente só executa a leitura atual do CSRF e o POST de rotação **enquanto detém o lock**, evitando que uma aba use um CSRF lido antes da rotação por outra. Nenhum access JWT, CSRF ou cookie é trocado por BroadcastChannel, localStorage ou sessionStorage.

O teste unitário `tests/refreshLock.test.js` verifica exclusividade assíncrona e ausência de fallback inseguro. O cenário `scripts/e2e_web_locks.py` verifica que duas abas conseguem recuperar sessões no Chromium usando o mesmo perfil/cookie e que o reload posterior também renova. A sequência de navegações permite sobreposição, mas **não garante** duas requisições simultâneas em todas as execuções de CI. Isso não é prova de correção para todas as corridas, nem substitui testes de falha de rede, recuperação pós-COMMIT ou compatibilidade entre navegadores.

O teste legado `scripts/e2e_two_tabs.py` permanece válido: o logout da aba com CSRF antigo ainda pode receber 403 e limpar apenas o estado local. Não confundir coordenação de **refresh** com coordenação de **logout**. O fluxo de sessão continua opt-in; o frontend Docker padrão mantém o comportamento legado.

## Evidência automatizada de navegador

O workflow `.github/workflows/e2e.yml` instala Playwright Python e Chromium, inicia o frontend Vite com `VITE_SESSION_AUTH=true` e um certificado HTTPS descartável, e executa `scripts/e2e_browser.py`. O teste utiliza um perfil de navegador vazio e uma conta temporária. Ignora a confiança no certificado **somente no contexto de teste**, mantendo HTTPS e o atributo `Secure`; essa configuração não deve ser usada em navegação real.

O teste de API existente continua verificando rotação, replay e restrições de Origin/CSRF pelo Gateway. A verificação do navegador complementa, mas não substitui, esses cenários. O script não imprime credenciais, JWT, CSRF ou cookies.
