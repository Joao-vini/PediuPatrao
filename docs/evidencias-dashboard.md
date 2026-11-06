# Evidências reais — Dashboard AdminLTE — 05/11/2026

Evidências históricas preservadas. Contagens e estado do Git neste relatório
referem-se àquela etapa; validação atual em [testes.md](testes.md).

## Homologação final desta continuidade

Resultado novo: **94 testes Java aprovados, 0 falhos/erros/ignorados;
7 testes JavaScript aprovados; BUILD SUCCESS**. Inclui nova fixture opt-in com
9 registros sintéticos e replica set com diretório e banco inéditos.
Chrome real conferiu 9 combinações × 5 endpoints, cards, gráficos preenchidos,
ranking, tooltip, loading, filtros, erros, CSRF e expiração real de sessão.
Não houve exceções JavaScript não tratadas ou console.error.

O relatório completo, os valores esperados/obtidos e os limites estão em
[homologacao-final.md](homologacao-final.md). As imagens vazias abaixo permanecem
intactas; novas capturas identificadas como teste estão em
[desktop demonstração](evidencias/adminlte-desktop-demonstracao.png) e
[mobile demonstração](evidencias/adminlte-mobile-demonstracao.png).

Logs atuais: target/homologacao-final-package.log e target/homologacao-browser.log.
O texto a seguir registra a execução anterior com base vazia, não substitui a
homologação preenchida e não deve ser apresentado como resultado novo.

## Histórico anterior: base vazia

Branch: `feature/dashboard-administrativo`. Java Temurin 21.0.12.1, Maven Wrapper
3.9.9, Spring Boot 3.4.4. Execuções na raiz Git/Maven interna `PediuPatrao`.

## Suíte Java e build

Execução completa com MongoDB de teste explicitamente habilitado:
**93 testes aprovados, 0 falhas, 0 erros, 0 ignorados; BUILD SUCCESS.**
Log final: `target/adminlte-final-package.log`; relatórios por classe em
`target/surefire-reports/TEST-*.xml`; JAR em `target/pediupatrao-1.0-SNAPSHOT.jar`.

| Classe | Testes aprovados |
| --- | ---: |
| DataInitializerTest | 4 |
| AuditoriaTelaTest | 4 |
| DashboardControllerTest | 7 |
| PedidoRF02Test | 7 |
| PedidoRF03Test | 9 |
| PedidoRF04RotasTest | 3 |
| ProdutoGestaoTest | 7 |
| UsuarioRF01Test | 12 |
| AuditoriaConsultaTest | 6 |
| AuditoriaRF05Test | 7 |
| DashboardServiceTest | 7 |
| PedidoMongoIntegrationTest | 1 |
| PedidoRF04Test | 9 |
| PedidoTransactionTest | 2 |
| ProdutoServiceTest | 8 |

Sem PP_TEST_MONGO_URI, a execução inicial desta continuidade teve 92 aprovados e
1 ignorado, conforme planejado para um ambiente sem MongoDB descartável.
Depois de localizar o executável fora do PATH, o teste foi efetivamente habilitado
na execução final. Portanto a pendência anterior de rollback foi resolvida no
ambiente de teste, sem presumir suporte do banco original.

## Integração real com MongoDB

Executável local: `C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe`.
Processo separado, somente `127.0.0.1:27028`, replica set `codexDashboard`, dbpath
`target/mongo-adminlte-isolado`; nenhuma conexão à URI existente na aplicação.
Cada teste cria banco próprio com prefixo `pediupatrao_test_` e UUID.
Nenhum registro ou banco existente foi apagado.

O teste real verificou:

1. Save do pedido seguido de falha na auditoria: total persistido continua 100.
2. Falha na gravação do pedido: nenhum evento de auditoria persistido.
3. Evento gravado antes da tentativa de exclusão que falha: pedido preservado,
   evento revertido.
4. Caminho de sucesso: desconto leva total de 100 a 90 e um evento é confirmado.

Uma execução inicial do teste revelou incompatibilidade ao usar Mockito.spy
sobre o proxy Spring do repositório. Corrigido com mock delegando ao repositório
real, mantendo MongoDB real e injeção de falhas. Não foi removida nenhuma asserção
para esconder a falha; rollback e commit foram verificados após a correção.

## JavaScript

`node --check src/main/resources/static/js/dashboard.js`: código 0.
`node --test src/test/js/dashboard.test.cjs`: **7 aprovados, 0 falhas, 0 ignorados**.
Cobertura: contexto da API, vazio, erro sem dados antigos, texto seguro, resposta
nos gráficos, sessão expirada, destruição de instâncias antigas, comparação das
partes do período e indisponibilidade da biblioteca Chart.js.

## Aplicação e navegador reais

Lançador: `scripts/verificar-dashboard-local.ps1`; automação sem pacote novo:
`src/test/js/dashboard.browser.cjs`, via CDP e WebSocket nativo do Node 24.
Chrome headless instalado na máquina. Spring Boot real em `127.0.0.1:18088`,
MongoDB descartável em base nova por execução, bootstrap e senhas aleatórios
somente em variáveis de ambiente. GERENTE e ATENDENTE de teste foram cadastrados
pela API autenticada com CSRF. Não foram inseridos pedidos para fabricar vendas.

Verificado com sucesso:

- Inicialização Spring Boot, conexão ao replica set e login ADMIN real.
- Anônimo redirecionado de `/dashboard` para login.
- CSS/JS AdminLTE 3.2.0, plugin PushMenu e três instâncias Chart.js carregados.
- Cinco endpoints REST respondendo 200 e JSON, com base de pedidos vazia.
- Estado vazio, cards em zero obtidos do MongoDB e comparação dentro do período.
- Formulário de filtros e atualização assíncrona; data inválida retorna JSON 400.
- Desktop 1440×1100: sidebar e início do conteúdo em 250 px, sem sobreposição.
- Mobile 390×844: sem overflow horizontal; abrir/recolher menu funcional.
- Falha de rede induzida pelo Chrome, mensagem de erro, indicadores ocultos e
  recuperação após nova atualização.
- GERENTE e ATENDENTE recebem 403 na página e nos cinco endpoints.

Log: `target/adminlte-browser.log`. Capturas reais, revisadas nesta sessão:

- [Desktop](evidencias/adminlte-desktop.png): viewport, não captura integral.
- [Mobile](evidencias/adminlte-mobile.png): página completa em viewport móvel.

As capturas exibem uma base de pedidos vazia e identidade de teste aleatória;
não representam movimento de vendas real. Uma primeira captura integral desktop
tinha comportamento inadequado de viewport; a captura final usa o viewport
com menu explicitamente expandido e verifica as coordenadas antes da imagem.
A automação também foi ajustada para não serializar o objeto jQuery cíclico e
para detectar Chart.js 2 pela função carregada, que não expõe Chart.version.

## Segurança e organização

Varredura direcionada em src/main para URI MongoDB com senha, prefixos comuns de
tokens GitHub/OpenAI e encode/setPassword com literal: nenhuma ocorrência.
Isso é uma verificação dirigida, não uma garantia contra qualquer segredo no
histórico do Git. Credenciais de testes unitários são fixtures, não contas reais.
`.gitignore` cobre target, .env, logs, temporários e PID; exemplos .env.example
podem ser versionados sem valores secretos.

`git diff --check`: sem erros de whitespace; avisos locais de LF/CRLF apenas.
Main permanece em `3bcb4c204b7f10adef103d1ad21275b82e996f78`.
Não houve commit, push, merge, deploy ou envio ao Teams.
Processos de aplicação/Chrome foram encerrados pelo lançador; o mongod dedicado
foi encerrado após a validação final. Dados de teste em target foram preservados.

## Não executado / limites

- Homologação com pedidos reais, ranking povoado e séries de vendas do negócio
  em navegador: não acessado; cálculos cobertos com fixtures de teste.
- Benchmark, explain e criação de índices em volume real.
- Failover multinó, perda de conexão durante commit, idempotência de comandos.
- Auditoria integral de acessibilidade e todos os dispositivos possíveis.
- Validação da topologia ou segurança do MongoDB original/produção.
- Gravação do vídeo, publicação no GitHub e homologação de produção.

Nenhuma dessas validações é declarada concluída. Procedimento de reprodução,
API e limites estão em dashboard-administrativo.md; rastreabilidade em
matriz-dashboard.md; roteiro de até três minutos em roteiro-video-dashboard.md.
