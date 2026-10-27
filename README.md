# EquipRent

Sistema web de aluguel de equipamentos para pequenas empresas, desenvolvido como projeto de portfólio com Java e Spring Boot. Cada equipamento representa uma unidade física; cada reserva terá um único equipamento.

## Situação atual

**Implementado:** base Spring Boot, apresentação em português com Thymeleaf e Bootstrap local, PostgreSQL por ambiente, Compose, Maven Wrapper, modelo JPA e migração V1; login/logout por sessão, perfis ADMIN/CLIENTE, primeiro administrador por bootstrap opcional, página de conta e cadastro/listagem paginada/desativação de clientes pelo ADMIN. Catálogo com cadastro/edição administrativa, consulta pelo cliente, busca, filtros, paginação e controle de manutenção/aposentadoria. Há testes MVC, unitário e de integração com PostgreSQL.

**Planejado:** disponibilidade por período, reservas e cancelamentos, cálculo estimado, retirada, devolução, atrasos e consulta do histórico de reservas. Recuperação/troca de senha, reativação e cadastro público não fazem parte desta entrega. Consulte [PLANEJAMENTO.md](PLANEJAMENTO.md) para as regras e etapas.

## Tecnologias e decisões

| Tecnologia | Uso nesta base |
| --- | --- |
| Java 21 | Linguagem e alvo da compilação (`release 21`) |
| Spring Boot 4.1.2 | Versão estável, dependências e servidor embutido |
| Maven 3.9.11 / Wrapper 3.3.4 | Build reproduzível sem Maven global |
| Spring MVC e Thymeleaf | HTML renderizado no servidor |
| Bootstrap 5.3.8 | CSS local, sem CDN durante o uso |
| Spring Data JPA / Hibernate | Entidades, relacionamentos e repositories; validação do esquema |
| PostgreSQL 17 | Banco local em contêiner, com volume persistente |
| Flyway | Migração V1 com quatro tabelas de domínio, restrições e índices |
| JUnit Jupiter e Spring Test / MockMvc | Teste da rota e renderização do template |
| Testcontainers / Maven Failsafe | Testes de integração com PostgreSQL 17 descartável |
| Spring Security | Login por sessão, autorização, CSRF e hash de senhas |
| Bean Validation | Validação de formulários e entradas do service |

Spring Boot 4.1.2 está listado como estável na [documentação oficial de requisitos](https://docs.spring.io/spring-boot/system-requirements.html), que inclui Java 21 na faixa suportada. Consulta em 27/10/2026; a versão está fixada no `pom.xml`, mesmo que a página mude. O `parent` gerencia as versões transitivas.

Testcontainers e o suporte de testes do Spring Security são dependências apenas de teste; suas versões são gerenciadas pelo Spring Boot. Não há Lombok, API separada, frontend Node ou infraestrutura de microserviços.

O [Maven Wrapper oficial](https://maven.apache.org/tools/wrapper/) baixa a versão fixada do Maven no primeiro uso e a guarda no cache do usuário (`~/.m2`). Isso não instala Maven globalmente. Os arquivos Bootstrap e sua licença MIT estão em `src/main/resources/static/vendor/bootstrap`.

## Pré-requisitos

- JDK **21**, com `java` no `PATH` e `JAVA_HOME` apontando para a mesma instalação.
- Docker com Compose v2, iniciado, para o PostgreSQL e os testes de integração. A aplicação também pode usar PostgreSQL 17 já instalado; a suite completa continua exigindo Docker para o Testcontainers.
- Acesso à internet no primeiro build e no primeiro download da imagem do banco.
- PowerShell no Windows. Maven instalado globalmente não é necessário.

Confira no PowerShell:

```powershell
java -version
$env:JAVA_HOME
docker version
docker compose version
.\mvnw.cmd -version
```

Caso tenha vários JDKs, selecione o 21 **somente nesta sessão**, ajustando o caminho para sua instalação:

```powershell
$env:JAVA_HOME = 'C:\caminho\para\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Configuração e execução no Windows

Na primeira configuração, execute na raiz do projeto. Se já tiver `.env`, preserve-o e não copie o exemplo novamente:

```powershell
Copy-Item .env.example .env
notepad .env
```

Preencha `DB_PASSWORD` com uma senha exclusiva do ambiente local. O exemplo não contém senha. Preserve o formato simples `CHAVE=valor`, sem aspas, expansão de variáveis ou comentários ao final da linha. Não compartilhe o `.env`; ele está no `.gitignore`.

| Variável | Significado | Exemplo local |
| --- | --- | --- |
| `DB_HOST` | Host acessível pela aplicação executada no Windows | `localhost` |
| `DB_PORT` | Porta publicada do PostgreSQL | `5432` |
| `DB_NAME` | Nome do banco | `equiprent` |
| `DB_USERNAME` | Usuário do banco | `equiprent` |
| `DB_PASSWORD` | Senha definida por você | Obrigatória, sem padrão |
| `SERVER_PORT` | Porta HTTP da aplicação | `8080` |

Com Docker Desktop aberto, inicie o banco e a aplicação:

```powershell
.\scripts\Import-LocalEnv.ps1
docker compose config --quiet
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

`Start-Local.ps1` carrega o `.env` atual e chama o Maven Wrapper na mesma sessão. Use esse comando ao iniciar a aplicação ou depois de editar o `.env`; chamar apenas `spring-boot:run` não recarrega o arquivo. O script não inicia o Docker nem altera credenciais do banco.

Abra **http://localhost:8080**. Se alterar `SERVER_PORT`, use a nova porta. `Ctrl+C` encerra a aplicação. Para parar o banco preservando os dados:

```powershell
docker compose down
```

O banco publica a porta apenas em `127.0.0.1`. O volume `postgres_data` persiste entre execuções. As variáveis `POSTGRES_*` inicializam o banco somente quando o volume está vazio: alterar a senha no `.env` depois disso não altera a senha de um usuário já criado. Para diagnosticar o banco, use `docker compose ps` e `docker compose logs postgres`. Se 5432 estiver ocupada, altere `DB_PORT` e recarregue as variáveis.

### Primeiro acesso: criar o administrador

Preserve as configurações de banco do seu `.env`. Adicione as quatro linhas abaixo, preenchendo nome, e-mail e uma senha exclusiva de **12 a 64 caracteres**. Não há senha padrão:

```dotenv
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_NAME=
BOOTSTRAP_ADMIN_EMAIL=
BOOTSTRAP_ADMIN_PASSWORD=
```

Com os campos preenchidos, execute:

```powershell
.\scripts\Import-LocalEnv.ps1
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

Abra **http://localhost:8080/login** e use as credenciais que você definiu. Em **Minha conta → Gerenciar clientes**, cadastre um cliente e teste seu acesso em uma janela privada ou após sair da conta administrativa.

Depois de confirmar o primeiro login, encerre a aplicação com `Ctrl+C`, altere `BOOTSTRAP_ADMIN_ENABLED=false` e remova a linha `BOOTSTRAP_ADMIN_PASSWORD` do `.env`. Execute `Start-Local.ps1` novamente. A importação também remove da sessão as variáveis opcionais retiradas do arquivo; a senha codificada permanece no banco.

O bootstrap cria somente o primeiro ADMIN. Se já existir qualquer administrador, não cria outro nem altera sua senha. Se o e-mail escolhido pertencer a um CLIENTE e não houver administrador, a inicialização falha sem promover o cliente. Configuração inválida também interrompe a inicialização sem imprimir a senha. Um bloqueio transacional da tabela serializa duas tentativas simultâneas de bootstrap.

As quatro variáveis são opcionais: arquivos `.env` das etapas anteriores continuam funcionando, com bootstrap desabilitado por padrão. Nenhum ADMIN foi criado no seu banco de desenvolvimento durante esta implementação.

### O que cada perfil pode fazer

| Endereço/ação | Visitante | CLIENTE ativo | ADMIN ativo |
| --- | --- | --- | --- |
| `/`, `/login` e CSS local | Sim | Sim | Sim |
| `/conta` | Vai para login | Somente seus dados | Seus dados e atalho administrativo |
| `/admin/clientes` e `/admin/clientes/novo` | Vai para login | 403 | Sim |
| Cadastrar/desativar cliente | Não | 403 | Sim, via POST com CSRF |
| `/equipamentos` e `/equipamentos/{id}` | Vai para login | Unidades operacionais/em manutenção | Todas as unidades |
| `/admin/equipamentos/novo` e `/admin/equipamentos/{id}/editar` | Vai para login | 403 | Sim |
| Cadastrar/editar equipamento e alterar condição | Não | 403 | Sim, via POST com CSRF |
| Sair da conta | — | POST com CSRF | POST com CSRF |

O cadastro sempre cria `CLIENTE` ativo, mesmo que alguém tente enviar campos como `role=ADMIN`, `id` ou `active`. A listagem tem 20 registros por página e não envia hashes às views. Desativar exige ausência de reservas `RESERVED`/`IN_USE`; reservas finalizadas e eventos são preservados. O fluxo não desativa administradores.

Contas desativadas não conseguem entrar. Uma sessão já aberta é invalidada na próxima requisição; o service também consulta o estado e o papel atuais no banco antes das operações administrativas. Sessões expiram após 30 minutos de inatividade. GET não efetua logout nem desativação.

### Catálogo e condição física

Abra **Minha conta → Equipamentos** ou **http://localhost:8080/equipamentos**. ADMIN cadastra uma unidade por código patrimonial; CLIENTE consulta unidades operacionais ou em manutenção. O código é único, diferencia maiúsculas/minúsculas e fica imutável após o cadastro. Nome, descrição, categoria e diária podem ser editados. A diária aceita de R$ 0,01 a R$ 9.999.999.999,99, com até duas casas decimais; alterar o preço não recalcula reservas existentes.

A busca por trecho de nome/código ignora maiúsculas/minúsculas, trata `%` e `_` como caracteres literais e combina categoria e condição. A listagem começa com 20 itens por página, permite entre 1 e 100, preserva filtros nos links e aceita somente nome, menor/maior diária ou data de cadastro como ordenação. O ID desempata resultados iguais.

Condição física não é disponibilidade por período. Todas as unidades nascem operacionais. O ADMIN pode colocá-las em manutenção e retornar à operação; manutenção é recusada enquanto houver reserva `IN_USE`. Reservas `RESERVED` são preservadas e aparecem em uma lista de pendências de 10 itens por página, visível apenas ao ADMIN. A aposentadoria exige ausência de reservas `RESERVED`/`IN_USE`, é definitiva e oculta a unidade do catálogo do CLIENTE, inclusive no acesso direto por ID. ADMIN continua consultando e editando seus dados; reservas e eventos não são apagados.

Edição de dados/preço e alteração de condição usam `PESSIMISTIC_WRITE` no equipamento dentro da transação. As futuras operações de reserva devem compartilhar esse bloqueio. Uma falha transitória de acesso ao banco apresenta uma página com orientação para tentar novamente, sem confirmar alteração parcial. A etapa 3 não cria endpoints de reserva, retirada, devolução ou cancelamento, e não altera a migração V1.

Roteiro manual, depois de iniciar com `Start-Local.ps1`:

1. Entre como ADMIN e cadastre `NB-001`, nome `Notebook de teste`, descrição, categoria Notebook e diária `49.90` (ou um código ainda não utilizado).
2. Confira o valor `R$ 49,90` nos detalhes. Edite nome/descrição/diária e verifique que o código permanece fixo.
3. Tente cadastrar outra unidade com o mesmo código e confira a mensagem de duplicidade. Teste filtros por nome, código, categoria e condição.
4. Coloque a unidade em manutenção e consulte-a como CLIENTE em uma janela privada: a condição aparece, sem controles administrativos.
5. Volte à conta ADMIN, retorne à operação e aposente **essa unidade de teste**. Ela permanece na consulta administrativa e deixa de aparecer para o CLIENTE. A aposentadoria não pode ser desfeita.

Pendências de reservas e concorrência são verificadas automaticamente com dados fictícios em PostgreSQL descartável. A interface de reservas será construída nas próximas etapas.

### Senhas e formulários

Senhas usam `DelegatingPasswordEncoder` com PBKDF2 nos parâmetros de `defaultsForSpringSecurity_v5_8()` e prefixo `{pbkdf2@SpringSecurity_v5_8}`. O algoritmo usa salt aleatório: duas contas com a mesma senha têm hashes diferentes. É uma transformação unidirecional, não uma senha que a aplicação consiga descriptografar. A escolha suporta Unicode sem o limite de bytes do BCrypt e não adiciona outra biblioteca criptográfica. Veja a [documentação oficial de armazenamento de senhas](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

CSRF permanece habilitado, e Thymeleaf inclui os tokens nos formulários POST. Erros de validação preservam nome/e-mail, mas nunca devolvem a senha no HTML. O formulário e o service usam Bean Validation. A senha inicial do cliente é definida pelo ADMIN; troca/recuperação de senha será uma evolução posterior.

### Como o ambiente chega ao Spring

O Spring **não lê `.env` automaticamente**. O script `Import-LocalEnv.ps1` lê somente as chaves conhecidas e as exporta para o processo atual do PowerShell, sem executar o conteúdo. Maven e Java iniciados nesse terminal herdam essas variáveis. `application.yaml` usa os placeholders `${DB_*}` para montar a conexão. A cada novo terminal, execute o script novamente. A IDE também precisa receber essas variáveis na configuração de execução, caso seja usada para iniciar a aplicação.

As chaves `BOOTSTRAP_ADMIN_*` seguem o mesmo caminho, mas são opcionais. Não coloque as credenciais nos argumentos de linha de comando ou no `application.yaml`.

Se executar Maven manualmente, os dois comandos precisam rodar no mesmo terminal:

```powershell
.\scripts\Import-LocalEnv.ps1
.\mvnw.cmd spring-boot:run
```

Uma falha `FATAL: password authentication failed` / `28P01` indica que o PostgreSQL rejeitou as credenciais usadas naquele processo. Salvar o `.env` sozinho não muda variáveis já carregadas no terminal. `Start-Local.ps1` evita essa divergência ao importar novamente antes de cada inicialização. Se o erro persistir com o script, conferir também se a senha atual do banco corresponde ao arquivo e se há configurações `SPRING_DATASOURCE_*` ou `SPRING_FLYWAY_*` sobrescrevendo a conexão.

O Docker Compose tem seu próprio suporte a `.env`, mas isso não exporta variáveis para a aplicação Java que roda fora do contêiner. Variáveis do terminal têm precedência no Compose. Nosso fluxo carrega o mesmo arquivo antes de iniciar ambos, evitando divergência. Veja a [documentação oficial do Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

Se a política local do PowerShell bloquear scripts, defina as seis variáveis diretamente com `$env:NOME = 'valor'` no terminal, usando os valores do seu `.env`, ou nas configurações de execução da IDE. Não é necessário alterar a política global do Windows.

Com PostgreSQL já instalado, use um banco vazio dedicado e as credenciais correspondentes, carregue as variáveis e pule os comandos Docker. A aplicação precisa do banco para inicializar; não existe perfil alternativo com banco em memória.

## Compilar, testar e empacotar

```powershell
.\mvnw.cmd clean verify
```

Com Docker Desktop em execução, esse comando compila para Java 21, executa os testes unitário/MVC (Surefire), gera `target/equiprent-0.0.1-SNAPSHOT.jar` e executa os testes de integração `*IT` (Failsafe). Não é necessário carregar `.env` nem iniciar o banco do Compose para testar.

O Testcontainers cria PostgreSQL 17 em porta aleatória e o remove ao terminar. `PersistenceIT`, `SecurityIT` e `EquipmentIT` substituem explicitamente URL, usuário e senha do datasource pelas credenciais de seus contêineres. Os testes de segurança usam contas fictícias e bootstrap configurado no próprio teste; os testes de persistência e catálogo desabilitam o bootstrap. A migração e os testes nunca usam o volume ou as credenciais do seu banco de desenvolvimento. O primeiro teste pode demorar mais para baixar a imagem. Docker indisponível faz a suite completa **falhar**, em vez de ignorar os testes silenciosamente.

Para executar somente os testes rápidos, sem Docker:

```powershell
.\mvnw.cmd test
```

Isso executa o teste MVC de renderização (com filtros isolados) e o teste unitário de precisão monetária. Não valida persistência ou segurança. A cadeia real de segurança é exercitada por `SecurityIT` no `verify`. Os relatórios completos ficam em `target/surefire-reports` e `target/failsafe-reports`.

Com o banco ativo e as variáveis carregadas, também é possível executar o JAR:

```powershell
java -jar target/equiprent-0.0.1-SNAPSHOT.jar
```

Para conferir manualmente a inicialização completa, espere `Started EquipRentApplication`, abra a página e verifique o log do Flyway. Na primeira execução após esta atualização, ele aplicará `V1__create_initial_schema.sql`; nas seguintes, informará que o esquema está atualizado. O Hibernate validará os mapeamentos após a migração. Não é preciso apagar o contêiner ou o volume existente.

Com o banco do Compose ativo, confira as tabelas e o histórico de migrações sem colocar senha no comando:

```powershell
docker compose exec postgres psql -U $env:DB_USERNAME -d $env:DB_NAME -c '\dt'
docker compose exec postgres psql -U $env:DB_USERNAME -d $env:DB_NAME -c 'TABLE flyway_schema_history;'
```

Devem aparecer `app_user`, `equipment`, `reservation`, `reservation_event` e `flyway_schema_history`. A V1 não cria contas ou dados fictícios. Para evoluir o esquema, escreva uma nova migração V2; não altere uma V1 já aplicada.

Em Linux/macOS, use `sh ./mvnw clean verify` e `sh ./mvnw spring-boot:run`, exportando as mesmas variáveis de ambiente antes da execução.

## Estrutura atual

```text
.mvn/wrapper/maven-wrapper.properties
mvnw / mvnw.cmd
pom.xml
compose.yaml
.env.example
scripts/Import-LocalEnv.ps1
scripts/Start-Local.ps1
src/main/java/com/equiprent/
  EquipRentApplication.java
  home/HomeController.java
  user/{User,UserRole,UserRepository,UserForm,UserSummary,UserService,UserController}.java
  security/{SecurityConfig,CustomUserDetailsService,CurrentUser,ActiveUserFilter,AdminBootstrap,...}.java
  equipment/{Equipment,EquipmentCategory,EquipmentCondition,EquipmentRepository,EquipmentService,...}.java
  equipment/{EquipmentController,EquipmentAdminController,EquipmentForm,EquipmentFilter,...}.java
  reservation/{Reservation,ReservationStatus,ReservationEvent,ReservationRepository,ReservationEventRepository}.java
src/main/resources/
  application.yaml
  db/migration/README.md
  db/migration/V1__create_initial_schema.sql
  templates/home/index.html
  templates/security/{login,account}.html
  templates/user/{form,list}.html
  templates/equipment/{form,list,details}.html
  templates/fragments/layout.html
  templates/error/{403,404,503}.html
  static/css/app.css
  static/vendor/bootstrap/
src/test/java/com/equiprent/home/HomeControllerTest.java
src/test/java/com/equiprent/equipment/EquipmentTest.java
src/test/java/com/equiprent/equipment/EquipmentIT.java
src/test/java/com/equiprent/persistence/PersistenceIT.java
src/test/java/com/equiprent/security/SecurityIT.java
PLANEJAMENTO.md
```

O Flyway é a única fonte de mudanças no esquema. `ddl-auto: validate` impede que o Hibernate crie ou altere tabelas; `open-in-view: false` exigirá que os futuros services carreguem os dados antes da renderização. Veja a [orientação oficial de inicialização do banco](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

Os relacionamentos são `ManyToOne` com carregamento `LAZY` e sem cascata de exclusão. O banco impede excluir usuários, equipamentos e reservas referenciados pelo histórico. Enums são textos com `CHECK`; valores monetários usam `BigDecimal` e `numeric`, com snapshot da diária na reserva. Eventos são imutáveis pelo mapeamento JPA. Limites de campos e responsabilidades do banco versus services estão no planejamento.

Autorização de clientes e equipamentos já existe nos filtros e nos services. Alterações no equipamento têm bloqueio transacional; disponibilidade e operações concorrentes sobre reservas ainda serão implementadas. Os repositories não são expostos como endpoints HTTP. As etapas 2 e 3 reutilizam a V1 existente, sem alterar seu checksum nem exigir outra migração.

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

## Próximo passo

Executar a etapa 4 do [planejamento](PLANEJAMENTO.md): consulta de disponibilidade por período, cálculo de diárias inclusivas, estimativa no servidor e criação da reserva com snapshot de preço e evento. Reutilizar os bloqueios de cliente/equipamento para impedir reservas simultâneas conflitantes e revalidar as regras dentro da transação.
