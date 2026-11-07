# ADR-0001 — Sessões web e visibilidade de perfis

**Status:** Parcialmente aceito — acesso a perfis restrito ao próprio usuário; estratégia de armazenamento e revogação de tokens continua proposta.

## Contexto

O frontend React obtém um token Bearer nos endpoints de cadastro/login e atualmente o persiste no armazenamento local do navegador através do Zustand `persist` (`auth-storage`). Esse armazenamento é acessível por JavaScript executado na mesma origem; portanto, se houver uma vulnerabilidade XSS, o token poderá ser extraído. Isso **não comprova** que exista XSS nesta aplicação.

O endpoint `GET /api/users/{id}` anteriormente permitia consultar o perfil e o e-mail de qualquer usuário autenticado. A política adotada para este gerenciador pessoal de tarefas é **autorização por proprietário**: cada usuário só pode consultar o próprio perfil.

## Melhoria já adotada (PR associado)

Sem alterar o armazenamento, o frontend passa a verificar a claim `exp` ao restaurar uma sessão, navegar e enviar requisições. Tokens expirados, malformados ou sem expiração válida deixam de ser usados no cliente. Há testes automatizados de fronteira de expiração e entradas inválidas.

**Essa verificação é apenas de experiência de usuário**: ela não valida a assinatura, nem substitui a verificação de JWT nos serviços Java. O navegador continua exposto ao risco inerente de armazenar credenciais acessíveis ao JavaScript.

## Alternativas para sessão (a decidir)

| Alternativa | Benefícios | Riscos / trade-offs |
| --- | --- | --- |
| `localStorage` (atual) | Preserva login entre reinícios do navegador; integração simples com Bearer | Token pode ser lido por scripts da origem; logout/revogação exige estratégia adicional |
| `sessionStorage` | Limita persistência à sessão da aba; implementação relativamente simples | Continua acessível via JavaScript e não elimina risco de XSS |
| Apenas memória | Evita armazenamento durável do bearer token | Recarregar a página encerra sessão sem renovação; XSS ainda pode usar a sessão ativa |
| Cookie `HttpOnly; Secure; SameSite` | JavaScript não consegue ler o cookie diretamente | Exige mudanças no servidor/gateway, revisão de CSRF, CORS, renovação e logout |

**Não foi aprovada nenhuma migração de armazenamento nesta etapa.** Uma implementação futura deve explicitar tempo de vida, refresh/revogação, rotação, HTTPS e estratégia de CSRF quando aplicável.

## Política de perfis (aceita)

- `GET /api/users/me`: retorna o usuário autenticado.
- `GET /api/users/{id}`: retorna os dados **somente quando** o ID solicitado corresponde ao usuário autenticado; caso contrário, responde HTTP 403, antes da consulta ao banco.
- `GET /api/users/exists/{id}`: consulta de existência restrita ao **próprio identificador autenticado**; outro identificador recebe HTTP 403, antes da consulta ao banco.

**Justificativa:** o domínio de tarefas pessoais não exige visualização pública de e-mail nem compartilhamento de perfis. A restrição evita revelar dados pessoais em consultas cruzadas. O endpoint `/me` continua como caminho preferencial para o perfil autenticado.

**Compatibilidade:** a restrição de `/users/exists/{id}` muda o comportamento para consumidores que consultavam IDs de terceiros. O frontend deste repositório não utiliza esse endpoint; qualquer integração externa deve ser verificada antes de adotar a nova política. O fluxo de sessão web (incluindo refresh e revogação) segue pendente.

## Limitações conhecidas

- A expiração pode ocorrer enquanto a página está aberta; o próximo render/requisição reavalia o token, mas não existe um relógio em tempo real na interface.
- O cliente não consegue revogar um JWT já emitido. A validade é imposta pelo servidor; revogação antecipada exige estratégia própria.
- O User Service agora recusa autenticação por JWT de contas desativadas ou excluídas, consultando o repositório a cada requisição autenticada. **O Todo Service ainda valida apenas a assinatura/expiração, sem consultar o estado da conta**, de forma que desativar uma conta não revoga imediatamente seu token naquele serviço.
- Não registrar valores de token, senha ou credenciais em logs, traces ou relatórios de erro.

## Critérios antes de marcar a decisão como aceita

1. ~~Confirmar a regra de visibilidade de perfis~~ — definido acesso ao próprio perfil; validado por testes de controller e E2E.
2. Selecionar a estratégia de sessão web, incluindo refresh e logout.
3. ~~Testar autorização de perfis com dois usuários no E2E~~ — perfil próprio HTTP 200, perfil de outro usuário HTTP 403 e acesso anônimo negado.
4. Validar expiração e revogação: o E2E exercita JWT assinado já expirado nos dois microsserviços, e o filtro do User Service testa contas desativadas/excluídas. A revogação imediata no Todo Service permanece pendente.
5. Atualizar este ADR e registrar consequências da decisão.

## Endurecimento incremental — tokens expirados e usuários desativados

O User Service verifica `enabled` antes de autenticar um bearer token associado à conta. Testes unitários do filtro validam quatro situações: usuário ativo, desativado, excluído e JWT inválido. O teste E2E produz um JWT **corretamente assinado, porém expirado**, com o segredo efêmero gerado no CI, e verifica sua rejeição tanto pelo User Service quanto pelo Todo Service, sem publicar valores de token.

**Não existe ainda revogação distribuída:** logout no browser apenas descarta o token local e o Todo Service segue validando tokens auto-contidos até expirarem. Uma solução de revogação precisa definir requisitos de disponibilidade, latência, cache, invalidação, identidade emissora/audiência e a dependência síncrona ou assíncrona entre serviços. Não introduzir chamadas entre serviços apenas para marcar esse requisito como concluído.


## Preparação do Gateway antes de cookies (sem habilitação de refresh HTTP)

A configuração do Spring Cloud Gateway foi atualizada para o prefixo suportado `spring.cloud.gateway.server.webflux`, inclusive rotas, CORS e filtros, e o acesso do endpoint Actuator `gateway` foi definido como `read-only` (sem ampliar sua exposição HTTP atual). O E2E verifica preflights para duas origens locais permitidas e uma origem desconhecida.

**O Gateway permanece com `allowCredentials: false`.** Isso é intencional: nenhum fluxo HTTP de refresh baseado em cookie foi habilitado. O User Service tem configuração de CORS própria com credenciais permitidas, mas ela não deve ser interpretada como habilitação de refresh via Gateway. Antes de usar cookies, é preciso alinhar políticas entre Gateway e User Service, definir autenticação por cookie, `SameSite`, domínio/caminho, `Secure`, verificação de `Origin`, CSRF e testes em navegador.

Não transformar `allowCredentials` em `true` isoladamente: CORS não substitui autenticação nem controle CSRF e não oferece proteção para clientes fora do navegador.
