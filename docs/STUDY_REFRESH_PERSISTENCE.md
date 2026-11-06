# Laboratório — persistência e consumo atômico de refresh token (etapa 2)

> **Estado atual:** tabela PostgreSQL migrada pelo Flyway, armazenamento de fingerprint e consumo único do token. **Ainda não há login/refresh/logout integrado, rotação para novo token ou revogação distribuída.**

## Por que separar o modelo do banco?

Na etapa 1, `RefreshSession` descreveu uma sessão e `RefreshTokenMaterial` gerou segredos aleatórios e fingerprints SHA-256. Nenhuma dessas classes gravava no banco.

Nesta etapa, `RefreshSessionStore` usa `JdbcTemplate` e uma migração SQL `V1__create_refresh_sessions.sql` para persistir **somente a impressão digital** do refresh token, nunca seu valor original.

A tabela possui:
- `id`: identificador da sessão
- `user_id`: identificador da conta
- `family_id`: permite modelar uma família de rotações futuras
- `token_fingerprint`: hash SHA-256 em hexadecimal, único e obrigatório
- `created_at`, `expires_at`: duração da sessão
- `consumed_at`: marca consumo único
- `revoked_at`: marca revogação

O banco existente é compartilhado por serviços. Por isso, esta migração não presume uma FK para a tabela de usuários, ainda administrada pelo Hibernate. A integridade com usuários e as migrações de esquema completo são **dívidas técnicas explícitas**, não funcionalidades resolvidas.

## A regra central: uma atualização condicional

```sql
UPDATE refresh_sessions
SET consumed_at = CURRENT_TIMESTAMP
WHERE token_fingerprint = ?
  AND consumed_at IS NULL
  AND revoked_at IS NULL
  AND expires_at > CURRENT_TIMESTAMP;
```

O resultado é o número de registros alterados: **1** significa que a primeira tentativa válida consumiu o token; **0** significa que não existe sessão elegível, que já foi consumida, revogada ou expirou.

O PostgreSQL aplica bloqueio de linha durante o `UPDATE`. Se duas requisições tentarem consumir o mesmo registro, apenas uma deverá satisfazer o filtro após o bloqueio; a outra deverá obter zero registros alterados. Essa proteção funciona mesmo com duas instâncias da aplicação.

**Isso ainda não constitui rotação atômica completa.** Na futura rotação, consumo do token anterior e persistência do token seguinte precisarão acontecer na **mesma transação**. Se houver falha entre operações, a aplicação não deve deixar sessão inconsistente. A detecção de reutilização maliciosa e revogação de famílias também ainda não foi implementada.

## Migração de bancos existentes

O User Service passa a executar Flyway no startup. Como o projeto já possui tabelas criadas por `ddl-auto: update`, foi configurado `baseline-on-migrate: true` com `baseline-version: 0` para permitir criar o histórico Flyway sem tentar recriar as tabelas antigas. A migração V1 cria **somente** `refresh_sessions`.

- Faça backup e valide a migração em cópia de ambiente antes de aplicá-la em um banco persistente.
- Não modifique uma migração já aplicada; crie `V2__...` para alterações subsequentes.
- Um novo serviço ou banco sem histórico deve executar as migrações desde o início.
- `ddl-auto: update` **continua ativo** para as tabelas antigas; a migração de todo o esquema para Flyway exige planejamento à parte.
- A primeira execução em bancos já modificados exige cuidado com histórico Flyway existente, permissões SQL e concorrência na inicialização.

## Como testar

```bash
mvn -f packages/user-service/pom.xml -Dtest=RefreshSessionStoreTest test
mvn -f packages/user-service/pom.xml verify
```

O teste de integração utiliza **Testcontainers** e inicia PostgreSQL real em Docker; portanto, é necessário um daemon Docker disponível. Os testes verificam persistência, índice único, expiração, revogação, tentativa duplicada e consumo concorrente por duas threads.

## Exercícios

1. Explique por que `SELECT` seguido de `UPDATE` sem bloqueio/transação pode permitir consumo duplicado.
2. Diferencie **restrição de unicidade** (`token_fingerprint`) e **atualização condicional** (`consumed_at IS NULL`).
3. Imagine duas instâncias do User Service. Onde fica a garantia de consumo único: na memória, no Redis ou no banco? Por quê?
4. Como garantir que, ao consumir um token, seja criado um sucessor na **mesma transação**?
5. O que aconteceria se um cliente legítimo perdesse a resposta depois de a rotação ter sido confirmada? Como projetar recuperação sem abrir brecha para replay?

**Para entrevistas:** “Implementei uma migração PostgreSQL de sessões e uma operação de consumo único com teste de concorrência usando Testcontainers. O fluxo de rotação transacional completo, os endpoints e a integração com o frontend continuam pendentes.”
