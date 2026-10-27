# EquipRent

Sistema web de aluguel de equipamentos para pequenas empresas, desenvolvido como projeto de portfólio com Java e Spring Boot. Cada equipamento representa uma unidade física; cada reserva terá um único equipamento.

## Situação atual

**Implementado:** base Spring Boot, rota `GET /`, apresentação em português com Thymeleaf e Bootstrap local, configuração PostgreSQL por ambiente, preparação do Flyway, Compose somente para o banco, Maven Wrapper e teste da camada web.

**Planejado:** autenticação ADMIN/CLIENTE, clientes, equipamentos, manutenção, disponibilidade por período, reservas e cancelamentos, cálculo estimado, retirada, devolução, atrasos, histórico, filtros e paginação. Nenhum cadastro, entidade de domínio ou login está implementado. Consulte [PLANEJAMENTO.md](PLANEJAMENTO.md) para as regras e etapas.

## Tecnologias e decisões

| Tecnologia | Uso nesta base |
| --- | --- |
| Java 21 | Linguagem e alvo da compilação (`release 21`) |
| Spring Boot 4.1.2 | Versão estável, dependências e servidor embutido |
| Maven 3.9.11 / Wrapper 3.3.4 | Build reproduzível sem Maven global |
| Spring MVC e Thymeleaf | HTML renderizado no servidor |
| Bootstrap 5.3.8 | CSS local, sem CDN durante o uso |
| Spring Data JPA / Hibernate | Infraestrutura de persistência, ainda sem entidades |
| PostgreSQL 17 | Banco local em contêiner, com volume persistente |
| Flyway | Migrações versionadas a partir da próxima etapa |
| JUnit Jupiter e Spring Test / MockMvc | Teste da rota e renderização do template |

Spring Boot 4.1.2 está listado como estável na [documentação oficial de requisitos](https://docs.spring.io/spring-boot/system-requirements.html), que inclui Java 21 na faixa suportada. Consulta em 27/10/2026; a versão está fixada no `pom.xml`, mesmo que a página mude. O `parent` gerencia as versões transitivas.

Bean Validation será adicionada ao criar os primeiros formulários; Spring Security e seus testes entrarão na etapa de autenticação. Testcontainers será adicionado quando houver testes de persistência. Não há Lombok, API separada, frontend Node ou infraestrutura de microserviços.

O [Maven Wrapper oficial](https://maven.apache.org/tools/wrapper/) baixa a versão fixada do Maven no primeiro uso e a guarda no cache do usuário (`~/.m2`). Isso não instala Maven globalmente. Os arquivos Bootstrap e sua licença MIT estão em `src/main/resources/static/vendor/bootstrap`.

## Pré-requisitos

- JDK **21**, com `java` no `PATH` e `JAVA_HOME` apontando para a mesma instalação.
- Docker com Compose v2, iniciado, para o PostgreSQL. Alternativamente, PostgreSQL 17 já instalado e um banco vazio acessível.
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

Execute na raiz do projeto:

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

Esse comando compila para Java 21, executa o teste MVC e gera `target/equiprent-0.0.1-SNAPSHOT.jar`. O teste carrega somente a camada web, sem banco ou `.env`; verifica HTTP 200, resolução do template, texto em português e link CSS processado pelo Thymeleaf. Ele **não comprova** conectividade PostgreSQL, execução do Flyway ou inicialização completa.

Com o banco ativo e as variáveis carregadas, também é possível executar o JAR:

```powershell
java -jar target/equiprent-0.0.1-SNAPSHOT.jar
```

Para conferir manualmente a inicialização completa, espere `Started EquipRentApplication`, abra a página e verifique o log do Flyway. Nesta etapa ele não encontrará migrações de domínio e poderá criar somente sua tabela técnica `flyway_schema_history`. Após a modelagem, os testes de integração usarão PostgreSQL real via Testcontainers.

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
src/main/resources/
  application.yaml
  db/migration/README.md
  templates/home/index.html
  static/css/app.css
  static/vendor/bootstrap/
src/test/java/com/equiprent/home/HomeControllerTest.java
PLANEJAMENTO.md
```

O Flyway será a única fonte de mudanças no esquema. `ddl-auto: validate` impede que o Hibernate crie ou altere tabelas; `open-in-view: false` mantém o acesso aos dados nos services transacionais. A pasta de migrações ainda não contém SQL; seu README mantém o diretório nos recursos do JAR. Veja a [orientação oficial de inicialização do banco](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## Validação desta entrega

- `java -version`: disponível JDK Temurin 25.0.5; JDK 21 não encontrado na instalação local consultada.
- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, compilação com `release 21`, um teste executado, zero falhas, zero erros e zero testes ignorados. JAR executável gerado.
- O teste MVC processou o template Thymeleaf real e aprovou a resposta da página inicial.
- `javap -verbose`: classe principal com versão de bytecode 65, correspondente ao alvo Java 21.
- Importação de ambiente conferida com valores fictícios: exemplo sem senha rejeitado e arquivo preenchido carregado corretamente na sessão.
- SHA-384 do Bootstrap local conferido contra o valor publicado na documentação oficial.
- `.\mvnw.cmd -B -ntp -DskipTests package`: empacotamento final aprovado após incluir o README de migrações, sem repetir o teste já aprovado. `jar tf` confirmou template, Bootstrap, diretório de migrações e bibliotecas Flyway/PostgreSQL no JAR.
- Docker não está disponível no `PATH`; inicialização completa com PostgreSQL e validação pelo comando `docker compose config` **não executadas**. O arquivo Compose foi revisado, mas ainda precisa de validação com Docker.
- Execução real em JDK 21 **pendente**. Compilar com `release 21` no JDK 25 não substitui essa verificação.
- A execução em JDK 25 exibiu avisos do Maven sobre `Unsafe` e do Mockito/Byte Buddy sobre anexação de agente (incluindo erro de carregamento do agente no caminho local com acento). O teste atual não usa mocks e passou; verificar esse ponto no JDK 21 antes de introduzir mocks nas próximas etapas.

Não houve instalação global de ferramentas nem publicação do projeto.

## Próximo passo

Executar a etapa 1 do [planejamento](PLANEJAMENTO.md): modelar usuários, equipamentos, reservas e eventos e escrever a primeira migração. O foco será aprender relacionamentos, restrições de banco, precisão monetária e a diferença entre estado físico e disponibilidade por período.
