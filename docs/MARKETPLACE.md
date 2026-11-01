# Busca pública e cadastro de clientes

## Comportamento atual — M2

- `/catalogo` e `/catalogo/{id}` mostram somente empresas ativas e equipamentos operacionais. Dados públicos: nome, descrição, categoria, empresa, cidade/UF e diária. Códigos patrimoniais, contas e históricos não integram essa saída.
- Nome, categoria, cidade e UF podem ser combinados. A cidade aceita parte do nome e ignora maiúsculas/minúsculas; não é busca geográfica por distância. Ordenação por nome ou diária, com paginação de até 48 itens e desempate por ID.
- Sem datas, o catálogo permite explorar os equipamentos sem afirmar disponibilidade. Com ambas as datas, retorna somente unidades disponíveis e calcula o total por dias inclusivos. Reservas sobrepostas, uso vencido e condição física seguem as mesmas regras da confirmação.
- A tela inicial leva à pesquisa. Nos detalhes, o visitante consulta datas e escolhe **Entrar para continuar**. Equipamento, período e filtros permanecem na sessão durante login, erro de senha e cadastro.
- `/cadastro` recebe nome, e-mail, senha e confirmação; cria exclusivamente CLIENTE, normaliza e-mail e grava hash de senha. Não cria empresa, vínculo administrativo ou reserva. Após cadastrar, é necessário entrar com a nova conta.
- A continuação usa destinos relativos construídos pelo servidor. Parâmetros como `returnUrl`, `role` e `companyId` não concedem acesso nem controlam o redirecionamento.
- O administrador altera cidade/UF em **Minha conta → Localização da empresa**. A V3 mantém empresas antigas com ambos os campos vazios. Elas continuam no catálogo geral, mas não correspondem a filtros de localização até preencherem esses dados.

O cliente ainda confirma a reserva diretamente. Aprovação da empresa, negociação estruturada e chat pertencem às próximas etapas. Cadastro com confirmação de e-mail, recuperação de acesso e proteção contra abuso para operação pública precisam de entregas próprias antes da publicação. Não há envio de e-mails nesta etapa.

## Testar localmente

Com JDK 21 e Docker Desktop iniciado, na raiz:

```powershell
.\mvnw.cmd clean verify
.\scripts\Import-LocalEnv.ps1
docker compose up -d --wait postgres
.\scripts\Start-Local.ps1
```

O Flyway aplica a V3 ao iniciar sobre o banco existente. Não é necessário apagar volume ou recriar contas.

1. Entre como administrador e preencha cidade/UF em **Minha conta → Localização da empresa**. Cadastre um equipamento operacional.
2. Para comparar empresas, use outra empresa real cadastrada pelo [procedimento local](EMPRESAS.md#cadastrar-outra-empresa-pelo-powershell) e cadastre seus equipamentos com o respectivo administrador.
3. Em janela anônima, abra `/catalogo`, pesquise e compare os valores. Informe as duas datas e confira o total inclusivo.
4. Abra um resultado, escolha **Entrar para continuar**, use **Cadastre-se como cliente** e crie sua conta pessoal.
5. Entre e confira se o equipamento, as datas e o link **Voltar aos resultados** preservaram a pesquisa. A reserva só nasce após a confirmação explícita.
6. Como administrador, confira que sua localização e os dados administrativos continuam restritos à sua empresa. Os dados públicos das outras empresas podem ser consultados no catálogo.

Os testes automatizados usam bancos descartáveis, incluindo cadastro concorrente com e-mail repetido, alteração concorrente da localização, ausência de privilégios administrativos no cadastro e disponibilidade alterada durante o login. Resultados e pendências estão em [VALIDACOES.md](VALIDACOES.md).
