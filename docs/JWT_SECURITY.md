# Validação de JWT e limites de autorização

## Escopo implementado

O User Service autentica credenciais e emite JWTs assinados com HS256. Os novos tokens contêm o UUID do usuário (`sub`), o nome de usuário, a expiração (atualmente 24 horas), o emissor `iss=todo-user-service` e a audiência `aud=todo-api`. User Service e Todo Service validam a assinatura, rejeitam tokens expirados e exigem as claims `exp` e `sub` (UUID válido). O Todo Service usa esse `sub` para aplicar as regras de propriedade das tarefas. **Os dois validadores agora rejeitam `iss` ou `aud` incompatíveis** com o contrato `todo-user-service` / `todo-api`. Durante a transição, tokens legados que não possuem *nenhuma das duas claims* continuam aceitos se estiverem corretamente assinados e não expirados. Tokens que apresentam somente uma das claims são rejeitados. Essa compatibilidade é controlada por `JWT_ACCEPT_LEGACY_WITHOUT_CLAIMS` (padrão `true`) em **ambos** os serviços; com `false`, `iss` e `aud` passam a ser obrigatórios. Isso não implementa revogação ou gestão de sessões.

O User Service consulta o cadastro em requisições autenticadas e recusa contas desativadas ou excluídas. O Todo Service **não** consulta o estado cadastral a cada requisição: um JWT ainda válido pode continuar autorizando tarefas após a desativação da conta.

A validação de assinatura e expiração **não equivale** a revogação. `iss` (emissor) e `aud` (destinatário) são emitidos e conferidos quando presentes. Exigi-los para todos os tokens depende de desativar o modo legado em ambos os serviços após expirar o último JWT antigo. Revogação distribuída e refresh tokens ativos ainda não fazem parte do contrato HTTP de autenticação. O JWT é assinado, **não criptografado**: dados sensíveis não devem ser incluídos em suas claims.

## Fluxo atual

```mermaid
sequenceDiagram
    participant C as Cliente
    participant U as User Service
    participant T as Todo Service
    C->>U: POST /users/login
    U-->>C: JWT HS256 (sub, username, exp, iss, aud)
    C->>T: Bearer JWT /todos
    T->>T: Validar assinatura e expiração
    T->>T: Autorizar acesso às tarefas do sub
    T-->>C: Resultado
```

O segredo simétrico compartilhado simplifica o laboratório, mas qualquer serviço que possua esse segredo também tem capacidade de emitir assinaturas. A adoção futura de chave assimétrica, rotação e validação explícita de emissor e destinatário exige migração compatível e testes em ambos os serviços.

## Evidências automatizadas

| Comportamento | Onde verificar |
| --- | --- |
| Emissão de JWT e extração da identidade | `user-service/.../JwtTokenProviderContractTest.java` |
| Rejeição de assinatura incorreta, token expirado e emissor/audiência incorretos | `JwtTokenProviderContractTest` nos dois serviços |
| Rejeição de conta desativada/excluída no User Service | `JwtAuthenticationFilterTest` |
| Expiração de JWT assinado verificada via Gateway | `scripts/e2e_gateway.py` |
| Autorização de tarefas por proprietário | `todo-service/.../TodoServiceTest.java` |

Execute na raiz do monorepo:

```bash
mvn -f packages/user-service/pom.xml -Dtest=JwtTokenProviderContractTest test
mvn -f packages/todo-service/pom.xml -Dtest=JwtTokenProviderContractTest test
```

Os testes utilizam credenciais fictícias. A aprovação do CI demonstra os cenários cobertos, não certifica segurança absoluta ou validação em produção.

O workflow [Docker Compose E2E](../.github/workflows/e2e.yml) executa os dois serviços em **modo estrito apenas no ambiente descartável de CI**, usando `JWT_ACCEPT_LEGACY_WITHOUT_CLAIMS=false`, e verifica a rejeição de JWTs corretamente assinados sem `iss`/`aud`. O Docker Compose local preserva o padrão `true` para compatibilidade. Essa verificação não significa que o modo estrito tenha sido ativado em instalações existentes.

## Limitações e decisões em aberto

- O frontend mantém o bearer token no `localStorage`, que é acessível por scripts na mesma origem em caso de XSS.
- Logout limpa o estado local, mas não invalida tokens já emitidos no Todo Service.
- O modo de compatibilidade (`JWT_ACCEPT_LEGACY_WITHOUT_CLAIMS=true`) mantém o aceite de tokens antigos sem `iss` e `aud`. **Para exigir ambas as claims**, configure `JWT_ACCEPT_LEGACY_WITHOUT_CLAIMS=false` em User e Todo Service **somente após ao menos 24 horas da implantação de todos os emissores atualizados**, acrescidas de eventual margem de relógio, e confirme que não existem emissores antigos ativos. Se os serviços forem usados fora de um ambiente descartável, planeje e teste o rollout/rollback; não altere isoladamente o TTL de 24 horas.
- A tabela de sessões de refresh e o consumo único de fingerprint já existem, mas não estão conectados à autenticação HTTP.

Consulte [ADR-0001 — Sessão web e perfis](ADR-0001-web-session-and-profile-visibility.md) e [ADR-0002 — Revogação distribuída](ADR-0002-distributed-token-revocation.md). A evolução é rastreada na [Issue #11](https://github.com/danmaciel/monorepo_sistema_todo_com_microservices/issues/11).
