# Requisitos do Dashboard Administrativo

Projeto Pediu Patrão, branch `feature/dashboard-administrativo`. Os identificadores
abaixo são da atividade do dashboard; os requisitos anteriores de usuários,
pedidos e auditoria continuam válidos. API em [api.md](api.md), validação e
roteiro do aluno em [testes.md](testes.md).

| ID | Requisito funcional | Implementação |
| --- | --- | --- |
| RF01 | Indicadores de vendas | Faturamento, pedidos, ticket, descontos, cancelados e registros sem valor |
| RF02 | Análise gráfica | Série diária com dias zerados, comparação de partes do período e tooltips |
| RF03 | Status dos pedidos | Distribuição por situação atual e tipo, incluindo cancelados nas contagens |
| RF04 | Produtos mais vendidos | Até 10 produtos por unidades, preços históricos e desempate por produtoId |
| RF05 | Filtros | Datas inicial/final, status e tipo aplicados de forma consistente |
| RF06 | APIs próprias | Cinco endpoints Spring Boot consultam pedidos persistidos no MongoDB |
| RF07 | Atualização e falhas | Fetch sem recarregar página, loading, vazio, erro, recuperação e sessão encerrada |

## Regras de cálculo

- `dataHora` de criação, fuso America/Sao_Paulo; início inclusivo e fim exclusivo
  à meia-noite do dia seguinte. Sem datas: início do mês até hoje. Sem data
  registrada: fora da consulta. Máximo de 366 dias inclusive.
- Quantidade e distribuições incluem todos os pedidos filtrados. Faturamento,
  ticket, descontos e ranking excluem cancelados e totais ausentes/negativos.
- Faturamento soma `valorTotal`, já líquido do desconto, incluindo entrega e
  pedidos em andamento. Não representa liquidação financeira.
- Ticket = faturamento / pedidos financeiros válidos, HALF_UP em duas casas;
  zero se não houver válidos. O desconto não é subtraído duas vezes.
- Correção de desconto substitui o percentual e recalcula sobre subtotal + entrega,
  sem desconto em cascata. Remoção zera tipo/informado/percentual/valor e restaura
  essa base, preservando itens e preços. Troco é recalculado; recebimento insuficiente
  impede a gravação. Corrigir/remover só antes da saída/retirada, nunca após cancelamento
  ou finalização, e exige GERENTE cuja conta continua ativa no banco.
- Ranking usa snapshots dos itens, soma unidades positivas e apresenta bruto
  antes de desconto/entrega. Não recalcula pedidos com o preço atual do catálogo.
- Série diária inclui zeros. Comparação divide o período em duas partes e mostra
  totais/dias de cada uma, sem percentual de crescimento ou normalização por dia.

## Requisitos não funcionais

| Área | Critério |
| --- | --- |
| Segurança | Login Spring Security; dashboard/API exclusivos de ADMIN; CSRF em escritas; credenciais externas |
| Consistência | Pedido e auditoria na mesma transação MongoDB; replica set obrigatório para escritas auditadas |
| Desempenho | Filtros no Mongo, projeção de campos, até 50.000 pedidos; 422 ao exceder, sem totais parciais |
| Responsividade | AdminLTE 3.2.0 e Chart.js 2.9.4 locais, com layouts desktop/mobile |
| Usabilidade | Português, campos rotulados, avisos aria-live e tabela alternativa ao gráfico |
| Manutenibilidade | Mesma aplicação Spring Boot/Thymeleaf, serviços e APIs próprios, testes de regressão preservados |
| Tratamento de falhas | Mensagens explícitas, nenhum fallback financeiro fictício e nenhum detalhe interno de banco na API |

## Perfis e autorização gerencial

- **ADMIN:** gerencia usuários/produtos, consulta pedidos, auditoria e dashboard.
  Não cria pedidos, aplica descontos ou cancela pedidos.
- **GERENTE:** registra/acompanha pedidos, gerencia produtos e executa descontos
  e cancelamentos autorizados. Não administra usuários nem acessa o dashboard.
- **ATENDENTE:** cadastra clientes, registra pedidos e avança status; não concede
  descontos, cancela pedidos, consulta auditoria ou acessa o dashboard.

Na prévia e na criação de pedido com desconto, e no cancelamento, permanecem
obrigatórios perfil GERENTE na sessão e credenciais de gerente ativo. A senha
adicional confirma a operação sensível, reduzindo o risco de uso casual de uma
sessão gerencial desbloqueada. A prévia do navegador não concede autorização
persistente: a criação precisa revalidar os dados e as credenciais no servidor.
Remover essa checagem permitiria confiar indevidamente no estado do cliente.

A revisão centralizou a sequência perfil + credenciais em
`PedidoService.autorizarOperacaoGerencial`, sem mudar rotas, formulários ou regras.
A aplicação posterior de desconto mantém a regra existente de sessão GERENTE,
sem segunda senha, com revalidação da conta ativa/perfil no banco em cada aplicação,
correção ou remoção. Não há bypass para ATENDENTE ou ADMIN. O servidor bloqueia
essas alterações inclusive se uma sessão antiga ainda carregar o perfil GERENTE.
Isso não equivale à invalidação imediata de todas as sessões em todas as outras rotas.

Desconto continua somente percentual, até 20%, com até duas casas decimais,
antes da saída/retirada;
cancelamento exige justificativa. CSRF, BCrypt, validação de estado, autoria na
auditoria, rollback e filtragem de dados sensíveis foram preservados.
Eventos DESCONTO_APLICADO, DESCONTO_SUBSTITUIDO e DESCONTO_REMOVIDO identificam
pedido, operador/perfil, data, valores anteriores/novos e campos alterados.
Pedido e evento são confirmados/revertidos juntos.

## Execução normal

Nenhuma fixture de src/test é carregada pela aplicação. O bootstrap ADMIN só é
ativado explicitamente por configuração externa. Não há faixa de demonstração
no template normal; ela pertence apenas à automação de teste. Java 21 é fixado
pelos lançadores. MongoTopologyCheck executa hello antes dos inicializadores e
recusa standalone/indisponibilidade, sem alterar topologia ou dados. A URI original
e SPRING_DATA_MONGODB_URI continuam sendo as fontes de conexão.

## Estado de validação

RF01–RF07 estão implementados e testados automaticamente. O cenário de nove
registros sintéticos foi homologado em replica set descartável e Chrome real,
com capturas em [evidencias/](evidencias/). Isso não é validação em produção.
Não foram executados teste de carga/failover multinó ou auditoria integral de
acessibilidade. A [matriz detalhada anterior](matriz-dashboard.md) foi preservada.
