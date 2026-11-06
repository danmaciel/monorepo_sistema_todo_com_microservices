# Modelo de sessão e material de refresh token

## Objetivo

O projeto contém a base para evoluir de um JWT de acesso de 24 horas para access tokens curtos e refresh tokens rotativos. **O login atual ainda não emite refresh tokens**; não existem endpoints de renovação nem integração com cookies ou frontend.

O modelo separa três responsabilidades:

| Componente | Responsabilidade |
| --- | --- |
| `RefreshTokenMaterial` | Gera segredo opaco de 32 bytes com `SecureRandom`, serializa em Base64URL e calcula fingerprint SHA-256 |
| `RefreshSession` | Modelo imutável com identificadores da sessão, usuário e família, fingerprint, expiração e revogação |
| `RefreshSessionStore` | Persiste fingerprint no PostgreSQL e permite consumo único por atualização condicional |

O refresh token original é um segredo apresentado pelo cliente. O banco armazena apenas o fingerprint, evitando exposição direta do valor caso a tabela seja lida indevidamente. SHA-256 é aplicável aqui porque os tokens são gerados com alta entropia; não substitui Argon2id/bcrypt para senhas escolhidas por pessoas.

## Estado e transições

```mermaid
stateDiagram-v2
    [*] --> Ativa: sessão criada
    Ativa --> Consumida: consumo único
    Ativa --> Revogada: revogação registrada
    Ativa --> Expirada: prazo alcançado
    Consumida --> [*]
    Revogada --> [*]
    Expirada --> [*]
```

O estado representa os campos disponíveis na persistência. **Não há hoje uma máquina de estados transacional de renovação nem política de replay implementada.** A presença de `family_id` permite modelar revogação de uma família em evolução posterior; não realiza essa ação automaticamente.

## Requisitos para a evolução

Uma renovação completa precisará consumir o token anterior e registrar o sucessor **na mesma transação**. Também deverá definir política de revogação por reutilização, tratamento de múltiplas abas, perdas de resposta e invalidação de sessões de contas desativadas.

O eventual transporte via cookie `HttpOnly; Secure; SameSite` requer projeto de CORS, CSRF, domínio e HTTPS. Não deve ser ativado apenas por adicionar o atributo do cookie.

## Testes e referências

```bash
mvn -f packages/user-service/pom.xml -Dtest=RefreshSessionTest test
mvn -f packages/user-service/pom.xml -Dtest=RefreshSessionStoreTest test
```

O segundo comando requer Docker para o PostgreSQL do Testcontainers. Consulte [REFRESH_TOKEN_PERSISTENCE.md](REFRESH_TOKEN_PERSISTENCE.md) para os invariantes SQL e testes de concorrência, e [ADR-0002](ADR-0002-distributed-token-revocation.md) para a avaliação de estratégias.
