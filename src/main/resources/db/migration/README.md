# Migrações do banco

`V1__create_initial_schema.sql` cria `app_user`, `equipment`, `reservation` e
`reservation_event`, com chaves, restrições e índices. Ela não insere usuários,
senhas ou dados de demonstração.

Ao iniciar a aplicação, o Flyway aplica a V1 se ainda não estiver registrada em
`flyway_schema_history`. Nas próximas inicializações, valida o checksum e não
reaplica a migração. Usar `V2__...sql` para alterações posteriores; não editar
migrações já aplicadas nem apagar o volume para atualizar o esquema.

O Hibernate está configurado com `ddl-auto=validate`. O Flyway é responsável
por modificar o esquema. Os testes `PersistenceIT` aplicam a mesma migração em
PostgreSQL 17 descartável e validam também uma segunda inicialização.
