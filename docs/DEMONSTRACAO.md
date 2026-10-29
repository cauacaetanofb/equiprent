# Demonstração do EquipRent

Use o perfil `demo` para apresentar o projeto sem depender de dados pessoais ou de cadastros feitos no ambiente normal. Todos os nomes, contatos e equipamentos desse conjunto são fictícios; e-mails usam `.example`.

## Executar do zero

1. Instale JDK 21 e inicie Docker Desktop com contêineres Linux. Confira `java -version`, `.\mvnw.cmd -version` e `docker info` na raiz do projeto.
2. Execute a suíte antes de gravar: `.\mvnw.cmd clean verify`.
3. Execute `.\scripts\Start-Demo.ps1`. O script prepara o PostgreSQL e inicia a aplicação.
4. Abra `http://localhost:8081`. Consulte `DEMO_ACCOUNT_PASSWORD` em `.local/demo.env` para entrar como `admin@equiprent.example`, `marina@equiprent.example` ou `rafael@equiprent.example`.

O arquivo é criado automaticamente com senhas aleatórias no primeiro uso, sem imprimir valores. A senha das três contas é compartilhada apenas neste conjunto fictício; no banco, cada conta recebe hash PBKDF2 com salt próprio. O ambiente normal continua com seu `.env` e seu fluxo de bootstrap.

| Recurso | Ambiente normal | Demonstração |
| --- | --- | --- |
| Aplicação | Porta 8080 por padrão | Porta 8081, bind em 127.0.0.1 |
| PostgreSQL | Porta 5432 por padrão | Porta 5440, bind em 127.0.0.1 |
| Banco/usuário | Configurado em `.env` | `equiprent_demo` |
| Compose | `compose.yaml`, projeto `equiprent` | `compose.demo.yaml`, projeto `equiprent-demo` |
| Volume padrão | `equiprent_postgres_data` | `equiprent-demo_demo_data` |
| Cookie de sessão | `JSESSIONID` | `EQUIPRENT_DEMO_SESSION` |
| Configuração local | `.env` | `.local/demo.env` |

Se as portas da demo estiverem ocupadas, edite `DEMO_DB_PORT` e `DEMO_SERVER_PORT` em `.local/demo.env` e execute novamente. O script aceita somente as quatro chaves geradas, sem aspas, espaços ou expansão de variáveis. Ele restaura as variáveis anteriores ao terminar. `-PrepareOnly` prepara apenas credenciais/banco; não inicia Java nem cria as contas, que são carregadas na inicialização do perfil.

O perfil desabilita o bootstrap normal. A carga exige banco com nome `equiprent_demo`, vazio na primeira execução, e uma senha fornecida. A migração repetível em `db/demo` cria o marcador `demo_seed`; não altera a V1. A transação bloqueia a carga por advisory lock e grava fixtures e marcador juntos. Dois processos iniciando a mesma demo não criam duas cargas.

## Dados iniciais

Na primeira inicialização são criadas **3 contas, 7 unidades, 7 reservas e 12 eventos**. As datas são relativas ao dia de criação na zona de São Paulo. Reiniciar não avança datas nem redefine senhas ou ações já feitas.

| Código | Unidade / diária | Cenário inicial |
| --- | --- | --- |
| NB-001 | Notebook para trabalho / R$ 89,90 | Livre hoje; reserva futura cancelada no histórico |
| CAM-001 | Câmera para criação de conteúdo / R$ 120,00 | Livre hoje; locação passada devolvida |
| FER-001 | Furadeira de impacto / R$ 35,00 | Reserva de Marina entre hoje e hoje + 2 |
| NB-002 | Notebook compacto / R$ 69,90 | Em uso por Rafael até amanhã |
| CAM-002 | Câmera para eventos / R$ 95,00 | Em uso atrasado por Marina; compromisso futuro de Rafael preservado |
| FER-002 | Parafusadeira sem fio / R$ 45,90 | Em manutenção; compromisso futuro de Marina preservado |
| NB-003 | Notebook de geração anterior / R$ 39,90 | Aposentado; visível somente ao ADMIN |

Se a demo ficar vários dias parada, seus estados passam a refletir o tempo decorrido. Recrie o banco para voltar ao roteiro com datas atuais. Editar a senha no arquivo não troca a senha de contas ou do PostgreSQL já existentes.

## Roteiro do vídeo

[Vídeo WebM](media/equiprent-demo.webm) · [Legendas WebVTT](media/equiprent-demo.vtt)

Gravação silenciosa de aproximadamente um minuto da aplicação real. Logins foram feitos antes de gravar. As legendas informam qual sessão está em uso; não há senha, `.env` nem tela de terminal no vídeo. Para assistir com legendas, abra o vídeo e carregue o arquivo `.vtt` em um player compatível. O WebM também pode ser aberto diretamente no navegador.

1. Rafael consulta catálogo e detalhes de NB-001.
2. Seleciona hoje até hoje + 2: três diárias de R$ 89,90, total de R$ 269,70.
3. Enquanto a estimativa de Rafael está aberta, Marina confirma o mesmo período em outra sessão.
4. Rafael envia a confirmação antiga e recebe indisponibilidade. A consulta inicial não bloqueava a unidade; o servidor revalidou a confirmação.
5. A sessão de Marina mostra a única reserva criada, com período e preço preservados.
6. O ADMIN registra retirada e devolução; a linha do tempo mostra os três eventos com autor e horário.

Esse roteiro demonstra uma estimativa desatualizada. Para provar a corrida simultânea com duas transações, execute:

```powershell
.\mvnw.cmd '-Dit.test=ReservationIT#shouldAllowExactlyOneOfTwoConcurrentOverlappingReservations' verify
```

Resultado esperado: uma criação, uma recusa e somente uma reserva/evento. O teste usa barreira de sincronização e PostgreSQL real, sem mock da disponibilidade.

## Reproduzir manualmente e capturar telas

Use janelas com sessões independentes, ou navegadores/perfis distintos. Duas abas da mesma janela compartilham cookies e não representam clientes separados.

- CLIENTE Marina: abra `/equipamentos` para capturar o catálogo. Consulte NB-001, de hoje até hoje + 2, e capture a estimativa antes de confirmar.
- CLIENTE Rafael: abra a mesma estimativa em uma sessão separada. Confirme primeiro com Marina, depois com Rafael, para mostrar a recusa.
- ADMIN: abra `/admin/reservas`, use os filtros de situação/atraso e capture a listagem. Abra a reserva criada, registre retirada/devolução e capture a linha do tempo.
- ADMIN: abra CAM-002 para mostrar a reserva futura afetada pelo atraso, sem cancelamento automático. Abra FER-002 para comparar manutenção com disponibilidade.
- Redimensione para 360 px e repita catálogo, estimativa, administração e histórico. As tabelas rolam dentro de sua própria região, sem alargar a página.

As oito capturas entregues estão em `docs/images`, nos pares `catalogo`, `reserva`, `administracao` e `historico`, com sufixos `desktop`/`mobile`. Mostram o conjunto inicial; o vídeo adiciona uma oitava reserva e suas movimentações.

## Reiniciar ou recriar a demonstração

`Ctrl+C` encerra a aplicação. Para parar PostgreSQL sem apagar a demo:

```powershell
docker compose -p equiprent-demo -f compose.demo.yaml --env-file .local/demo.env down
```

Depois, `.\scripts\Start-Demo.ps1` recupera o mesmo volume e preserva todas as alterações.

Para **apagar somente os dados da demonstração** e preparar datas atuais, pare a aplicação e execute explicitamente:

```powershell
docker compose -p equiprent-demo -f compose.demo.yaml --env-file .local/demo.env down -v
.\scripts\Start-Demo.ps1
```

Os parâmetros `-p equiprent-demo` e `-f compose.demo.yaml` identificam o volume da demo. O comando normal `docker compose down -v` na raiz apontaria para outro projeto e não faz parte deste roteiro.

## Limites da carga

A carga inicial é uma fixture transacional exclusiva do perfil, com estados passados/futuros definidos para a apresentação. Ela usa as entidades diretamente para montar o histórico e não representa um endpoint que permite burlar os services. Operações feitas pelo navegador continuam passando pelas validações, autorização e bloqueios normais.

O perfil não é uma configuração de produção: usa contas fictícias com a mesma senha inicial e bind local. Não há publicação nem hospedagem incluída nesta entrega.
