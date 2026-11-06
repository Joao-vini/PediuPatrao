# Dashboard Administrativo — Pediu Patrão

Registro técnico das etapas anteriores. Afirmações abaixo sobre ausência de
commits e contagens antigas descrevem aquelas etapas. A revisão atual organiza
cinco commits locais; resultados vigentes em [testes.md](testes.md).

Entrega local em `feature/dashboard-administrativo`, base `3bcb4c2`, Java 21,
Spring Boot 3.4.4, Spring Security, Thymeleaf e MongoDB. Sem novas dependências,
sem segunda aplicação, sem commit, push, merge ou deploy.

Homologação final com dados preenchidos, valores esperados/obtidos, limites e
capturas novas: [homologacao-final.md](homologacao-final.md). A base sintética
usa 9 registros, sem acessar vendas reais ou modificar código de produção.

## Requisitos funcionais da nova atividade

Numeração fornecida pelo professor para a nova atividade. Os RF01–RF05
históricos de usuários, pedidos, descontos e auditoria continuam preservados.
Rastreabilidade e limites de validação: [matriz-dashboard.md](matriz-dashboard.md).

| ID | Requisito | Implementação |
| --- | --- | --- |
| RF01 | Consultar indicadores de vendas | Faturamento, pedidos, ticket, descontos, cancelados, valores ausentes |
| RF02 | Analisar vendas por período | Série diária, inclusive dias sem vendas |
| RF03 | Acompanhamento de status dos pedidos | Quantidade por status atual e distribuição por tipo |
| RF04 | Identificar produtos mais vendidos | Top 10 por unidades com preço histórico |
| RF05 | Filtrar resultados | Datas, status e tipo de pedido; atualização sem recarregar a página |
| RF06 | Integração com APIs próprias | Cinco endpoints Spring Boot sobre MongoDB; interface consome resumo |
| RF07 | Atualização dos dados e tratamento de erros | Fetch assíncrono, vazio, carregamento, falha, sessão expirada e nova tentativa |

## Requisitos não funcionais

- Autenticação de sessão existente e autorização no servidor; CSRF preservado.
- API não expõe clientes, endereços, observações ou credenciais. Respostas sem cache.
- Nenhum valor fictício de fallback. Falhas de banco produzem 503, nunca números inventados.
- BigDecimal no servidor, duas casas decimais, HALF_UP. Browser apenas formata valores.
- Período máximo de 366 dias inclusive, até 50.000 pedidos por consulta.
  Acima disso retorna 422, sem totais parciais. Para volumes superiores, evoluir
  para agregações MongoDB no servidor; esta versão soma a projeção em memória.
- Conteúdo dinâmico inserido via textContent, sem interpretar HTML dos produtos.
- Layout responsivo; filtros rotulados, mensagem aria-live, tabela dos valores do gráfico.
- AdminLTE 3.2.0 e Chart.js 2.9.4 já presentes no repositório, sem instalação.
- Escritas auditadas de pedidos na mesma transação MongoDB, com confirmação majority.

## Modelo e regras de cálculo

Fonte exclusiva: coleção `pedidos`, consultada por MongoTemplate. Nenhum dado de
teste é carregado em execução normal. Produtos usam snapshots em `Pedido.itens`,
sem consultar o preço atual do catálogo, que pode ter sido alterado.

- Data de referência: `dataHora` de criação, fuso `America/Sao_Paulo`.
  De é inclusivo às 00:00; até é exclusivo às 00:00 do dia seguinte.
- Datas ausentes ficam fora da consulta. Sem filtros de data: primeiro dia do
  mês atual até hoje no fuso de São Paulo. Futuro explícito é permitido.
- Quantidade de pedidos: todos os registros filtrados, inclusive cancelados e
  registros sem valor. Distribuição usa o status atual, não histórico do status.
- Faturamento: soma `valorTotal` não negativo dos pedidos não cancelados.
  Inclui pedidos em andamento e taxa de entrega, já descontado `valorDesconto`.
  Não representa receita recebida: não há registro de liquidação no modelo.
- Ticket médio: faturamento / quantidade de pedidos não cancelados com total válido.
  Se não houver denominador, zero. Não se divide pela quantidade de itens.
- Descontos: soma dos valores não negativos dos pedidos elegíveis ao faturamento;
  não são subtraídos novamente do total, evitando desconto em dobro.
- Cancelados não entram no faturamento, ticket, série de vendas ou ranking;
  continuam na quantidade total e na distribuição de status.
- Total ausente/negativo: contado em `semValor`, excluído das métricas financeiras
  e do ranking. Não há tentativa de inferir preço de registros antigos.
- Ranking: soma de `itens.quantidade` positivas por produtoId, limitado a 10,
  desempate por produtoId. Ignora item nulo/sem id/quantidade não positiva.
  Nome vem do snapshot; se houver renomeações escolhe um nome deterministicamente.
  Valor bruto = preço unitário histórico × quantidade; sem rateio do desconto
  do pedido nem da entrega. Preço ausente não é inventado: contribui zero no bruto.
- Série diária inclui zeros nos dias sem pedidos válidos. Os quatro endpoints
  de detalhe aplicam as mesmas regras do endpoint de resumo. A tela usa uma única
  resposta de resumo para manter os componentes coerentes na mesma atualização.
- Distribuição `tipos` no resumo conta todos os pedidos filtrados, inclusive
  cancelados e sem valor; ausência de tipo vira SEM_TIPO. Nenhum endpoint duplicado.
- Comparação dentro do período: divide os dias em duas partes contíguas e mostra
  a soma de faturamento de cada uma, explicitando datas e quantidades de dias.
  Se o número de dias for ímpar, a primeira parte tem um dia a mais. Não há
  percentual de crescimento ou ajuste por dias, nem alteração da regra financeira.

## Integração oficial AdminLTE

O repositório continha os assets 3.2.0, mas o layout anterior não os carregava.
Agora `/dashboard` usa `templates/layout-adminlte.html`, com AdminLTE real:
wrapper, main-header, main-sidebar, content-wrapper, small-box, card, PushMenu e
CardWidget. O usuário autenticado aparece com escape HTML. Logout usa POST e CSRF.
As demais páginas continuam no layout anterior; Bootstrap 4 e 5 não se misturam
no dashboard. Thymeleaf gera URLs com contexto e inclui `dashboard :: content`.

Assets locais, carregados na ordem correta:

| Biblioteca | Versão | Arquivos em static |
| --- | --- | --- |
| AdminLTE | 3.2.0 | dist/css/adminlte.min.css, dist/js/adminlte.min.js |
| jQuery | 3.6.0 | plugins/jquery/jquery.min.js |
| Bootstrap + Popper | 4.6.1 | plugins/bootstrap/js/bootstrap.bundle.min.js |
| Chart.js | 2.9.4 | plugins/chart.js/Chart.min.js |
| Font Awesome Free | 5.15.4 | plugins/fontawesome-free/css/all.min.css e webfonts |

CSS AdminLTE já inclui Bootstrap 4.6.1. Dashboard não depende de CDN.
Chart.js desenha uma linha diária e dois gráficos de rosca, com tooltips em
português e resumos textuais; as instâncias antigas são destruídas ao atualizar.
A tabela diária oferece alternativa acessível ao gráfico. Complementos de
identidade estão em dashboard.css e não substituem o CSS oficial.

AdminLTE é distribuído sob MIT; texto integral em `static/dist/LICENSE-AdminLTE.txt`,
copiado da tag oficial v3.2.0. Cabeçalhos dos demais fornecedores preservados.
Bootstrap, jQuery e Chart.js: MIT. Font Awesome: código MIT, fontes SIL OFL 1.1,
ícones CC BY 4.0, conforme cabeçalho distribuído.
Fontes: [dependências oficiais](https://adminlte.io/docs/3.2/dependencies.html),
[distribuição 3.2](https://adminlte.io/docs/3.2/),
[licença oficial](https://github.com/ColorlibHQ/AdminLTE/blob/v3.2.0/LICENSE).
Foi mantida a versão estável já incorporada, sem migração desnecessária para v4.

## Consistência e transações

Operações cobertas em PedidoService: criar (inclusive dois eventos quando há
desconto), avançar status, aplicar/remover desconto, cancelar e excluir. Ambos
os overloads públicos de criação e desconto são transacionais, incluindo chamadas
externas que delegam internamente. A auditoria participa da sessão Mongo vinculada
à transação do pedido, sem REQUIRES_NEW. Erros não são capturados nem suprimidos
no serviço: propagam para acionar rollback. Não há compensação manual ou retry
automático que possa duplicar pedidos após resultado incerto de commit.

A configuração encontrada aponta para localhost e não declara replicaSet. Isso
não prova o tipo do servidor. Não foi feita conexão ao banco existente para sondar
topologia; o suporte do banco existente permanece NÃO VERIFICADO. Foi encontrado
MongoDB 9.0 em `C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe`, fora do PATH.
Um processo separado em 127.0.0.1:27028, replica set codexDashboard, com dbpath
novo em target/mongo-adminlte-isolado, permitiu validar rollback real sem tocar
no banco existente. O teste passou com reversão e commit de sucesso; veja evidências.

**Estratégia para ambiente sem suporte: falhar sem gravar parcialmente.** O
MongoTransactionManager inicia transação; Mongo standalone rejeita a operação
transacional antes da gravação. Não existe fallback para saves independentes.
Leituras do dashboard não exigem transação e continuam disponíveis em standalone.
Para continuar usando escritas auditadas, configurar replica set ou cluster
compatível. Esse requisito operacional é uma mudança deliberada para garantir
consistência, sem afirmar que o standalone passou a ter atomicidade multidocumento.

Criar coleções antes de transações: a inicialização existente já cria pedidos;
garantir também `auditorias` em ambiente novo. O teste real cria ambas explicitamente.
Os testes de proxy provam acionamento de rollback. O teste Mongo separado
verificou também o estado persistido após falhas e após commit de sucesso.
Conflitos simultâneos podem exigir nova tentativa pelo usuário; não foi implementado
controle de idempotência para repetição de comandos após falhas de rede.

## Consultas e índices

Consulta Mongo filtra `dataHora`, opcionalmente `status` e `tipoPedido`, antes de
retornar projeção financeira e dos itens. Não há findAll do banco inteiro.
Nenhum índice foi criado automaticamente no banco existente. Planejar inicialmente
`{dataHora: 1}`; com volume real, avaliar `{status:1, tipoPedido:1, dataHora:1}` e
variantes para filtros opcionais via explain em ambiente autorizado. O índice
composto não substitui o de data quando seus prefixos de igualdade não são usados.
Índices multikey de itens não são necessários para esta consulta.

## API

Todos GET e exclusivos de sessão ADMIN. Base `/api/admin/dashboard`.

| Rota | Resposta |
| --- | --- |
| `/api/admin/dashboard` | filtro, indicadores, vendas, situacao, produtos, tipos |
| `/api/admin/dashboard/indicadores` | objeto Indicadores |
| `/api/admin/dashboard/vendas` | lista diária de data, faturamento, pedidos |
| `/api/admin/dashboard/status` | lista de status, quantidade |
| `/api/admin/dashboard/produtos` | lista de produtoId, nome, quantidade, valorBruto |

Parâmetros opcionais compartilhados:

| Nome | Formato / valores |
| --- | --- |
| inicio, fim | yyyy-MM-dd; início <= fim; até 366 dias inclusive |
| status | RECEBIDO, EM_PREPARO, EM_PREPARACAO, PRONTO, PRONTO_PARA_RETIRADA, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO, SEM_STATUS |
| tipo | ENTREGA, RETIRADA, BALCAO |

Exemplo de requisição em sessão autenticada:

```http
GET /api/admin/dashboard?inicio=2026-10-01&fim=2026-10-03&tipo=RETIRADA
Accept: application/json
```

Exemplo ESTRUTURAL de indicadores para período sem pedidos (não é evidência de
dados reais nem conteúdo inserido no sistema):

```json
{"faturamento":0.00,"pedidos":0,"pedidosValidos":0,"cancelados":0,"ticketMedio":0.00,"descontos":0.00,"semValor":0}
```

Erros: 400 filtros inválidos; 403 perfil sem permissão; sessão ausente redireciona
para login (302, comportamento existente); 422 volume excessivo; 503 indisponibilidade
de dados com mensagem genérica sem exceção interna. Erros de validação não consultam
o Mongo. 400, 422 e 503 retornam JSON com campo `erro`, sem cache ou detalhes
internos. 403 e redirecionamentos seguem Spring Security. A interface reconhece
o redirecionamento de sessão expirada.

## Executar e demonstrar

1. Abrir terminal na pasta interna `PediuPatrao`, conferir Java 21 e branch.
2. Configurar `SPRING_DATA_MONGODB_URI` externamente apontando para ambiente
   autorizado. Para escritas auditadas é obrigatório MongoDB com replica set.
   Não reutilizar banco de produção nos testes.
3. Se não houver administrador, configurar externamente
   `APP_BOOTSTRAP_ADMIN_ENABLED=true`, `APP_BOOTSTRAP_ADMIN_USERNAME` e
   `APP_BOOTSTRAP_ADMIN_PASSWORD` somente para bootstrap. Desabilitar depois.
   Contas existentes não são promovidas, reativadas nem têm senha alterada.
4. Iniciar com `.\mvnw.cmd spring-boot:run` ou
   `java -jar target/pediupatrao-1.0-SNAPSHOT.jar`.
5. Usar a porta configurada na aplicação, acessar `/login`, autenticar ADMIN e
   abrir `/dashboard` pelo menu. Selecionar período que contenha pedidos reais.
   Sem registros, a tela mostra ausência de dados; não popula o banco.
6. Conferir total, cancelados, ticket, descontos, gráfico diário e ranking;
   alterar filtros e atualizar. Com GERENTE/ATENDENTE, verificar acesso negado.

## Testes e evidências

Comandos seguros utilizados (sem iniciar a aplicação completa):

```powershell
.\mvnw.cmd -o "-Dmaven.repo.local=C:\Users\Joaozinho\.m2\repository" package "-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
node --check src/main/resources/static/js/dashboard.js
node --test src/test/js/dashboard.test.cjs
git diff --check
```

O parâmetro maven.repo.local resolve a configuração local desta máquina; pode
ser omitido em outra instalação com cache Maven configurado corretamente.
O modo offline exige dependências previamente disponíveis.

Cobertura: cálculos e vazios, cancelados, quantidades, preço histórico, fuso,
datas inválidas, filtros BSON, projeção, limite de volume; API/MVC com Spring
Security e Thymeleaf real, roles e erros; proxy Spring transacional com falhas
de gravação de pedido/auditoria; regressões anteriores. JS executa requisição,
estado vazio, erro, sessão expirada e renderização segura por texto em DOM simulado.
Não houve instalação de dependências. Houve execução real no Chrome headless
contra aplicação Spring Boot e base descartável vazia; resultados em evidências.

Resultados finais e artefatos: ver `evidencias-dashboard.md`. Logs em `target/`
são locais e ignorados no Git; o documento preserva os resultados para entrega.

Teste real opcional: `PedidoMongoIntegrationTest`. Não lê a URI da aplicação;
habilitado somente com PP_TEST_MONGO_URI explícita. Aceita exclusivamente
127.0.0.1:27028, sem credenciais, banco com prefixo pediupatrao_test_ e verifica
o replica set codexDashboard por hello.
Cria banco isolado por UUID, não apaga dados nem banco ao terminar.

```powershell
# Somente se existir replica set local DESCARTÁVEL preparado:
$env:PP_TEST_MONGO_URI='mongodb://127.0.0.1:27028/pediupatrao_test_dashboard?replicaSet=codexDashboard&serverSelectionTimeoutMS=3000'
.\mvnw.cmd "-Dtest=PedidoMongoIntegrationTest" test
Remove-Item Env:PP_TEST_MONGO_URI
```

Esse teste verifica rollback real de alteração de pedido após falha da auditoria,
falha de gravação do pedido sem evento, e rollback do evento quando exclusão falha.
Foi executado nesta máquina contra replica set descartável, após corrigir o
teste para delegar ao repositório real sem espionar um proxy Spring. Também
verifica commit conjunto de sucesso. Não confundir esse teste com validação
da infraestrutura de produção. Homologação com volume e dados de negócio
autorizados continua necessária antes de produção.

## Arquivos da entrega

- Backend novo: MongoTransactionConfig, DashboardService, DashboardController,
  DashboardErrorHandler.
- Backend alterado: PedidoService (transações e gerente inativo), SecurityConfig
  (ADMIN), AuditoriaService (nulos), DataInitializer (bootstrap externo).
- UI: layout-adminlte.html, dashboard.html, dashboard.css, dashboard.js e link
  na sidebar existente; licença AdminLTE em static/dist/LICENSE-AdminLTE.txt.
- Testes: DashboardServiceTest, DashboardControllerTest, PedidoTransactionTest,
  PedidoMongoIntegrationTest, dashboard.test.cjs, dashboard.browser.cjs e lançador
  scripts/verificar-dashboard-local.ps1; regressões de bootstrap,
  autorização e nulos da etapa anterior preservadas.
- Homologação preenchida opt-in: DashboardDemonstracaoTest (somente src/test),
  dashboard.demonstracao.cjs; lançador com -Demonstracao e -Apresentacao.
- Documentos: este arquivo, evidencias-dashboard.md, roteiro-video-dashboard.md
  matriz-dashboard.md e histórico da preparação em estabilizacao-dashboard.md.
- Organização: README.md aponta para os entregáveis; .gitignore protege target,
  arquivos .env, logs, temporários e PID. Capturas reais em docs/evidencias/.

Tudo permanece local e sem commit para revisão. Não incluir target, credenciais,
variáveis de ambiente ou dados do banco ao preparar o envio pelo GitHub.

## Reprodução exata do ambiente descartável validado

Não iniciar contra o banco original. As portas 27028 (Mongo), 18088 (Spring) e
9228 (Chrome CDP) devem estar livres. O lançador recusa portas de aplicação/browser
ocupadas; inicia e encerra somente seus próprios processos, restaura variáveis
de ambiente e gera senhas aleatórias, sem gravá-las em arquivos.

Terminal 1, na raiz interna do projeto (manter mongod em primeiro plano):

```powershell
$testDbPath = Join-Path (Get-Location) ('target/mongo-replica-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDbPath -Force | Out-Null
& 'C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe' --dbpath $testDbPath --port 27028 --bind_ip 127.0.0.1 --replSet codexDashboard --wiredTigerCacheSizeGB 0.25
```

Terminal 2, mesma raiz (Node 24 e Chrome instalados; ajustar caminhos se necessário):

```powershell
$env:PP_TEST_MONGO_URI='mongodb://127.0.0.1:27028/pediupatrao_test_adminlte?directConnection=true&serverSelectionTimeoutMS=5000'
$env:PP_TEST_MONGO_INIT='codexDashboard'
.\mvnw.cmd -o "-Dmaven.repo.local=C:\Users\Joaozinho\.m2\repository" package "-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
node --check src/main/resources/static/js/dashboard.js
node --test src/test/js/dashboard.test.cjs
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verificar-dashboard-local.ps1
Remove-Item Env:PP_TEST_MONGO_URI,Env:PP_TEST_MONGO_INIT
```

PP_TEST_MONGO_INIT inicializa somente o servidor dedicado 127.0.0.1:27028 e
somente se ainda não houver replica set. Na execução final desta sessão, já
inicializado, foi usada a URI com `replicaSet=codexDashboard`, sem INIT.
O teste cria coleções/bancos novos e não apaga dados. Encerrar mongod com Ctrl+C
no Terminal 1 depois de terminar. Não é necessário instalar dependências frontend.

Para navegação manual em ambiente autorizado: definir externamente a URI e as
credenciais de bootstrap não previsíveis, se necessárias, e iniciar o JAR.

```powershell
# Configurar SPRING_DATA_MONGODB_URI e bootstrap externamente antes de executar.
java -jar target/pediupatrao-1.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18088
```

Abrir `http://127.0.0.1:18088/login` e depois `/dashboard`. Não reutilizar as
contas aleatórias da automação: o lançador as mantém somente na memória da execução.

## Plano de commits e entrega posterior

Sugestão de divisão, sem nenhum commit executado nesta sessão:

1. `fix: proteger bootstrap, autorização gerencial e auditoria com nulos`.
2. `fix: transacionar pedidos e auditoria com testes de rollback MongoDB`.
3. `feat: implementar API administrativa e indicadores de vendas`.
4. `feat: integrar dashboard AdminLTE e gráficos interativos`.
5. `test: homologar nove pedidos sintéticos e preparar evidências acadêmicas`.

Alguns arquivos participam de mais de um grupo; selecionar trechos ao preparar
os commits, garantindo que cada conjunto compile e passe nos testes pertinentes.
Antes de publicar: revisar diff, executar testes com ambiente descartável e
conferir que somente fontes, licenças, docs e capturas aprovadas serão incluídos.
Publicar a branch de funcionalidade no GitHub somente em etapa posterior
autorizada; abrir PR conforme o fluxo da disciplina, sem modificar main diretamente.
Gravar vídeo seguindo o roteiro de até três minutos e citar os limites das evidências.
Nenhum envio ao Teams faz parte desta execução.

## Dados sintéticos para a apresentação

Após preparar o replica set novo conforme os dois terminais acima:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verificar-dashboard-local.ps1 -Demonstracao
```

O lançador gera uma base demo exclusiva, executa a fixture de teste com serviços
existentes e valida a interface e as APIs reais. Não altera o banco padrão.
O aviso de demonstração é inserido apenas pela automação no DOM, sem mudar a
interface normal ou ativar seed em produção. As datas de demonstração são fixas:
01/10/2026 a 07/10/2026. Capturas antigas de base vazia não são sobrescritas.

Para manter uma janela Chrome visível e a aplicação pronta para gravação manual,
use `-Apresentacao`; o teste retorna ao ADMIN e aos filtros do cenário ao final,
e o terminal aguarda Enter antes de encerrar seus processos. Nenhuma senha é
exibida: o login de teste é efetuado automaticamente. Esse modo interativo é
opcional; a homologação automatizada usa Chrome headless.

DashboardDemonstracaoTest só executa com PP_DEMO_URI explícita, replica set
codexDashboard em 127.0.0.1:27028 e banco inédito pediupatrao_test_demo_<UUID>.
Exporta expectativas sintéticas em PP_DEMO_EXPECTED, obrigatoriamente dentro
de target. Sem essas variáveis o teste é ignorado. Os dois testes opt-in exigem
configuração explícita; não usar o resultado histórico de 93 como contagem atual.
