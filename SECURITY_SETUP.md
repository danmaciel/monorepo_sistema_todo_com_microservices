# Configuracao segura para Docker Compose

- Copie `.env.example` para `.env` e defina `USER_DB_PASSWORD` e `TODO_DB_PASSWORD` fortes e distintos. No HMAC legado (padrao), defina tambem `JWT_SECRET` de pelo menos 32 bytes. No RS256 opcional, configure o par RSA somente no User Service e a chave publica no Todo Service, conforme `docs/JWT_SECURITY.md`. O `.env` esta ignorado pelo Git.
- Rode `docker compose config` para validar a configuracao (nao publique a saida: ela pode incluir segredos).
- Cada servico possui um banco PostgreSQL e credenciais proprias. **Somente no modo HMAC** o segredo de assinatura e compartilhado; no RS256 o Todo Service nao recebe chave privada. Para producao, aplique gestao de segredos, isolamento de rede, rotacao de chaves e migracoes controladas.
- User DB usa `127.0.0.1:5432` e Todo DB usa `127.0.0.1:5433`; Redis, Eureka e servicos internos tambem estao limitados a loopback no host.
- Tokens JWT e senhas que tenham utilizado os valores antigos devem ser revogados/trocados. O historico Git ainda contem valores anteriores ate uma limpeza especifica.
- Valide autenticacao e fluxo de usuarios/tarefas apos iniciar com os novos segredos.

- Volumes antigos do banco compartilhado nao sao migrados pelo Compose novo. Nao execute `docker compose down -v` para preservar dados locais sem antes confirmar que sao descartaveis.
