# Histórico de validações

Registros por etapa. Pendências históricas podem ter sido resolvidas em etapas posteriores.

## Validação da etapa 8 — entrega consolidada em 29/10/2026

- `clean verify` executado com **Temurin 21.0.12.1**: **276 testes**, 14 unitário/MVC e 262 de integração, zero falhas, erros ou ignorados. A execução completa precedeu as gravações.
- Comando documentado `-Dit.test=ReservationIT#shouldAllowExactlyOneOfTwoConcurrentOverlappingReservations verify` executado separadamente e aprovado: 14 testes unitário/MVC e o teste concorrente selecionado. Conferidos 29 links locais, `git diff --check` e ausência das duas credenciais geradas nos arquivos destinados ao Git.
- `DemoDataIT`: sete testes de dados/estados/preços/eventos, idempotência com alterações preservadas, duas inicializações simultâneas, recusa de banco não vazio, senha ausente, rollback de toda a carga e recusa de nome de banco incorreto. A suíte normal confirmou que apenas a V1 é aplicada fora do perfil demo.
- `Start-Demo.ps1` executado de fato com banco/volume novos, credenciais geradas localmente, migrações do zero e login nas três contas fictícias. Reinício preservou 3 contas, 7 unidades, 8 reservas e 15 eventos após o vídeo. Testados execução fora da raiz, restauração das variáveis e manutenção do arquivo de credenciais.
- Oito capturas (quatro páginas em 1366/360 px), sem overflow da página ou violações axe nas regras WCAG 2/2.1 A/AA selecionadas. Imagens inspecionadas; não substituem avaliação completa de acessibilidade.
- Vídeo WebM de aproximadamente 62 segundos, 1280×900, com dez legendas em português. Confirmação desatualizada recusada, reserva do outro cliente preservada e ciclo ADMIN completo, com três eventos. Reprodução e quadros de conflito/histórico conferidos no Chrome.
- V1 e banco de desenvolvimento preservados; senhas da demo somente em `.local/demo.env` ignorado. Nenhum commit, push ou deploy executado. CI remoto, outros navegadores e leitores de tela permanecem sem verificação nesta tarefa.
- Aplicação e banco da demo encerrados após a revisão, preservando o volume e as contas para a próxima execução de `Start-Demo.ps1`.

## Validação da etapa 2 — 27/10/2026

- `java -version`: disponível JDK Temurin 25.0.5; JDK 21 não encontrado na instalação local consultada.
- `.\mvnw.cmd -B -ntp verify`: **BUILD SUCCESS**, compilação com `release 21`, **109 testes** (2 unitário/MVC, 75 de persistência e 32 de segurança), zero falhas, zero erros e zero testes ignorados. JAR executável gerado.
- Docker disponível e PostgreSQL 17 iniciado automaticamente pelo Testcontainers, sem usar o banco de desenvolvimento.
- Validados: aplicação HTTP com banco real, migração do zero, Hibernate `validate`, segunda inicialização sem reaplicar a V1, normalização/unicidade de e-mail, unicidade patrimonial, relacionamentos, precisão monetária, snapshot após mudança de preço, restrições de estados/timestamps, preservação de histórico e rollback de reserva quando o evento falha.
- Segurança validada: login real, credenciais inválidas, CSRF, logout somente por POST, ADMIN/CLIENTE/visitante, campos privilegiados ignorados, senha ausente do HTML, hash com salt, contas inativas e sessões abertas, permissões no service, pendências antes da desativação, bootstrap inválido/desabilitado/idempotente e criação concorrente do primeiro administrador.
- Diagnóstico de execução local: inicialização completa pelo Maven após importar `.env`, conexão PostgreSQL e `/login` respondendo HTTP 200, em porta temporária e com bootstrap desabilitado. Instância de diagnóstico encerrada ao fim.
- `Start-Local.ps1` verificado com dados fictícios: substitui variáveis antigas antes de iniciar o processo filho, remove opcionais ausentes, propaga falha do Maven e restaura o diretório original. Esse ajuste de inicialização não exigiu repetir a suite Java já aprovada.
- Execução real em JDK 21 **pendente**. Compilar com `release 21` no JDK 25 não substitui essa verificação.
- A execução em JDK 25 exibiu avisos do Maven sobre `Unsafe` e do Mockito/Byte Buddy sobre anexação de agente (incluindo erro de carregamento do agente no caminho local com acento). O teste atual não usa mocks e passou; verificar esse ponto no JDK 21 antes de introduzir mocks nas próximas etapas.

Não houve instalação global de ferramentas nem publicação do projeto.

## Validação da etapa 3 — 27/10/2026

- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, **149 testes** (2 unitário/MVC, 75 de persistência, 32 de segurança e 40 do catálogo), sem falhas, erros ou testes ignorados; JAR executável gerado.
- `EquipmentIT` valida templates reais, permissões/CSRF, cadastro, duplicidade, campos extras, limites e precisão de preço, edição com código fixo, preço histórico, condições e reservas pendentes, preservação de eventos, busca/filtros, caracteres literais, paginação estável e limitada, aposentados ocultos do CLIENTE e 404.
- O teste concorrente usa duas transações reais: mantém a linha do equipamento bloqueada, observa a segunda transação aguardando no PostgreSQL, confirma uma reserva e verifica que a aposentadoria reconsulta as pendências e é recusada. Isso valida o bloqueio do catálogo; os testes de criação concorrente de reservas pertencem à etapa 4.
- Migração V1 intacta, banco de desenvolvimento preservado e `.env`/`.local` continuam ignorados pelo Git. Nenhuma dependência nova.
- Executado com JDK 25 e `release 21`. Execução efetiva em JDK 21 e revisão visual manual no navegador continuam pendentes; os templates foram exercitados por MockMvc.

## Validação da etapa 4 — 27/10/2026

- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, **190 testes** (14 unitário/MVC, 75 de persistência, 32 de segurança, 40 do catálogo e 29 de reservas), sem falhas, erros ou ignorados; JAR executável gerado.
- Diárias inclusivas, virada de mês/ano, ano bissexto, máximo de 365 dias, meia-noite de São Paulo, precisão monetária e limite do total verificados com relógio controlado.
- Disponibilidade e confirmação verificadas com sobreposições em todas as posições, dias limítrofes, unidades distintas, estados finalizados, manutenção/aposentadoria e atraso de devolução. Templates reais, CSRF, autorização, proprietário/valores adulterados, confirmação privada, preservação de filtros e recuperação de conflitos também exercitados.
- Concorrência real: exatamente uma reserva em disputa de período; ambas em períodos compatíveis/unidades distintas; criação contra manutenção/desativação; recarga do cliente após espera; preço lido após bloqueio; timeout com formulário de nova tentativa. Falha deliberada de inserção do evento reverte a reserva.
- Migração V1 preservada, sem dependências novas ou uso do banco de desenvolvimento. `.env` e diagnósticos em `.local` permanecem ignorados pelo Git.
- JDK 25 com `release 21`; execução efetiva em JDK 21 e revisão visual manual continuam pendentes. Os avisos de agente Mockito/Byte Buddy do ambiente continuam presentes, sem falha na suite.

## Validação da etapa 5 — 27/10/2026

- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, **226 testes** (14 unitário/MVC e 212 de integração), sem falhas, erros ou ignorados; JAR executável gerado.
- Os 36 testes de `ReservationManagementIT` cobrem matriz de acesso, propriedade em linhas/contagem/detalhes/cancelamento, entradas adulteradas, CSRF, filtros combinados, datas históricas, curingas literais, paginação estável e limitada, snapshots preservados e motivo administrativo obrigatório.
- Cancelamento simultâneo por titular e ADMIN produz um único evento. Estado recarregado após bloqueio, criação concorrente no período liberado, falha de evento com rollback e timeout preservando motivo/formulário foram verificados com PostgreSQL real e relógio fixo.
- Listagem de 20 reservas com equipamentos distintos usa no máximo três comandos SQL (usuário, página com relações e contagem). Templates reais e navegação nas páginas de erro passaram na suite completa.
- V1 intacta, sem dependências novas ou uso do banco de desenvolvimento. Execução em JDK 25 com `release 21`; JDK 21 efetivo e revisão visual manual continuam pendentes.

## Validação da etapa 6 — 27/10/2026

- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, **267 testes** (14 unitário/MVC e 253 de integração), sem falhas, erros ou ignorados; JAR executável gerado.
- Os 41 testes de `ReservationOperationsIT` cobrem transições válidas/inválidas, período inclusivo, equipamento inapto, outro uso aberto, autorização/CSRF, entradas adulteradas, devolução antecipada/pontual/tardia e valores históricos preservados.
- Relógio controlado verifica 23:59:59/00:00 de São Paulo para retirada, atraso e devolução histórica, inclusive quando UTC já chegou ao dia seguinte. Filtro de atrasos respeita propriedade, contagem e paginação; reservas afetadas são visíveis apenas ao ADMIN.
- Concorrência real: retirada contra cancelamento, retiradas/devoluções duplicadas, dois usos da mesma unidade, devolução contra outra retirada e devolução com manutenção contra nova reserva. Falha de evento reverte estado, instantes e condição física. Timeout reapresenta a tela e preserva a opção de manutenção, sem gravações parciais.
- Linha do tempo validada com autor, ordem por instante/ID, observação de manutenção e privacidade. Consulta de detalhes com três eventos usa até cinco comandos SQL, sem uma consulta por autor.
- V1 preservada, sem dependências novas e sem usar o banco de desenvolvimento. JDK 25 com `release 21`; execução efetiva em JDK 21 e revisão visual no navegador permanecem pendentes para a etapa 7.

## Validação da etapa 7 — 28/10/2026

- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, **269 testes** (14 unitário/MVC e 255 de integração), sem falhas, erros ou ignorados, executados em **Temurin 21.0.12.1** efetivo. A pendência de JDK 21 registrada nas etapas anteriores foi resolvida. O JDK portátil foi obtido do [Adoptium](https://adoptium.net/installation/ci-scripts), verificado por SHA-256 e extraído em `.local`, sem mudar a instalação global.
- Fluxo completo em Chrome headless com ADMIN e dois CLIENTEs em sessões independentes, usando PostgreSQL 17 descartável. Foram **52 verificações** de tela (26 estados em 1366 e 360 px), sem rolagem horizontal da página nem violações detectadas nas regras WCAG 2/2.1 A/AA selecionadas no axe. Tabelas têm rolagem própria. Capturas de layout, validação e histórico também foram inspecionadas.
- Conferidos cadastro, duplicidade de e-mail, foco no erro, nome preservado/senha limpa, busca/paginação, reserva/estimativa, retirada, devolução com manutenção, cancelamento por teclado, histórico privado, moeda/datas, valores e textos máximos, telas vazias, erros 400/403/404 e logout. Nenhum recurso externo obrigatório foi solicitado.
- Workflow validado com **actionlint 1.7.12**; `git diff --check` aprovado. Migração V1 intacta e dados de desenvolvimento preservados. Aplicação e banco descartáveis encerrados; ferramentas de revisão e evidências em `.local` ignoradas pelo Git.
- Pendentes: primeira execução do workflow no GitHub após o push, testes em outros navegadores e com leitores de tela. A auditoria automatizada não estabelece conformidade integral de acessibilidade.

