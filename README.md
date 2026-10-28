# EquipRent

Sistema web de aluguel de equipamentos para pequenas empresas, desenvolvido como projeto de portfólio com Java e Spring Boot. Cada equipamento representa uma unidade física; cada reserva tem um único equipamento.

## Situação atual

**Implementado:** base Spring Boot, apresentação em português com Thymeleaf e Bootstrap local, PostgreSQL por ambiente, Compose, Maven Wrapper, modelo JPA e migração V1; login/logout por sessão, perfis ADMIN/CLIENTE, primeiro administrador por bootstrap opcional, página de conta e cadastro/listagem paginada/desativação de clientes pelo ADMIN. Catálogo com cadastro/edição administrativa, consulta pelo cliente, busca, filtros, paginação e controle de manutenção/aposentadoria. Disponibilidade, estimativa, criação e cancelamento de reservas, com valores históricos e eventos atômicos. Minhas reservas e visão administrativa, retirada/devolução, manutenção ao devolver, atrasos atuais e históricos, reservas afetadas e linha do tempo. Há testes MVC, unitários e de integração com PostgreSQL, incluindo concorrência.

**Interface revisada:** navegação com seção atual, foco nos erros, rótulos associados aos campos, contraste ajustado e tabelas roláveis por teclado em telas pequenas. Fluxo completo exercitado com contas separadas e CI com JDK 21 configurado.

**Próxima etapa:** preparação da demonstração de portfólio. Recuperação/troca de senha, reativação, cadastro público, pagamentos e multas não fazem parte desta entrega. Consulte [PLANEJAMENTO.md](PLANEJAMENTO.md) para as regras e etapas.

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
| Buscar disponibilidade em `/equipamentos` | Vai para login | Sim | Sim |
| Estimar em `/reservas/nova` | Vai para login | Sim | 403 |
| Criar em `POST /reservas` | Não | Sim, com CSRF | 403 |
| `/reservas/{id}/confirmacao` | Vai para login | Somente o proprietário (alheia: 404) | 403 nesta etapa |
| `/reservas` e `/reservas/{id}` | Vai para login | Somente suas reservas | 403; usar área administrativa |
| `/admin/reservas` e `/admin/reservas/{id}` | Vai para login | 403 | Todas as reservas |
| `POST /reservas/{id}/cancelar` | Não | Somente a própria, com CSRF | 403; usar área administrativa |
| `POST /admin/reservas/{id}/cancelar` | Não | 403 | Sim, com motivo e CSRF |
| `POST /admin/reservas/{id}/retirar` | Não | 403 | Sim, dentro do período e com CSRF |
| `POST /admin/reservas/{id}/devolver` | Não | 403 | Sim, para reserva em uso e com CSRF |
| Sair da conta | — | POST com CSRF | POST com CSRF |

O cadastro sempre cria `CLIENTE` ativo, mesmo que alguém tente enviar campos como `role=ADMIN`, `id` ou `active`. A listagem tem 20 registros por página e não envia hashes às views. Desativar exige ausência de reservas `RESERVED`/`IN_USE`; reservas finalizadas e eventos são preservados. O fluxo não desativa administradores.

Contas desativadas não conseguem entrar. Uma sessão já aberta é invalidada na próxima requisição; o service também consulta o estado e o papel atuais no banco antes das operações administrativas. Sessões expiram após 30 minutos de inatividade. GET não efetua logout nem desativação.

### Catálogo e condição física

Abra **Minha conta → Equipamentos** ou **http://localhost:8080/equipamentos**. ADMIN cadastra uma unidade por código patrimonial; CLIENTE consulta unidades operacionais ou em manutenção. O código é único, diferencia maiúsculas/minúsculas e fica imutável após o cadastro. Nome, descrição, categoria e diária podem ser editados. A diária aceita de R$ 0,01 a R$ 9.999.999.999,99, com até duas casas decimais; alterar o preço não recalcula reservas existentes.

A busca por trecho de nome/código ignora maiúsculas/minúsculas, trata `%` e `_` como caracteres literais e combina categoria e condição. A listagem começa com 20 itens por página, permite entre 1 e 100, preserva filtros nos links e aceita somente nome, menor/maior diária ou data de cadastro como ordenação. O ID desempata resultados iguais.

Condição física não é disponibilidade por período. Todas as unidades nascem operacionais. O ADMIN pode colocá-las em manutenção e retornar à operação; manutenção direta é recusada enquanto houver reserva `IN_USE`, mas pode ser registrada junto da devolução. Reservas `RESERVED` são preservadas. Quando houver manutenção ou devolução atrasada, as reservas com término a partir de hoje aparecem em uma lista de pendências de 10 itens por página, visível apenas ao ADMIN. A aposentadoria exige ausência de reservas `RESERVED`/`IN_USE`, é definitiva e oculta a unidade do catálogo do CLIENTE, inclusive no acesso direto por ID. ADMIN continua consultando e editando seus dados; reservas e eventos não são apagados.

Edição de dados/preço, alteração de condição e todas as operações de reserva compartilham `PESSIMISTIC_WRITE` no equipamento dentro da transação. Uma falha transitória no catálogo apresenta uma página com orientação para tentar novamente; nas operações da reserva, a tela reaparece preservando entradas e exibindo mensagem de nova tentativa. As etapas 3 a 6 não alteram a migração V1.

Roteiro manual, depois de iniciar com `Start-Local.ps1`:

1. Entre como ADMIN e cadastre `NB-001`, nome `Notebook de teste`, descrição, categoria Notebook e diária `49.90` (ou um código ainda não utilizado).
2. Confira o valor `R$ 49,90` nos detalhes. Edite nome/descrição/diária e verifique que o código permanece fixo.
3. Tente cadastrar outra unidade com o mesmo código e confira a mensagem de duplicidade. Teste filtros por nome, código, categoria e condição.
4. Coloque a unidade em manutenção e consulte-a como CLIENTE em uma janela privada: a condição aparece, sem controles administrativos.
5. Volte à conta ADMIN, retorne à operação e aposente **essa unidade de teste**. Ela permanece na consulta administrativa e deixa de aparecer para o CLIENTE. A aposentadoria não pode ser desfeita.

Pendências de reservas e concorrência são verificadas automaticamente com dados fictícios em PostgreSQL descartável.

### Disponibilidade, estimativa e reserva

No catálogo, preencha **Disponível a partir de** e **Até (inclusive)**. O início deve ser hoje ou depois, o fim não pode anteceder o início e o período tem até 365 diárias. As datas usam `America/Sao_Paulo`; os dois dias extremos contam. Sem datas, o catálogo mantém a consulta geral; com apenas uma data ou período inválido, exibe erro. Busca, categoria, condição, ordenação e paginação continuam combináveis.

Uma unidade está disponível quando é operacional, não possui reserva `RESERVED`/`IN_USE` sobreposta e não tem devolução atrasada em `IN_USE`. Atraso bloqueia qualquer novo período. Reservas `RETURNED`/`CANCELLED` não bloqueiam; uma `RESERVED` passada sem retirada não é atraso de devolução. Períodos que compartilham um dia conflitam: uma reserva que termina no dia 12 permite outra a partir do dia 13.

CLIENTE acessa **Estimar e reservar** nos resultados ou **Consultar período e reservar** nos detalhes. A consulta GET mostra os dias, a diária e o total calculado no servidor, sem gravar reserva. A confirmação POST revalida tudo e usa o preço vigente sob bloqueio; a estimativa anterior não segura a unidade nem congela a diária. Após confirmar, valores ficam imutáveis na reserva. Exemplo: R$ 49,90 × 3 = R$ 149,70. O total máximo armazenável é R$ 999.999.999.999,99; excedê-lo apresenta uma mensagem para reduzir o período, sem arredondamento ou gravação parcial.

A transação usa `READ_COMMITTED`, bloqueia cliente → equipamento e revalida conta ativa, perfil CLIENTE, datas, condição, sobreposição e atraso. O usuário é recarregado após adquirir o bloqueio, pois já pode estar no contexto JPA desde a autenticação; isso permite enxergar uma desativação concorrente. Veja a semântica de [refresh com bloqueio na documentação Jakarta Persistence](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html). A busca e a confirmação compartilham o predicado de reservas bloqueantes. O proprietário vem da sessão, e valores/status extras enviados no formulário são ignorados.

Reserva e evento inicial são gravados na mesma transação; qualquer falha reverte ambos. A confirmação faz redirecionamento para uma URL privada do proprietário. Atualizar essa página não cria outra reserva; reenviar o POST para o mesmo período retorna conflito sem duplicar histórico. O protocolo depende de escritas via services: SQL externo não participa dos bloqueios de negócio.

Roteiro manual da etapa 4:

1. Como ADMIN, cadastre uma unidade operacional com diária de R$ 49,90 e dois clientes de teste, se ainda não tiver esses cadastros.
2. Entre como CLIENTE em janela privada, busque um período futuro de três dias e clique em **Estimar e reservar**. Confira o total de R$ 149,70.
3. Confirme e anote a URL de confirmação. Atualize a página: o número e os valores permanecem iguais.
4. Como outro CLIENTE, tente consultar o mesmo período para a mesma unidade: ela não aparece na disponibilidade. A URL de confirmação do primeiro cliente retorna 404.
5. Busque um período começando no dia seguinte ao término: a unidade volta a aparecer. Se mudar a diária como ADMIN, a confirmação já gravada mantém o valor histórico.

As reservas podem ser acompanhadas e canceladas conforme as regras abaixo; ADMIN também registra retirada e devolução. Não é necessário apagar o volume do banco ou recriar o administrador.

### Minhas reservas, administração e cancelamento

CLIENTE acessa **Minhas reservas** em `/reservas`; ADMIN acessa **Reservas** em `/admin/reservas`. Clique no número para ver detalhes, valores contratados e situação. O escopo de propriedade é aplicado no banco antes de contar e paginar: IDs e filtros enviados pelo navegador não permitem consultar ou cancelar reservas de outro cliente. A área administrativa também revalida o papel no service. Contas inativas não operam, mas seu histórico continua disponível ao ADMIN.

As listas combinam nome/código do equipamento, situação e período; ADMIN também pesquisa nome/e-mail do cliente. Busca textual ignora caixa e trata `%`, `_` e `!` literalmente. Datas são limites independentes e inclusivos: mostram reservas cujo período coincide em pelo menos um dia com o intervalo informado, incluindo datas passadas. Paginação padrão de 20, entre 1 e 100; ordenação por cadastro mais recente ou início crescente/decrescente, sempre com ID decrescente como desempate. Os links preservam os filtros.

As consultas combinadas usam [Specifications do Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html). A listagem carrega equipamento e cliente na consulta da página, antes de montar os DTOs, para evitar uma consulta extra por linha. Nenhum hash de senha é enviado às views.

Só é permitido cancelar uma reserva `RESERVED`, inclusive se o período previsto passou e não houve retirada. O motivo é obrigatório para ADMIN, opcional para CLIENTE e limitado a 1.000 caracteres. O formulário aceita apenas esse motivo; proprietário, equipamento, período e preços não podem ser alterados. Cancelar é definitivo, preserva o contrato/histórico e deixa de bloquear o período; a condição física e outras reservas ainda podem impedir nova locação.

O cancelamento POST usa CSRF, bloqueia equipamento → reserva e recarrega o estado após esperar o bloqueio. A mudança para `CANCELLED`, o instante e o evento com autor/motivo são atômicos. Repetir o pedido ou cancelar `IN_USE`/`RETURNED`/`CANCELLED` informa estado inválido sem duplicar evento. Os detalhes exibem o registro do cancelamento em horário de São Paulo. A antiga URL de confirmação passa a mostrar a situação atual quando a reserva já foi cancelada.

Roteiro manual da etapa 5:

1. Como CLIENTE, crie uma reserva de teste e abra **Minhas reservas**. Teste filtros, abra os detalhes e confira diária/total.
2. Cancele a reserva. Confira a situação **Cancelada**, autor e horário; os preços devem permanecer iguais. Uma nova busca pelo período deixa de ser bloqueada por essa reserva.
3. Como outro CLIENTE, tente abrir a URL de detalhes anterior: deve receber 404 e não ver a reserva na própria lista.
4. Como ADMIN, abra **Reservas**, busque um cliente e cancele outra reserva de teste informando o motivo. O cliente titular consegue consultar esse motivo nos detalhes.
5. Atualize os detalhes após cancelar: o formulário de cancelamento desaparece. Histórico de equipamentos aposentados e clientes desativados continua acessível ao ADMIN.

### Retirada, devolução, atrasos e linha do tempo

ADMIN abre `/admin/reservas/{id}` e usa **Confirmar retirada** depois de entregar fisicamente o equipamento. A reserva precisa estar `RESERVED`, o dia atual deve estar entre início e fim (inclusive), a unidade deve estar operacional e não pode existir outra reserva em uso para ela. Retirada antecipada ou após o período é recusada. Uma reserva vencida sem retirada continua `RESERVED`, podendo ser cancelada; não é tratada como devolução atrasada.

Depois da retirada, **Confirmar devolução** registra o recebimento e a conferência. Com **Encaminhar equipamento para manutenção** marcado, a unidade passa a `MAINTENANCE`; desmarcado, fica `OPERATIONAL`. A devolução exige `IN_USE` e aceita recebimento antecipado, no prazo ou atrasado. O total contratado não muda e não há multa automática. Uma devolução antecipada libera a unidade para nova reserva, inclusive no mesmo dia, respeitadas as demais regras de disponibilidade.

Retirada e devolução são exclusivas de ADMIN e só ocorrem por POST com CSRF. A transação bloqueia equipamento → reserva, recarrega os registros e verifica o estado atual. Estado, instante, condição física na devolução e evento são gravados juntos; falha de evento reverte tudo. Duas retiradas da mesma unidade não podem ficar abertas. Repetir uma operação concluída informa estado inválido sem duplicar evento. Os instantes e o autor vêm do servidor; o formulário de devolução aceita apenas a opção de manutenção.

As listas e os detalhes mostram **Devolução atrasada** quando `IN_USE` e hoje é posterior ao fim, usando `America/Sao_Paulo`. O filtro **Somente devoluções atrasadas em aberto** combina com os filtros existentes e respeita a propriedade. Após devolver, a indicação **Devolvida com atraso** compara a data local da devolução ao fim contratado e permanece histórica. Não existe status `OVERDUE` nem tarefa agendada para mudar estados.

ADMIN pode usar **Consultar equipamento e reservas afetadas** nos detalhes para ver compromissos atuais/futuros prejudicados por manutenção ou devolução atrasada. Eles são preservados, sem cancelamento automático. A lista é restrita ao ADMIN; reservas expiradas sem retirada continuam disponíveis na listagem geral para regularização.

A **Linha do tempo** mostra criação, cancelamento ou retirada/devolução, com autor, estados, instante em São Paulo e observação, ordenados por instante e ID. O evento de devolução registra se houve encaminhamento para manutenção, mesmo se a condição do catálogo mudar depois. CLIENTE vê somente a linha do tempo de suas próprias reservas. Os autores são carregados junto dos eventos, sem consulta adicional por evento.

Roteiro manual da etapa 6:

1. Como CLIENTE, reserve um equipamento operacional com **início hoje** e término hoje ou depois. Isso permite testar a retirada imediatamente.
2. Como ADMIN, abra **Reservas → número da reserva → Confirmar retirada**. Confira **Em uso**, horário e evento na linha do tempo. A opção de cancelamento desaparece.
3. Confirme a devolução após conferir a unidade. Confira **Devolvida**, o novo evento e os mesmos valores contratados.
4. Em outra reserva de teste, faça retirada e devolução marcando manutenção. Confira o equipamento em **Em manutenção** e a observação no histórico.
5. Como CLIENTE, confira sua linha do tempo e a ausência dos controles de retirada/devolução. Os cenários de atraso e meia-noite são cobertos pelos testes com relógio controlado; não é necessário mudar o relógio do computador.

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

O Testcontainers cria PostgreSQL 17 em porta aleatória e o remove ao terminar. `PersistenceIT`, `SecurityIT`, `EquipmentIT`, `ReservationIT`, `ReservationManagementIT` e `ReservationOperationsIT` substituem explicitamente URL, usuário e senha do datasource pelas credenciais de seus contêineres. Os testes de segurança usam contas fictícias e bootstrap configurado no próprio teste; os demais desabilitam o bootstrap. A migração e os testes nunca usam o volume ou as credenciais do seu banco de desenvolvimento. O primeiro teste pode demorar mais para baixar a imagem. Docker indisponível faz a suite completa **falhar**, em vez de ignorar os testes silenciosamente.

Para executar somente os testes rápidos, sem Docker:

```powershell
.\mvnw.cmd test
```

Isso executa o teste MVC de renderização (com filtros isolados), precisão monetária e cálculo/validação de períodos e totais. Não valida persistência ou segurança. A cadeia real de segurança e a concorrência são exercitadas nos testes `*IT` durante o `verify`. Os relatórios completos ficam em `target/surefire-reports` e `target/failsafe-reports`.

Para reproduzir somente a integração de reservas, incluindo os testes concorrentes:

```powershell
.\mvnw.cmd '-Dit.test=ReservationIT' verify
.\mvnw.cmd '-Dit.test=ReservationManagementIT' verify
.\mvnw.cmd '-Dit.test=ReservationOperationsIT' verify
```

As threads e transações são reais. Testes coordenam tentativas simultâneas e, nos casos de espera, consultam o bloqueio no PostgreSQL. Um teste aplica `lock_timeout` de dois segundos somente nas conexões do seu contêiner descartável e confere o formulário de nova tentativa sem gravações parciais. Isso não muda as configurações do banco local.

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
  reservation/{ReservationService,ReservationController,ReservationForm,RentalPeriod,AvailabilityRules,...}.java
  reservation/{ReservationManagementService,ReservationManagementController,ReservationFilter,CancellationForm,...}.java
  reservation/{ReservationOperationsService,ReturnForm,ReservationTimelineEntry}.java
src/main/resources/
  application.yaml
  db/migration/README.md
  db/migration/V1__create_initial_schema.sql
  templates/home/index.html
  templates/security/{login,account}.html
  templates/user/{form,list}.html
  templates/equipment/{form,list,details}.html
  templates/reservation/{form,confirmation,list,details}.html
  templates/fragments/layout.html
  templates/error/{403,404,503}.html
  static/css/app.css
  static/vendor/bootstrap/
src/test/java/com/equiprent/home/HomeControllerTest.java
src/test/java/com/equiprent/equipment/EquipmentTest.java
src/test/java/com/equiprent/equipment/EquipmentIT.java
src/test/java/com/equiprent/reservation/{RentalPeriodTest,ReservationIT,ReservationManagementIT,ReservationOperationsIT}.java
src/test/java/com/equiprent/persistence/PersistenceIT.java
src/test/java/com/equiprent/security/SecurityIT.java
PLANEJAMENTO.md
```

O Flyway é a única fonte de mudanças no esquema. `ddl-auto: validate` impede que o Hibernate crie ou altere tabelas; `open-in-view: false` exige que os services carreguem os dados antes da renderização. Veja a [orientação oficial de inicialização do banco](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

Os relacionamentos são `ManyToOne` com carregamento `LAZY` e sem cascata de exclusão. O banco impede excluir usuários, equipamentos e reservas referenciados pelo histórico. Enums são textos com `CHECK`; valores monetários usam `BigDecimal` e `numeric`, com snapshot da diária na reserva. Eventos são imutáveis pelo mapeamento JPA. Limites de campos e responsabilidades do banco versus services estão no planejamento.

Autorização existe nos filtros e nos services. As operações de reserva compartilham os bloqueios de negócio. Consultas de CLIENTE sempre aplicam propriedade; ADMIN tem área específica para todas as reservas. Os repositories não são expostos como endpoints HTTP. As etapas 2 a 6 reutilizam a V1 existente, sem alterar seu checksum nem exigir outra migração.

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

## Próximo passo

Executar a etapa 8 do [planejamento](PLANEJAMENTO.md): preparar dados fictícios opcionais, capturas, demonstração curta e apresentação das decisões técnicas. Hospedagem/publicação continua sendo uma tarefa separada.

## Validação da etapa 7 — 28/10/2026

- `.\mvnw.cmd -B -ntp clean verify`: **BUILD SUCCESS**, **269 testes** (14 unitário/MVC e 255 de integração), sem falhas, erros ou ignorados, executados em **Temurin 21.0.12.1** efetivo. A pendência de JDK 21 registrada nas etapas anteriores foi resolvida. O JDK portátil foi obtido do [Adoptium](https://adoptium.net/installation/ci-scripts), verificado por SHA-256 e extraído em `.local`, sem mudar a instalação global.
- Fluxo completo em Chrome headless com ADMIN e dois CLIENTEs em sessões independentes, usando PostgreSQL 17 descartável. Foram **52 verificações** de tela (26 estados em 1366 e 360 px), sem rolagem horizontal da página nem violações detectadas nas regras WCAG 2/2.1 A/AA selecionadas no axe. Tabelas têm rolagem própria. Capturas de layout, validação e histórico também foram inspecionadas.
- Conferidos cadastro, duplicidade de e-mail, foco no erro, nome preservado/senha limpa, busca/paginação, reserva/estimativa, retirada, devolução com manutenção, cancelamento por teclado, histórico privado, moeda/datas, valores e textos máximos, telas vazias, erros 400/403/404 e logout. Nenhum recurso externo obrigatório foi solicitado.
- Workflow validado com **actionlint 1.7.12**; `git diff --check` aprovado. Migração V1 intacta e dados de desenvolvimento preservados. Aplicação e banco descartáveis encerrados; ferramentas de revisão e evidências em `.local` ignoradas pelo Git.
- Pendentes: primeira execução do workflow no GitHub após o push, testes em outros navegadores e com leitores de tela. A auditoria automatizada não estabelece conformidade integral de acessibilidade.

## Interface, acessibilidade e roteiro de revisão

A interface funciona sem JavaScript e sem CDN. A seção atual fica sublinhada na navegação; o primeiro `Tab` alcança “Ir para o conteúdo”. Erros recebem foco e os formulários preservam os campos seguros; a senha nunca volta preenchida. As tabelas mantêm cabeçalhos e semântica tabular, com rolagem horizontal limitada à região identificada: use gesto de deslizar ou `Tab` seguido das setas. Textos longos quebram dentro da página.

O cancelamento exige abrir “Revisar cancelamento”, conferir as consequências e confirmar. Se houver erro, essa área fica aberta. Retirada e devolução têm ações separadas com explicação e confirmação; a opção de manutenção informa o efeito sobre reservas futuras. Telas 403/404/503 e o fallback de erros oferecem retorno à navegação, sem detalhes técnicos de exceções.

Para repetir a revisão local, use dados fictícios e duas sessões independentes (por exemplo, janela normal e anônima).

1. ADMIN: cadastre um cliente e duas unidades operacionais com diária de R$ 49,90. Tente cadastrar o mesmo e-mail novamente e confira o erro, o foco e a preservação do nome.
2. CLIENTE: busque equipamentos com início e fim **hoje**, selecione um item por página e avance. Busca, categoria, condição, datas e ordenação devem permanecer.
3. Consulte a estimativa e confirme uma reserva de um dia: total de R$ 49,90. Confira “Minhas reservas”, detalhes e criação na linha do tempo.
4. ADMIN: abra a reserva em `/admin/reservas`, confirme a retirada e depois a devolução. Teste a opção de manutenção: a unidade deixa de aceitar novas reservas, com histórico e valor preservados.
5. CLIENTE: faça outra reserva na unidade livre e use “Revisar cancelamento”. Confira o evento e a liberação do período. Uma segunda conta CLIENTE não pode abrir os detalhes dessa reserva; `/admin/clientes` deve ser negado ao CLIENTE.
6. Repita em desktop e com viewport de **360 px**, incluindo navegação por teclado, rolagem das tabelas, filtros sem resultados e páginas fora do intervalo. Confira datas brasileiras, moeda, logout e retorno após erros.

A revisão automatizada de acessibilidade cobre problemas detectáveis por axe; não equivale a uma certificação WCAG nem substitui testes com leitores de tela e outros navegadores.

## Integração contínua

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) executa `bash ./mvnw -B -ntp clean verify` em Ubuntu com Temurin **21**, a cada push em `main`, pull request ou disparo manual. O Testcontainers inicia PostgreSQL 17 descartável; não é preciso cadastrar `.env`, senhas ou um banco externo no GitHub. As permissões são de leitura do código; os relatórios Surefire/Failsafe ficam disponíveis como artefato por sete dias, inclusive quando um teste falha.

As ações estão fixadas por SHA, com suas versões nos comentários. Referências: [setup-java](https://github.com/actions/setup-java), [checkout](https://github.com/actions/checkout) e [upload-artifact](https://github.com/actions/upload-artifact). O wrapper é chamado por `bash` porque está versionado sem bit executável. Depois do primeiro push desta etapa, acompanhe o workflow na aba **Actions** do repositório: a execução remota só pode ser confirmada após esse envio.
