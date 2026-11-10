# Pediu Patrão

Sistema de pedidos de pizzaria em Java 21, Spring Boot 3.4.4, Spring Security,
MongoDB e Thymeleaf. Inclui usuários, produtos, clientes, pedidos com múltiplos
itens, descontos/correções/remoções gerenciais, status, cancelamento e auditoria.
Dashboard com AdminLTE oficial 3.2.0 e Chart.js 2.9.4 locais.
Branch de trabalho: `feature/dashboard-administrativo`.

## Documentação da entrega

- [Requisitos funcionais, não funcionais e perfis](docs/requisitos.md)
- [APIs, parâmetros, exemplos e erros](docs/api.md)
- [Testes atuais, evidências, limpeza e roteiro manual](docs/testes.md)

Os documentos históricos e as quatro capturas permanecem em docs. Não representam
novas execuções. Licenças e avisos dos fornecedores foram preservados.

## Executar normalmente no Windows / VS Code

Pré-requisitos: JDK 21 e MongoDB acessível com replica set ou cluster compatível
com transações. Maven Wrapper incluído; dependências podem precisar de download
na primeira execução. O JDK instalado nesta máquina é:
`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`.

Abra PowerShell na pasta interna que contém pom.xml e mvnw.cmd:

```powershell
Set-Location 'C:\Users\Joaozinho\Downloads\PediuPatrao\PediuPatrao'
.\scripts\iniciar-normal.ps1
```

Se a política local impedir scripts, sem alterar a política permanente:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/iniciar-normal.ps1
```

O script fixa Java 21 para Maven/aplicação e restaura o ambiente do terminal ao
terminar. Não inicia Chrome, não cria MongoDB, não encerra serviços de outros
projetos e não executa fixtures, testes ou apresentação. Usa application.properties
original e respeita `SPRING_DATA_MONGODB_URI`, `SERVER_PORT` e demais configurações
externas. Login padrão: **http://localhost:8080/login**; dashboard: **/dashboard**
com conta ADMIN existente. Se SERVER_PORT for diferente, ajustar a URL.
Encerrar a execução normal com Ctrl+C no próprio terminal.

Para verificar apenas Java/configuração, sem conectar ao banco:

```powershell
.\scripts\iniciar-normal.ps1 -VerificarAmbiente
```

Também é possível executar Maven diretamente, fixando o JDK:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" spring-boot:run
```

## MongoDB e autenticação

A URI original não foi alterada. Defina SPRING_DATA_MONGODB_URI externamente se
precisar de outra conexão autorizada; não grave senhas no código ou Git. O nome
replicaSet na URI precisa corresponder à topologia real: adicioná-lo à string
não converte um servidor standalone.

Antes dos inicializadores, a aplicação faz somente `hello` para conferir topologia.
Servidor indisponível ou standalone provoca erro explicativo antes da inicialização
de dados. Pedidos/auditoria compartilham transação: não há fallback para gravações
independentes. O responsável informa o banco original na porta 27017, replica set
`rs0`. Ele não foi acessado nem reconfigurado nesta revisão; a validação executada
usou exclusivamente outro replica set, descartável, na porta 27028.

Se o banco existente for standalone, a infraestrutura precisa ser preparada pelo
responsável: backup verificado e configuração/migração controlada para replica set,
sem apagar ou sobrescrever os dados. Esta entrega não faz essa alteração automática.

O bootstrap ADMIN continua desativado por padrão. Se não houver conta, sua ativação
é uma etapa explícita e externa com `APP_BOOTSTRAP_ADMIN_ENABLED=true`,
`APP_BOOTSTRAP_ADMIN_USERNAME` e `APP_BOOTSTRAP_ADMIN_PASSWORD`. Não há senha padrão. Contas existentes não são
promovidas, reativadas nem têm senha alterada. Desabilitar o bootstrap após uso.

## Funcionalidades e perfis

ADMIN administra usuários/produtos, consulta pedidos/auditoria e acessa o dashboard.
GERENTE registra pedidos e autoriza descontos/cancelamentos. ATENDENTE cadastra
clientes, registra pedidos e acompanha status sem privilégios gerenciais.

No detalhe do pedido, GERENTE ativo pode corrigir o percentual existente ou usar
**Remover desconto**, com confirmação. O total volta ao subtotal mais entrega;
campos de desconto são zerados e itens/preços históricos preservados. Operações
só são aceitas antes da saída/retirada, até 20%, com até duas casas decimais no
percentual, CSRF e auditoria transacional.
O dashboard consulta os valores persistidos ao atualizar; não usa dados fixos.

## Testes e modo isolado opcional

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" test
node --test src/test/js/dashboard.test.cjs
```

Executar Maven com Java 21. Três integrações são opt-in e nunca devem apontar para
o banco original. Comandos e resultados efetivamente executados: [docs/testes.md](docs/testes.md).
O modo normal não depende de Node, Chrome ou scripts de teste; esses componentes
são necessários somente para validação de interface e demonstração isolada.

Para testes pessoais com MongoDB novo e dados sintéticos explicitamente identificados:

```powershell
.\scripts\iniciar-teste-manual.ps1
```

Esse modo é opcional, separado da execução normal. O roteiro explica contas próprias
de teste, troca de perfis e encerramento. Não usar seus dados como vendas reais.

## Estrutura

- src/main/java: controllers, serviços, modelos, repositórios e segurança/configuração.
- src/main/resources: configuração normal, templates e assets/licenças oficiais.
- src/test: regressões, integrações opt-in e automação de navegador.
- scripts: inicialização normal com Java 21 e validações isoladas opcionais.
- docs: entrega acadêmica e evidências preservadas.
- target: build/logs/bases locais ignorados no Git; não publicar.

 Apresentação do Dashboard Administrativo

Vídeo demonstrativo do sistema Pediu Patrão, apresentando os indicadores de vendas, gráficos, filtros e integração com a API REST.

Duração: 2 minutos e 45 segundos.

▶️ [Assistir à apresentação do Dashboard Administrativo no YouTube](https://youtu.be/tQbge58183o)
