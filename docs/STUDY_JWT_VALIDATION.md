# Guia de estudo — validação de JWT no monorepo

> **Objetivo:** compreender e reproduzir os testes existentes. A aprovação do CI é evidência de que o código passou nas verificações, não de domínio individual do tema.

## 1. Conceitos para explicar com suas palavras

**Autenticação** comprova quem fez a requisição; **autorização** determina a quais recursos essa identidade pode acessar. No projeto, User Service emite o JWT e valida também o estado da conta; Todo Service valida assinatura/expiração e extrai o ID do usuário, mas não consulta sua situação atual.

JWT é composto por **header.payload.signature**. Os dois primeiros segmentos são codificados, **não criptografados**. Não inclua senha, segredo, dados pessoais desnecessários ou informações confidenciais no payload.

- `sub`: quem é a identidade? Aqui, UUID do usuário.
- `exp`: quando o token deixa de ser válido?
- `iat`: quando foi emitido?
- `iss`: quem emitiu? **Ainda não é validado no projeto.**
- `aud`: para qual serviço/consumidor? **Ainda não é validado no projeto.**
- Assinatura HS256: o validador conhece o mesmo segredo do emissor. Uma chave conhecida pelo Todo Service também lhe permite assinar tokens; é uma limitação do desenho simétrico.

**Importante:** um JWT assinado e não expirado não demonstra que o usuário continue ativo no sistema. Isso requer alguma forma de verificar ou distribuir o estado de autorização.

## 2. Comportamento comprovado e lacunas

| Aspecto | Evidência existente | Situação |
| --- | --- | --- |
| Emissão de JWT com `sub` e `username` | `User Service / JwtTokenProvider` | Implementado |
| Validação criptográfica de HS256 | `JwtTokenProvider` de ambos | Implementada |
| Rejeição de JWT expirado | E2E e testes de contrato | Testado |
| Rejeição de assinatura feita por outra chave | Testes de contrato | Testado |
| Checagem de conta desativada | Filtro do User Service | Implementada e testada |
| Checagem de conta desativada pelo Todo Service | Não há consulta de estado no filtro | **Não implementada** |
| Validação de `iss` e `aud` | Não configurada nos providers | **Não implementada** |
| Refresh token rotativo | ADR-0002/Issue #11 | **Proposto; não implementado** |
| Logout com revogação no backend | O frontend descarta o JWT local | **Não implementado** |

Os testes de contrato novos usam segredos **fictícios e somente de teste**. Não há chave real do ambiente publicada.

## 3. Execute e inspecione você mesmo

```bash
mvn -f packages/user-service/pom.xml -Dtest=JwtTokenProviderContractTest test
mvn -f packages/todo-service/pom.xml -Dtest=JwtTokenProviderContractTest test
mvn -f packages/user-service/pom.xml verify
mvn -f packages/todo-service/pom.xml verify
```

Abra os dois arquivos `JwtTokenProvider.java` e responda, sem copiar:

1. Por que o Todo Service pode validar o JWT emitido pelo User Service sem chamar esse serviço?
2. Se alguém desativar uma conta, qual dos dois serviços interrompe o acesso imediatamente? Qual é o limite dessa garantia?
3. Por que um token roubado do `localStorage` pode ser usado por terceiros enquanto for válido?
4. Por que reduzir `exp` de 24 horas para cinco minutos, isoladamente, prejudicaria a experiência do usuário?
5. Qual a diferença entre um refresh token rotativo e uma lista de revogação em Redis?
6. Onde incluiríamos a validação de `iss` e `aud`, e como testaríamos uma migração de tokens legados?
7. O que acontece se o Redis ficar indisponível? Qual política de falha escolheríamos e por quê?

## 4. Exercícios progressivos

- **Básico:** altere temporariamente um payload de JWT de teste sem recalcular a assinatura. Execute os testes e explique por que o token passa a ser inválido. Não faça commits de tokens reais.
- **Intermediário:** escreva um teste que emita JWT com um segredo e tente validá-lo com outro.
- **Intermediário:** explique por que o User Service consulta `enabled` e o Todo Service não o faz; proponha um teste para desativação de usuário com token ainda válido.
- **Avançado:** desenhe uma migração compatível para `iss`, `aud` e access tokens de curta duração, evitando derrubar sessões existentes.
- **Arquitetura:** compare **introspecção síncrona**, **token curto + refresh** e **revogação compartilhada** com base em latência, disponibilidade, consistência e custo operacional.

## 5. Como apresentar isso em uma entrevista

Uma explicação fiel seria: *“No monorepo de estudo, o User Service emite JWT e o Todo Service valida localmente. Criamos testes de expiração e assinatura e documentamos as limitações de revogação entre serviços. A solução de refresh token rotativo está planejada, ainda não implantada.”*

Evite dizer que o projeto possui **SSO, OAuth 2.0, revogação distribuída pronta, autenticação zero trust ou ambiente de produção**: essas propriedades não são demonstradas pelo código atual.

## 6. Referências e próximos passos

- [ADR-0001: sessão web e política de perfis](ADR-0001-web-session-and-profile-visibility.md)
- [ADR-0002: alternativas de revogação distribuída](ADR-0002-distributed-token-revocation.md)
- [Issue #11: implementação incremental](https://github.com/danmaciel/monorepo_sistema_todo_com_microservices/issues/11)

**Próximo marco técnico:** definir contrato de `iss`/`aud` e estratégia de migração, com testes que falhem para valores incorretos, antes de introduzir refresh tokens ou reduzir a validade atual.
