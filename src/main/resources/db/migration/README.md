# Migrações do banco

`V1__create_initial_schema.sql` cria `app_user`, `equipment`, `reservation` e
`reservation_event`, com chaves, restrições e índices. Ela não insere usuários,
senhas ou dados de demonstração.

`V2__add_company_ownership.sql` adiciona empresas e vínculos, preservando os
registros anteriores na Empresa inicial. `V3__add_company_location.sql` adiciona
cidade e UF opcionais, preenchidas juntas e sem localização automática.

`V4__add_reservation_requests_and_offers.sql` acrescenta solicitações pendentes,
recusas, propostas versionadas, aceites e valores do acordo aprovado. Os valores
dos contratos anteriores são copiados sem mudar estados nem inventar aceites.

`V5__add_reservation_chat.sql` cria `chat_message`, com autor, texto, horário,
chave de submissão por conversa/autor e índices para histórico e frequência.
Nenhuma conta, solicitação, proposta, contrato ou evento anterior é alterado.

Ao iniciar a aplicação, o Flyway aplica as migrações pendentes e registra em
`flyway_schema_history`. Nas próximas inicializações, valida os checksums e não
reaplica as migrações. Usar `V6__...sql` para alterações posteriores; não editar
migrações já aplicadas nem apagar o volume para atualizar o esquema.

O Hibernate está configurado com `ddl-auto=validate`. O Flyway é responsável
por modificar o esquema. Os testes `PersistenceIT`, `CompanyMigrationIT` e
`CompanyLocationMigrationIT`, `ReservationNegotiationMigrationIT` e `ChatMigrationIT` verificam instalação limpa, atualização de V1/V2/V3/V4,
preservação dos dados e segunda inicialização em PostgreSQL 17 descartável.
