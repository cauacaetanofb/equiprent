# Preparação para clientes reais

Este documento organiza o trabalho posterior à revisão funcional da M5. As entregas abaixo são propostas, não funcionalidades implementadas. A hospedagem continua adiada, e cada implementação deve ter seu escopo aprovado antes de começar.

## O que existe hoje

- Busca pública por equipamento, cidade/UF e período; cadastro pessoal e login por sessão.
- Empresas e administradores provisionados localmente, com dados administrativos separados por empresa.
- Solicitação, chat privado, proposta com aceite, aprovação, cancelamento/recusa, retirada e devolução.
- Valores contratados e históricos preservados. Mensagens novas são permitidas por sete dias após o encerramento; **esse prazo não apaga o histórico**.
- Migrações versionadas, testes com bancos descartáveis e workflow de verificação no GitHub Actions. O resultado remoto deve ser conferido após o push.

O `.env` e o Compose atuais atendem à execução local. Não definem, por si só, a configuração de um serviço publicado. Resultados efetivamente verificados e limitações estão em [VALIDACOES.md](VALIDACOES.md).

## Ordem proposta antes de um piloto público

| Prioridade | Entrega a definir e implementar | Evidência necessária para concluir |
| --- | --- | --- |
| 1 — Acesso | Recuperação de senha e confirmação de e-mail, com escolha do serviço de envio e do remetente. | Percurso de solicitação e recebimento testado; links com prazo e uso único; reenvio e falha de entrega tratados; conta alheia não pode ser alterada; política para sessões já abertas definida e testada. |
| 2 — Proteção contra abuso | Limites para login, cadastro, recuperação, criação de solicitações e consulta/envio do chat. O limite atual do chat cobre somente envios por participante/conversa. | Tentativas repetidas e concorrentes limitadas; resposta clara ao usuário; funcionamento verificado com a quantidade de instâncias planejada; ausência de dados privados em mensagens de erro e logs. |
| 3 — Dados e privacidade | Definir quais dados são necessários, quem pode consultá-los, por quanto tempo ficam guardados e como atender solicitações sobre conta e dados. | Política documentada, procedimentos de atendimento e retenção testados; conciliação entre histórico de aluguel, mensagens e encerramento da conta; responsabilidades da plataforma e empresas revisadas. |
| 4 — Recuperação do serviço | Rotina de backup, destino protegido, prazo de guarda e metas de perda aceitável de dados e tempo de recuperação. | Restauração de uma cópia em banco separado, com conferência de contas, contratos, mensagens e migrações. A existência do arquivo de backup sozinha não conclui esta entrega. |
| 5 — Publicação e acompanhamento | Escolher hospedagem/domínio e configurar HTTPS, proxy, cookies, segredos, acesso ao banco, logs e alertas. Definir atualização e recuperação após falha. | Ambiente de homologação testado; credenciais separadas e bootstrap desativado; banco sem acesso público desnecessário; sessão e redirecionamentos verificados por HTTPS; teste de indisponibilidade e recuperação documentado. |
| 6 — Atendimento e regras comerciais | Definir entrada/saída de empresas, responsáveis pelo suporte, retirada/entrega, tratamento de disputas e modelo de receita. | Procedimentos aprovados e contatos de suporte disponíveis; telas e condições consistentes com as decisões. Cobrança, comissão, caução e multa só entram após definição explícita. |

As prioridades indicam uma ordem de trabalho proposta. Todos os itens aplicáveis ao piloto precisam de revisão antes de clientes reais. Nenhuma data de lançamento foi assumida.

## Próxima implementação proposta

Começar pelo acesso: recuperação de senha e confirmação de e-mail. Antes de implementar, apresentar telas, regras de validade dos links, comportamento para contas atuais, serviço de envio, configuração e testes. A escolha do provedor e os custos dependem de uma decisão própria; nenhum serviço foi contratado ou conectado nesta etapa.

A implementação deve permitir teste local sem enviar mensagens para pessoas reais. As mudanças seguintes em proteção, dados, backups e publicação também terão escopos próprios.

## Decisões ainda abertas

- Quem pode cadastrar uma empresa e como verificar, suspender ou regularizar seu acesso.
- Endereço e horários de retirada, entrega, área atendida e responsabilidade pelo equipamento.
- Modelo de receita e condições comerciais; o sistema atual registra preços, mas não processa pagamentos.
- Regras de retenção de contas, negociações, mensagens e backups, incluindo quem atende pedidos sobre dados.
- Hospedagem, domínio, serviço de e-mail, metas de recuperação e responsável por incidentes.
- Visibilidade do repositório e licença do produto, preservando as licenças de componentes de terceiros.

## Conferência antes de publicar

- [ ] Entregas aprovadas e critérios acima atendidos em homologação.
- [ ] Fluxo completo e isolamento entre empresas repetidos sobre a versão candidata à publicação.
- [ ] GitHub Actions aprovado nessa versão; dependências e configuração de segurança revisadas.
- [ ] Navegadores escolhidos para suporte e tecnologias assistivas conferidos; limitações documentadas.
- [ ] Backup restaurado e procedimento de incidente exercitado.
- [ ] Regras comerciais, atendimento e tratamento de dados definidos para o piloto.
- [ ] Autorização explícita para publicação.

O acompanhamento do desenvolvimento permanece em [PLANEJAMENTO.md](../PLANEJAMENTO.md). O roteiro funcional está em [MARKETPLACE.md](MARKETPLACE.md).
