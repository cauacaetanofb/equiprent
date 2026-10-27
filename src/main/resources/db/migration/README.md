# Migrações do banco

Esta pasta ainda não contém migrações SQL nem tabelas de domínio.

Na etapa de modelagem, adicionar `V1__create_initial_schema.sql` e validar com
PostgreSQL. Usar novas versões para alterações posteriores; não editar migrações
já aplicadas. O Flyway ignora este Markdown, mas sua presença mantém a pasta
nos recursos empacotados.

O Hibernate está configurado com `ddl-auto=validate`. O Flyway é responsável
por modificar o esquema e pode criar sua tabela técnica `flyway_schema_history`
mesmo antes da primeira migração de domínio.
