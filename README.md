# EquipRent

Sistema web de aluguel de equipamentos para pequenas empresas, desenvolvido como projeto de portfólio com Java e Spring Boot. Cada equipamento representa uma unidade física; cada reserva terá um único equipamento.

## Situação atual

**Implementado:** base Spring Boot, rota `GET /`, apresentação em português com Thymeleaf e Bootstrap local, configuração PostgreSQL por ambiente, Compose somente para o banco, Maven Wrapper, entidades e repositories de usuários/equipamentos/reservas/eventos, migração Flyway V1 e testes MVC, unitário e de integração com PostgreSQL.

**Planejado:** autenticação ADMIN/CLIENTE, telas e operações de cadastro, manutenção, disponibilidade por período, reservas e cancelamentos, cálculo estimado, retirada, devolução, atrasos, consulta do histórico, filtros e paginação. O modelo de dados já existe, mas não há login, formulários ou services de negócio. Consulte [PLANEJAMENTO.md](PLANEJAMENTO.md) para as regras e etapas.

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

Spring Boot 4.1.2 está listado como estável na [documentação oficial de requisitos](https://docs.spring.io/spring-boot/system-requirements.html), que inclui Java 21 na faixa suportada. Consulta em 27/10/2026; a versão está fixada no `pom.xml`, mesmo que a página mude. O `parent` gerencia as versões transitivas.

Bean Validation será adicionada ao criar os primeiros formulários; Spring Security e seus testes entrarão na etapa de autenticação. Testcontainers é usado apenas nos testes e sua versão é gerenciada pelo Spring Boot. Não há Lombok, API separada, frontend Node ou infraestrutura de microserviços.

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

Carregue as variáveis e inicie:

```powershell
.\scripts\Import-LocalEnv.ps1
docker compose config --quiet
docker compose up -d --wait postgres
.\mvnw.cmd spring-boot:run
```

Abra **http://localhost:8080**. Se alterar `SERVER_PORT`, use a nova porta. `Ctrl+C` encerra a aplicação. Para parar o banco preservando os dados:

```powershell
docker compose down
```

O banco publica a porta apenas em `127.0.0.1`. O volume `postgres_data` persiste entre execuções. As variáveis `POSTGRES_*` inicializam o banco somente quando o volume está vazio: alterar a senha no `.env` depois disso não altera a senha de um usuário já criado. Para diagnosticar o banco, use `docker compose ps` e `docker compose logs postgres`. Se 5432 estiver ocupada, altere `DB_PORT` e recarregue as variáveis.

### Como o ambiente chega ao Spring

O Spring **não lê `.env` automaticamente**. O script `Import-LocalEnv.ps1` lê somente as chaves conhecidas e as exporta para o processo atual do PowerShell, sem executar o conteúdo. Maven e Java iniciados nesse terminal herdam essas variáveis. `application.yaml` usa os placeholders `${DB_*}` para montar a conexão. A cada novo terminal, execute o script novamente. A IDE também precisa receber essas variáveis na configuração de execução, caso seja usada para iniciar a aplicação.

O Docker Compose tem seu próprio suporte a `.env`, mas isso não exporta variáveis para a aplicação Java que roda fora do contêiner. Variáveis do terminal têm precedência no Compose. Nosso fluxo carrega o mesmo arquivo antes de iniciar ambos, evitando divergência. Veja a [documentação oficial do Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/).

Se a política local do PowerShell bloquear scripts, defina as seis variáveis diretamente com `$env:NOME = 'valor'` no terminal, usando os valores do seu `.env`, ou nas configurações de execução da IDE. Não é necessário alterar a política global do Windows.

Com PostgreSQL já instalado, use um banco vazio dedicado e as credenciais correspondentes, carregue as variáveis e pule os comandos Docker. A aplicação precisa do banco para inicializar; não existe perfil alternativo com banco em memória.

## Compilar, testar e empacotar

```powershell
.\mvnw.cmd clean verify
```

Com Docker Desktop em execução, esse comando compila para Java 21, executa os testes unitário/MVC (Surefire), gera `target/equiprent-0.0.1-SNAPSHOT.jar` e executa os testes de integração `*IT` (Failsafe). Não é necessário carregar `.env` nem iniciar o banco do Compose para testar.

O Testcontainers cria PostgreSQL 17 em porta aleatória e o remove ao terminar. `PersistenceIT` substitui explicitamente URL, usuário e senha do datasource pelas credenciais do contêiner. A migração e os testes nunca usam o volume ou as credenciais do seu banco de desenvolvimento. O primeiro teste pode demorar mais para baixar a imagem. Docker indisponível faz a suite completa **falhar**, em vez de ignorar os testes silenciosamente.

Para executar somente os testes rápidos, sem Docker:

```powershell
.\mvnw.cmd test
```

Isso executa o teste MVC (template real, HTTP 200, idioma e links) e o teste unitário de precisão monetária. Não valida persistência. Os relatórios completos ficam em `target/surefire-reports` e `target/failsafe-reports`.

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
src/main/java/com/equiprent/
  EquipRentApplication.java
  home/HomeController.java
  user/{User,UserRole,UserRepository}.java
  equipment/{Equipment,EquipmentCategory,EquipmentCondition,EquipmentRepository}.java
  reservation/{Reservation,ReservationStatus,ReservationEvent,ReservationRepository,ReservationEventRepository}.java
src/main/resources/
  application.yaml
  db/migration/README.md
  db/migration/V1__create_initial_schema.sql
  templates/home/index.html
  static/css/app.css
  static/vendor/bootstrap/
src/test/java/com/equiprent/home/HomeControllerTest.java
src/test/java/com/equiprent/equipment/EquipmentTest.java
src/test/java/com/equiprent/persistence/PersistenceIT.java
PLANEJAMENTO.md
```

O Flyway é a única fonte de mudanças no esquema. `ddl-auto: validate` impede que o Hibernate crie ou altere tabelas; `open-in-view: false` exigirá que os futuros services carreguem os dados antes da renderização. Veja a [orientação oficial de inicialização do banco](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

Os relacionamentos são `ManyToOne` com carregamento `LAZY` e sem cascata de exclusão. O banco impede excluir usuários, equipamentos e reservas referenciados pelo histórico. Enums são textos com `CHECK`; valores monetários usam `BigDecimal` e `numeric`, com snapshot da diária na reserva. Eventos são imutáveis pelo mapeamento JPA. Limites de campos e responsabilidades do banco versus services estão no planejamento.

Ainda não há garantia de concorrência para criação de reservas nem autorização: esses comportamentos serão implementados nos services das etapas seguintes. Os repositories não são expostos como endpoints HTTP.

## Validação da etapa 1 — 27/10/2026

- `java -version`: disponível JDK Temurin 25.0.5; JDK 21 não encontrado na instalação local consultada.
- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, compilação com `release 21`, **77 testes** (2 unitário/MVC e 75 de integração), zero falhas, zero erros e zero testes ignorados. JAR executável gerado.
- Docker disponível e PostgreSQL 17 iniciado automaticamente pelo Testcontainers, sem usar o banco de desenvolvimento.
- Validados: aplicação HTTP com banco real, migração do zero, Hibernate `validate`, segunda inicialização sem reaplicar a V1, normalização/unicidade de e-mail, unicidade patrimonial, relacionamentos, precisão monetária, snapshot após mudança de preço, restrições de estados/timestamps, preservação de histórico e rollback de reserva quando o evento falha.
- Execução real em JDK 21 **pendente**. Compilar com `release 21` no JDK 25 não substitui essa verificação.
- A execução em JDK 25 exibiu avisos do Maven sobre `Unsafe` e do Mockito/Byte Buddy sobre anexação de agente (incluindo erro de carregamento do agente no caminho local com acento). O teste atual não usa mocks e passou; verificar esse ponto no JDK 21 antes de introduzir mocks nas próximas etapas.

Não houve instalação global de ferramentas nem publicação do projeto.

## Próximo passo

Executar a etapa 2 do [planejamento](PLANEJAMENTO.md): autenticação com sessão, perfis ADMIN/CLIENTE, criação segura do primeiro administrador e cadastro de clientes. O foco será aprender a diferença entre autenticação e autorização, codificação de senhas e proteção de formulários com CSRF.
