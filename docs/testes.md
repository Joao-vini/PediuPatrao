# Testes, evidências e roteiro manual

Este é o documento final de validação. Requisitos em [requisitos.md](requisitos.md),
contratos em [api.md](api.md) e inicialização no [README](../README.md).
Resultados anteriores de 94/96/107 testes Java são históricos. A execução final abaixo
inclui remoção/correção de descontos, conta ativa e inicialização normal sem seeds.

## Revisão e organização

| Grupo | Decisão | Motivo |
| --- | --- | --- |
| Java, templates, CSS/JS, Maven e testes | Manter; mudanças pequenas identificadas abaixo | Funcionalidades e regressões existentes |
| AdminLTE, Chart.js, Bootstrap, jQuery, fontes e licenças | Manter | Distribuição oficial utilizada pelo layout |
| Quatro imagens em docs/evidencias | Manter | Comprovam estados vazio/preenchido, desktop/mobile |
| README + requisitos.md + api.md + testes.md | Documentação principal consolidada | Cobre os entregáveis do professor sem repetir relatórios longos |
| dashboard-administrativo.md, matriz-dashboard.md | Preservar; candidatos futuros à consolidação/remoção | Conteúdo funcional agora resumido nos documentos finais |
| evidencias-dashboard.md, homologacao-final.md | Preservar histórico e evidências | Documentam execuções efetivas anteriores |
| estabilizacao-dashboard.md | Preservar histórico; possível arquivo histórico separado no futuro | Diagnóstico inicial e decisões de consistência |
| roteiro-video-dashboard.md | Manter como apoio opcional | Roteiro de até três minutos |
| target, logs, bases descartáveis e .env | Fora do Git; preservar bancos e logs referenciados | Somente seis logs antigos redundantes foram removidos após inventário |
| .github ignorado e configurações de IDE | Não alterar sem necessidade | Origem/localidade não atribuída automaticamente ao autor desta revisão |

**Nenhum documento antigo ou captura foi excluído.** A tabela é inventário e
proposta; qualquer remoção posterior depende de autorização do aluno.
Git não atribui autoria de alterações ainda sem commit. Tudo que já estava
modificado ou novo foi tratado como preexistente, independentemente de autoria;
o histórico da conversa não substitui uma prova de autoria no repositório.

Na revisão anterior, a validação gerencial foi centralizada, um retorno simplificado
e Lombok/comentários redundantes retirados. Nesta etapa foram implementadas remoção
explícita e correção de descontos, conta GERENTE ativa consultada em cada alteração,
auditoria específica e verificação de topologia antes dos inicializadores.
Não foi removido teste, biblioteca de interface ou licença. JPA e dialecto
Thymeleaf foram preservados, sem inferir inutilidade apenas por uma busca textual.

Inventário anterior à exclusão em target/limpeza-inventario.json. Foram removidos,
somente de target, seis logs Maven reconhecidos como gerados, ignorados no Git e
sem referências nos fontes/docs: demonstracao-rf01-rf04-test.log, rf01-package.log,
rf05-package.log, rf05-test.log, validacao-final-package.log e validacao-final-test.log.
Os resultados mais recentes e todas as bases/diretórios MongoDB foram preservados.
Nenhum documento histórico foi removido. O README voltou a priorizar execução normal;
scripts de teste/demonstração permanecem opcionais.

Um defeito real do lançador foi corrigido: no Windows PowerShell 5, stderr nativo
com avisos do Mockito era elevado a exceção por ErrorActionPreference=Stop.
As chamadas Maven agora registram a saída e verificam o exit code; falhas reais
continuam abortando. O novo lançador manual encerra somente seus próprios processos.

Na revisão técnica para os cinco commits locais, foi corrigida uma divergência:
percentuais com mais de duas casas eram calculados com precisão maior que a
persistida no snapshot. Agora são recusados antes de salvar, com teste de regressão;
zeros decimais adicionais sem mudança de valor continuam aceitos. O teste de
rollback passou a aceitar somente 127.0.0.1:27028, sem credenciais, e replica set
codexDashboard, impedindo uso acidental do servidor original nessa integração.
As demais alterações funcionais já pendentes foram revisadas e preservadas.

## Resultados atuais

Execução final em **06/11/2026**, Java Temurin 21.0.12.1 explicitamente fixado:

| Verificação | Resultado efetivo |
| --- | --- |
| Suíte Java completa | **108 aprovados, 0 falhas, 0 erros, 0 ignorados** |
| JavaScript unitário | **7 aprovados, 0 falhas, 0 ignorados** |
| Maven package | **BUILD SUCCESS**, JAR executável gerado |
| MongoDB real descartável | Correção/remoção, rollback da remoção após falha de auditoria, falhas de pedido/exclusão e commit aprovados |
| Inicialização normal real | Spring Boot iniciado em banco novo sem fixture: zero pedidos/produtos/clientes/usuários/auditorias; login 200 e dashboard anônimo 302 |
| Topologia | Replica set/cluster aceitos; standalone e falha de conexão recusados sem escrita ou detalhes internos |
| Chrome real | Cenários vazio/preenchido, 45 comparações, correção/remoção com confirmação, auditoria filtrada, roles, CSRF e sessão encerrada aprovados |
| git diff --check | Sem erros de whitespace; apenas avisos locais LF/CRLF |

Logs atuais: `target/git-final-package.log`, `target/git-revisao-unitarios.log`,
`target/git-mongo-init.log`, `target/git-browser.log` e relatórios Surefire.
Os logs revisao-* e manual-preparacao-* registram a etapa anterior, não são resultado novo.
Os logs do PowerShell podem estar em UTF-16; Get-Content detecta o BOM ou aceita
`-Encoding Unicode`. Não versionar esses arquivos.

Os 108 incluem 17 de PedidoRF04Test, 5 de PedidoRF04RotasTest, 3 de MongoTopologyCheckTest
e três integrações opt-in (rollback, fixture e inicialização normal). A preparação
do replica set e a fixture do navegador foram executadas também separadamente;
não são somadas novamente aos 108. Nenhum teste foi pulado para declarar o build validado.

Sem variáveis opt-in, a execução local desta etapa teve 105 aprovados e 3 ignorados.
A execução final habilitou os três em bancos novos, sem acessar a configuração
Mongo original. Não considerar os números históricos como resultados atuais.

Cobertura preservada: RF01–RF07, cálculos, filtros, estados vazios, API e roles,
gerente desativado, credenciais ausentes, nulos na auditoria, transações/rollback,
UI e atualização assíncrona. Duas regressões novas verificam cancelamento por
credencial desativada e bloqueio sem credenciais antes de ler/gravar dados.

Os testes opt-in exigem PP_TEST_MONGO_URI (rollback/inicialização normal) e PP_DEMO_URI/PP_DEMO_EXPECTED
(fixture). Sem configuração explícita ficam ignorados; nunca usar a URI da aplicação
para habilitá-los. A fixture recusa bancos já existentes e usa nove registros de teste.

Comandos de teste/build usados nesta máquina, com as variáveis apontando somente
para o replica set descartável dedicado:

```powershell
.\mvnw.cmd -o "-Dmaven.repo.local=C:\Users\Joaozinho\.m2\repository" package "-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
node --test src/test/js/dashboard.test.cjs
git diff --check
```

O servidor de teste foi iniciado em um diretório `target/mongo-entrega-<UUID>`
novo, somente 127.0.0.1:27028, replica set codexDashboard. A fixture recebeu uma
URI `pediupatrao_test_demo_<UUID>` inédita e exportou expectativas em target.
O teste de transações cria seu próprio banco UUID. No final os processos foram
encerrados e os arquivos de dados preservados. O banco original não foi sondado.

### Cenário sintético de referência

01–07/10/2026: 9 registros, 7 financeiros válidos, 1 cancelado e 1 legado sem valor.
Faturamento R$ 488,50; ticket R$ 69,79; descontos R$ 6,50. Série diária:
80,00; 58,50; 45,00; 70,00; 0,00; 125,00; 110,00. Dados não são vendas reais.

- [Desktop preenchido](evidencias/adminlte-desktop-demonstracao.png)
- [Mobile preenchido](evidencias/adminlte-mobile-demonstracao.png)
- [Desktop vazio](evidencias/adminlte-desktop.png)
- [Mobile vazio](evidencias/adminlte-mobile.png)
- [Detalhes dos registros e cálculos da homologação anterior](homologacao-final.md)

O navegador automatizado compara nove combinações de filtros nos cinco endpoints
(45 comparações), cards, três gráficos, ranking, tooltip e recuperação de erro.
Isso não equivale à validação pessoal do aluno nem a teste de produção.

Nesta etapa a automação também corrigiu P2 de 10% para 5% pela interface e removeu
o desconto com confirmação. Conferiu itens preservados e auditoria filtrada em
/auditoria. Faturamento/ticket/descontos foram 491,75 / 70,25 / 3,25 após corrigir
e 495,00 / 70,71 / 0,00 após remover. Ao final o percentual foi restaurado pela API
para manter o cenário de apresentação coerente. Um endereço de auditoria usado
inicialmente no novo teste era inexistente; foi corrigido para a tela real, sem
criar endpoint extra nem retirar as verificações.
O teste em dinheiro confere também troco antes/depois no snapshot auditado,
além de bloquear remoção se o recebimento não cobrir o total restaurado.

## Execução normal, separada dos testes

Para operar com sua configuração autorizada, use `scripts/iniciar-normal.ps1`.
Ele fixa Java 21, respeita a URI original/variáveis externas, não carrega fixtures,
não inicia navegador/MongoDB e não encerra serviços de terceiros. O teste de ambiente
`-VerificarAmbiente` não conecta ao banco. O bootstrap ADMIN continua opt-in.

MongoTopologyCheck envia hello antes dos inicializadores: exige replica set ou
cluster compatível. O responsável informa o original na porta 27017 com replica set
rs0; essa informação não foi revalidada por acesso ao original nesta revisão.
Não houve acesso a dados existentes nem
conversão automática de standalone. Se necessário, o responsável deve preparar
backup e migração/configuração de replica set sem apagar os dados.

## Teste manual isolado opcional — Windows e VS Code

### 1. Preparar e iniciar

Pré-requisitos: Java 21, Node 24, Chrome e mongod instalados. A máquina usada na
validação tem mongod em `C:\Program Files\MongoDB\Server\9.0\bin\mongod.exe`.
No VS Code, abrir **Terminal → Novo Terminal**, selecionar PowerShell e entrar
na pasta interna que contém pom.xml, mvnw.cmd e scripts. Se o terminal estiver
na pasta externa do clone, executar `Set-Location .\PediuPatrao` uma única vez.

```powershell
.\scripts\iniciar-teste-manual.ps1
```

Se a política local impedir scripts, executar a mesma tarefa em processo isolado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/iniciar-teste-manual.ps1
```

Isso não altera a política permanente. Se mongod estiver em outro caminho, usar
`-MongoExecutable 'C:\caminho\mongod.exe'`. O script cria diretório/base novos em
target, usa somente loopback (27028/18088/9228), prepara replica set e JAR, executa
os cenários automatizados e mantém Chrome visível em ADMIN para uso pessoal.
Porta ocupada causa recusa; não encerre processos de terceiros para liberar portas.
Modo offline depende do cache Maven já instalado nesta máquina.

O primeiro preparo pode levar algum tempo. Se falhar, o erro indica o log em
target. Não tente resolver apontando para o MongoDB original. Nenhum serviço do
Windows ou MongoDB preexistente é alterado e não há limpeza de dados ao terminar.

URL: **http://127.0.0.1:18088/dashboard**. A autenticação inicial usa conta ADMIN
temporária e senha aleatória em memória. Para apenas visualizar dados e filtros,
essa sessão basta. Para trocar de perfil e autorizar operações, seguir o próximo
passo; as senhas automáticas do seed não são conhecidas nem compartilhadas.

### 2. Criar contas pessoais de teste antes de sair do ADMIN inicial

Na sessão ADMIN aberta, acessar **Usuários → Novo usuário** e criar três contas
novas com perfis ADMIN, GERENTE e ATENDENTE. Escolher nomes identificados como teste
e senhas próprias, diferentes e não previsíveis. Memorizar/guardar de forma privada;
não colocar senhas em scripts, docs, capturas ou arquivos do projeto.

Essas contas existem somente na base descartável desta execução. Não tente usar
a senha do usuário `demo-gerente` da fixture. **Não faça logout antes de ter criado
seu próprio ADMIN**, para conseguir voltar ao dashboard mais tarde.
Os três perfis ficam acessíveis pelo login normal, com senhas escolhidas pelo aluno.

### 3. Conferir o dashboard inicial

Confirmar o aviso **AMBIENTE DE DEMONSTRAÇÃO — DADOS DE TESTE** e os valores de
referência de outubro acima. Testar tooltip, menu recolhível, datas, status e tipo.
Selecionar setembro/2026: nenhum pedido e indicadores zerados; restaurar outubro.
Ao navegar ou fazer login de novo, o aviso inserido pela automação pode desaparecer;
a base continua sendo de teste. Identifique verbalmente os dados no vídeo/capturas.

### 4. Produtos e cliente

Como ADMIN, abrir **Produtos** e cadastrar dois produtos ativos: um tipo Pizza,
preço 50,00; outro tipo Bebida, preço 10,00. Usar nomes com prefixo ALUNO TESTE.
Também é possível usar os produtos existentes, mas os valores de referência abaixo
pressupõem esses dois preços. Não modificar os preços dos produtos do seed.

Sair pelo botão **Sair** e entrar com seu ATENDENTE. Abrir **Clientes → Novo** e
cadastrar um cliente claramente fictício, com telefone/endereço de teste, sem
informações pessoais reais. Selecione esse cliente nos pedidos seguintes.
ADMIN não cadastra clientes; GERENTE apenas consulta clientes. Isso é regra existente.

### 5. Criar pedidos de retirada, entrega e balcão

Criar via **Pedidos → Novo pedido**, usando **Adicionar produto** para múltiplos
itens e PIX como pagamento para simplificar o troco. Não forçar campos por DevTools.

1. **A — Retirada:** 2 pizzas de 50,00 + 1 bebida de 10,00, sem desconto: 110,00.
   Criar como ATENDENTE. Anotar o número/id do pedido mostrado na interface.
2. Sair e entrar como seu GERENTE. **B — Entrega:** 1 pizza + 2 bebidas, endereço
   fictício e entrega 5,00. No formulário, abrir **Autorizar desconto**, informar
   percentual 10 e as credenciais do seu GERENTE ativo. A prévia e a criação
   revalidam a operação: total esperado 67,50, desconto 7,50.
3. **C — Balcão:** 1 pizza + 1 bebida, sem desconto: 60,00. Manter antes da saída
   para testar o cancelamento. Não avançar esse pedido até FINALIZADO.

ATENDENTE não pode obter desconto/cancelamento apenas informando a senha de um
gerente. É necessário entrar como GERENTE. ADMIN também não executa essas operações.

### 6. Desconto posterior e avanço dos status

Ainda como GERENTE, abrir o detalhe do pedido A e aplicar desconto percentual 10.
Essa ação usa a sessão gerencial existente, sem segunda senha: desconto 11,00 e
total 99,00. Antes de avançar, usar **Corrigir desconto** para 5% (104,50), depois
**Remover desconto** e confirmar (110,00, desconto zerado). Conferir eventos
DESCONTO_SUBSTITUIDO e DESCONTO_REMOVIDO na auditoria; reaplicar 10% (99,00) para
manter os valores de referência abaixo. Conta desativada não pode alterar desconto,
mesmo com uma sessão antiga. Recebimento em dinheiro deve cobrir o total restaurado.
Avançar A por Iniciar preparo → Pedido pronto → Registrar retirada
→ Finalizar pedido. Não pular etapas. Desconto após saída/retirada deve ser recusado.

No pedido B, avançar por preparo → pronto → saiu para entrega; opcionalmente
finalizar. O valor financeiro continua 67,50. Para pedidos em dinheiro, testar
separadamente valor recebido >= total e troco correto; não alterar o cenário PIX.

### 7. Cancelar C e consultar auditoria

No detalhe de C, informar motivo de teste e as credenciais do seu GERENTE ativo.
Cancelar e confirmar status CANCELADO, justificativa e responsável. Não usar a
ação Excluir pedido neste roteiro: cancelamento já exercita a regra sem apagar nada.
Consultar **Auditoria** como GERENTE ou seu ADMIN: conferir criação, desconto,
transições e cancelamento, autoria/horário e valores, sem senhas expostas.

### 8. Voltar ao dashboard e comparar

Entrar com seu ADMIN e abrir `/dashboard`. Novos pedidos recebem a data atual,
enquanto o seed é de 01–07/10/2026. Filtrar **hoje**, todos os status/tipos. Anotar
os valores anteriores aos pedidos manuais; se já houver pedidos no mesmo período,
comparar os acréscimos, não assumir base zero.

Se o período tinha zero e foram criados apenas A, B e C conforme descrito:

| Indicador | Esperado após as ações |
| --- | ---: |
| Pedidos / válidos / cancelados | 3 / 2 / 1 |
| Faturamento | 166,50 |
| Ticket | 83,25 |
| Descontos | 18,50 |
| Ranking Pizza / Bebida | 3 unidades / 3 unidades |
| Bruto histórico Pizza / Bebida | 150,00 / 30,00 |

Os indicadores incluem A e B, excluem o cancelado C das métricas financeiras e
incluem C nas contagens. Testar filtros por tipo/status e atualização sem reload.
O aluno deve anotar os resultados observados; esta tabela é expectativa para um
roteiro futuro, não evidência de teste manual já executado.

### 9. Permissões e falhas

- ADMIN acessa dashboard e usuários, mas não cria/desconta/cancela pedidos.
- GERENTE acessa pedidos/auditoria, mas `/dashboard` e `/api/admin/dashboard`
  devem retornar acesso negado. ATENDENTE também não acessa dashboard/auditoria.
- Sem sessão, abrir `/dashboard` deve encaminhar ao login.
- Como ADMIN, desativar apenas o GERENTE criado por você, tentar novo login
  com ele e confirmar bloqueio; depois reativar pelo seu ADMIN se necessário.
  Isso não é prova de revogação instantânea de uma sessão antiga já autenticada.
- Datas invertidas/parâmetros inválidos devem apresentar erro; período vazio
  apresenta vazio. Não interromper ou trocar o banco real para simular falha.

### 10. Encerrar

Fechar abas quando terminar e voltar ao terminal que está aguardando. Pressionar
Enter: Chrome e aplicação são encerrados; o lançador externo encerra o mongod
que criou. Em caso de erro/Ctrl+C, o finally tenta encerrar os processos próprios.
Não usar taskkill genérico contra java/mongod nem apagar target ou diretórios de dados.
As contas ficam nos arquivos da base descartável encerrada; não são contas de um
ambiente permanente. Uma nova execução cria outro diretório/base exclusivos.

## Limites

O modo gráfico interativo e as ações pessoais do aluno ainda precisam ser feitos
pelo aluno; o lançador é verificado automaticamente com `-VerificarSemJanela`.
Produção, carga/failover e acessibilidade integral não foram homologados.
Nenhum commit, push, pull, merge ou deploy faz parte destes procedimentos.
