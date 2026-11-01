# Empresas e permissões

Cada administrador tem uma conta individual e um vínculo com uma empresa. O cliente mantém uma conta pessoal e pode ter reservas em empresas diferentes.

Cada conta administrativa pertence a uma única empresa; várias contas podem pertencer à mesma empresa. Convites de funcionários, troca de empresa na sessão e painel global da plataforma ainda não fazem parte da interface. O procedimento local abaixo cadastra uma nova empresa com seu primeiro administrador, usando um e-mail ainda não cadastrado.

O catálogo continua exigindo login e o cliente ainda confirma diretamente suas reservas. Busca pública, solicitações sujeitas a aprovação, negociação e chat estão nas etapas M2 a M4 do [planejamento](../PLANEJAMENTO.md).

## Atualizar o ambiente existente

Pré-requisitos: JDK 21, Docker Desktop iniciado e `.env` normal configurado. Na raiz do projeto:

```powershell
.\scripts\Import-LocalEnv.ps1
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

Na inicialização, Flyway aplica a nova `V2__add_company_ownership.sql`. A V1 permanece intacta. A migração cria a **Empresa inicial**, associa os equipamentos/reservas existentes a ela e vincula os administradores atuais. Contas, senhas, IDs, períodos, valores, estados e eventos anteriores são preservados. Clientes existentes recebem vínculo comercial com essa empresa; contas globalmente inativas continuam inativas.

Para escolher o nome dessa empresa, adicione ao `.env`:

```dotenv
INITIAL_COMPANY_NAME=Minha locadora
```

Essa configuração atualiza somente o nome da empresa de código interno `INITIAL` na inicialização normal. Se estiver ausente/vazia, mantém o nome existente. Não muda credenciais nem renomeia outras empresas.

## Cadastrar outra empresa pelo PowerShell

Com o banco normal iniciado, execute:

```powershell
.\scripts\Register-Company.ps1 -Code LOCADORA_B -Name "Locadora B" -AdminName "Responsável B" -AdminEmail "responsavel.b@example.com"
```

Substitua os valores pelos dados desejados. O script solicita a senha sem mostrá-la; ela deve ter de 12 a 64 caracteres. A senha não é passada na linha de comando nem salva em arquivo pelo script. Use um código estável, em letras maiúsculas, números, hífen ou sublinhado, com até 50 caracteres.

O script carrega o `.env`, executa a aplicação sem servidor HTTP e restaura as variáveis de ambiente e o diretório ao terminar. Pode receber `-EnvFile "caminho\ambiente.env"` para um ambiente específico. Empresa, conta e vínculo são criados na mesma transação; falha reverte o cadastro. Código de empresa ou e-mail existente é recusado, sem promover clientes ou redefinir senhas. O cadastro local exige acesso às credenciais do banco e não deve ser exposto como uma ação pública no site.

Depois, entre na aplicação normal com a nova conta. Em **Minha conta**, confira o nome da empresa. O catálogo administrativo começa vazio; cadastre os equipamentos dessa empresa. O bootstrap antigo continua disponível somente para criar o primeiro administrador da Empresa inicial.

## Regras de acesso e concorrência

- Empresa é obtida do vínculo do administrador ou do equipamento, nunca de um campo livre recebido do navegador.
- Listas, contagens, filtros e detalhes administrativos ficam no escopo da empresa. Cliente continua vendo somente suas próprias reservas, inclusive históricas.
- Vínculo administrativo inativo, empresa inativa ou conta inativa impedem administração, mesmo com autenticação antiga. Empresas inativas não aparecem no catálogo do cliente nem aceitam novas reservas; o histórico do cliente continua consultável. Não há tela de desativação/reativação de empresas ou vínculos nesta entrega.
- A tabela `company_customer` guarda o bloqueio comercial por empresa. O administrador só vê clientes vinculados à sua empresa e não desativa a conta global deles. O cadastro de clientes pelo administrador é mantido como fluxo transitório; o cadastro pessoal público será implementado em M2.
- Escritas administrativas mantêm bloqueios compartilhados da empresa, vínculo e conta administrativa antes dos bloqueios de cliente/equipamento/reserva. Criação de reserva bloqueia empresa → cliente → equipamento. Bloquear novos aluguéis de um cliente usa empresa → cliente e verifica pendências somente naquela empresa. O bloqueio compartilhado permite operações simultâneas e faz uma desativação concorrente aguardar a transação em andamento.
- A reserva guarda a empresa derivada da unidade. Uma chave estrangeira composta impede associar uma reserva à empresa de outro equipamento. Equipamento não pode ser transferido de empresa pela aplicação.
- O provisionamento local serializa cadastros pelo bloqueio da tabela de empresas; não há permissão administrativa global na interface.

## Verificações

Com Docker iniciado:

```powershell
.\mvnw.cmd clean verify
```

Para executar os testes de integração novos, além dos testes unitários/MVC:

```powershell
.\mvnw.cmd '-Dit.test=CompanyIT,CompanyMigrationIT' verify
```

Resultados e pendências estão em [VALIDACOES.md](VALIDACOES.md).
