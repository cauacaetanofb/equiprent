# Identidade visual do EquipRent — v2

A interface segue o manual v2.0 e o kit fornecidos pelo responsável pelo produto em 02/11/2026. A base é branca ou grafite neutro, com fonte Inter, pesos moderados, separadores leves e amarelo para ações principais.

## Recursos e distribuição

| Arquivo | Responsabilidade |
| --- | --- |
| `src/main/resources/static/brand/tokens.css` | Cópia original dos tokens v2 e declaração da fonte local. |
| `src/main/resources/static/brand/fonts/Inter-variable.ttf` | Fonte variável Inter. |
| `src/main/resources/static/brand/fonts/OFL.txt` | Licença da fonte, preservada para distribuição. |
| `src/main/resources/static/brand/logos/` | Logos principal e reverso em SVG, com letras em curvas. |
| `src/main/resources/static/favicon.ico` | Favicon dedicado do kit. |
| `src/main/resources/static/css/app.css` | Integração com Bootstrap, composição e responsividade. |
| `src/main/resources/static/js/theme.js` | Escolha de tema e sincronização da preferência no navegador. |
| `src/main/resources/templates/fragments/layout.html` | Recursos, cabeçalho, seletor e rodapé compartilhados. |

O kit original fica em `equiprent-kit-v2/`, ignorado pela regra `/equiprent-kit-v2/` no `.gitignore`. Manual, fotos de referência, exportações alternativas e JSON de referência não são necessários para executar o aplicativo. Somente os seis arquivos efetivamente utilizados foram copiados para os recursos versionados. Assim, um checkout do GitHub funciona sem a pasta original. Não remover a licença OFL.

Os recursos são locais, sem dependência de serviços externos de fontes. A configuração de segurança permite GET em `/brand/**` e `/favicon.ico`, mantendo ações de escrita e telas privadas protegidas.

## Cores e componentes

| Papel | Claro | Escuro |
| --- | --- | --- |
| Fundo | `#FFFFFF` | `#171717` |
| Superfície de apoio | `#F7F7F5` | `#222222` |
| Texto | `#242424` | `#F5F5F3` |
| Texto secundário | `#626262` | `#B5B5B0` |
| Borda de controle | `#878781` | `#81817B` |
| Divisor | `#E4E4E1` | `#3B3B39` |
| Ação principal | `#F4BD18`, texto `#242424` | Mesmo par |

- Inter usa peso 400 na leitura, 500 em ações e 600 nos títulos. Arial/sans-serif é a alternativa.
- O logo usa os SVGs originais, sem redigitar letras, recolorir ou deformar. A largura mínima do logo completo é 144 px; a versão reversa acompanha o tema escuro.
- Campos e botões usam raio de 6 px e altura mínima de 48 px; cartões funcionais usam raio de 8 px. O amarelo é reservado às ações principais.
- Links de conteúdo são sublinhados nos dois temas. Foco visível, rótulos persistentes e mensagens de erro permanecem disponíveis.
- Estados de equipamento, cliente e reserva usam texto com indicador discreto. As cores semânticas não substituem os rótulos nem alteram os estados do servidor.
- Tabelas têm rolagem própria em telas menores. Texto longo pode quebrar linha; campos de data e a busca se reorganizam conforme o espaço disponível.

## Temas

O cabeçalho oferece somente **Tema claro** e **Tema escuro**, alinhados às ações de navegação. O rótulo **Tema do site** permanece acessível a leitores de tela.

O padrão é claro. A preferência explícita usa a chave `equiprent.theme` no `localStorage`, com valores `light` ou `dark`. Ela pertence ao navegador e é compartilhada entre abas da mesma origem. O antigo valor `system`, valores desconhecidos e a remoção da preferência voltam ao padrão claro. Mudanças na aparência do sistema operacional não trocam o tema do aplicativo.

O script aplica `data-theme` e `data-bs-theme` antes dos estilos. A troca não recarrega a página nem substitui formulários ou rascunhos. Se o armazenamento for bloqueado, a escolha funciona na página atual. Sem JavaScript, o seletor fica oculto e a interface permanece clara, com formulários funcionais.

## Aplicação às telas

A página inicial concentra título, descrição curta e busca por equipamento, cidade e período. Atalhos são gerados a partir das categorias reais do aplicativo. A seção **Como funciona** explica solicitação, negociação e confirmação pela empresa, com acompanhamento de retirada e devolução.

O catálogo apresenta resultados em linhas, com categoria, nome, empresa, cidade/UF, diária e total estimado quando há período. Filtros, ordenação e paginação continuam disponíveis. Cidade é um filtro textual, sem geolocalização ou cálculo de distância.

Formulários, conta, administração, reservas, propostas, chat e erros compartilham a mesma identidade. Não foram acrescentados pagamentos, sinal, entrega ou cadastro público de empresas a partir dos exemplos do manual. As fotos ilustrativas do manual não foram publicadas como equipamentos reais.

## Conferência

Use os comandos de [DESENVOLVIMENTO.md](DESENVOLVIMENTO.md). Confira a busca, os atalhos de categoria, resultados, formulários e páginas autenticadas em ambos os temas, desktop e celular. Alterne o tema com campos preenchidos, recarregue, abra outra aba e navegue pelo teclado. Confira também o tema claro e os formulários com JavaScript desativado.

Resultados executados e limitações em [VALIDACOES.md](VALIDACOES.md).
