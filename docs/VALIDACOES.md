# Validação do sistema

## Verificação da M4 — 01/11/2026

- `clean verify` em JDK 21 e PostgreSQL 17: **407 testes aprovados**, sendo 14 unitários/MVC e 393 de integração, sem falhas, erros ou testes ignorados. Foram acrescentados 37 cenários de chat e um de migração V4 → V5.
- Cobertos: acesso pelo titular e administradores da empresa, isolamento entre empresas/clientes, contas e vínculos inativos, bloqueio comercial, CSRF, campos adulterados, texto escapado, validação, paginação e consulta incremental. Mensagens não alteram propostas, aceite, preço, estado ou disponibilidade.
- Reenvio mantém a mensagem original; alteração de texto com a mesma chave é recusada. Limite de dez envios por participante/conversa em 60 segundos, fronteira exata dos sete dias após recusa/cancelamento/devolução e pedidos com início passado são exercitados com relógio controlado.
- Concorrência real em PostgreSQL: envios duplicados, disputa pelo décimo envio, cancelamento, bloqueio comercial, desativação de empresa/vínculo/administrador/cliente e prazo que termina enquanto o envio aguarda bloqueio. Falha na gravação reverte a transação e permite nova tentativa.
- V5 validada em banco vazio e sobre dados V4: preserva contas, empresas, equipamentos, solicitações, contratos, propostas, aceites e eventos. Somente a tabela de mensagens e seus índices são acrescentados. V1 a V4 permanecem intactas.
- JAR executado em PostgreSQL exclusivo e descartável, com duas empresas e dois clientes. Chrome: troca entre sessões, atualização automática, rascunhos de mensagem/proposta preservados, carregamento de mensagens antigas, resposta perdida seguida de reenvio sem duplicata, conexão interrompida, limite de frequência e sessão encerrada. Envio sem JavaScript e negativa de acesso por outro cliente também passaram. Proposta de R$ 40,00 por três dias, aceite e aprovação mantiveram o contrato de R$ 120,00 independente da conversa.
- Após o ajuste final da rolagem, o JAR foi atualizado e a jornada de navegador repetida: **20 verificações automatizadas de interface** em 1366 px e 360 px, sem rolagem horizontal, erros JavaScript ou violações nas regras WCAG 2/2.1 A/AA selecionadas pelo axe-core. Capturas da conversa foram inspecionadas visualmente. Teclado, posição de leitura ao carregar mensagens antigas e acompanhamento de novas mensagens no final foram conferidos. Isso não equivale a uma auditoria completa de acessibilidade.
- `git diff --check` e os 25 links locais da documentação passaram. Os relatórios dos testes Java permanecem em `target/surefire-reports` e `target/failsafe-reports`.
- O `.env` e o banco normal não foram usados nem alterados. O volume `equiprent_postgres_data` foi preservado; o ambiente e as credenciais temporárias da revisão foram descartados.

## Verificação da M3 — 01/11/2026

- `clean verify` em JDK 21 e PostgreSQL 17: **369 testes aprovados**, sendo 14 unitários/MVC e 355 de integração, sem falhas, erros ou testes ignorados. Foram acrescentados 40 cenários de negociação e um de migração V3 → V4.
- Depois do ajuste final do rótulo de valor na rota antiga de confirmação, os 14 testes unitários/MVC e o cenário HTTP de consulta, solicitação e reenvio foram repetidos com sucesso; o JAR foi atualizado. `git diff --check` e os 25 links locais da documentação também passaram.
- Cobertos: proposta inicial aceita no envio, estimativa mantida na sessão, alteração/expiração da estimativa, valores e condições versionados, aceite obsoleto, aprovação exclusiva da empresa, duplicidade/reenvio, recusa com motivo, cancelamento, início passado, preservação dos valores na retirada/devolução e histórico paginado.
- Concorrência em PostgreSQL: submissões idênticas, aprovações sobrepostas, aprovação versus contraproposta, aceite versus nova proposta, aprovação versus cancelamento/manutenção/bloqueio comercial e desativação de empresa, vínculo, administrador ou cliente enquanto a aprovação espera. Falhas na gravação de proposta/evento revertem a transação.
- V4 validada tanto em banco vazio quanto sobre dados da V3, preservando contratos `RESERVED`, `IN_USE`, `RETURNED` e `CANCELLED`, seus preços e eventos, sem criar aceites retroativos. V1 a V3 permanecem intactas.
- JAR executado em banco exclusivo e descartável com duas empresas e dois clientes. No Chrome, o fluxo real solicitação → proposta → aceite → aprovação mudou a diária de R$ 50,00 para R$ 40,00 em três dias: contrato de R$ 120,00 e estimativa inicial de R$ 150,00 preservada. Pedidos pendentes não bloquearam o catálogo; aprovação bloqueou somente a unidade correspondente.
- **24 verificações automatizadas de interface**, com capturas e inspeção visual de telas representativas em 1366 px e 360 px: estimativa, pedido pendente, proposta/erro, aceite, contrato aprovado, conflito, recusa, cancelamento, prazo encerrado e listas. Sem rolagem horizontal, erros JavaScript ou violações nas regras WCAG 2/2.1 A/AA selecionadas pelo axe-core. Teclado conferido na expansão/recolhimento do cancelamento e no foco do motivo/botão. Isso não equivale a uma auditoria completa de acessibilidade.
- O `.env` e o banco normal não foram usados nem alterados por esta verificação. O volume `equiprent_postgres_data` foi preservado; processo Java, contêiner, volume e credenciais temporários da revisão foram descartados.

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

- Persistência, integridade monetária e migrações de V1/V2/V3/V4 até V5 com preservação dos dados.
- Autenticação, CSRF, atividade das contas e autorização por proprietário/empresa.
- Catálogo, disponibilidade, períodos inclusivos e valores históricos.
- Concorrência real entre transações, rollback de alterações e registros de eventos.
- Cancelamento, retirada, devolução, manutenção e atrasos.
- Provisionamento de empresas e bloqueio comercial de clientes por empresa.
- Busca pública, cidade/UF, preços inclusivos, paginação e restrição dos campos publicados.
- Cadastro pessoal sem privilégios administrativos, CSRF, senhas, e-mail normalizado e duplicidade concorrente.
- Continuidade da pesquisa durante cadastro/login, destinos relativos e revalidação da disponibilidade.
- Edição de localização da própria empresa, validação de cidade/UF e atualizações concorrentes.
- Solicitação, propostas versionadas, aceite e aprovação, incluindo isolamento por empresa/titular, CSRF, texto escapado e valores calculados no servidor.
- Chat privado, histórico paginado, reenvio sem duplicação, limite por participante, prazo após encerramento, preservação da negociação e conflitos concorrentes.

## Pendências

Execução remota no GitHub Actions, outros navegadores e leitores de tela devem ser conferidos separadamente. Publicação e requisitos de operação comercial permanecem no [planejamento](../PLANEJAMENTO.md).
