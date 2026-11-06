# Homologação final com dados sintéticos — 05/11/2026

Relatório histórico preservado. Para a revisão técnica posterior, os cinco
commits locais e a contagem atual de testes, consulte [testes.md](testes.md).

**AMBIENTE DE DEMONSTRAÇÃO — DADOS DE TESTE. Nenhum valor abaixo representa venda real.**

Branch `feature/dashboard-administrativo`, raiz Git e Maven na pasta interna
`PediuPatrao`. Main preservada em `3bcb4c204b7f10adef103d1ad21275b82e996f78`.
Não houve alteração de código de produção nesta homologação: somente fixture,
automação de teste, lançador, documentação e novas imagens.

Arquivos criados nesta etapa: DashboardDemonstracaoTest.java,
src/test/js/dashboard.demonstracao.cjs, este relatório e as duas imagens com
sufixo `-demonstracao.png`. Atualizados: dashboard.browser.cjs,
scripts/verificar-dashboard-local.ps1, README.md, dashboard-administrativo.md,
evidencias-dashboard.md, matriz-dashboard.md e roteiro-video-dashboard.md.
estabilizacao-dashboard.md foi lido e preservado como histórico.
Os 33 arquivos locais inventariados antes desta tarefa permanecem presentes;
os arquivos de produção e as imagens vazias mantiveram seus SHA-256 originais.

## Escopo e isolamento

Replica set `codexDashboard`, MongoDB local em `127.0.0.1:27028`, dbpath novo
`target/mongo-homologacao-<UUID>` nesta execução, sem reutilizar os diretórios
anteriores. A aplicação usa uma base `pediupatrao_test_demo_<UUID>` inédita por
execução. A fixture recusa outra porta, replica set, prefixo ou banco já existente.
Não lê a URI original e não remove bancos ou documentos.

`DashboardDemonstracaoTest` existe somente em src/test e depende de PP_DEMO_URI
explícita. Não é componente Spring, migration, seed de produção ou initializer.
O lançador só a executa com `-Demonstracao` ou `-Apresentacao` explícitos.

Usuário GERENTE, cliente e catálogo sintéticos usam os serviços existentes.
Os oito pedidos válidos usam PedidoService transacional, inclusive criação com
desconto, avanço normal das etapas e cancelamento com justificativa/credenciais.
As credenciais são aleatórias, codificadas com BCrypt e nunca exportadas.

Duas intervenções ficam exclusivamente na fixture: atribuir datas históricas
aos pedidos criados, pois o serviço usa Instant.now; e inserir um único documento
legado sem tipo/status/total para testar compatibilidade. Não foi criado um
caminho de produção que aceite esse documento incompleto. A alteração de preço
do catálogo também passa pelo ProdutoService e preserva snapshots antigos.

## Registros persistidos e valores conferidos

Período: 01/10/2026 a 07/10/2026, fuso America/Sao_Paulo. Todos os nomes e
observações identificam explicitamente TESTE ou DADOS SINTÉTICOS.

| Código | Dia | Tipo | Situação final | Composição / total persistido |
| --- | --- | --- | --- | --- |
| P1 | 01/10 | Retirada | Recebido | 2 Pizza A × R$ 40 = R$ 80,00 |
| P2 | 02/10 | Entrega | Em preparo | Pizza A 40 + 2 Bebida B × 10 + entrega 5; desconto 10% = 6,50; total R$ 58,50 |
| P8 | 03/10 | Retirada | Recebido | 3 Porção D × R$ 15 = R$ 45,00 |
| P3 | 04/10 | Balcão | Finalizado | Pizza C histórica 60 + Bebida B 10 = R$ 70,00 |
| P4 | 04/10 | Retirada | Cancelado | 3 Pizza A × R$ 40 = R$ 120,00; excluído das métricas financeiras |
| P9 | 05/10 | Ausente | Ausente | Documento legado sintético, total ausente; semValor = 1 |
| P5 | 06/10 | Entrega | Saiu para entrega | 2 Pizza C históricas × 60 + entrega 5 = R$ 125,00 |
| P6 | 07/10 | Balcão | Pronto | 3 Bebida B × R$ 10 = R$ 30,00 |
| P7 | 07/10 | Retirada | Finalizado | Pizza C após reajuste 70 + Bebida B 10 = R$ 80,00 |

| Indicador | Esperado pelos registros | API e tela obtidos |
| --- | ---: | ---: |
| Pedidos | 9 | 9 |
| Pedidos financeiros válidos | 7 | 7 |
| Cancelados | 1 | 1 |
| Sem valor válido | 1 | 1 |
| Faturamento | R$ 488,50 | R$ 488,50 |
| Descontos | R$ 6,50 | R$ 6,50 |
| Ticket médio, 488,50 / 7, HALF_UP | R$ 69,79 | R$ 69,79 |

Série esperada e obtida: **80,00; 58,50; 45,00; 70,00; 0,00; 125,00; 110,00**.
Dia 05/10 tem um legado sem valor, mas nenhuma venda elegível. Comparação da tela:
01–04/10 (4 dias) = **R$ 253,50**; 05–07/10 (3 dias) = **R$ 235,00**.
Tooltip do primeiro ponto conferido no canvas: **R$ 80,00**.

Status: recebido 2; em preparo 1; finalizado 2; pronto 1; cancelado 1;
saiu para entrega 1; sem status 1. Tipos: retirada 4; entrega 2; balcão 2;
sem tipo 1. Ambas as distribuições somam 9 e incluem o cancelado.

Ranking esperado e obtido: Bebida B **7 / R$ 70,00**; Pizza C **4 / R$ 250,00**;
Pizza A **3 / R$ 120,00**; Porção D **3 / R$ 45,00**. No empate, produtoId
ascendente; a fixture verifica essa ordem explicitamente. O bruto de Pizza C
comprova o preço histórico de 60 em três unidades e o novo preço de 70 em uma;
não usa o preço atual retroativamente. O pedido cancelado não entra no ranking.

## Filtros, endpoints e interface

Nove combinações, cada uma nos cinco endpoints (45 comparações HTTP), com cards
também comparados ao resultado esperado após cada alteração de formulário:

| Filtro | Pedidos | Faturamento esperado = obtido | Ticket esperado = obtido |
| --- | ---: | ---: | ---: |
| Todo o período | 9 | 488,50 | 69,79 |
| Retirada | 4 | 205,00 | 68,33 |
| Entrega | 2 | 183,50 | 91,75 |
| Balcão | 2 | 100,00 | 50,00 |
| Cancelado | 1 | 0,00 | 0,00 |
| 02–04/10 | 4 | 173,50 | 57,83 |
| 01–04/10 + entrega + em preparo | 1 | 58,50 | 58,50 |
| 01–07/09, vazio | 0 | 0,00 | 0,00 |
| Sem status | 1 | 0,00 | 0,00 |

Rotas: GET `/api/admin/dashboard` e detalhes `/indicadores`, `/vendas`, `/status`,
`/produtos`. Todos responderam 200 e JSON consistente nas consultas válidas.
Datas inválidas, intervalo invertido, tipo/status desconhecidos: 400 com JSON erro.
O limite 422 e a falha de persistência 503 continuam cobertos nos testes locais.

AdminLTE e Chart.js locais carregados na aplicação Spring Boot real. O teste
confere dados das três instâncias Chart.js, conteúdo da tabela do ranking, cards,
tooltip e comparação; preserva um marcador JavaScript para provar que os filtros
não recarregam a página. Loading foi observado com latência real induzida pelo
Chrome, sem resposta financeira mockada. Falha de rede oculta indicadores e
permite nova atualização. Logout real exercita a mensagem de sessão encerrada.
Exceções JavaScript não tratadas e console.error: nenhuma ocorrência.

Desktop 1440×2200 e mobile 390×844: capturas com identificação explícita dos dados,
revisadas visualmente. Sidebar sem sobreposição, gráficos e nomes legíveis, sem
overflow horizontal no celular. As imagens anteriores de base vazia foram
preservadas e conferidas por SHA-256.

- [Desktop preenchido](evidencias/adminlte-desktop-demonstracao.png)
- [Mobile preenchido](evidencias/adminlte-mobile-demonstracao.png)
- [Desktop vazio preservado](evidencias/adminlte-desktop.png)
- [Mobile vazio preservado](evidencias/adminlte-mobile.png)

## Segurança e transações

ADMIN consulta; GERENTE e ATENDENTE recebem 403 na página e cinco endpoints;
anônimo é redirecionado. POST sem CSRF é recusado com 403. Senhas do bootstrap e
perfis de teste são aleatórias e só permanecem na memória dos processos.
As respostas do dashboard não incluem cliente, endereço, senha ou credencial.

Regras gerenciais preservadas: somente sessão GERENTE pode conceder desconto;
na criação com desconto e no cancelamento também há revalidação de credenciais
de gerente ativo. Isso separa a permissão da sessão da autorização da operação.
A rota existente de desconto posterior usa a sessão GERENTE sem segunda senha;
não foi uniformizada nem enfraquecida nesta homologação. Não se declara testada
a invalidação imediata de todas as sessões preexistentes após desativar contas.

Regressões reconfirmadas na suíte: gerente desativado não reautoriza operação,
bootstrap sem senha fixa, auditoria com nulos e filtragem de dados sensíveis.
PedidoMongoIntegrationTest reconfirmou rollback após falha da auditoria, da
gravação do pedido e da exclusão, e commit conjunto. Teste em replica set novo;
nenhuma afirmação sobre suporte transacional do banco original foi feita.

## Resultados desta execução

- Java/Maven: **94 aprovados, 0 falhos, 0 erros, 0 ignorados; BUILD SUCCESS**,
  incluindo os dois testes opt-in MongoDB e demonstração.
- JavaScript unitário: **7 aprovados, 0 falhos, 0 ignorados**.
- Fixture executada novamente pelo lançador em outra base nova: 1 teste aprovado.
- Chrome headless: cenário preenchido + cenários anteriores vazios, segurança,
  falhas e sessão expirada aprovados. Não são contados como testes JUnit extras.
- `git diff --check`: sem erros. Logs e exportações somente em target, ignorado.

Logs desta execução: `target/homologacao-final-package.log`,
`target/homologacao-browser.log`, `target/homologacao-fixture-<UUID>.log`.
O JSON esperado é gerado a partir dos registros persistidos, com conferências
independentes explícitas de totais, série, contagens e ranking; não é fallback
da aplicação. `target/homologacao-esperado-ultima.json` é evidência local sintética.
Aplicação, Chrome e MongoDB de teste foram encerrados; portas 18088, 9228 e 27028
verificadas sem processos em escuta. Diretórios e dados das execuções foram preservados.

## Reproduzir e gravar o vídeo

1. Seguir os dois terminais de replica set novo em dashboard-administrativo.md.
2. Após inicializar o replica set e gerar o JAR, executar:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verificar-dashboard-local.ps1 -Demonstracao
```

Esse comando homologa com Chrome headless e encerra seus processos ao final.
Para apresentar/gravar manualmente, abrir Chrome visível, voltar ao ADMIN e manter
a aplicação ligada até pressionar Enter no terminal:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verificar-dashboard-local.ps1 -Apresentacao
```

O modo interativo não grava vídeo. É uma opção para execução manual posterior;
a homologação desta sessão usou Chrome headless. Os dados são novos em cada
execução. Não reutilizar bancos ou credenciais anteriores. Encerrar mongod ao final,
sem apagar diretórios de dados. O roteiro final dura até três minutos.

## Estado e limites da entrega

Implementado, testado automaticamente e homologado com dados sintéticos em ambiente
descartável. Não validado com dados reais do negócio ou produção; não executados
teste de carga, failover multinó e auditoria integral de acessibilidade.
Esses itens não foram exigidos como bloqueios da apresentação acadêmica.

Para entregar ao professor ainda é necessário revisar os materiais, gravar o vídeo
manualmente e realizar commits/publicação no GitHub somente quando autorizados.
Fontes e docs permanecem locais sem commit; main intacta. Plano de commits em
dashboard-administrativo.md. Não houve pull, commit, push, merge, rebase ou deploy.
