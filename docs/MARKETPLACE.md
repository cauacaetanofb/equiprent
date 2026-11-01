# Busca, solicitações e aprovação de reservas

## Busca e acesso do cliente

- `/catalogo` e `/catalogo/{id}` mostram somente empresas ativas e equipamentos operacionais. Dados públicos: nome, descrição, categoria, empresa, cidade/UF e diária. Códigos patrimoniais, contas e históricos não integram essa saída.
- Nome, categoria, cidade e UF podem ser combinados. A cidade aceita parte do nome e ignora maiúsculas/minúsculas; não é busca geográfica por distância. Ordenação por nome ou diária, com paginação de até 48 itens e desempate por ID.
- Sem datas, o catálogo permite explorar os equipamentos sem afirmar disponibilidade. Com ambas as datas, retorna somente unidades disponíveis e calcula o total por dias inclusivos. Reservas sobrepostas, uso vencido e condição física seguem as mesmas regras da confirmação.
- A tela inicial leva à pesquisa. Nos detalhes, o visitante consulta datas e escolhe **Entrar para continuar**. Equipamento, período e filtros permanecem na sessão durante login, erro de senha e cadastro.
- `/cadastro` recebe nome, e-mail, senha e confirmação; cria exclusivamente CLIENTE, normaliza e-mail e grava hash de senha. Não cria empresa, vínculo administrativo ou reserva. Após cadastrar, é necessário entrar com a nova conta.
- A continuação usa destinos relativos construídos pelo servidor. Parâmetros como `returnUrl`, `role` e `companyId` não concedem acesso nem controlam o redirecionamento.
- O administrador altera cidade/UF em **Minha conta → Localização da empresa**. A V3 mantém empresas antigas com ambos os campos vazios. Elas continuam no catálogo geral, mas não correspondem a filtros de localização até preencherem esses dados.

## Solicitação e negociação — M3

1. O cliente consulta equipamento e datas. A estimativa exibida fica registrada na sessão por até 30 minutos. A submissão identifica essa estimativa; diária e total não são aceitos do navegador. Se o preço mudar antes do envio, é preciso consultar novamente.
2. **Solicitar reserva deste período** registra `REQUESTED` (**Aguardando aprovação**). O envio aceita explicitamente os valores exibidos da proposta inicial. Não bloqueia o equipamento nem garante disponibilidade; outra pessoa pode solicitar o mesmo período.
3. A empresa pode aprovar os valores iniciais aceitos ou enviar uma nova proposta de diária e condições textuais. O servidor calcula o total pelos dias inclusivos. Cada alteração cria uma versão com autoria e horário, preservando as anteriores.
4. Mudança de preço ou condições exige **Aceitar proposta atual** pelo titular. Aceite de uma versão anterior não vale para a atual. A aceitação ainda não confirma o aluguel.
5. Somente um administrador ativo da empresa pode **Aprovar e confirmar reserva**. Na transação, o servidor verifica empresa, vínculo, administrador, cliente, bloqueio comercial, período, condição da unidade, disponibilidade e versão aceita. O acordo e o evento são gravados juntos; falha reverte ambos.
6. A aprovação muda para `RESERVED`, fixa diária, total e condições do acordo e bloqueia o período. A estimativa inicial continua preservada. Edições futuras no catálogo, retirada e devolução não mudam o valor contratado.

Solicitação pendente repetida para o mesmo cliente, unidade e datas retorna o registro existente, sem alterar sua proposta ou aceite. Reenvio do mesmo formulário também retorna o mesmo registro, mesmo após encerramento. Uma nova tentativa depois de cancelamento exige consultar e enviar outro formulário.

A empresa pode **Recusar solicitação** com motivo obrigatório (`REJECTED`); o titular pode cancelar (`CANCELLED`). O histórico permanece. Reservas já confirmadas continuam canceláveis antes da retirada; o administrador precisa informar o motivo. Depois da retirada, segue o fluxo de devolução.

Pedidos não expiram automaticamente. Quando a data inicial passa, a tela sinaliza **Prazo para aprovação encerrado** e impede proposta, aceite e aprovação. Ainda é possível o cliente cancelar ou a empresa recusar. Datas não podem ser editadas: para outro período, faça nova solicitação.

Sob aprovações simultâneas conflitantes, uma confirma e a outra informa indisponibilidade, preservando o pedido pendente. O histórico de propostas é paginado, com dez versões por página. Outros clientes e outras empresas não acessam a negociação, nem por ID ou formulário adulterado.

O chat pertence à M4. Não há taxas, caução, cobrança ou multa automática. Confirmação de e-mail, recuperação de acesso e proteção contra abuso precisam de entregas próprias antes da publicação.

## Testar localmente

Com JDK 21 e Docker Desktop iniciado, na raiz:

```powershell
.\mvnw.cmd clean verify
.\scripts\Import-LocalEnv.ps1
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

O Flyway aplica a V4 ao iniciar sobre o banco existente. Contratos anteriores mantêm estados, períodos, valores e eventos, sem propostas ou aceites retroativos inventados. Não apague o volume nem recrie contas para atualizar.

1. Entre como administrador e preencha cidade/UF em **Minha conta → Localização da empresa**. Cadastre um equipamento operacional.
2. Para comparar empresas, use outra empresa real cadastrada pelo [procedimento local](EMPRESAS.md#cadastrar-outra-empresa-pelo-powershell) e cadastre seus equipamentos com o respectivo administrador.
3. Em janela anônima, abra `/catalogo`, pesquise e compare os valores. Informe as duas datas e confira o total inclusivo.
4. Abra um resultado, escolha **Entrar para continuar**, use **Cadastre-se como cliente** e crie sua conta pessoal.
5. Entre e confira equipamento, datas e **Voltar aos resultados**. Envie a solicitação e confira **Aguardando aprovação**, sem bloqueio no catálogo.
6. Em outra sessão de navegador, entre como administrador da empresa. Em **Reservas**, abra o pedido, expanda **Enviar nova proposta** e informe diária e condições. Exemplo: três dias a R$ 40,00 devem totalizar R$ 120,00.
7. Como cliente, atualize os detalhes e aceite a nova proposta. Como administrador, atualize e aprove. Confira o valor contratado e a estimativa inicial preservada.
8. Crie dois pedidos sobrepostos antes de aprovar qualquer um. Aprove um: o outro deve indicar conflito, mantendo o histórico. Recuse-o com motivo ou cancele como titular. Confira também as opções de cancelamento, retirada e devolução no contrato confirmado.
9. Com outro cliente ou administrador de outra empresa, tente abrir a URL privada da solicitação: o acesso deve ser negado, sem revelar os dados.

Os testes automatizados usam bancos descartáveis e incluem migração V3 → V4, aceites obsoletos, duplicidade, aprovações concorrentes, cancelamento, manutenção, desativação, CSRF e isolamento. Resultados e pendências estão em [VALIDACOES.md](VALIDACOES.md).
