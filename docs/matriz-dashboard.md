# Matriz de rastreabilidade — Dashboard Administrativo

RF01–RF07 seguem a numeração fornecida pelo professor nesta atividade.
Validação automatizada não equivale a homologação com dados reais de negócio.
Os testes de cálculo usam fixtures explicitamente de teste; a inspeção no Chrome
agora inclui aplicação real com MongoDB descartável preenchido por 9 registros
sintéticos, além do cenário vazio preservado. Nenhum dado é apresentado como
venda real. Valores esperados/obtidos: [homologacao-final.md](homologacao-final.md).

As situações distinguem: **implementado**, **testado automaticamente**,
**homologado em ambiente descartável** e **não validado em ambiente real**.

| Requisito | Arquivos responsáveis | Endpoint | Testes / evidências | Situação |
| --- | --- | --- | --- | --- |
| RF01 — Indicadores de vendas | DashboardService.java, dashboard.html, dashboard.js | GET /api/admin/dashboard/indicadores e resumo | DashboardServiceTest, DashboardControllerTest, DashboardDemonstracaoTest e Chrome | Implementado; automático aprovado; homologado sintético (488,50 / 9 / 69,79 / 6,50); ambiente real não validado |
| RF02 — Análise gráfica de vendas | DashboardService.java, layout-adminlte.html, dashboard.js | GET /api/admin/dashboard/vendas e resumo | DashboardServiceTest, dashboard.test.cjs, dashboard.browser.cjs + dashboard.demonstracao.cjs | Implementado; automático aprovado; série preenchida, dia zero, comparação e tooltip homologados sintéticos; ambiente real não validado |
| RF03 — Acompanhamento de status dos pedidos | DashboardService.java, dashboard.html, dashboard.js | GET /api/admin/dashboard/status; campo tipos do resumo | DashboardServiceTest, DashboardDemonstracaoTest, Chrome preenchido | Implementado; automático aprovado; dois gráficos e contagens somando 9 homologados sintéticos; ambiente real não validado |
| RF04 — Produtos mais vendidos | DashboardService.java, dashboard.js | GET /api/admin/dashboard/produtos e resumo | DashboardServiceTest, DashboardDemonstracaoTest, tabela do Chrome comparada à API | Implementado; automático aprovado; quantidades 7/4/3/3, preços históricos e empate homologados sintéticos; catálogo real não acessado |
| RF05 — Filtros de consulta | DashboardService.filtro/consultar, DashboardController.java, dashboard.html | Todos os cinco GET | DashboardServiceTest, DashboardControllerTest, DashboardDemonstracaoTest e Chrome | Implementado; automático aprovado; 9 combinações × 5 endpoints e formulário homologados sintéticos; ambiente real não validado |
| RF06 — Integração com APIs próprias | DashboardController.java, DashboardService.java, dashboard.js | GET /api/admin/dashboard e quatro detalhes | DashboardControllerTest; Chrome compara JSON e componentes Chart.js com registros sintéticos persistidos | Implementado; automático aprovado; Spring Boot/MongoDB/API real homologados em base descartável preenchida; produção não acessada |
| RF07 — Atualização dos dados e tratamento de erros | dashboard.js, DashboardErrorHandler.java | Resumo; erros 400/422/503 | Sete testes dashboard.test.cjs; DashboardControllerTest; Chrome com latência, bloqueio de rede e logout reais | Implementado; automático aprovado; loading, atualização sem reload, vazio, erro, recuperação e expiração homologados em ambiente descartável; sem fallback fictício |

## Requisitos não funcionais e consistência

| Requisito | Implementação / teste | Situação |
| --- | --- | --- |
| Segurança | SecurityConfig, Spring Security/CSRF existentes; ControllerTest e Chrome testam ADMIN, GERENTE, ATENDENTE e anônimo | Autorização automatizada e real aprovadas; sem credenciais fixas |
| Responsividade | AdminLTE 3.2.0 e CSS complementar; Chrome 1440×1100 e 390×844 | Viewports verificados; revisão humana em outros dispositivos continua recomendada |
| Usabilidade | Português, indicadores rotulados, mensagens aria-live, tabela diária, tooltips e logout | Verificado em cenários vazio e sintético preenchido; não se declara auditoria completa de acessibilidade |
| Desempenho | Projeção no Mongo, filtros anteriores à leitura, até 366 dias / 50.000 pedidos | Limites testados; benchmark e explain/índices em volume real não executados |
| Manutenibilidade | Mesmos cinco endpoints e serviço; layout isolado das telas antigas; sem dependência nova | Build e regressões aprovados; documentação e licença presentes |
| Tratamento de falhas | 400/422/503 JSON, rede, expiração de sessão, nova tentativa | Testes unitários/MVC/JS e navegador documentados |
| Consistência pedido/auditoria | MongoTransactionConfig, @Transactional em escritas de PedidoService, PedidoTransactionTest e PedidoMongoIntegrationTest | Rollback real e commit testados em replica set isolado; não garante suporte do banco original |

Não executados: carga real, homologação com pedidos reais, auditoria integral de
acessibilidade, failover multinó, perda de rede no commit e validação do servidor
MongoDB original. Esses limites não são marcados como validados.
