# EquipRent

Sistema web de gestão de aluguel de equipamentos para empresas. Organiza unidades físicas, disponibilidade por período, reservas, retiradas, devoluções e histórico, com acesso separado por empresa.

[Execução local](docs/DESENVOLVIMENTO.md) · [Empresas e permissões](docs/EMPRESAS.md) · [Planejamento](PLANEJAMENTO.md) · [Validações](docs/VALIDACOES.md)

## Funcionalidades

| Cliente | Administrador da empresa |
| --- | --- |
| Consultar catálogo e disponibilidade após entrar na conta | Cadastrar equipamentos e clientes |
| Estimar diárias e confirmar reservas | Gerenciar catálogo, diária e condição física |
| Consultar somente suas reservas e histórico | Consultar reservas da própria empresa |
| Cancelar reservas ainda não retiradas | Cancelar com motivo e registrar retirada/devolução |
| Usar a mesma conta em diferentes empresas | Acompanhar atrasos e compromissos afetados |

Contas administrativas têm vínculo com uma empresa. Equipamentos e reservas mantêm essa associação; códigos patrimoniais podem se repetir entre empresas. Bloquear novos aluguéis de um cliente em uma empresa preserva sua conta e seu acesso às demais.

## Executar localmente

Pré-requisitos: JDK 21 e Docker Desktop iniciado com contêineres Linux. O Maven Wrapper baixa o Maven no primeiro uso.

Na raiz do projeto, crie `.env` somente se ele ainda não existir e preencha a senha do banco:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Inicie o banco e a aplicação:

```powershell
.\scripts\Import-LocalEnv.ps1
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

Abra **http://localhost:8080**, ou a porta definida em `SERVER_PORT`. Para uma instalação nova, configure o [primeiro administrador](docs/DESENVOLVIMENTO.md#primeiro-administrador) antes de entrar. Não existe senha padrão.

Use `Ctrl+C` para encerrar a aplicação. `docker compose down` para o banco preservando os dados. Não remova o volume para atualizar o código.

## Cadastrar empresas

Com o banco configurado, o responsável pelo ambiente pode cadastrar uma empresa e seu primeiro administrador:

```powershell
.\scripts\Register-Company.ps1 -Code LOCADORA_B -Name "Locadora B" -AdminName "Responsável B" -AdminEmail "responsavel.b@example.com"
```

Substitua os dados pelos desejados. A senha é solicitada sem exibição; o script não a grava em arquivo nem a coloca na linha de comando. Veja as [regras de provisionamento e migração](docs/EMPRESAS.md).

## Arquitetura e regras

Java 21, Spring Boot, MVC, Thymeleaf, Bootstrap local, PostgreSQL 17, JPA/JDBC e Flyway. As versões estão definidas no `pom.xml` e nos arquivos do projeto.

- Controllers recebem formulários com campos permitidos; services aplicam autorização, regras e transações.
- Flyway controla o esquema; Hibernate apenas o valida. Não há consultas JPA durante a renderização fora do service.
- Datas são inclusivas em `America/Sao_Paulo`; valores usam `BigDecimal` e preservam a diária contratada.
- Bloqueios no banco impedem confirmações sobrepostas. Reserva e evento são gravados na mesma transação.
- Senhas usam PBKDF2; autenticação por sessão e ações de alteração protegidas por CSRF.
- Contas, equipamentos e reservas com histórico são preservados. Alterar a diária não reescreve contratos anteriores.

## Testes

Com Docker iniciado:

```powershell
.\mvnw.cmd clean verify
```

Os testes de integração iniciam bancos PostgreSQL descartáveis, sem usar `.env` nem o volume da aplicação. Relatórios ficam em `target/surefire-reports` e `target/failsafe-reports`. Resultados e pendências estão em [docs/VALIDACOES.md](docs/VALIDACOES.md).

A [integração contínua](.github/workflows/ci.yml) executa a suíte em JDK 21 a cada push para `main` e pull request. O resultado remoto deve ser conferido na aba Actions do GitHub.

## Evolução do produto

A separação de empresas está implementada. Busca pública, cadastro pessoal de clientes, solicitações sujeitas à aprovação, negociação de valores e chat estão planejados. Hoje o cliente confirma a reserva diretamente, e seu cadastro é feito pela empresa.

Ainda não há pagamento, recuperação de senha, multa automática, cadastro público de empresas, carrinho ou edição do período de uma reserva. A publicação depende de configuração e operação próprias. O [planejamento](PLANEJAMENTO.md) distingue o comportamento atual das próximas entregas.
