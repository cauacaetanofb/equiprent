# Validação do sistema

## Verificação da limpeza — 01/11/2026

- `clean verify` em JDK 21 e PostgreSQL 17: **289 testes aprovados**, sendo 14 unitários/MVC e 275 de integração, sem falhas, erros ou testes ignorados. Os sete testes exclusivos da carga de demonstração foram removidos junto com esse recurso; os testes de negócio, migração, segurança, concorrência e isolamento entre empresas foram preservados.
- JAR iniciado com perfil normal em PostgreSQL descartável e bootstrap desativado: V1 e V2 aplicadas, sem contas, equipamentos ou reservas criados automaticamente. O pacote não contém classes, migrações ou configuração de demonstração.
- Páginas inicial e de login retornaram HTTP 200 no Chrome, em larguras de 1366 px e 360 px, sem rolagem horizontal. Quatro verificações com axe-core não encontraram violações nas regras WCAG 2/2.1 A/AA selecionadas; isso não substitui uma avaliação completa de acessibilidade. O catálogo continua redirecionando visitantes para o login.
- `.env` da raiz conferido por hash e inalterado. Volume normal `equiprent_postgres_data` preservado, sem conexão a ele durante a verificação. Migrações V1 e V2 intactas. Ambientes Docker de demonstração e seus volumes removidos.

## Executar novamente

```powershell
.\mvnw.cmd clean verify
```

Os relatórios completos ficam em `target/surefire-reports` e `target/failsafe-reports`. Testcontainers usa bancos PostgreSQL descartáveis e não lê o `.env` nem o volume da aplicação.

## Cobertura principal

- Persistência, integridade monetária e migração V1 → V2 com preservação dos dados.
- Autenticação, CSRF, atividade das contas e autorização por proprietário/empresa.
- Catálogo, disponibilidade, períodos inclusivos e valores históricos.
- Concorrência real entre transações, rollback de alterações e registros de eventos.
- Cancelamento, retirada, devolução, manutenção e atrasos.
- Provisionamento de empresas e bloqueio comercial de clientes por empresa.

## Pendências

Execução remota no GitHub Actions, outros navegadores e leitores de tela devem ser conferidos separadamente. Publicação e requisitos de operação comercial permanecem no [planejamento](../PLANEJAMENTO.md).
