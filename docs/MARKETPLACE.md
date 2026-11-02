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

Não há taxas, caução, cobrança ou multa automática. Confirmação de e-mail, recuperação de acesso e proteção contra abuso para operação pública precisam de entregas próprias antes da publicação.

## Conversa por solicitação — M4

- A seção **Conversa com a empresa/o cliente** aparece nos detalhes da solicitação ou reserva. Somente o titular e os administradores ativos daquela empresa leem e enviam mensagens. Cada mensagem identifica autor, participante e horário de São Paulo.
- Mensagens de texto têm até 2000 caracteres. O limite é de dez mensagens por participante em cada conversa, em uma janela móvel de 60 segundos. Autor, horário, empresa e reserva são determinados no servidor; outros campos enviados pelo navegador não concedem acesso.
- As últimas 30 mensagens aparecem em ordem cronológica, em uma área com rolagem acessível por teclado. **Carregar mensagens anteriores** busca mais 30 por vez, preservando a posição de leitura. Novas mensagens acompanham o final quando ele já está visível; a leitura de trechos anteriores não é interrompida. A atualização automática consulta novas mensagens a cada cinco segundos enquanto a página está visível, sem substituir rascunhos de mensagem ou proposta. Há também **Atualizar conversa**.
- Em falha de conexão ou resposta perdida, o texto continua na mesma página e pode ser reenviado sem duplicar a mensagem já recebida. O rascunho não é armazenado permanentemente; sair, recarregar ou encerrar a sessão pode descartá-lo. Se a sessão/permissão mudar, a atualização é interrompida e a tela orienta entrar novamente.
- Com JavaScript desativado, envio por formulário, consulta e histórico continuam disponíveis; a atualização é manual. Mensagens são exibidas como texto, sem executar HTML ou scripts. Não há anexos, edição ou exclusão de mensagens pela aplicação.
- A conversa fica aberta durante `REQUESTED`, `RESERVED` e `IN_USE`. Um pedido com início passado ainda pode ser discutido, embora continue sem possibilidade de aprovação. Recusa, cancelamento ou devolução permitem novos envios até o instante do encerramento mais sete dias (168 horas). A partir desse limite, o histórico fica somente para leitura. Conta/empresa/vínculo inativos e bloqueio comercial impedem novos envios; a consulta mantém as permissões já existentes para o histórico.
- Conversar não altera preços, datas, aceite, estado ou disponibilidade. O acordo continua sendo registrado pelas ações de proposta, aceite e aprovação.

O envio bloqueia empresa/vínculo/administrador → cliente → reserva; não bloqueia o equipamento. O bloqueio da reserva protege contagem, prazo e inclusão contra envios simultâneos entre instâncias. A chave de submissão é única por autor e conversa; repetir o mesmo envio mantém o registro original. A V5 acrescenta somente a tabela `chat_message` e índices, sem modificar os registros anteriores.

## Testar localmente

Com JDK 21 e Docker Desktop iniciado, na raiz:

```powershell
.\mvnw.cmd clean verify
.\scripts\Import-LocalEnv.ps1
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

O Flyway aplica a V5 ao iniciar sobre o banco existente. Solicitações, contratos, propostas, aceites e eventos anteriores são preservados. Não apague o volume nem recrie contas para atualizar.

1. Entre como administrador e preencha cidade/UF em **Minha conta → Localização da empresa**. Cadastre um equipamento operacional.
2. Para comparar empresas, use outra empresa real cadastrada pelo [procedimento local](EMPRESAS.md#cadastrar-outra-empresa-pelo-powershell) e cadastre seus equipamentos com o respectivo administrador.
3. Em janela anônima, abra `/catalogo`, pesquise e compare os valores. Informe as duas datas e confira o total inclusivo.
4. Abra um resultado, escolha **Entrar para continuar**, use **Cadastre-se como cliente** e crie sua conta pessoal.
5. Entre e confira equipamento, datas e **Voltar aos resultados**. Envie a solicitação e confira **Aguardando aprovação**, sem bloqueio no catálogo.
6. Em outra sessão de navegador, entre como administrador da empresa. Em **Reservas**, abra o pedido, expanda **Enviar nova proposta** e informe diária e condições. Exemplo: três dias a R$ 40,00 devem totalizar R$ 120,00.
7. Como cliente, atualize os detalhes e aceite a nova proposta. Como administrador, atualize e aprove. Confira o valor contratado e a estimativa inicial preservada.
8. Crie dois pedidos sobrepostos antes de aprovar qualquer um. Aprove um: o outro deve indicar conflito, mantendo o histórico. Recuse-o com motivo ou cancele como titular. Confira também as opções de cancelamento, retirada e devolução no contrato confirmado.
9. Com outro cliente ou administrador de outra empresa, tente abrir a URL privada da solicitação: o acesso deve ser negado, sem revelar os dados.
10. Abra a mesma solicitação em duas sessões: cliente e administrador. Envie mensagens dos dois lados e aguarde até cinco segundos para vê-las. Deixe uma proposta ou mensagem parcialmente digitada e confira que ela permanece durante a atualização.
11. Confira autor/horário, histórico e o formulário sem JavaScript. Após cancelar ou recusar, confira o prazo exibido de sete dias. O fechamento desse prazo e as condições concorrentes são exercitados pelos testes automatizados com relógio controlado.

Os testes automatizados usam bancos descartáveis e incluem migrações V3 → V4 e V4 → V5, aceites obsoletos, duplicidade, concorrência, cancelamento, manutenção, desativação, CSRF, isolamento, paginação de mensagens e limites do chat. Resultados e pendências estão em [VALIDACOES.md](VALIDACOES.md).
