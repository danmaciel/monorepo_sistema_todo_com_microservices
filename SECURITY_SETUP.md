# Configuracao segura para Docker Compose

- Copie `.env.example` para `.env` e defina `POSTGRES_PASSWORD` e `JWT_SECRET` com valores exclusivos e fortes. O `.env` esta ignorado pelo Git.
- Rode `docker compose config` para validar a configuracao (nao publique a saida: ela pode incluir segredos).
- As credenciais sao compartilhadas entre os servicos somente para este exemplo. Para producao, aplique segregacao de acessos, gestao de segredos e migracoes de banco controladas.
- As portas de PostgreSQL, Redis, Eureka e servicos internos estao limitadas a `127.0.0.1` no host; gateway/frontend continuam conforme a configuracao original.
- Tokens JWT e senhas que tenham utilizado os valores antigos devem ser revogados/trocados. O historico Git ainda contem valores anteriores ate uma limpeza especifica.
- Valide autenticacao e fluxo de usuarios/tarefas apos iniciar com os novos segredos.
