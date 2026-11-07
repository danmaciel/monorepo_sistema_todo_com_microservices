# ADR-0003 — Transporte de refresh session por cookie e defesa CSRF

**Status: proposta; nenhuma autenticação por cookie está ativa.**

## Contexto verificável

O User Service emite JWT Bearer de 24 horas para login/cadastro. O frontend ainda armazena o access JWT em localStorage. PostgreSQL possui emissão, rotação e revogação internas de refresh sessions, **não conectadas ao login HTTP**. O Gateway e o User Service estão com CORS `allowCredentials: false`. A configuração de Spring Security no User Service ainda desabilita CSRF globalmente, pois não usa cookies como credencial de sessão.

O objetivo futuro é usar access token de vida curta em memória no frontend e refresh token opaco em cookie HttpOnly. Essa migração não será ativada por uma mudança isolada de CORS ou por adicionar um cookie ao login.

## Decisão proposta sobre o cookie

A classe **não conectada a controllers** `RefreshCookiePolicy` serializa um cookie candidato:

| Propriedade | Valor candidato | Motivo |
| --- | --- | --- |
| Nome | `__Secure-todo-refresh` | Prefixo requer atributo Secure |
| `HttpOnly` | true | JavaScript não deve ler o refresh token |
| `Secure` | true | Envio apenas via HTTPS em navegadores conformes |
| `SameSite` | Strict | Restringir envio em contexto cross-site; exige validar topologia do frontend |
| `Path` | `/api/users` | Compartilhado entre rotas candidatas de refresh e logout no Gateway |
| `Domain` | omitido | Cookie host-only |
| `Max-Age` | no máximo o tempo restante da sessão absoluta no PostgreSQL | Não manter cookie utilizável após a expiração no banco |

A remoção usa o mesmo nome, caminho, atributos e `Max-Age=0`. A classe não registra o token, não define cookie nas respostas e não é chamada por nenhuma operação de autenticação atual. Valores e topologia devem ser revistos antes de produção.

### Requisitos de transporte

1. Usar **HTTPS ponta a ponta na fronteira pública**, terminando TLS em um proxy confiável. Não enfraquecer `Secure` para viabilizar testes em HTTP local; testes reais de navegador precisam de configuração HTTPS apropriada.
2. O frontend em `localhost:5173` e Gateway em `localhost:8080` têm origens distintas (porta), ainda que possam ser same-site. Requisições com cookie exigem `credentials: include`/equivalente no cliente e configuração CORS restrita, com origem explícita e `allowCredentials: true` **somente no rollout que implementar CSRF**.
3. Atributos de cookie são emitidos pelo User Service, mas vistos pelo navegador em **`/api/users/...` na origem pública do Gateway**. O caminho deve ser escolhido na perspectiva do navegador, não da rota interna `/users/...` após `StripPrefix`.
4. Não expor o refresh token em JSON, cabeçalho Authorization, URL, logs, traces ou armazenamento acessível a scripts. Access JWT continua sendo Bearer na API de tarefas.

## Contrato HTTP candidato (não implementado)

| Rota pública | Método | Entrada | Saída esperada |
| --- | --- | --- | --- |
| `/api/users/login` | POST | username e password | Access JWT + usuário; no rollout final emite cookie HttpOnly |
| `/api/users/refresh` | POST | cookie refresh + proteção CSRF | Novo access JWT e cookie rotacionado; erro não informa se a sessão existe |
| `/api/users/logout` | POST | cookie refresh + proteção CSRF | Resposta sem corpo e cookie expirado; revoga família conhecida |
| `/api/users/me` | GET | Bearer JWT | Perfil do próprio usuário |

O cadastro exige uma decisão explícita: também criar refresh session após registro ou solicitar login. O contrato atual não muda nesta etapa.

## Proteção CSRF exigida antes de ativar cookies

**SameSite e CORS, isoladamente, não constituem uma estratégia completa contra CSRF.**

O fluxo de refresh/logout deve exigir:
- Conferência fail-closed do cabeçalho `Origin` contra lista exata de origens da aplicação (esquema, host e porta). Não refletir qualquer `Origin` nem confiar cegamente em `X-Forwarded-*` recebido de clientes externos.
- Uma proteção CSRF explícita associada à sessão/cliente, com entrega e validação definidas em contrato, mais testes de ausência, valor inválido e replay conforme o mecanismo escolhido. Antes de implementar, definir onde o token anti-CSRF pode ser obtido sem tornar o refresh token legível ao JavaScript.
- Endpoints `POST` sem mutações por `GET`; `Content-Type` suportado e política de `Sec-Fetch-Site` como defesa adicional, não substituta da verificação principal.
- Preflight CORS e testes pelo Gateway com origens permitidas, maliciosas, ausentes, `null` e requisições sem cabeçalho CSRF.

**Não** mudar `http.csrf(AbstractHttpConfigurer::disable)` ou `allowCredentials` parcialmente: a habilitação do fluxo exige implementação e testes coordenados no User Service, Gateway e navegador.

## Concorrência e recuperação

O banco já serializa rotação e revogação dentro da mesma família; ainda há risco de perda de resposta após COMMIT e tentativas simultâneas em várias abas que levam a revogação fail-closed. O frontend deverá coordenar renovações e tratar indisponibilidade/401 sem loop. Não prometer retry idempotente sem definir o risco de replay de token roubado.

Mesmo após logout HTTP, access JWTs já emitidos continuarão válidos até a expiração enquanto Todo Service não consultar estado de sessão/revogação. Encurtar access JWT para minutos deve ser uma etapa posterior, sincronizada com o cliente.

## Critérios antes de mudar o status para aceito

- Testes do contrato no User Service e E2E através do Gateway em HTTPS.
- Cookie seguro na resposta real de login e refresh, sem segredos em logs.
- Origem/CSRF e credenciais CORS validados juntos em ambos os caminhos.
- Rotação, replay, logout, token expirado, conta desativada, concorrência entre abas e falha após COMMIT com comportamento documentado.
- Migração/rollback do frontend e expiração dos access JWTs compatíveis.
- Revisão de topologia HTTPS, proxy, SameSite e domínio na implantação alvo.

**Escopo desta PR:** documentação de contrato + classe de serialização isolada e testes unitários. Não existe endpoint HTTP de refresh/logout nem cookie emitido na execução atual.

## Etapa isolada: política de Origin para endpoints futuros

A classe `RefreshRequestOriginPolicy` implementa a verificação exata e *fail-closed* do cabeçalho `Origin`: exige exatamente um valor, igual à origem explicitamente configurada com esquema, host e porta. Não aceita ausência de cabeçalho, valor literal `null`, origens similares, valores múltiplos ou fallback por `Referer`/`X-Forwarded-Host`. A lista de origens permitidas rejeita URLs com caminhos, query, fragmento, credenciais ou entradas duplicadas.

**A classe ainda não está ligada ao Spring Security nem a controllers**: é uma política testada isoladamente, não uma defesa ativa de um endpoint existente. A lista de origens em produção deverá vir de configuração explícita, compatível com a topologia HTTPS aprovada, e a instalação da política deverá ocorrer junto do endpoint e dos testes E2E.

**Origin não é autenticação e não substitui token CSRF.** Clientes HTTP não-browser podem falsificar esse cabeçalho; por isso o fluxo futuro deve combinar cookie/refresh token, verificação de Origin e o mecanismo CSRF definido para a sessão. A configuração CORS continua sem credenciais.
