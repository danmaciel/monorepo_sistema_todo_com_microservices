# Inventário do esquema PostgreSQL e plano de migração segura

Este documento descreve a situação observada no código do projeto demonstrativo.
**Não é um procedimento aprovado para migrar um banco com dados reais.**

## Estado atual

- `User Service`: entidade JPA `User` mapeada para `public.users`, com `spring.jpa.hibernate.ddl-auto=update` nos perfis local e Docker.
- `Todo Service`: entidade JPA `Todo` mapeada para `public.todos`, também com `ddl-auto=update`.
- O User Service usa Flyway com `baseline-on-migrate=true` e a migração `V1__create_refresh_sessions.sql` para `public.refresh_sessions`.
- User Service e Todo Service compartilham a mesma base `tododb` no Compose. Alterar um serviço ou migrar a base pode afetar o outro.
- O inventário inicial foi realizado antes da migração das entidades para Flyway; a implementação atual é descrita adiante.

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

A implementação para a base demonstrativa passa a usar `ddl-auto=validate`; não executar este procedimento cegamente em bancos externos ou volumes legados sem conferir o esquema.


## Implementação no repositório demonstrativo

Na etapa de migração do laboratório, foram adicionadas migrações para as entidades de negócio:

- User Service: `V2__create_users.sql`, após a migração `V1` já existente de `refresh_sessions`; mantém a tabela histórica padrão `flyway_schema_history`.
- Todo Service: `V1__create_todos.sql`; usa `todo_flyway_schema_history` para **não compartilhar o controle de versões do Flyway** com o User Service.
- Ambos os serviços agora usam `spring.jpa.hibernate.ddl-auto=validate`, sem criação/alteração automática de tabelas pelo Hibernate.
- As definições `CREATE TABLE IF NOT EXISTS` não apagam tabelas existentes. **Isso não significa que uma base antiga, com esquema divergente, foi automaticamente migrada.** A verificação Hibernate e o teste E2E precisam passar.
- Os testes PostgreSQL do armazenamento de sessões usam a tabela `users` criada pela própria migration, em vez de fabricar um esquema diferente só para o teste.

A inicialização de banco novo deve ser validada pela pipeline Docker Compose E2E. O histórico de migrações não foi reescrito. Bancos locais anteriores continuam exigindo verificação do schema antes de reutilizar volumes; para ambiente descartável, crie um volume novo conforme o procedimento normal de Docker, sem apagar arquivos ou histórico Git.

**Nota:** os dois serviços ainda compartilham o mesmo PostgreSQL e usuário de banco. A separação de esquemas/credenciais é uma melhoria futura; esta etapa apenas separa os históricos de versões do Flyway.

## Restrições e ressalvas

O projeto não implementa bancos separados por serviço, deploy produtivo ou rollback automático de migrações. O teste de contrato mínimo é uma *evidência inicial*, não uma substituição dessas etapas.
