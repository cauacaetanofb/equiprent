# Validação do sistema

## Verificação da M2 — 01/11/2026

- `clean verify` em JDK 21 e PostgreSQL 17: **328 testes aprovados**, sendo 14 unitários/MVC e 314 de integração, sem falhas, erros ou testes ignorados. São 39 novos casos de integração para M2 e migração V2 → V3.
- Após os ajustes finais de mensagens e acessibilidade, os 14 testes unitários/MVC e os 38 casos de `MarketplaceAccessIT` foram repetidos com sucesso.
- JAR iniciado em PostgreSQL descartável: jornada real no Chrome de busca entre duas empresas → detalhes → cadastro com validação de senhas → login incorreto/correto → retorno ao equipamento e datas → confirmação de reserva. Três diárias de R$ 29,90 resultaram em R$ 89,70. A edição da localização da empresa A apareceu na busca pública e preservou a empresa B.
- **18 verificações visuais/automatizadas** em larguras de 1366 px e 360 px: início, resultados, busca vazia, detalhes, login, cadastro, erro de cadastro, formulário de reserva e localização da empresa. Sem rolagem horizontal, erros JavaScript ou violações nas regras WCAG 2/2.1 A/AA selecionadas pelo axe-core. Isso não substitui avaliação completa de acessibilidade com tecnologias assistivas.
- `.env` da raiz inalterado e volume normal `equiprent_postgres_data` preservado. Os testes não acessaram o banco normal. V1 e V2 permanecem intactas; a V3 foi validada em instalação limpa e atualização com dados anteriores. O ambiente e as credenciais temporárias de verificação são descartados ao concluir a tarefa.

## Executar novamente

```powershell
.\mvnw.cmd clean verify
```

Os relatórios completos ficam em `target/surefire-reports` e `target/failsafe-reports`. Testcontainers usa bancos PostgreSQL descartáveis e não lê o `.env` nem o volume da aplicação.

## Cobertura principal

- Persistência, integridade monetária e migrações de V1/V2 até V3 com preservação dos dados.
- Autenticação, CSRF, atividade das contas e autorização por proprietário/empresa.
- Catálogo, disponibilidade, períodos inclusivos e valores históricos.
- Concorrência real entre transações, rollback de alterações e registros de eventos.
- Cancelamento, retirada, devolução, manutenção e atrasos.
- Provisionamento de empresas e bloqueio comercial de clientes por empresa.
- Busca pública, cidade/UF, preços inclusivos, paginação e restrição dos campos publicados.
- Cadastro pessoal sem privilégios administrativos, CSRF, senhas, e-mail normalizado e duplicidade concorrente.
- Continuidade da pesquisa durante cadastro/login, destinos relativos e revalidação da disponibilidade.
- Edição de localização da própria empresa, validação de cidade/UF e atualizações concorrentes.

## Pendências

Execução remota no GitHub Actions, outros navegadores e leitores de tela devem ser conferidos separadamente. Publicação e requisitos de operação comercial permanecem no [planejamento](../PLANEJAMENTO.md).
