# ADR-0001 — Sessões web e visibilidade de perfis

**Status:** Proposto — decisão sobre armazenamento do token e visibilidade de perfis pendente de definição.

## Contexto

O frontend React obtém um token Bearer nos endpoints de cadastro/login e atualmente o persiste no armazenamento local do navegador através do Zustand `persist` (`auth-storage`). Esse armazenamento é acessível por JavaScript executado na mesma origem; portanto, se houver uma vulnerabilidade XSS, o token poderá ser extraído. Isso **não comprova** que exista XSS nesta aplicação.

O endpoint `GET /api/users/{id}` exige autenticação, mas permite consultar dados de outro usuário, incluindo e-mail. Falta uma decisão explícita sobre quais campos de um perfil deveriam ser visíveis a terceiros.

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

## Política de perfis (a decidir)

- `GET /api/users/me`: retorna o usuário autenticado.
- `GET /api/users/{id}`: atualmente retorna ID, nome de usuário, e-mail e criação para qualquer cliente autenticado com um UUID existente.
- `GET /api/users/exists/{id}`: atualmente permite consultar existência de conta para clientes autenticados.

Alternativas: perfil público mínimo (sem e-mail), acesso exclusivamente ao próprio perfil ou permissões administrativas explícitas. **Até que a política seja definida, não ampliar o uso desses endpoints em interfaces públicas.**

## Limitações conhecidas

- A expiração pode ocorrer enquanto a página está aberta; o próximo render/requisição reavalia o token, mas não existe um relógio em tempo real na interface.
- O cliente não consegue revogar um JWT já emitido. A validade é imposta pelo servidor; revogação antecipada exige estratégia própria.
- Mudanças de `enabled` no User Service não invalidam automaticamente um JWT no Todo Service, que verifica assinatura e expiração sem consultar estado da conta.
- Não registrar valores de token, senha ou credenciais em logs, traces ou relatórios de erro.

## Critérios antes de marcar a decisão como aceita

1. Confirmar a regra de visibilidade de perfis e os dados que podem ser retornados.
2. Selecionar a estratégia de sessão web, incluindo refresh e logout.
3. Testar autenticação e autorização com dois usuários no E2E.
4. Validar comportamento após expiração/revogação nos dois microsserviços.
5. Atualizar este ADR e registrar consequências da decisão.
