# Inventário do esquema PostgreSQL e plano de migração segura

Este documento descreve a situação observada no código do projeto demonstrativo.
**Não é um procedimento aprovado para migrar um banco com dados reais.**

## Estado atual

- `User Service`: entidade JPA `User` mapeada para `public.users`, com `spring.jpa.hibernate.ddl-auto=update` nos perfis local e Docker.
- `Todo Service`: entidade JPA `Todo` mapeada para `public.todos`, também com `ddl-auto=update`.
- O User Service usa Flyway com `baseline-on-migrate=true` e a migração `V1__create_refresh_sessions.sql` para `public.refresh_sessions`.
- User Service e Todo Service compartilham a mesma base `tododb` no Compose. Alterar um serviço ou migrar a base pode afetar o outro.
- Não há neste repositório um conjunto completo de migrações versionadas que assuma as tabelas `users` e `todos` em lugar do Hibernate.

## Verificação de leitura

Com a aplicação iniciada e o cenário E2E concluído, execute:

```bash
python3 scripts/verify_db_schema.py
```

O script usa `information_schema.columns` e **apenas SELECT** para verificar um contrato mínimo de colunas, tipos e nulabilidade das tabelas `users`, `todos` e `refresh_sessions`. Ele não imprime dados de usuários nem senhas, e não altera estruturas ou registros. A pipeline E2E também executa a verificação.

**O que ele não demonstra:** fidelidade integral do esquema; tamanhos de varchar; PKs/FKs; índices e unicidade; check constraints; defaults; collation; compatibilidade de todas as versões antigas; nem segurança de uma migração efetiva.

## Caminho recomendado para futura migração

1. Levantar o DDL efetivo em um banco **descartável**, criado pelas versões atuais dos dois serviços. Revisar diferenças em comparação com as anotações JPA e as versões PostgreSQL suportadas.
2. Inventariar explicitamente PKs, índices, colunas, defaults, constraints e dependências das tabelas `users`, `todos` e `refresh_sessions`. Definir **qual serviço é dono de cada esquema ou tabela** e como as migrações são coordenadas.
3. Exercitar uma cópia **anonimizada** de um banco existente, após backup e com plano de recuperação validado. Evitar comandos destrutivos e não alterar a migração V1 já aplicada.
4. Introduzir migrações Flyway novas em PRs separados, somente após decidir a política de baseline para bancos existentes e validar inicialização em base vazia e em cópia legada. Flyway não deve recriar uma tabela preexistente.
5. Só então mudar o Hibernate gradualmente para `ddl-auto=validate` e testar E2E, retomada da aplicação e dados preservados, antes de considerar remover `update`.

Não alterar `ddl-auto` agora: seria uma mudança comportamental arriscada sem ter a evolução de `users`/`todos` inteiramente versionada.

## Restrições e ressalvas

O projeto não implementa bancos separados por serviço, migrations completas para todas as entidades, deploy produtivo ou rollback automático de migrações. O teste de contrato mínimo é uma *evidência inicial*, não uma substituição dessas etapas.
