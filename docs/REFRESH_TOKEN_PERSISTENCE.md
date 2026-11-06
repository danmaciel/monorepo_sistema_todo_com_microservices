# Persistência de sessões e controle de concorrência

## Estrutura e migração

O User Service utiliza Flyway para versionar a criação de `refresh_sessions` por meio de `V1__create_refresh_sessions.sql`. O projeto mantém suas tabelas legadas sob `ddl-auto: update` e usa baseline 0 para introduzir o histórico Flyway em esquemas pré-existentes. Esta coexistência é **transitória** e exige auditoria e backup antes de migrar um banco persistente.

A tabela contém `id`, `user_id`, `family_id`, `token_fingerprint` (único), `created_at`, `expires_at`, `consumed_at` e `revoked_at`. O relacionamento com usuários não recebe chave estrangeira nesta migração porque o esquema compartilhado ainda é administrado parcialmente pelo Hibernate. O isolamento por serviço e a integridade referencial devem ser revistos em uma migração futura.

## Garantia de consumo único

A operação utiliza atualização condicional em PostgreSQL:

```sql
UPDATE refresh_sessions
SET consumed_at = CURRENT_TIMESTAMP
WHERE token_fingerprint = ?
  AND consumed_at IS NULL
  AND revoked_at IS NULL
  AND expires_at > CURRENT_TIMESTAMP;
```

Se uma requisição modifica **uma linha**, ela consumiu o token. Tentativas posteriores modificam **zero linhas**. O banco arbitra requisições concorrentes por bloqueio da linha e reavaliação do predicado, evitando que duas instâncias consumam o mesmo registro. O relógio do banco determina a expiração.

Uma restrição única sobre `token_fingerprint` evita armazenar dois registros com o mesmo fingerprint; é uma garantia distinta de consumo único.

## Evidências de teste

O teste `RefreshSessionStoreTest` inicia PostgreSQL real com Testcontainers, aplica o Flyway e verifica:

- Registro e consulta do fingerprint, sem persistir o token em texto puro.
- Consumo único e rejeição de tentativa repetida.
- Duas tentativas concorrentes, das quais apenas uma deve ter sucesso.
- Bloqueio de sessões expiradas ou revogadas e rejeição de fingerprints duplicados.

```bash
mvn -f packages/user-service/pom.xml -Dtest=RefreshSessionStoreTest test
mvn -f packages/user-service/pom.xml verify
```

Requer um daemon Docker acessível.

## Limites de consistência

**Consumo único não significa rotação transacional completa.** O fluxo futuro deve garantir que o consumo do token anterior e a criação do sucessor sejam confirmados ou revertidos juntos. Também exige identificação e tratamento explícito de replay e revogação de família.

A migração inicial pode precisar de adequações ao histórico de um ambiente existente. Não alterar uma versão Flyway aplicada em produção; criar uma nova migração e validar uma cópia restaurada do banco.

Veja [REFRESH_TOKEN_DESIGN.md](REFRESH_TOKEN_DESIGN.md), [ADR-0002](ADR-0002-distributed-token-revocation.md) e a [Issue #11](https://github.com/danmaciel/monorepo_sistema_todo_com_microservices/issues/11).
