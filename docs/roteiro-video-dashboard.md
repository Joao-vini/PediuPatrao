# Roteiro de vídeo — até 3 minutos

Preparar antes: replica set descartável novo conforme dashboard-administrativo.md;
executar o lançador com `-Apresentacao` e aguardar a mensagem de pronto no terminal.
O Chrome volta ao ADMIN de teste, filtros de 01/10/2026 a 07/10/2026 e aviso
**AMBIENTE DE DEMONSTRAÇÃO — DADOS DE TESTE**. Login é feito pelo lançador com
credenciais aleatórias; não exibir senha, URI ou variáveis de ambiente.
Gravar manualmente, sem apresentar os valores sintéticos como vendas reais.

| Tempo | Demonstração e fala sugerida |
| --- | --- |
| 0:00–0:20 | Mostrar login/identificação ADMIN e o aviso de teste. “O lançador autentica uma conta aleatória em ambiente descartável. O painel integra AdminLTE oficial 3.2.0 e Chart.js ao Spring Boot/Thymeleaf existentes. Estes nove registros são sintéticos.” |
| 0:20–0:50 | Mostrar cards: 488,50, 9, 69,79 e 6,50. “São sete pedidos financeiros válidos, um cancelado e um legado sem valor. Faturamento inclui entrega e já considera o desconto. O ticket divide pelos sete válidos.” |
| 0:50–1:20 | Passar cursor no gráfico e mostrar status/tipos/ranking. “A série tem um dia sem vendas; o ranking preserva preço histórico e exclui o cancelado. As partes do período somam 253,50 e 235,00, com quantidades de dias explícitas.” |
| 1:20–1:45 | Filtrar entrega e atualizar: 183,50 em dois pedidos. Selecionar setembro vazio e restaurar outubro. Abrir /api/admin/dashboard/indicadores?inicio=2026-10-01&fim=2026-10-07 em outra aba. “As cinco APIs são do próprio projeto e alimentam a interface sem recarregar a página.” |
| 1:45–2:10 | Mostrar SecurityConfig e evidências de 403 para GERENTE/ATENDENTE. “ADMIN consulta o dashboard; desconto/cancelamento permanecem gerenciais, CSRF foi preservado. Pedido e auditoria compartilham transação, exigindo replica set.” |
| 2:10–2:40 | Mostrar log final e relatório: 108 Java, 7 JavaScript, BUILD SUCCESS. “Rollback e commit foram reconfirmados em MongoDB real descartável; Chrome comparou nove filtros nos cinco endpoints, gráficos preenchidos, falhas de rede e sessão encerrada.” |
| 2:40–2:55 | Mostrar docs e branch feature/dashboard-administrativo. “A documentação contém RF01–RF07, regras, endpoints e execução. Main preservada e alterações organizadas em cinco commits locais, sem push ou deploy.” |

Para mostrar a tela de login sem revelar senha, abrir /login na sessão de teste
e retornar a /dashboard; explicar que a autenticação foi feita pelo lançador.
Não fingir digitação nem expor credenciais para a gravação.

Mostrar a matriz RF01–RF07 e distinguir homologação sintética de validação em
produção. Carga/failover e dados reais do negócio ficam fora desta entrega local.
O vídeo não é gravado automaticamente. Ao terminar, pressionar Enter no terminal
do lançador para encerrar aplicação/Chrome e depois Ctrl+C no terminal mongod.
Os dados descartáveis não precisam ser apagados. Publicação só em etapa autorizada.
