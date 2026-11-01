ALTER TABLE company ADD COLUMN city varchar(120);
ALTER TABLE company ADD COLUMN state varchar(2);
ALTER TABLE company ADD CONSTRAINT ck_company_location CHECK (
    (city IS NULL AND state IS NULL) OR
    (city IS NOT NULL AND length(btrim(city)) > 0 AND state IS NOT NULL AND
     state IN ('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA',
               'PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'))
);
CREATE INDEX idx_company_location ON company(state, lower(city)) WHERE active;
