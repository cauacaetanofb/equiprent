# Desenvolvimento e execução local

## Pré-requisitos

- JDK 21, com `JAVA_HOME` apontando para a instalação usada pelo Maven.
- Docker Desktop iniciado com contêineres Linux e Docker Compose.
- PowerShell no Windows. Não é necessário instalar Maven globalmente.
- Internet no primeiro download do Maven, das dependências e da imagem PostgreSQL.

```powershell
java -version
.\mvnw.cmd -version
docker version
```

Se houver vários JDKs, selecione o 21 na sessão, usando o caminho da sua instalação:

```powershell
$env:JAVA_HOME = 'C:\caminho\para\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Configuração do ambiente

Na raiz do projeto:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

| Variável | Uso |
| --- | --- |
| `DB_HOST` | Host do PostgreSQL acessível pelo processo Java. Normalmente `localhost`. |
| `DB_PORT` | Porta do banco, normalmente `5432`; use outra se já estiver ocupada. |
| `DB_NAME` | Nome do banco dedicado ao EquipRent. |
| `DB_USERNAME` | Usuário do banco. |
| `DB_PASSWORD` | Senha local, sem valor padrão no projeto. |
| `SERVER_PORT` | Porta HTTP, normalmente `8080`. |
| `INITIAL_COMPANY_NAME` | Opcional: nome da empresa que recebe registros anteriores à V2. |

Use `CHAVE=valor`, sem aspas, expansão de variáveis ou comentários ao final da linha. O `.env` é ignorado pelo Git. O script aceita somente as chaves conhecidas e não imprime seus valores.

## Primeiro administrador

Em uma instalação sem administrador, preencha no `.env`:

```dotenv
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_NAME=
BOOTSTRAP_ADMIN_EMAIL=
BOOTSTRAP_ADMIN_PASSWORD=
```

Informe nome, e-mail e uma senha exclusiva de 12 a 64 caracteres. Depois, inicie o banco e a aplicação conforme a seção seguinte. O administrador será vinculado à Empresa inicial, cujo nome pode ser definido por `INITIAL_COMPANY_NAME`.

Após confirmar o login, pare a aplicação, configure `BOOTSTRAP_ADMIN_ENABLED=false` e remova `BOOTSTRAP_ADMIN_PASSWORD` do `.env`. Na próxima inicialização, o carregador remove da sessão as variáveis opcionais retiradas do arquivo.

O bootstrap só cria o primeiro ADMIN. Se já houver administrador, mantém suas credenciais. Não promove uma conta CLIENTE existente. Para uma nova empresa, use o [procedimento de cadastro](EMPRESAS.md#cadastrar-outra-empresa-pelo-powershell).

## Iniciar e encerrar

```powershell
.\scripts\Import-LocalEnv.ps1
docker compose config --quiet
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

`Start-Local.ps1` importa o `.env` atual e inicia o Java pelo Maven Wrapper. O Compose usa as mesmas variáveis carregadas. Espere a mensagem `Started EquipRentApplication` e abra `http://localhost:8080` ou a porta configurada.

`Ctrl+C` encerra a aplicação. Para parar o banco e preservar seus dados:

```powershell
docker compose down
```

O volume persistente é `equiprent_postgres_data`. Não é necessário apagar dados para editar código ou atualizar o sistema. O banco é publicado apenas em `127.0.0.1`.

## Migrações e dados

Flyway aplica V1 (domínio de locação), V2 (empresas e vínculos), V3 (cidade/UF), V4 (solicitações e propostas) e `V5__add_reservation_chat.sql` (mensagens privadas). As migrações preservam contas, preços, reservas e históricos. A V3 não preenche localizações por suposição. A V4 preserva contratos sem inventar aceites antigos. A V5 acrescenta a tabela de mensagens sem modificar as negociações anteriores.

Tabelas: `app_user`, `company`, `company_membership`, `company_customer`, `equipment`, `reservation`, `reservation_offer`, `reservation_event` e `chat_message`, além de `flyway_schema_history`. Não há carga automática de contas, equipamentos, reservas ou mensagens. A Empresa inicial é parte da estrutura de migração e tem nome configurável.

Hibernate usa `ddl-auto=validate`. Evoluções de banco exigem novas migrações; mantenha V1 a V5 intactas depois de aplicadas.

## Diagnóstico de conexão

```powershell
docker compose ps
docker compose logs postgres
```

Se aparecer `password authentication failed`, carregue novamente o `.env` com `Start-Local.ps1` e confira se a senha corresponde à existente no PostgreSQL. Mudar o `.env` não altera a senha de um banco já inicializado. Confira também configurações `SPRING_DATASOURCE_*` ou `SPRING_FLYWAY_*` que possam estar sobrescrevendo a conexão.

Se a porta estiver ocupada, altere `DB_PORT`, carregue as variáveis e recrie somente o contêiner com `docker compose up -d --wait postgres`; o volume é preservado. A aplicação precisa de PostgreSQL para iniciar.

## Compilar, testar e empacotar

```powershell
.\mvnw.cmd clean verify
```

Esse comando executa testes unitários/MVC e testes de integração `*IT`, gerando `target/equiprent-0.0.1-SNAPSHOT.jar`. Testcontainers cria PostgreSQL descartável em portas aleatórias. Os testes não carregam `.env` nem usam o volume da aplicação. Docker indisponível faz a suíte de integração falhar.

Para executar somente testes unitários/MVC, sem Docker:

```powershell
.\mvnw.cmd test
```

Para verificar isolamento de empresas e migração:

```powershell
.\mvnw.cmd '-Dit.test=CompanyIT,CompanyMigrationIT' verify
```

Para verificar a disputa simultânea pelo mesmo equipamento:

```powershell
.\mvnw.cmd '-Dit.test=ReservationNegotiationIT#shouldAllowOnlyOneOfTwoConcurrentOverlappingApprovals' verify
```

Para verificar busca pública, cadastro, continuidade após login, localização e migração V2 → V3:

```powershell
.\mvnw.cmd '-Dit.test=MarketplaceAccessIT,CompanyLocationMigrationIT' verify
```

Para verificar propostas, aceite, aprovação, cancelamento concorrente e migração V3 → V4:

```powershell
.\mvnw.cmd '-Dit.test=ReservationNegotiationIT,ReservationNegotiationMigrationIT' verify
```

Para verificar mensagens, isolamento, limites, prazo após encerramento e migração V4 → V5:

```powershell
.\mvnw.cmd '-Dit.test=ChatIT,ChatMigrationIT' verify
```

Relatórios: `target/surefire-reports` e `target/failsafe-reports`. Para executar o JAR com o banco já iniciado:

```powershell
.\scripts\Import-LocalEnv.ps1
java -jar target/equiprent-0.0.1-SNAPSHOT.jar
```

Em Linux/macOS, use `sh ./mvnw` e exporte as variáveis do ambiente antes da execução.

## Interface e verificações manuais

Em um banco de teste dedicado, use contas individuais e duas empresas. Confira os seguintes cenários:

O roteiro da jornada pública e da revisão completa, até retirada/devolução e bloqueio comercial entre empresas, está em [MARKETPLACE.md](MARKETPLACE.md). Os cenários manuais usam um banco de teste dedicado; não execute mudanças de estado em contratos reais para simular a jornada.

1. Administrador A mantém somente seus equipamentos e reservas; administrador B não acessa esses registros por URL ou formulário.
2. Cliente consulta suas reservas entre empresas, mas não acessa reservas de outro cliente.
3. Confirmação, cancelamento, retirada e devolução mantêm valores e eventos coerentes.
4. Duas confirmações sobrepostas para uma unidade resultam em um sucesso e um conflito.
5. Bloqueio comercial em uma empresa preserva o login e o acesso do cliente às outras.
6. Revisar desktop e 360 px, teclado, foco, erros, moeda/datas e rolagem das tabelas.

Os resultados ficam em [VALIDACOES.md](VALIDACOES.md). A preparação para clientes reais, incluindo recuperação de acesso, proteção contra abuso, dados, backups e publicação, está organizada em [OPERACAO.md](OPERACAO.md); essas entregas ainda precisam de implementação e validação próprias.

## Integração contínua

O [workflow](../.github/workflows/ci.yml) executa `clean verify` em JDK 21 no GitHub Actions, com PostgreSQL via Testcontainers. Não depende das credenciais locais. Os relatórios são publicados como artefatos do workflow. Confirme o resultado remoto na aba Actions após o push.
