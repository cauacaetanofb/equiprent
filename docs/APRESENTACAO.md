# Apresentação do EquipRent em 3–5 minutos

Use o vídeo ou a aplicação local enquanto fala. A proposta abaixo dura cerca de quatro minutos, com espaço para navegar e receber perguntas. Adapte as frases ao seu jeito de explicar.

## 0:00–0:35 — Problema e escopo

“O EquipRent organiza o aluguel de unidades físicas de equipamentos. Uma empresa precisa saber quem reservou cada item, em qual período, se ele já saiu e se foi devolvido. Eu implementei esse fluxo com dois perfis: o cliente cuida das próprias reservas; o administrador mantém os cadastros e registra retirada e devolução.”

Mostre o catálogo. Explique que dois notebooks iguais são duas unidades com códigos diferentes. O escopo é uma empresa pequena, sem pagamentos ou carrinho nesta versão.

## 0:35–1:15 — Escolhas de arquitetura

“Usei Java 21, Spring Boot, Thymeleaf e PostgreSQL em um monólito. Isso permite manter a autenticação por sessão e renderizar as páginas sem duplicar a aplicação em um frontend separado. Organizei o código por funcionalidade. Os controllers recebem formulários; os services aplicam autorização, regras e transações; os repositories acessam o banco.”

Mostre brevemente o diagrama do README. Diga que Flyway controla as migrações e Hibernate valida o esquema. O teste usa PostgreSQL real em Testcontainers porque as garantias dependem do comportamento desse banco.

## 1:15–2:15 — Regra principal: disponibilidade e concorrência

“Estar operacional não significa estar disponível. A consulta considera o período, as reservas pendentes e os equipamentos em uso atrasado. Uma consulta também não segura a unidade: duas pessoas podem consultar a mesma disponibilidade antes de confirmar.”

Mostre a estimativa e o trecho do vídeo em que outra sessão confirma primeiro.

“Na confirmação, a transação bloqueia a linha do cliente e depois a do equipamento. Depois da espera, os dados são recarregados e a disponibilidade é verificada novamente. Só então salvo reserva e evento juntos. Há um teste com duas threads e transações reais que exige uma confirmação e uma recusa. Se a gravação do evento falhar, a reserva também é revertida.”

Se perguntarem sobre alternativas, explique que um bloqueio Java protegeria somente um processo. Uma restrição de exclusão no PostgreSQL poderia reforçar a defesa; nesta versão, todos os services de escrita compartilham o mesmo protocolo de bloqueios.

## 2:15–3:05 — Dinheiro, tempo e histórico

Mostre a reserva e sua linha do tempo.

“As diárias são inclusivas: hoje até depois de amanhã são três diárias. Uso `BigDecimal` e copio a diária e o total para a reserva. Alterar o catálogo depois não reescreve o preço contratado. Os limites de dia usam São Paulo e um `Clock` injetável, então consigo testar meia-noite sem depender do relógio da máquina.”

“Retirada e devolução são transições de estado autorizadas ao ADMIN. Cada uma registra autor e horário. A devolução pode encaminhar a unidade para manutenção, atomicamente. Reservas futuras afetadas permanecem visíveis para decisão administrativa; não são canceladas silenciosamente.”

## 3:05–3:45 — Segurança e evidência

“Ocultar botões não é autorização. As rotas e os services validam perfil e atividade; a consulta do cliente também restringe pelo proprietário da reserva. Um ID de outro cliente retorna 404. Os formulários não aceitam dono, papel ou preço histórico enviados pelo navegador. Senhas usam PBKDF2, há proteção CSRF e logout por POST.”

Mostre o comando `clean verify` e os relatórios ou o resultado do CI realmente disponível. Nesta entrega foram 276 testes aprovados localmente em JDK 21. Diferencie teste local de execução remota no GitHub; só afirme que o workflow passou após conferir seu resultado.

## 3:45–4:15 — Limites e próximos passos

“A entrega cobre o ciclo de locação, com perfil demo separado e dados reproduzíveis. Ainda não inclui pagamento, multas ou recuperação de senha. A interface foi revisada em desktop e 360 px, mas a auditoria automatizada não substitui leitores de tela e outros navegadores. Eu priorizaria recuperação de acesso, notificações e observabilidade conforme a necessidade real.”

Finalize apontando para README, demonstração e teste concorrente, para que outra pessoa possa reproduzir as evidências.

## Perguntas para ensaiar

| Pergunta | Ponto central da resposta |
| --- | --- |
| Por que não JWT? | O navegador usa o mesmo monólito; sessão e CSRF atendem esse fluxo sem introduzir gerenciamento de tokens. |
| Por que não um campo `disponivel`? | Disponibilidade depende do período consultado e das pendências, não de um estado fixo da unidade. |
| Por que bloquear o equipamento? | Ele existe mesmo quando ainda não há reservas, evitando a corrida de duas inserções em um conjunto vazio. |
| Como evita deadlock? | Ordem consistente cliente → equipamento → reserva; operações que começam no equipamento não bloqueiam cliente depois. Há tratamento de falhas transitórias. |
| Como mantém preço correto? | Leitura sob bloqueio, snapshot com `BigDecimal` e valor imutável após criação. |
| Por que não apagar registros? | Desativação/aposentadoria preservam vínculos e histórico; FKs impedem exclusões inconsistentes. |
| Por que Testcontainers? | Exercita constraints, locks, transações e Flyway no mesmo banco usado pela aplicação. |
| O que você ainda não garante? | Escritas externas precisam respeitar o protocolo; não há certificação de acessibilidade nem operação de produção preparada. |
