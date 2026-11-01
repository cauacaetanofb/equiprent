# Empresas e permissões

Cada administrador tem uma conta individual e um vínculo com uma empresa. O cliente mantém uma conta pessoal e pode ter reservas em empresas diferentes.

Cada conta administrativa pertence a uma única empresa; várias contas podem pertencer à mesma empresa. Convites de funcionários, troca de empresa na sessão e painel global da plataforma ainda não fazem parte da interface. O procedimento local abaixo cadastra uma nova empresa com seu primeiro administrador, usando um e-mail ainda não cadastrado.

O catálogo público em `/catalogo` permite pesquisar sem login, e o cliente pode criar sua própria conta em `/cadastro`. O cliente envia solicitações e aceita propostas; somente a empresa aprova e confirma a reserva. O chat continua previsto para M4 no [planejamento](../PLANEJAMENTO.md).

## Atualizar o ambiente existente

Pré-requisitos: JDK 21, Docker Desktop iniciado e `.env` normal configurado. Na raiz do projeto:

```powershell
.\scripts\Import-LocalEnv.ps1
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

Na inicialização, Flyway aplica as migrações pendentes. A V2 cria a **Empresa inicial**, associa os equipamentos/reservas existentes a ela e vincula os administradores atuais. A V3 adiciona cidade e UF opcionais. A V4 acrescenta solicitações e propostas, copiando os valores dos contratos anteriores sem criar aceites retroativos. V1 a V3 permanecem intactas. Contas, senhas, IDs, períodos, valores, estados e eventos anteriores são preservados. Clientes existentes recebem vínculo comercial com essa empresa; contas globalmente inativas continuam inativas.

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

## Publicar cidade e UF

Em **Minha conta → Localização da empresa**, preencha cidade e UF. Os dados aparecem no catálogo público e passam a participar dos filtros de localização. Somente o administrador ativo da própria empresa pode alterá-los; a empresa é obtida da sessão.

Empresas anteriores à V3 continuam válidas e aparecem como **Localização não informada** até preencherem os campos. Cidade e UF são preenchidas ou apagadas juntas. A localização informa a cidade da empresa; não representa endereço de retirada nem área de entrega.

## Regras de acesso e concorrência

- Empresa é obtida do vínculo do administrador ou do equipamento, nunca de um campo livre recebido do navegador.
- Listas, contagens, filtros e detalhes administrativos ficam no escopo da empresa. Cliente continua vendo somente suas próprias reservas, inclusive históricas.
- Vínculo administrativo inativo, empresa inativa ou conta inativa impedem administração, mesmo com autenticação antiga. Empresas inativas não aparecem no catálogo do cliente nem aceitam novas reservas; o histórico do cliente continua consultável. Não há tela de desativação/reativação de empresas ou vínculos nesta entrega.
- A tabela `company_customer` guarda o bloqueio comercial por empresa. O administrador só vê clientes vinculados à sua empresa e não desativa a conta global deles. O cadastro pessoal público cria somente uma conta CLIENTE; o vínculo comercial nasce na primeira solicitação enviada. O cadastro pelo administrador continua disponível.
- Pedidos `REQUESTED` não impedem o bloqueio comercial, mas ficam sem possibilidade de aceite, nova proposta ou aprovação enquanto o bloqueio persistir. O cliente ainda pode cancelar e a empresa pode recusar. Reservas confirmadas/em uso precisam ser resolvidas antes do bloqueio.
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
