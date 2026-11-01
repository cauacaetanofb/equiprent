# Planejamento do EquipRent

## 1. Objetivo e limites

Evoluir o EquipRent para um marketplace de aluguel de equipamentos: o cliente pesquisa um equipamento e um período, compara a disponibilidade e a diária de diferentes empresas, envia uma solicitação e conversa com a empresa pelo site. Após a negociação, um administrador da empresa aprova a reserva. Retirada, devolução e histórico continuam fazendo parte do fluxo.

**Entrega atual:** etapas 0 a 8 implementadas para uma única empresa por instalação: ciclo completo de locação, interface revisada em desktop/360 px, CI configurado e material de portfólio. Perfil `demo` com banco separado, contas fictícias, oito capturas, vídeo com legendas e roteiro de apresentação. Última validação registrada: suite aprovada em JDK 21 efetivo, com 276 testes e PostgreSQL, inclusive concorrência. Esses resultados pertencem à base atual e não comprovam as funcionalidades futuras do marketplace. Execução remota do CI ainda precisa ser conferida.

**Direção aprovada em 01/11/2026:** planejamento do marketplace, com solicitação sem bloqueio de disponibilidade, aprovação pela empresa, concordância do cliente com mudanças de preço e chat vinculado à solicitação. Esta revisão altera somente este documento. A implementação de código será apresentada para aprovação antes de cada etapa; a publicação online permanece adiada.

**M1 implementada em 01/11/2026:** empresas, vínculos administrativos, isolamento de equipamentos/reservas/clientes, bloqueio comercial por empresa e provisionamento local. Migração V2 preserva os registros anteriores. Suite completa aprovada com 296 testes em JDK 21/PostgreSQL. Busca pública, aprovação e chat continuam pendentes. [Operação e testes da M1](docs/EMPRESAS.md).

**Como ler este documento:** as seções 2 a 6 registram a arquitetura, as regras e as entregas da base existente. As seções 7 a 9 descrevem a evolução para marketplace e registram o estado de cada etapa. A M1 substitui o acesso administrativo global pelo vínculo de empresa; a confirmação direta pelo cliente permanece até M3.

### Base implementada — etapas 0 a 8

- Autenticação com perfis `ADMIN` e `CLIENTE`.
- Cadastro de clientes e equipamentos: nome, descrição, categoria, código individual e diária.
- Disponibilidade por período, criação e cancelamento de reservas de um único equipamento.
- Estimativa do aluguel com diária histórica, retirada, devolução e identificação de atraso.
- Histórico de reservas e movimentações com autor e instante da operação.
- Manutenção, preservação de registros e desativação em vez de exclusão de históricos.
- Busca, filtros, paginação e interface responsiva em português.

**Limites da base entregue:** não há várias empresas, chat, aprovação de solicitações, pagamentos, multas automáticas, e-mails, aplicativos móveis, carrinho com vários equipamentos, agendamento de manutenção, prorrogação ou reprogramação de reservas. Várias empresas, chat e aprovação passam a integrar o novo planejamento. Os demais itens continuam fora do primeiro ciclo do marketplace, salvo revisão explícita de escopo. Datas de uma solicitação/reserva não serão editadas nesse ciclo: cancelar e solicitar novamente evita alterar silenciosamente as condições negociadas.

## 2. Arquitetura e decisões técnicas da base existente

Monólito Spring Boot com Spring MVC e páginas Thymeleaf. Um processo Java e um PostgreSQL. Bootstrap local para o layout, sem build JavaScript obrigatório. Java 21 e Spring Boot 4.1.2, cuja compatibilidade foi confirmada na [documentação oficial](https://docs.spring.io/spring-boot/system-requirements.html) em 27/10/2026.

Organização **por funcionalidade**, mantendo classes pequenas e papéis explícitos. Não criar interfaces de service sem necessidade, controllers genéricos, repositórios base próprios ou camadas que só repassem chamadas.

```text
com.equiprent
  EquipRentApplication
  home/
    HomeController                         # existe
  user/                                    # implementado na etapa 2
    User, UserRole, UserRepository
    UserService, UserController, UserForm, UserSummary
  equipment/                               # catálogo e condição física implementados na etapa 3
    Equipment, EquipmentCondition, EquipmentCategory
    EquipmentRepository, EquipmentService, EquipmentController
    EquipmentForm, EquipmentSummary
  reservation/                             # fluxo de reservas implementado nas etapas 4 a 6
    Reservation, ReservationStatus, ReservationEvent
    ReservationRepository, ReservationEventRepository
    ReservationService, ReservationController, RentalPeriod, AvailabilityRules
    ReservationForm, ReservationEstimate, ReservationConfirmation
    ReservationManagementService, ReservationManagementController
    ReservationFilter, ReservationSummary, ReservationDetails, CancellationForm
    ReservationOperationsService, ReturnForm, ReservationTimelineEntry
  security/                                # implementado na etapa 2
    SecurityConfig, CustomUserDetailsService, CurrentUser
    ActiveUserFilter, AdminBootstrap, AccountController
    ApplicationSecurityConfiguration, NavigationAdvice
  demo/                                    # opcional, somente no perfil demo
    DemoDataLoader, DemoNavigationAdvice
  shared/                                  # somente quando necessário
    configuração de Clock e tratamento de erros comuns

resources/templates/{home,user,security,fragments,error}/  # implementadas
resources/templates/{equipment,reservation}/              # implementadas
resources/static/{css,vendor}/
resources/db/migration/V1__create_initial_schema.sql  # implementada
```

Services, controllers e formulários do catálogo e da criação de reservas estão implementados. Disponibilidade é uma consulta paginada do catálogo; seu predicado de bloqueio é compartilhado com a confirmação. O Clock da zona de negócio é usado nos cadastros, períodos e auditoria. Não há classes vazias para operações futuras.

O controller recebe formulários, valida entradas e escolhe a view. O service aplica regras, autorização por proprietário e transações. O repository consulta e persiste. Entidades representam o estado persistente; formulários não recebem campos como papel, proprietário ou preço histórico do navegador. Objetos de saída são introduzidos quando necessários para montar views sem consultas fora do service.

Flyway controla o esquema e Hibernate apenas o valida (`ddl-auto=validate`). `open-in-view=false` torna explícito o carregamento dos dados antes da renderização. Não expor entidades diretamente como contratos de entrada. O cadastro de clientes já usa `jakarta.validation` no formulário e no service.

**Por que isso é explicável em uma entrevista:** a arquitetura acompanha a complexidade de uma empresa pequena; mantém regras testáveis em services, usa transações reais para concorrência e renderiza HTML no servidor sem duplicar a aplicação em duas tecnologias.

## 3. Modelo de dados implementado na etapa 1

IDs `Long` gerados no banco. Nomes de tabelas em inglês, evitando `user` como nome SQL reservado: usar `app_user`. Enums persistidos como texto (`EnumType.STRING`), com restrições SQL coerentes. Datas do aluguel como `LocalDate`/`date`; instantes de auditoria como `Instant`/`timestamptz`.

| Entidade | Campos principais e restrições |
| --- | --- |
| `User` | id, name, email normalizado e único, passwordHash, role (`ADMIN`, `CLIENTE`), active, createdAt. Cliente é um usuário com papel CLIENTE; não duplicar perfil em outra tabela sem necessidade. |
| `Equipment` | id, assetCode único e imutável, name, description, category, dailyRate (`numeric(12,2)` positivo), condition (`OPERATIONAL`, `MAINTENANCE`, `RETIRED`), createdAt, updatedAt. Cada linha é uma unidade física. |
| `Reservation` | id, customerId, equipmentId, startDate, endDate, status, dailyRateSnapshot (`numeric(12,2)`), estimatedAmount (`numeric(14,2)`), createdAt, pickedUpAt opcional, returnedAt opcional, cancelledAt opcional. |
| `ReservationEvent` | id, reservationId, actorId, previousStatus opcional apenas na criação, newStatus, occurredAt, note opcional (motivo obrigatório no cancelamento administrativo). Histórico imutável de transições. |

Categorias iniciais propostas: `NOTEBOOK`, `CAMERA`, `TOOL`, `OTHER`, exibidas em português. Enum basta para o escopo inicial; cadastro dinâmico de categorias pode surgir se houver necessidade real.

```mermaid
erDiagram
    APP_USER ||--o{ RESERVATION : realiza
    EQUIPMENT ||--o{ RESERVATION : recebe
    RESERVATION ||--|{ RESERVATION_EVENT : registra
    APP_USER ||--o{ RESERVATION_EVENT : executa
    APP_USER {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash
        varchar role
        boolean active
        timestamptz created_at
    }
    EQUIPMENT {
        bigint id PK
        varchar asset_code UK
        varchar name
        varchar description
        varchar category
        numeric daily_rate
        varchar condition
        timestamptz created_at
        timestamptz updated_at
    }
    RESERVATION {
        bigint id PK
        bigint customer_id FK
        bigint equipment_id FK
        date start_date
        date end_date
        varchar status
        numeric daily_rate_snapshot
        numeric estimated_amount
        timestamptz created_at
        timestamptz picked_up_at
        timestamptz returned_at
        timestamptz cancelled_at
    }
    RESERVATION_EVENT {
        bigint id PK
        bigint reservation_id FK
        bigint actor_id FK
        varchar previous_status
        varchar new_status
        timestamptz occurred_at
        varchar note
    }
```

Chaves estrangeiras obrigatórias e com `ON DELETE RESTRICT`, sem cascatas JPA. `customerId` deve apontar para um CLIENTE; o service de reserva valida essa regra e obtém o proprietário da sessão. Já existem índices por equipamento/status/período, cliente/data de criação, evento/reserva/instante e autor do evento. Validar planos e ajustar índices conforme as consultas de negócio evoluírem, sem antecipar otimizações.

Restrições de banco: fim >= início, diária e total positivos, valores de enums válidos e timestamps compatíveis com o estado. Preço e total são snapshots imutáveis; alteração do catálogo não reescreve reservas. O código patrimonial permanece estável para identificar o item mesmo se nome ou descrição mudarem. O histórico não pretende guardar cada edição de perfil ou descrição; essa auditoria ampliada é evolução futura.

### Decisões consolidadas na modelagem

| Campo | Limite e comportamento |
| --- | --- |
| Nome de usuário/equipamento | 120 caracteres; obrigatório e não vazio |
| E-mail | 254 caracteres; normalizado com `strip()` e `toLowerCase(Locale.ROOT)`; banco exige forma normalizada e unicidade |
| Hash da senha | 255 caracteres; entidade recebe hash pronto; codificação PBKDF2 e login implementados na etapa 2 |
| Código patrimonial | 50 caracteres; sem espaços nas extremidades, único e sensível a maiúsculas/minúsculas |
| Descrição de equipamento | 2.000 caracteres; obrigatória e não vazia |
| Nota do evento | 1.000 caracteres; opcional |
| Papel, categoria, condição e status | `varchar(20)` com enum Java e `CHECK` no banco |

Construtores JPA protegidos e construtores explícitos para criação; sem setters genéricos. Relações `ManyToOne` usam `LAZY`, sem listas bidirecionais desnecessárias. O chamador informa os instantes; na etapa de negócio eles virão de um `Clock` injetado. Código patrimonial, datas e valores contratados não são atualizáveis pelo mapeamento JPA. Eventos usam `@Immutable` e repository sem método de exclusão; isso não impede alterações via SQL administrativo.

O construtor monetário usa escala 2 com `RoundingMode.UNNECESSARY`, rejeitando frações de centavo antes de o PostgreSQL arredondá-las. Positividade e precisão máxima são protegidas no banco. Formulários e services também validam entradas com mensagens amigáveis, incluindo o total calculado na criação da reserva.

A V1 verifica a coerência de cada par de estados em um evento e dos timestamps de cada reserva. Ela ainda não garante a sequência completa entre eventos, um evento para toda alteração, o perfil do autor, disponibilidade ou ausência de reservas sobrepostas. Os testes de persistência não substituem os futuros testes de service, autorização e concorrência. Os SQLs que simulam alterações nos testes servem apenas para validar o modelo.

## 4. Regras de negócio e premissas da base existente

### Período, diárias e dinheiro

Adotar dias civis **inclusivos** na zona `America/Sao_Paulo`: início e fim pertencem ao aluguel. `days = ChronoUnit.DAYS.between(startDate, endDate) + 1`. Uma reserva em 10/11/2026 até 10/11/2026 custa uma diária; até 12/11 custa três. A devolução pode acontecer até o fim do último dia. Reservas contíguas sem conflito começam no dia seguinte ao fim da anterior.

Na criação: início >= hoje, fim >= início e limite implementado de 365 diárias. Usar `Clock` injetável para não depender do relógio do computador nos testes. Limites e mensagens são validados no servidor, mesmo que o HTML também limite os campos. Datas aceitam anos até 9999, compatíveis com o formulário; totais acima de R$ 999.999.999.999,99 são recusados antes da gravação para respeitar `numeric(14,2)`.

Dinheiro em reais com `BigDecimal`, criado a partir de texto, nunca de `double`. A diária aceita no máximo duas casas e valor positivo; rejeitar frações adicionais em vez de arredondar silenciosamente. Copiar a diária do equipamento dentro da transação e calcular `dailyRateSnapshot.multiply(BigDecimal.valueOf(days))`, em escala 2. Exemplo: R$ 49,90 × 3 = R$ 149,70. O total é uma estimativa contratada; devolução antecipada ou tardia não recalcula automaticamente e não gera multa nesta versão.

### Disponibilidade versus condição física

`OPERATIONAL` indica condição física apta ao uso. Não significa livre em todos os períodos. Disponibilidade é uma **consulta** que combina condição física, reservas que bloqueiam o intervalo e pendências de devolução. Não persistir um booleano `available` nem marcar a condição como “reservado”.

Uma reserva `RESERVED` ou `IN_USE` conflita quando `existing.startDate <= requested.endDate && existing.endDate >= requested.startDate`. `CANCELLED` e `RETURNED` não bloqueiam novas reservas. Após devolução antecipada e conferência pelo ADMIN, a unidade pode ser reservada novamente, inclusive no mesmo dia; cada contrato mantém sua própria estimativa de diárias.

Uma unidade `MAINTENANCE` ou `RETIRED` não aceita novas reservas nem retirada. Manutenção não tem previsão de término na primeira versão, portanto bloqueia qualquer período novo. Uma reserva futura não muda automaticamente a condição física.

Se houver aluguel `IN_USE` vencido, bloquear novas reservas de qualquer período até a devolução: não há previsão confiável de liberação. Reservas futuras já existentes continuam registradas e são sinalizadas ao administrador; retirada é impedida enquanto a unidade estiver com outro cliente. Não cancelar compromissos silenciosamente.

Mudança para manutenção não é permitida durante uso; o administrador registra a devolução e pode marcar manutenção na mesma transação. Com reservas futuras, permitir manutenção e destacá-las como afetadas para cancelamento manual com motivo. Aposentadoria (`RETIRED`) exige ausência de reservas `RESERVED`/`IN_USE`. Essas regras precisam usar o mesmo bloqueio transacional da reserva.

### Concorrência: impedir a dupla reserva

Uma consulta prévia na interface não garante que o item continuará disponível. A operação de criação deverá:

1. Abrir uma transação `@Transactional` em isolamento `READ_COMMITTED`.
2. Bloquear primeiro a linha do cliente e revalidar que está ativo; depois buscar `Equipment` por ID com `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`). A desativação já bloqueia o cliente: compartilhar esse bloqueio impedirá que uma reserva nasça durante a desativação. Bloquear o equipamento funciona mesmo quando ainda não existe nenhuma reserva.
3. Depois de adquirir os bloqueios, revalidar condição, período, atraso pendente e sobreposição em uma nova consulta.
4. Ler a diária protegida, inserir reserva e evento de criação na mesma transação e confirmar.

A segunda solicitação para a mesma unidade espera; após a primeira confirmar, enxerga a reserva e recebe uma mensagem de indisponibilidade. Equipamentos diferentes não se bloqueiam mutuamente. Não usar `synchronized`, pois só protegeria uma instância Java. Bloquear apenas reservas existentes também não resolve o caso de conjunto vazio.

Quando uma operação precisar bloquear cliente e equipamento, a ordem será cliente → equipamento → reserva. Desativação bloqueia somente cliente. Alterações de condição/preço e operações que não precisem bloquear cliente começam pelo equipamento e depois bloqueiam/recarregam a reserva, sem adquirir bloqueio de cliente posteriormente. Timeout/deadlock devem resultar em mensagem para tentar novamente, sem sucesso parcial. A tela HTML deve reapresentar o formulário com erro; se houver endpoint JSON no futuro, conflito pode usar HTTP 409.

O protocolo depende de todas as escritas de negócio passarem pelos services; scripts externos não podem criar reservas diretamente. Uma restrição de exclusão PostgreSQL é uma defesa adicional possível, mas não será adicionada sem necessidade na primeira versão. A decisão inicial privilegia bloqueio explícito, fácil de explicar e testar, com documentação desse limite.

### Autorização, manutenção de cadastros e histórico

- CLIENTE consulta, cria e cancela somente suas reservas. O proprietário vem da sessão, nunca de um ID de cliente enviado no formulário. Buscar detalhes por ID **e** proprietário; retornar 404 para uma reserva alheia evita revelar sua existência.
- ADMIN cadastra/edita equipamentos e clientes, controla manutenção, consulta todas as reservas, cancela com motivo e registra retirada/devolução. A primeira versão não permite que CLIENTE escolha seu próprio papel.
- Autenticação por formulário e sessão do Spring Security, senha codificada com `PasswordEncoder`, CSRF habilitado e logout via POST. Nenhuma operação de alteração em GET. Não é necessário JWT para um monólito Thymeleaf.
- Cadastro de clientes inicialmente feito pelo ADMIN. Definir criação segura do primeiro ADMIN na etapa de autenticação, por configuração de bootstrap local, sem senha fixa no repositório.
- Bloquear ações de contas inativas também no service, inclusive sessões já abertas. Desativação de cliente exige resolver reservas pendentes; preservar acesso administrativo ao histórico.
- Sem exclusão física de usuários/equipamentos com reservas. Preferir desativação do usuário e `RETIRED` para equipamento. Não apagar reservas nem eventos.
- Autorização deve existir no servidor; ocultar botões é apenas uma conveniência visual. Evitar colocar dados pessoais, senhas ou formulários completos nos logs.

## 5. Estados da reserva na base existente

Enums em inglês e rótulos na interface em português:

| Estado | Rótulo | Transições permitidas |
| --- | --- | --- |
| `RESERVED` | Reservada | `IN_USE` por ADMIN; `CANCELLED` pelo proprietário ou ADMIN |
| `IN_USE` | Em uso | `RETURNED` por ADMIN |
| `RETURNED` | Devolvida | Nenhuma |
| `CANCELLED` | Cancelada | Nenhuma |

```mermaid
stateDiagram-v2
    [*] --> RESERVED: cliente cria
    RESERVED --> IN_USE: administrador registra retirada
    RESERVED --> CANCELLED: cliente titular ou administrador cancela
    IN_USE --> RETURNED: administrador registra devolução
    RETURNED --> [*]
    CANCELLED --> [*]
```

Retirada requer dia atual dentro do período contratado, equipamento operacional e nenhuma outra reserva em uso. Não permitir retirada antecipada; uma reserva não retirada até o fim permanece `RESERVED` para regularização/cancelamento manual, sem gerar falso atraso de devolução.

Cancelamento é permitido enquanto `RESERVED`, inclusive após o começo previsto se ainda não houve retirada. `IN_USE` não pode ser cancelada. Repetir retirada/devolução/cancelamento de um registro já finalizado deve informar estado inválido sem criar eventos duplicados. Cada transição e seu evento são atômicos.

**Atraso não é um estado persistido.** Enquanto em uso, está atrasada se `LocalDate.now(clock) > endDate`. Depois da devolução, comparar a data de `returnedAt` na zona de negócio com `endDate` preserva a indicação histórica. Não usar job só para mudar status; a condição pode ser calculada na consulta. Testar a fronteira entre 23:59 do último dia e 00:00 do próximo.

## 6. Etapas de implementação da base existente

Cada etapa termina com código, testes e README atualizados. Os critérios abaixo são gates: não marcar concluída só porque a tela aparece. A sequência evita implementar operações de negócio antes de haver modelagem e autorização.

### Etapa 0 — Base inicial

- [x] Inspecionar pasta e instruções existentes.
- [x] Fixar Java, Spring Boot e Maven Wrapper.
- [x] Criar aplicação e página inicial em português.
- [x] Configurar PostgreSQL, Compose, volume e variáveis de ambiente.
- [x] Preparar Flyway sem entidades nem migrações de domínio.
- [x] Criar teste MVC e documentação de uso e planejamento.
- [x] Compilar, executar teste e empacotar pelo Wrapper.
- [x] Validar inicialização completa contra PostgreSQL (confirmada pelo usuário e pelos testes da etapa 1).
- [ ] Validar execução em JDK 21.

**Conclusão:** build, teste MVC e inicialização completa com PostgreSQL aprovados. Docker agora está disponível. A máquina consultada continua usando JDK 25 com compilação `release 21`; execução efetiva em JDK 21 permanece pendente.

**Testes:** HTTP 200, template real resolvido, idioma, conteúdo de apresentação e URLs CSS. Na etapa 1, a resposta HTTP também foi validada com servidor real e PostgreSQL.

**Aprendizado:** ciclo Maven, auto-configuração, MVC, configuração externa e responsabilidades do Flyway.

### Etapa 1 — Modelo e migração (depende da 0)

- [x] Revisar premissas e definir tamanhos máximos dos campos.
- [x] Criar entidades e enums sem telas de negócio.
- [x] Escrever `V1__create_initial_schema.sql` com chaves, checks e índices iniciais.
- [x] Criar repositories necessários e mapeamentos explícitos.
- [x] Adicionar testes Spring Data JPA com PostgreSQL Testcontainers e suporte Flyway.
- [x] Verificar banco vazio e segunda inicialização sem mudanças indevidas.

**Conclusão:** migração sobe do zero, Hibernate valida o esquema e relações persistem sem apagar histórico. Nenhum `ddl-auto=update` ou migração já aplicada editada.

**Testes:** unicidade de e-mail normalizado e código patrimonial; FK obrigatória; rejeição de período invertido/preços inválidos; valores `BigDecimal` preservados e rollback. Não substituir PostgreSQL por H2 nos testes de integração.

**Aprendizado:** cardinalidade, restrições, migrações e precisão monetária.

**Resultado em 27/10/2026:** `clean verify` aprovado com 2 testes unitário/MVC e 75 casos de integração, zero falhas/erros/ignorados. O teste de integração sobe a aplicação completa, valida o esquema com Hibernate, consulta os repositories e reinicia o contexto sobre o mesmo banco. Usa PostgreSQL 17 temporário, sem ler o `.env` ou modificar o volume de desenvolvimento.

### Etapa 2 — Autenticação, perfis e clientes (depende da 1)

- [x] Adicionar Spring Security, seu suporte de teste e Bean Validation.
- [x] Implementar login/logout com sessão, CSRF e mensagens em português.
- [x] Criar primeiro ADMIN por configuração local sem segredo versionado.
- [x] Criar formulário de cadastro de cliente pelo ADMIN, validação e hash de senha.
- [x] Proteger rotas por papel e criar testes da matriz de acesso.
- [x] Implementar desativação com validação de pendências.

**Conclusão:** login válido funciona; senha incorreta falha; banco só guarda hash; CLIENTE não administra e conta inativa não opera. A criação de administrador não depende de senha padrão pública.

**Testes:** anônimo redirecionado ao login, ADMIN autorizado, CLIENTE recebe 403 nas ações administrativas, CSRF ausente rejeitado, e-mail duplicado rejeitado e papel enviado pelo navegador ignorado/rejeitado. Testar senha e usuário inativo.

**Aprendizado:** diferença entre autenticação, papel e propriedade dos dados; proteção de formulários e armazenamento de senhas.

**Decisões implementadas:** login por e-mail normalizado e sessão; CSRF habilitado; logout/desativação por POST. `CurrentUser` revalida papel e atividade no banco dentro dos services. `ActiveUserFilter` invalida a sessão de conta desativada na próxima requisição. Os formulários só recebem nome, e-mail e senha; clientes são sempre criados com papel CLIENTE. Listagem de 20 clientes por página, com `UserSummary` sem hash.

Senhas de 12 a 64 caracteres usam PBKDF2 com salt aleatório e prefixo versionado `{pbkdf2@SpringSecurity_v5_8}`. Não há senha padrão, cadastro público, recuperação/troca de senha ou reativação nesta entrega. O bootstrap opcional recebe `BOOTSTRAP_ADMIN_*`, cria apenas o primeiro administrador e não promove clientes nem troca senhas existentes. Sua transação usa bloqueio de tabela para serializar inicializações simultâneas; desativação usa bloqueio de linha e impede clientes com reservas pendentes.

**Resultado em 27/10/2026:** `verify` aprovado com 109 testes (2 unitário/MVC, 75 de persistência e 32 de segurança), sem falhas/erros/ignorados. A V1 não foi alterada; não há nova migração nesta etapa. O banco de desenvolvimento não foi usado nos testes. Ver instruções de bootstrap e roteiro manual no README.

### Etapa 3 — Catálogo e condição física (depende da 2)

- [x] Criar formulário e service de equipamento com código único, categoria e diária.
- [x] Criar listagem, detalhes e edição para ADMIN.
- [x] Exibir catálogo consultável por CLIENTE.
- [x] Implementar manutenção, retorno a operacional e aposentadoria.
- [x] Adicionar busca por nome/código, filtro por categoria/condição e paginação.

**Conclusão:** ADMIN mantém unidades individuais; cliente consulta; dados inválidos não persistem. Paginação padrão de 20, limitada entre 1 e 100, ordenação estável por ID como desempate e campos de ordenação permitidos explicitamente.

**Testes:** códigos duplicados, diária negativa/zero/mais de duas casas, campos vazios, permissões, filtros combinados, página vazia e limite de paginação. Testar manutenção e preservação do histórico.

**Aprendizado:** validação em camadas, consultas paginadas e distinção entre formulário e entidade.

**Decisões implementadas:** código patrimonial imutável, único e sensível a maiúsculas/minúsculas; busca parcial sem distinguir caixa, com curingas do SQL tratados literalmente. Rótulos em português e valores em reais. Cadastro sempre operacional, independente de campos extras enviados pelo navegador. Alterações de dados/preço e condição bloqueiam a linha do equipamento; preço histórico das reservas permanece intacto. Services revalidam papel e atividade no banco.

Manutenção é proibida com `IN_USE`; com `RESERVED`, as reservas permanecem e aparecem paginadas apenas para ADMIN. Aposentadoria exige ausência de ambas as pendências, é definitiva e preserva histórico. CLIENTE não vê aposentados, inclusive por ID direto (404); ADMIN pode consultá-los e editar seus dados descritivos. Não há exclusão física. O tratamento de falha transitória nesta etapa apresenta página 503 para nova tentativa. Reservar/cancelar e a devolução com manutenção na mesma transação continuam nas etapas seguintes.

**Resultado em 27/10/2026:** `clean verify` aprovado com 149 testes (2 unitário/MVC, 75 de persistência, 32 de segurança e 40 do catálogo), sem falhas/erros/ignorados. Inclui templates reais e concorrência: aposentadoria espera pelo bloqueio da unidade e revalida uma reserva confirmada pela outra transação. PostgreSQL 17 descartável, sem alterar V1 ou banco de desenvolvimento. JDK 21 efetivo e revisão visual manual ainda pendentes; execução realizada com JDK 25 e `release 21`.

### Etapa 4 — Disponibilidade, estimativa e criação (depende da 3)

- [x] Usar o `Clock` já disponível e implementar o cálculo de diárias inclusivas.
- [x] Criar busca de equipamentos disponíveis por período.
- [x] Implementar estimativa no servidor e snapshot de diária na reserva.
- [x] Implementar transação com bloqueio pessimista do equipamento.
- [x] Registrar criação e evento atomicamente, associando o cliente autenticado.
- [x] Exibir confirmação e mensagem amigável em conflito.

**Conclusão:** duas tentativas simultâneas sobrepostas para a mesma unidade geram exatamente uma reserva; a outra falha por indisponibilidade. A estimativa e o proprietário não podem ser adulterados pelo formulário. A consulta de disponibilidade e a criação usam as mesmas regras.

**Testes obrigatórios:**

| Cenário | Resultado esperado |
| --- | --- |
| Mesmas datas, intervalo contido e intervalo que contém outro | Conflito |
| Fim de uma reserva igual ao início de outra | Conflito: dias inclusivos |
| Começo no dia seguinte ao término | Permitido |
| Mesmas datas em equipamentos diferentes | Permitido |
| Equipamento em manutenção/aposentado ou aluguel vencido em uso | Bloqueado |
| Período passado, invertido ou maior que o limite | Rejeitado |
| Um dia / três dias / mudança de mês e ano / ano bissexto | Quantidade correta |
| R$ 49,90 × 3; diária editada depois da reserva | R$ 149,70; snapshot preservado |
| Falha ao inserir evento | Reserva também revertida |
| Dois clientes concorrentes sobre o mesmo equipamento | Uma confirmação e um conflito |

O teste concorrente usa duas threads e duas transações reais, conexões distintas e PostgreSQL Testcontainers. Preparar os dados com commit antes das threads, coordenar o início com barreira/latch e limitar esperas com timeout. Não usar um único teste `@Transactional` que esconda commits; não validar concorrência com mocks. Conferir reservas **e** eventos após ambas as operações. Testar também reservas não conflitantes simultâneas, criação concorrente com entrada em manutenção e criação concorrente com desativação do cliente.

**Aprendizado:** condições de corrida, isolamento, bloqueios, atomicidade e cálculos determinísticos.

**Decisões implementadas:** datas opcionais em conjunto na busca do catálogo e obrigatórias na reserva; a mesma expressão de reservas bloqueantes é usada nas duas consultas. Estimativa GET não grava nem segura a unidade. Confirmação POST usa o preço vigente sob bloqueio e gera reserva/evento na mesma transação `READ_COMMITTED`; o usuário é recarregado após o bloqueio para enxergar desativação concorrente. Formulário aceita somente equipamento e datas. A URL de confirmação pertence ao CLIENTE titular; outro cliente recebe 404. ADMIN consulta disponibilidade, mas não cria reservas em nome de clientes nesta versão.

Valores da estimativa podem mudar antes da confirmação; a interface informa a revalidação. Depois da criação, o snapshot não muda. Conflitos e timeout de bloqueio reapresentam as datas com mensagem amigável. O teste de timeout configura limite menor exclusivamente no PostgreSQL descartável. A V1 permanece intacta. Listagem de reservas e cancelamento não foram antecipados.

**Resultado em 27/10/2026:** `clean verify` aprovado com 190 testes (14 unitário/MVC, 75 de persistência, 32 de segurança, 40 de catálogo e 29 de reservas), sem falhas/erros/ignorados. Os cenários obrigatórios acima foram cobertos, incluindo transações concorrentes e rollback de reserva/evento. Testes com PostgreSQL 17 descartável e relógio controlado, sem tocar no banco de desenvolvimento. Execução em JDK 25 com `release 21`; JDK 21 efetivo e revisão visual manual seguem pendentes.

### Etapa 5 — Minhas reservas e cancelamento (depende da 4)

- [x] Criar listagem e detalhes do cliente com busca/filtros e paginação.
- [x] Criar visão administrativa de todas as reservas.
- [x] Implementar cancelamento por POST, motivo administrativo e evento.
- [x] Impedir alteração de proprietário, período e snapshot após criação.
- [x] Testar tentativa de acesso por IDs de outro cliente.

**Conclusão:** cliente A não lê nem cancela reservas de B, mesmo alterando a URL ou o corpo. Cancelamento libera o período e preserva os dados e eventos.

**Testes:** matriz anônimo/CLIENTE titular/CLIENTE alheio/ADMIN em listagem, detalhes e cancelamento; CSRF; cancelamento duplicado; cancelamento de registro em uso; filtros sem vazamento de dados; duas tentativas de cancelamento produzem um único evento.

**Aprendizado:** autorização por registro, prevenção de acesso indevido por ID e histórico.

**Decisões implementadas:** rotas CLIENTE em `/reservas` e ADMIN em `/admin/reservas`. Busca por equipamento, status e período para ambos; busca por nome/e-mail do cliente somente na área administrativa. Datas opcionais e independentes filtram sobreposição inclusiva, inclusive histórica. Paginação padrão 20, máximo 100 e ID decrescente como desempate. Consulta com relações carregadas evita N+1; DTOs não incluem hashes.

Cancelamento aceita somente motivo (até 1.000 caracteres), obrigatório para ADMIN e opcional para CLIENTE. Services revalidam papel/atividade e propriedade. Equipamento é bloqueado antes da reserva, cujo estado é recarregado após a espera; cancelamento/evento são atômicos. Somente `RESERVED` pode ser cancelada, mesmo após o período previsto se não houve retirada. Repetição não cria evento. Detalhes mostram autor, motivo e horário em São Paulo; a confirmação antiga passa a refletir o estado atual. Histórico continua acessível ao ADMIN para clientes inativos/equipamentos aposentados. Nenhuma alteração na V1.

**Resultado em 27/10/2026:** `clean verify` aprovado com 226 testes (14 unitário/MVC e 212 de integração), sem falhas/erros/ignorados. Os 36 novos testes cobrem autorização, filtros, preservação de valores, motivos, cancelamentos concorrentes, corrida com nova reserva, rollback de evento e timeout. Lista de 20 reservas com equipamentos distintos validada com até três comandos SQL. PostgreSQL 17 descartável, sem usar banco de desenvolvimento. JDK 25 com `release 21`; JDK 21 efetivo e revisão visual manual ainda pendentes.

### Etapa 6 — Retirada, devolução e atrasos (depende da 5)

- [x] Implementar transições apenas por ADMIN e validar estado atual sob bloqueio.
- [x] Registrar instantes e eventos de retirada/devolução.
- [x] Permitir condição de manutenção ao devolver, na mesma transação.
- [x] Exibir atrasos atuais, devoluções tardias e reservas futuras afetadas.
- [x] Exibir linha do tempo completa, integrada aos filtros de status/cliente/equipamento/período já implementados na etapa 5.

**Conclusão:** somente a máquina de estados definida é aceita. Nunca existem duas retiradas abertas para a mesma unidade. Atrasos usam a zona de negócio e continuam identificáveis após a devolução. Não há multa nem alteração automática do preço histórico.

**Testes:** cada transição válida e inválida; CLIENTE não retira/devolve; retirada antecipada ou após prazo rejeitada; cancelamento contra retirada concorrente; retirada/devolução duplicada; manutenção bloqueia retirada; falha de evento reverte status; limites de meia-noite com Clock fixo; devolução antecipada, pontual e tardia.

**Aprendizado:** máquinas de estados, consistência transacional, tempo de negócio e auditoria.

**Decisões implementadas:** POST `/admin/reservas/{id}/retirar` e `/devolver`, com CSRF e papel/atividade revalidados no service. Retirada apenas de `RESERVED`, dentro do período inclusivo, com unidade operacional e sem outro `IN_USE`. Devolução apenas de `IN_USE`; formulário aceita somente opção de manutenção. Bloqueio equipamento → reserva, recarga após espera, evento/instantes/condição física atômicos. Repetições e transições inválidas não criam eventos. Preço histórico não muda em devolução antecipada ou tardia.

Atraso atual calculado por data civil de São Paulo; devolução tardia usa a data local do instante registrado, preservando a indicação histórica. Listas ganham filtro de atrasos em aberto sem perder escopo do cliente ou paginação. Detalhes mostram linha do tempo com autores carregados junto dos eventos e desempate por ID. Evento de devolução guarda a decisão operacional/manutenção. Equipamentos em manutenção ou com `IN_USE` atrasado exibem ao ADMIN as reservas `RESERVED` com fim a partir de hoje, paginadas; compromissos permanecem até cancelamento manual. V1 intacta.

**Resultado em 27/10/2026:** `clean verify` aprovado com 267 testes (14 unitário/MVC e 253 de integração), sem falhas/erros/ignorados. Os 41 novos testes cobrem o fluxo completo, limites de meia-noite/UTC, autorização, atrasos, manutenção, reservas afetadas, rollback e concorrência entre as operações. Detalhes com três eventos validados em até cinco comandos SQL. PostgreSQL 17 descartável; banco de desenvolvimento preservado. Execução em JDK 25 com `release 21`; JDK 21 efetivo e revisão visual manual seguem pendentes para a etapa 7.

### Etapa 7 — Acabamento e fluxo completo (depende da 6)

- [x] Padronizar navegação, layout, formulários e mensagens de erro.
- [x] Revisar telas vazias, filtros persistidos na paginação e confirmações.
- [x] Revisar valores em reais, datas brasileiras, labels, foco e navegação por teclado.
- [x] Testar desktop e largura de 360 px sem rolagem horizontal indevida.
- [x] Executar fluxo completo com contas separadas de ADMIN e CLIENTE.
- [x] Configurar CI com JDK 21, testes unitários/MVC e integração PostgreSQL.

**Conclusão:** fluxo cadastro → consulta → reserva → retirada → devolução funciona e o histórico é coerente. Suite completa passa em JDK 21; nenhum endpoint administrativo depende só de botões ocultos.

**Testes:** roteiro ponta a ponta, erros 403/404, validações preservando dados digitados, ausência de recursos externos obrigatórios e revisão de consultas para evitar N+1 nas listagens.

**Aprendizado:** integração, experiência do usuário, acessibilidade básica e automação de qualidade.

**Decisões implementadas:** navegação indica a seção atual com `aria-current`; destino do link de salto e alertas recebem foco nativo, sem JavaScript. Labels de checkboxes usam o ID gerado pelo Thymeleaf. Tabelas preservam semântica e cabeçalhos, com região nomeada/focável para rolagem por teclado e gesto; nomes e códigos longos não alargam a página. Cores secundárias e botões foram ajustados após detectar contraste insuficiente sobre o fundo. Erros compartilham navegação/rodapé e há fallback amigável. Cancelamento fica em confirmação expansível, aberta após validação inválida. Lista vazia de clientes permite recuperar a primeira página.

**Revisão de interface em 28/10/2026:** Chrome headless com Playwright e axe em banco PostgreSQL 17 descartável, com ADMIN e dois CLIENTEs em sessões separadas. Foram 52 verificações (26 estados de tela × 1366/360 px), sem overflow da página e sem violações detectadas nas regras WCAG 2 A/AA e 2.1 A/AA selecionadas. Conferidos fluxo completo, manutenção, cancelamento por teclado, datas/moeda, paginação preservando filtros, telas vazias, erros 400/403/404, foco e preservação de dados após e-mail duplicado, histórico privado, valores/textos máximos e logout. Recursos externos obrigatórios: zero. Capturas inspecionadas para layout, formulário com erro e linha do tempo; ferramentas e evidências ficaram em `.local`, fora do Git. Instância/banco temporários encerrados; dados de desenvolvimento preservados. Outros navegadores e leitores de tela não foram exercitados.

**CI:** workflow com ações fixadas por SHA, cache Maven, `clean verify`, limite de 20 minutos e relatórios de testes por sete dias. Passou no actionlint 1.7.12. Usa Temurin 21 e PostgreSQL 17 via Testcontainers, sem `.env` ou segredos do ambiente local. Execução no GitHub Actions fica pendente do primeiro push desta etapa.

**Resultado em 28/10/2026:** `clean verify` aprovado com **269 testes** (14 unitário/MVC e 255 de integração), zero falhas, erros ou ignorados. Execução efetiva em **Temurin 21.0.12.1**, baixado do Adoptium e verificado por SHA-256, extraído apenas em `.local` sem instalação global. Dois testes adicionados cobrem navegação por perfil, recuperação de página vazia e marcação de foco/validação com preservação de campos seguros. Suite existente voltou a validar segurança, domínio, rollback, concorrência e limites de consultas. V1 intacta. A pendência histórica de execução em JDK 21 das etapas anteriores está resolvida; o CI remoto e testes com outros navegadores/leitores de tela permanecem sem execução nesta entrega.

### Etapa 8 — Preparação do portfólio (depende da 7)

- [x] Criar dados fictícios opcionais em perfil de demonstração, sem dados pessoais reais.
- [x] Produzir capturas de catálogo, reserva, administração e histórico em desktop/mobile.
- [x] Gravar demonstração curta do fluxo e de um conflito de reservas.
- [x] Atualizar README com funcionalidades reais, imagens, arquitetura e limitações.
- [x] Documentar como executar a suite e reproduzir o teste concorrente.
- [x] Preparar explicação de 3–5 minutos sobre escolhas, tradeoffs e melhorias futuras.

**Conclusão:** outra pessoa consegue executar seguindo apenas o README e reproduzir a demonstração. A documentação diferencia decisões implementadas de evoluções futuras. Qualquer hospedagem/publicação exige uma tarefa posterior específica.

**Testes:** instalação limpa, migrações do zero, dados de demo repetíveis, comandos conferidos e ausência de segredos em arquivos/imagens. Executar toda a suite antes de registrar a demonstração.

**Aprendizado:** comunicação técnica, demonstração de evidências e apresentação do projeto em entrevistas.

**Decisões implementadas:** perfil `demo` usa PostgreSQL `equiprent_demo`, Compose/volume próprios, portas 8081/5440 e cookie distinto, com bind local. `Start-Demo.ps1` gera credenciais em `.local/demo.env`, preserva esse arquivo nas próximas execuções e restaura o ambiente do PowerShell. O bootstrap normal fica fora do perfil. A carga exige banco dedicado e vazio, valida a senha fornecida e grava 3 contas, 7 unidades, 7 reservas e 12 eventos. Advisory lock serializa a carga; marcador e fixtures são atômicos. Reiniciar não duplica nem redefine datas, valores ou ações. Uma migração repetível exclusiva de `db/demo` cria o marcador; a V1 continua intacta e o perfil normal não aplica esse arquivo.

**Material entregue:** README reorganizado com imagens, arquitetura, limitações e comandos; guias em `docs/DEMONSTRACAO.md`, `docs/DESENVOLVIMENTO.md`, `docs/VALIDACOES.md` e `docs/APRESENTACAO.md`. Oito PNGs (desktop/360 px), WebM de aproximadamente um minuto e legendas WebVTT em português. O vídeo registra estimativas concorrentes em sessões separadas, recusa da confirmação desatualizada, retirada e devolução. O teste simultâneo de duas transações é explicado separadamente, sem confundir vídeo com prova de corrida de threads.

**Validação da entrega, consolidada em 29/10/2026:** `clean verify` com **276 testes** (14 unitário/MVC e 262 de integração), zero falhas/erros/ignorados, em Temurin 21.0.12.1. Sete testes novos cobrem consistência da carga, idempotência/preservação, inicialização concorrente, banco não vazio, senha ausente, rollback e nome de banco incorreto. Script executado com volume novo, reinício real preservando a reserva gravada no vídeo, execução fora da raiz, credenciais preservadas e variáveis restauradas. Capturas auditadas com axe e inspecionadas; vídeo reproduzido no Chrome, com quadros e legendas conferidos. Dados normais preservados. CI remoto, outros navegadores, leitores de tela e hospedagem não foram validados nesta entrega.

## 7. Marketplace — fluxo e modelo planejados

**Situação:** fluxo aprovado em 01/11/2026. M1 implementa empresas e permissões; jornada pública, novos estados, propostas e chat continuam planejados. Company é uma entidade JPA; os vínculos administrativos e comerciais são tabelas acessadas por JDBC. Nomes de entidades e estados das etapas seguintes ainda são propostas.

### 7.1. Jornada do cliente e da empresa

1. Visitante pesquisa equipamento e período sem precisar entrar na conta. O catálogo mostra empresa, equipamento, diária e total estimado para os dias inclusivos; cidade/UF é uma proposta de filtro a validar antes da etapa M2.
2. Cliente entra na própria conta e envia uma solicitação para uma unidade física e datas específicas. A tela deixa explícito que o envio ainda não garante o aluguel.
3. Solicitação fica como **Aguardando aprovação**. Cliente e administradores autorizados daquela empresa podem consultar os detalhes e conversar no chat vinculado a ela.
4. Empresa e cliente negociam. Uma proposta estruturada registra diária, total e condições combinadas. Se o preço ou as condições mudarem, o cliente deve aceitar a versão correspondente antes da confirmação; uma mensagem livre no chat não substitui esse aceite registrado.
5. Administrador da empresa aprova. O servidor revalida autorização, atividade das contas/empresa, período, equipamento, disponibilidade e proposta aceita. Somente após a transação terminar a interface informa **Reserva confirmada**.
6. Empresa registra retirada e devolução. Cliente acompanha a própria reserva, os termos aceitos e o histórico. Recusa e cancelamento mantêm os registros e a conversa.

```mermaid
flowchart TD
    A[Busca pública por equipamento e período] --> B[Comparação entre empresas]
    B --> C[Cliente entra e solicita]
    C --> D[Aguardando aprovação]
    D --> E[Conversa e negociação]
    E --> F[Condições aceitas pelo cliente]
    F --> G[Empresa tenta aprovar]
    G --> H{Disponibilidade revalidada?}
    H -->|Sim| I[Reserva confirmada]
    H -->|Não| J[Informar conflito e manter sem confirmação]
    I --> K[Retirada]
    K --> L[Devolução e histórico]
```

### 7.2. Empresas, contas e isolamento dos dados

| Participante | Acesso planejado |
| --- | --- |
| Visitante | Consultar catálogo público e disponibilidade; não acessar reservas, conversas ou dados pessoais de clientes. |
| Cliente | Usar uma conta pessoal para solicitar equipamentos de diferentes empresas; consultar, negociar e cancelar somente as próprias solicitações/reservas, conforme o estado. |
| Administrador de empresa | Usar uma conta individual vinculada à empresa; administrar somente seus equipamentos, solicitações, conversas e operações de retirada/devolução. |
| Operação da plataforma | Necessidade, permissões e forma de cadastrar empresas ainda a definir; não pressupor acesso global a conversas nem disponibilizar um painel adicional neste ciclo. |

O papel administrativo passa a ser associado a um vínculo com uma empresa, em vez de conceder acesso a todos os dados do sistema. Cada administrador terá login próprio para identificar a autoria das ações. Uma empresa não poderá editar a senha nem desativar globalmente a conta de um cliente que também aluga em outras empresas.

O escopo de empresa deve ser validado no servidor em cada consulta e alteração, inclusive detalhes por ID, pesquisas, paginação, contagens e mensagens. Não confiar em `companyId`, autor, perfil ou proprietário recebidos do navegador. Para dados privados de outra empresa/cliente, manter a abordagem de não revelar sua existência. Catálogo público usará uma saída própria, sem dados privados de administração ou clientes.

### 7.3. Modelo conceitual proposto

| Conceito | Responsabilidade e invariantes |
| --- | --- |
| `Company` | Identificar a empresa dona dos equipamentos, seus dados públicos e sua situação de atividade. Campos de cadastro e política de aprovação ainda serão detalhados. |
| `CompanyMembership` | Vincular uma conta individual à empresa e definir permissão/atividade nesse vínculo. A escolha de empresa na interface nunca concede acesso por si só. |
| `Equipment` | Pertencer a uma empresa. Proposta: código patrimonial único por empresa, permitindo que duas empresas tenham `NB-001`; cada equipamento continua sendo uma unidade física. Não transferir unidades com histórico entre empresas neste ciclo. |
| `Reservation` | Reutilizar o registro atual, adicionando a fase de solicitação. Guardar cliente, empresa, equipamento, período e estado coerentes entre si. A empresa deve ser derivada do equipamento e permanecer estável. |
| `ReservationOffer` | Registrar versões de preço/condições e o aceite do cliente. Preservar a estimativa inicial e, na aprovação, fixar a proposta aceita como valor contratado imutável. |
| `ReservationEvent` | Manter autor e instante das transições, incluindo solicitação, aprovação e recusa. Negociação/aceite também precisam de registro próprio, sem inventar mudanças de status para representar mensagens. |
| `ChatMessage` | Vincular cada mensagem a uma solicitação/reserva e ao autor autenticado, com data/hora e ordem estável. A empresa da conversa é sempre a da solicitação. |

Manter o monólito Spring Boot, Thymeleaf e PostgreSQL como base. Introduzir módulos de empresa e conversa conforme a implementação exigir, sem criar microserviços ou trocar o frontend para atender este fluxo. A implementação do chat e seus limites serão escolhidos na etapa M4.

Datas continuam inclusivas em `America/Sao_Paulo`, com `Clock` injetável, limite de 365 diárias e dinheiro em reais com `BigDecimal`. Proposta inicial de negociação: uma diária acordada positiva, total calculado pelo servidor e condições textuais; descontos, taxas ou caução separados exigiriam nova definição. Mudar o preço do catálogo não altera uma proposta já aceita nem um contrato confirmado.

### 7.4. Estados, disponibilidade e aprovação

| Estado proposto | Rótulo | Transições previstas |
| --- | --- | --- |
| `REQUESTED` | Aguardando aprovação | `RESERVED` pela empresa após as revalidações; `REJECTED` pela empresa com motivo; `CANCELLED` pelo cliente titular. |
| `RESERVED` | Reserva confirmada | `IN_USE` pela empresa na retirada; `CANCELLED` pelo cliente ou pela empresa conforme a regra de cancelamento. |
| `IN_USE` | Em uso | `RETURNED` pela empresa. |
| `REJECTED` | Solicitação recusada | Nenhuma; uma nova tentativa gera outra solicitação. |
| `CANCELLED` | Cancelada | Nenhuma. |
| `RETURNED` | Devolvida | Nenhuma. |

Negociação e espera pelo aceite acontecem dentro de `REQUESTED`; o estado da proposta indica se falta concordância do cliente. O cliente aceita as condições, mas a decisão de confirmar continua sendo da empresa. Repetir uma ação não pode gerar duas confirmações ou eventos duplicados.

- **Pendências não bloqueiam:** várias solicitações podem existir para a mesma unidade e datas. Busca, chat e aceite de proposta não seguram o equipamento.
- **Confirmação bloqueia:** `RESERVED` e `IN_USE` continuam bloqueando períodos sobrepostos. Uso vencido, manutenção e aposentadoria mantêm as restrições atuais.
- **Conflito posterior é esperado:** a empresa pode receber solicitações enquanto o item estava disponível e encontrar um conflito ao aprovar. Informar o conflito sem confirmar nem apagar a solicitação. As demais pendências ficam sinalizadas, sem recusa automática neste primeiro fluxo.
- **Revalidação na aprovação:** não aprovar período cujo início já passou, conta/empresa sem permissão ou atividade, unidade indisponível ou proposta sem aceite válido. Prazo de expiração automática de pedidos será decidido separadamente.
- **Preço precisa de aceite:** o envio da solicitação registra concordância com a estimativa/condições apresentadas naquela versão. Qualquer contraproposta da empresa invalida o aceite anterior para fins de aprovação até o cliente aceitar a nova versão. Campos ocultos enviados pelo navegador não são prova de aceite ou preço.
- **Confirmação atômica:** travar os registros necessários, recarregar seu estado, conferir a versão aceita e a disponibilidade, gravar `RESERVED`, valores contratados e evento na mesma transação. Falha em qualquer parte reverte toda a confirmação.

O bloqueio do equipamento continua sendo o ponto de serialização de aprovações conflitantes, inclusive entre instâncias da aplicação. Definir uma ordem única para os bloqueios adicionais de empresa/vínculo/cliente/solicitação antes de implementar M1 e M3; preço, condições, aceite e cancelamento concorrentes não podem escapar da revalidação. Compartilhar o predicado de disponibilidade entre catálogo e aprovação, agora sem contar `REQUESTED` como reserva bloqueante.

O desfecho obrigatório para duas aprovações concorrentes sobrepostas é **uma confirmação e um conflito**, com somente um período bloqueado e eventos coerentes. As duas solicitações e seus históricos permanecem registrados.

### 7.5. Conversa e negociação

Haverá uma conversa por solicitação, acessível somente ao cliente titular e aos administradores com vínculo ativo na empresa correspondente. Pessoas de outra empresa, outros clientes e visitantes não poderão ler nem enviar mensagens, mesmo conhecendo o ID.

Proposta inicial para M4: mensagens de texto, histórico paginado, indicação do autor/horário e atualização periódica da conversa; envio via POST com CSRF, limites de tamanho/frequência e texto escapado na exibição. Anexos, chamadas e notificações externas ficam para outro ciclo. Essas escolhas serão apresentadas antes da implementação.

Salvar mensagens não confirma reservas nem altera datas/preços automaticamente. Ações de proposta, aceite e aprovação continuam estruturadas e autorizadas no servidor. Não permitir edição/exclusão de mensagens neste primeiro ciclo; conservar a conversa para consulta após recusa, cancelamento ou devolução. O prazo para permitir novos envios após esses estados ainda precisa ser definido.

### 7.6. Preservação da base e migração

- Adicionar migrações Flyway novas; não reescrever a V1 já aplicada nem apagar volumes para acomodar o marketplace.
- Vincular registros existentes a uma empresa inicial explicitamente definida e converter administradores atuais em vínculos dessa empresa. Apresentar esse mapeamento antes de executar qualquer migração sobre dados reais.
- Preservar IDs, datas, estados, valores e eventos das reservas existentes. Elas já são contratos confirmados/históricos e não devem voltar para `REQUESTED` nem exigir aceite retroativo inventado.
- Compatibilizar vínculos e chaves estrangeiras para impedir equipamento, empresa e solicitação inconsistentes. Planejar a migração da unicidade do código patrimonial para o escopo da empresa.
- Rever a administração global de clientes, o bootstrap, as rotas atuais e os formulários para que nenhuma operação antiga permita contornar o isolamento ou confirmar diretamente pelo cliente.
- Adaptar a demo e os testes com pelo menos duas empresas e clientes compartilhados, mantendo o banco fictício separado do ambiente real. Planejar atualização de demos já semeadas sem alterar silenciosamente suas contas ou apagar seus dados.
- Validar criação de banco vazio e atualização de um banco da versão anterior em PostgreSQL descartável antes de orientar atualização de ambientes existentes.

## 8. Etapas propostas para o marketplace

**Autorização de trabalho:** o usuário aprovou o fluxo e esta atualização documental. Antes de cada implementação, explicar mudanças, arquivos/partes afetados e verificações previstas; aguardar aprovação. M1 foi aprovada e implementada; M2 a M5 continuam pendentes. Não executar commit, push, alteração de visibilidade no GitHub ou publicação apenas por constarem no plano.

### M1 — Empresas e permissões

- [x] Detalhar e apresentar para aprovação o modelo de empresa/vínculo, a adaptação dos papéis e as migrações de dados existentes.
- [x] Vincular equipamentos e reservas à empresa, restringindo serviços, consultas e administração ao escopo autorizado.
- [x] Adaptar gestão de contas e bootstrap para impedir privilégios globais de administradores de empresa.
- [x] Cobrir migração, constraints, relações e tentativas de acesso/alteração entre empresas.

**Critério de conclusão:** empresa A não lê nem modifica dados privados de B, inclusive por ID, filtros, contagens ou formulários adulterados. O fluxo existente funciona dentro da empresa correta. Instalação limpa e migração da base anterior preservam os históricos e valores.

**Entrega em 01/11/2026:** V2 cria Empresa inicial e vínculos preservando contas, valores e histórico. Cada administrador pertence a uma empresa; seu papel global ADMIN apenas abre a área administrativa, e o vínculo ativo define o escopo real. CompanyPolicy revalida empresa/vínculo/conta e usa bloqueios compartilhados durante escritas. CompanySetup e Register-Company.ps1 cadastram empresas localmente, sem servidor HTTP ou promoção de contas existentes. INITIAL_COMPANY_NAME configura o nome inicial. Bloqueio de cliente fica em company_customer e afeta somente novos aluguéis naquela empresa. Retirada, devolução e consultas administrativas são restritas à empresa; cliente mantém seu histórico entre empresas.

**Validação:** clean verify aprovado com 296 testes (14 unitários/MVC e 282 de integração), incluindo 19 casos novos de empresa e um de migração real V1 → V2. Demonstração nova contém duas empresas; instâncias nomeadas preservam a demo anterior. CI remoto e publicação não foram executados. Consulte docs/EMPRESAS.md e docs/VALIDACOES.md para comandos e evidências adicionais.

### M2 — Busca pública entre empresas e acesso do cliente (depende de M1)

- [ ] Liberar consulta pública por equipamento/período, exibindo empresa, diária e total estimado com paginação.
- [ ] Definir e implementar a entrada de novos clientes: proposta de cadastro público de conta pessoal, sem escolha de papel administrativo.
- [ ] Preservar pesquisa e datas ao solicitar login; manter reservas, dados pessoais e administração privados.
- [ ] Confirmar campos de localização e critérios de exibição de empresas/equipamentos antes de implementar os filtros correspondentes.

**Critério de conclusão:** visitante compara resultados de pelo menos duas empresas e entra/cria conta para continuar, sem receber dados privados. A busca usa a mesma regra de disponibilidade das operações de reserva. Esta etapa intermediária ainda não é uma liberação comercial do marketplace.

### M3 — Solicitação, negociação de valores e aprovação (depende de M2)

- [ ] Substituir a confirmação direta pelo cliente por `REQUESTED`, com novos estados/eventos e migrações compatíveis.
- [ ] Implementar propostas versionadas, aceite do cliente, aprovação, recusa com motivo e cancelamento da solicitação.
- [ ] Revalidar período, atividade, autorização, versão aceita e disponibilidade na transação de aprovação.
- [ ] Manter retirada/devolução e histórico dos contratos confirmados, restritos à empresa responsável.
- [ ] Testar corridas de aprovação, contraproposta, aceite, cancelamento, manutenção e desativação.

**Critério de conclusão:** pedidos pendentes não bloqueiam o catálogo; somente a empresa confirma. Mudança de condições exige novo aceite. Duas aprovações simultâneas conflitantes geram uma reserva confirmada e uma recusa da operação, preservando solicitações e histórico. Sem chat ainda, a negociação estruturada pode ser exercitada pelos formulários de proposta/aceite.

### M4 — Chat por solicitação (depende de M3)

- [ ] Apresentar a forma de atualização da conversa, os limites de mensagens e a política de envio após encerramento.
- [ ] Implementar persistência, envio e consulta paginada de mensagens entre os participantes autorizados.
- [ ] Integrar conversa, proposta, aceite e ações da reserva na mesma jornada, sem confundir mensagem com decisão de negócio.
- [ ] Testar isolamento entre empresas/clientes, CSRF, conteúdo malicioso, limites e ordenação/paginação.

**Critério de conclusão:** somente os participantes autorizados leem/enviam mensagens; conteúdo é exibido como texto seguro, e o histórico permanece consultável após o encerramento conforme as permissões. Conversar não segura disponibilidade nem aprova a reserva.

### M5 — Fluxo completo, demo e preparação para uso real (depende de M4)

- [ ] Adaptar a demonstração para duas empresas, busca pública, solicitação, negociação, aprovação e retirada/devolução.
- [ ] Atualizar apresentação do produto, README e guias para refletir somente as capacidades efetivamente implementadas.
- [ ] Executar a suite completa em JDK 21/PostgreSQL, incluindo isolamento entre empresas, migrações, concorrência e regressões da base.
- [ ] Revisar desktop/mobile, teclado, estados vazios, conflitos e telas da conversa; documentar verificações feitas e pendentes.
- [ ] Definir o trabalho necessário antes de clientes reais: recuperação de acesso, proteção contra abuso, privacidade/retenção de dados, backups/restauração, TLS, segredos e operação do serviço.

**Critério de conclusão:** jornada visitante → cliente → solicitação → conversa → acordo → aprovação → retirada → devolução demonstrada com empresas isoladas e evidências de teste. A conclusão funcional não equivale a autorização para publicação ou uso comercial; os requisitos operacionais precisam de revisão própria. Hospedagem continua adiada pelo usuário.

## 9. Decisões a revisar antes das respectivas implementações

O fluxo central já foi aprovado. Os pontos abaixo são propostas ou perguntas pendentes; não tratá-los como funcionalidades prontas nem decisões comerciais tomadas.

| Decisão | Proposta ou situação atual | Momento da revisão |
| --- | --- | --- |
| Entrada de empresas | M1: provisionamento local controlado cria empresa e primeiro administrador; não há cadastro público de empresas. | Implementado em M1 |
| Vínculos administrativos | M1: conta individual vinculada a uma empresa. Convites, remoção e gestão de várias empresas pela mesma conta ficam para revisão futura. | Base implementada em M1 |
| Migração dos dados atuais | Empresa inicial recebe os dados antigos; nome configurável por INITIAL_COMPANY_NAME. V1 preservada e V2 testada com dados anteriores. | Implementado em M1 |
| Desativação de empresa/vínculo | M1 revalida atividade e preserva contratos; não oferece tela para essas alterações. Definir operação/regularização antes de criar tal interface. | Verificações implementadas; gestão futura |
| Cadastro e recuperação de acesso | Proposta de cadastro público de cliente; escolher confirmação de contato e fluxo de recuperação, sem senha definida pela empresa. | Antes de M2; recuperação antes de uso real |
| Localização e retirada | Proposta de cidade/UF na busca; definir endereço público, retirada presencial, entrega e área atendida. | Antes de M2 |
| Composição do preço | Proposta de diária negociada e total por dias inclusivos; taxas, caução e descontos separados não estão definidos. | Antes de M3 |
| Solicitações sem resposta | Definir expiração e limites contra pedidos repetidos; nunca aprovar pedido com início passado. | Antes de M3 |
| Cancelamento e recusa | Manter motivo da empresa; revisar prazos e regras comerciais. Não incluir multa automática neste ciclo. | Antes de M3 |
| Conversas encerradas | Proposta de consulta preservada; definir até quando pode haver novos envios e limites de frequência/tamanho. | Antes de M4 |
| Horário comercial e tolerância | Manter dias civis inclusivos em São Paulo; horários de retirada e tolerâncias ainda não definidos. | Antes de uso real |
| Monetização | Mensalidade das empresas, comissão por locação ou outro modelo ainda não escolhidos; não adicionar cobrança por suposição. | Antes de operação comercial |
| GitHub e licença do produto | Visibilidade e condições de uso/distribuição do código ainda serão decididas; preservar licenças de terceiros. | Antes de distribuição comercial |
| Publicação e operação | Hospedagem adiada; planejar produção, backups, restauração, privacidade e suporte em tarefa própria. | Antes de uso real |

**Registro desta revisão — 01/11/2026:** somente o planejamento foi atualizado. Nenhuma migração, alteração de código, execução de testes da aplicação ou operação de publicação faz parte desta entrega. Os 276 testes citados nas seções anteriores são resultados históricos da base, não testes executados nesta revisão.
