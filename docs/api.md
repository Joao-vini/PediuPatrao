# API administrativa

Base `/api/admin/dashboard`. Todos os endpoints usam **GET**, sessão autenticada
com perfil **ADMIN** e `Cache-Control: no-store`. Fonte: coleção `pedidos` do
MongoDB via DashboardService/MongoTemplate. A interface não usa serviços externos.

| Endpoint | Resposta |
| --- | --- |
| `/api/admin/dashboard` | filtro, indicadores, vendas, situacao, produtos e tipos |
| `/api/admin/dashboard/indicadores` | objeto com faturamento, pedidos, pedidosValidos, cancelados, ticketMedio, descontos e semValor |
| `/api/admin/dashboard/vendas` | lista de data, faturamento e pedidos válidos por dia |
| `/api/admin/dashboard/status` | lista de status e quantidade |
| `/api/admin/dashboard/produtos` | lista de produtoId, nome, quantidade e valorBruto |

## Parâmetros opcionais comuns

| Nome | Valores / validação |
| --- | --- |
| inicio | `yyyy-MM-dd`; padrão primeiro dia do mês no fuso de São Paulo |
| fim | `yyyy-MM-dd`; padrão hoje; fim >= início, máximo 366 dias inclusive |
| status | RECEBIDO, EM_PREPARO, EM_PREPARACAO, PRONTO, PRONTO_PARA_RETIRADA, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO, SEM_STATUS |
| tipo | ENTREGA, RETIRADA, BALCAO; ausente/vazio seleciona todos |

Datas são convertidas a Instant com início inclusivo e fim exclusivo no dia
seguinte. A distribuição `tipos` conta também registros antigos sem tipo como
SEM_TIPO; esse rótulo não é um valor de filtro aceito. Limite de 50.000 pedidos
por consulta; filtros e projeção são aplicados antes do cálculo em memória.

## Requisição e resposta

Em sessão ADMIN de teste, abrir no navegador:

```http
GET /api/admin/dashboard/indicadores?inicio=2026-10-01&fim=2026-10-07
Accept: application/json
```

Resposta do cenário **sintético**, conferida no ambiente descartável:

```json
{
  "faturamento": 488.50,
  "pedidos": 9,
  "pedidosValidos": 7,
  "cancelados": 1,
  "ticketMedio": 69.79,
  "descontos": 6.50,
  "semValor": 1
}
```

Exemplos de entradas dos outros endpoints (dados sintéticos, não vendas reais):

```json
{"data":"2026-10-01","faturamento":80.00,"pedidos":1}
```

```json
{"status":"CANCELADO","quantidade":1}
```

```json
{"produtoId":"identificador-do-produto-de-teste","nome":"TESTE — Bebida B","quantidade":7,"valorBruto":70.00}
```

As três respostas de detalhe acima são listas desses objetos; o produtoId é
gerado pelo MongoDB e o texto mostrado é apenas uma descrição estrutural.
Período vazio retorna indicadores zerados, ranking/distribuições vazios e série
diária com zeros. Não substitui indisponibilidade do banco por dados fictícios.
As fórmulas e a exclusão de cancelados estão em [requisitos.md](requisitos.md).

## Erros

| Código | Situação | Tratamento |
| --- | --- | --- |
| 302 | Sem sessão / sessão encerrada | Redireciona ao login, conforme Spring Security existente |
| 400 | Datas, intervalo, status ou tipo inválidos | JSON com `erro`; não consulta o Mongo |
| 403 | GERENTE/ATENDENTE ou outra permissão insuficiente | Bloqueio do Spring Security; não presumir JSON nessa resposta |
| 422 | Mais de 50.000 pedidos selecionados | JSON com `erro`; reduzir período/filtros, sem totais parciais |
| 503 | Falha de acesso ao banco | JSON genérico com `erro`, sem exceção interna ou credenciais |

Exemplo estrutural de erro:

```json
{"erro":"Não foi possível consultar os pedidos. Tente novamente mais tarde."}
```

GET de consulta não precisa de token CSRF; isso não altera a obrigatoriedade do
token nos POST/PUT/PATCH/DELETE do sistema. As APIs não enviam cliente, endereço,
observações, senhas ou credenciais gerenciais. Não colocar senhas em URLs.

## Aplicar, corrigir e remover desconto de pedido

Os cinco GET administrativos acima permanecem inalterados. As operações de pedido
continuam separadas e exclusivas de **GERENTE autenticado e ativo no banco**:

| Método / rota | Parâmetros | Efeito |
| --- | --- | --- |
| POST /api/pedidos/{id}/desconto | percentual, de 0 a 20, até duas casas decimais | Aplica ou substitui percentual sobre a base original; zero normaliza ausência de desconto |
| DELETE /api/pedidos/{id}/desconto | nenhum | Zera desconto e restaura subtotal + entrega |
| POST /pedidos/{id}/desconto | tipoDesconto=PERCENTUAL ou SEM_DESCONTO; descontoInformado | Formulário Thymeleaf existente, redireciona ao detalhe |

POST/DELETE exigem token CSRF da sessão (por exemplo, X-CSRF-TOKEN). Não são
operações GET e não devem ser chamadas por link. O botão de remoção pede confirmação
na interface; confirmação não substitui as validações do servidor.

```http
POST /api/pedidos/ID-DE-TESTE/desconto?percentual=5
X-CSRF-TOKEN: TOKEN-DA-SESSAO
```

```http
DELETE /api/pedidos/ID-DE-TESTE/desconto
X-CSRF-TOKEN: TOKEN-DA-SESSAO
```

Resposta 200 da API: pedido atualizado. Fragmento estrutural após remover desconto
de uma base de subtotal 100,00 + entrega 5,00 (não é uma venda real):

```json
{"tipoDesconto":"SEM_DESCONTO","descontoInformado":0.00,"descontoPercentual":0.00,"valorDesconto":0.00,"valorTotal":105.00}
```

400: percentual inválido, estado incompatível ou troco insuficiente; 403: perfil,
conta desativada/alterada ou CSRF inválido; 404: pedido inexistente. Pedidos cancelados,
finalizados, retirados ou que já saíram para entrega não aceitam alteração.
O JSON completo preserva os itens originais. As falhas de persistência propagam
erro e rollback, sem confirmar parcialmente pedido/auditoria.
