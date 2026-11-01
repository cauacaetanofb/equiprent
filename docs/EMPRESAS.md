# Empresas e permissões — M1

A M1 adiciona empresas à aplicação existente. Cada administrador tem uma conta individual e um vínculo com uma empresa. O cliente mantém uma conta pessoal e pode ter reservas em empresas diferentes.

Nesta etapa, cada conta administrativa pertence a uma única empresa; várias contas podem pertencer à mesma empresa. Convites de funcionários, troca de empresa na sessão e painel global da plataforma ainda não fazem parte da interface. O procedimento local abaixo cadastra uma nova empresa com seu primeiro administrador, usando um e-mail ainda não cadastrado.

O catálogo continua exigindo login e o cliente ainda confirma diretamente suas reservas. Busca pública, solicitações sujeitas a aprovação, negociação e chat estão nas etapas M2 a M4 do [planejamento](../PLANEJAMENTO.md).

## Atualizar o ambiente existente

Pré-requisitos: JDK 21, Docker Desktop iniciado e `.env` normal configurado. Na raiz do projeto:

```powershell
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

Na inicialização, Flyway aplica a nova `V2__add_company_ownership.sql`. A V1 permanece intacta. A migração cria a **Empresa inicial**, associa os equipamentos/reservas existentes a ela e vincula os administradores atuais. Contas, senhas, IDs, períodos, valores, estados e eventos anteriores são preservados. Clientes existentes recebem vínculo comercial com essa empresa; contas globalmente inativas continuam inativas.

Para escolher o nome dessa empresa, adicione ao `.env`:

```dotenv
INITIAL_COMPANY_NAME=Minha locadora
```

Essa configuração atualiza somente o nome da empresa de código interno `INITIAL` na inicialização normal. Se estiver ausente/vazia, mantém o nome existente. Não muda credenciais nem renomeia outras empresas. O perfil demo ignora esse procedimento de configuração.

## Cadastrar outra empresa pelo PowerShell

Com o banco normal iniciado, execute:

```powershell
.\scripts\Register-Company.ps1 -Code LOCADORA_B -Name "Locadora B" -AdminName "Responsável B" -AdminEmail "responsavel.b@example.com"
```

Substitua os valores pelos dados desejados. O script solicita a senha sem mostrá-la; ela deve ter de 12 a 64 caracteres. A senha não é passada na linha de comando nem salva em arquivo pelo script. Use um código estável, em letras maiúsculas, números, hífen ou sublinhado, com até 50 caracteres.

O script carrega o `.env`, executa a aplicação sem servidor HTTP e restaura as variáveis de ambiente e o diretório ao terminar. Pode receber `-EnvFile "caminho\ambiente.env"` para um ambiente específico. Empresa, conta e vínculo são criados na mesma transação; falha reverte o cadastro. Código de empresa ou e-mail existente é recusado, sem promover clientes ou redefinir senhas. O cadastro local exige acesso às credenciais do banco e não deve ser exposto como uma ação pública no site.

Depois, entre na aplicação normal com a nova conta. Em **Minha conta**, confira o nome da empresa. O catálogo administrativo começa vazio; cadastre os equipamentos dessa empresa. O bootstrap antigo continua disponível somente para criar o primeiro administrador da Empresa inicial.

## Experimentar duas empresas sem apagar a demo antiga

```powershell
.\scripts\Start-Demo.ps1 -Instance marketplace
```

Abra **http://localhost:8082**. Essa instância usa o projeto Compose `equiprent-demo-marketplace`, volume próprio, banco na porta `5441` e credenciais em `.local/demo-marketplace.env`. A demo original (`8081`/`5440`) e seu volume são preservados. Instâncias nomeadas usam `8082`/`5441` como padrão; para executar várias delas ao mesmo tempo, ajuste as portas nos respectivos arquivos locais.

| Conta fictícia | Acesso |
| --- | --- |
| `admin@equiprent.example` | Administrador da Locadora Demo A |
| `empresa.b@equiprent.example` | Administrador da Locadora Demo B |
| `marina@equiprent.example` | Cliente com reservas nas duas empresas |
| `rafael@equiprent.example` | Outro cliente |

A senha está em `DEMO_ACCOUNT_PASSWORD` no arquivo local dessa instância:

```powershell
notepad .\.local\demo-marketplace.env
```

Uma carga nova cria duas empresas, quatro contas, oito equipamentos e oito reservas, com 13 eventos. As duas empresas têm uma unidade de código `NB-001`, com preços e históricos independentes. Uma demo já semeada pela versão anterior mantém seus registros e suas contas; reiniciar não adiciona automaticamente a segunda empresa nem reinicializa a carga.

Roteiro manual:

1. Entre como administrador A; confirme o nome da empresa e seus equipamentos/reservas.
2. Saia e entre como administrador B; confira somente sua unidade e sua reserva. Tentar abrir detalhes administrativos de uma reserva da empresa A deve resultar em 404.
3. Entre como Marina; confira seus próprios registros nas duas empresas e os nomes das empresas no catálogo e nas reservas.
4. Cadastre um equipamento em uma empresa; o mesmo código pode existir na outra, mas não pode se repetir dentro da mesma empresa.
5. Para testar bloqueio de cliente, resolva as reservas pendentes dele naquela empresa e use **Bloquear nesta empresa**. O cliente continua entrando e consultando o histórico; novos aluguéis nessa empresa são recusados e os de outra empresa continuam permitidos.

Use `Ctrl+C` para encerrar a aplicação. Para parar somente essa demonstração, preservando os dados:

```powershell
docker compose -p equiprent-demo-marketplace -f compose.demo.yaml --env-file .local/demo-marketplace.env down
```

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

Em 01/11/2026, a suite completa passou em JDK 21: **296 testes**, sendo 14 unitários/MVC e 282 de integração. Os 20 novos casos verificam isolamento por empresa, contagens/filtros, adulteração de IDs, bloqueio comercial local, vínculos inativos, provisionamento atômico/concorrente e migração de um banco real na V1 para V2. Usam PostgreSQL descartável, sem acessar o `.env` nem o banco de desenvolvimento. Os testes anteriores continuam cobrindo disponibilidade, concorrência de reservas, cancelamento, retirada/devolução, segurança e a demo.

Execução remota no GitHub Actions e publicação continuam pendentes. Capturas e vídeo já existentes em `docs/images` e `docs/media` representam a versão anterior; a atualização completa do material do marketplace está prevista em M5.
