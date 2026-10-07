# Pediu Patrão - Atividade Prática M1

## Descrição

Sistema de gestão de pedidos de uma pizzaria, desenvolvido e evoluído para a disciplina de Análise e Projeto de Software. A atividade inclui controle de usuários, cadastro e acompanhamento de pedidos, descontos, cancelamentos e auditoria.

## Tecnologias utilizadas

- Java 21
- Spring Boot 3.4.4
- Spring Security
- MongoDB e Spring Data MongoDB
- Thymeleaf
- Maven Wrapper
- HTML, CSS, JavaScript e Bootstrap
- JUnit 5 e Mockito para testes

## Requisitos implementados

- **RF01:** perfis ADMIN, GERENTE e ATENDENTE, com autorização no backend e proteção contra atribuição indevida de privilégios.
- **RF02:** pedidos com itens, quantidades, preços, descontos e total calculados no servidor. Os preços utilizados vêm do cadastro de produtos.
- **RF03:** fluxo `RECEBIDO → EM_PREPARO → PRONTO → SAIU_PARA_ENTREGA/RETIRADO → FINALIZADO`. Entrada e saída registram horário e responsável no backend. Transições inválidas são bloqueadas.
- **RF04:** desconto somente percentual, limitado a 20% e permitido antes da saída. Somente GERENTE pode aplicar desconto ou cancelar pedido. Cancelamento exige justificativa.
- **RF05:** auditoria persistente no MongoDB para criação e alterações disponíveis de pedidos, incluindo status, desconto e cancelamento, e alterações de clientes. Registra identidade, perfil, horário, valores anteriores/posteriores e justificativa quando necessária.

## Principais alterações

- Ajuste das permissões nas rotas, nos services e nas ações exibidas pela interface.
- Validação de quantidades e cálculo dos valores usando dados confiáveis do servidor.
- Controle das etapas do pedido, com registro de entrada e saída e compatibilidade de leitura com estados antigos.
- Restrição de desconto e cancelamento ao GERENTE, inclusive em comparação ao ADMIN.
- Registro dos campos de cliente que realmente mudaram, incluindo telefone e endereço.
- Proteção dos registros de auditoria contra alteração por HTTP e filtragem de dados sensíveis na gravação e consulta.

## Como executar

Pré-requisitos:

- JDK 21 instalado, com `JAVA_HOME` e `PATH` configurados.
- MongoDB disponível e em execução.
- Acesso à internet na primeira execução para baixar as dependências pelo Maven Wrapper.

Por padrão, a aplicação conecta ao MongoDB em `mongodb://127.0.0.1:27017/pizzaria`. Outra conexão pode ser definida pela variável de ambiente `SPRING_DATA_MONGODB_URI`.

No PowerShell, abra a raiz do projeto, a pasta que contém `pom.xml` e `mvnw.cmd`, e execute:

```powershell
.\mvnw.cmd spring-boot:run
```

Não é necessário instalar o Maven separadamente, pois o projeto possui o wrapper.

## Testes

Na mesma pasta, execute:

```powershell
.\mvnw.cmd test
```

A validação final possui 69 testes automatizados passando. Eles verificam permissões, cálculo dos pedidos, transições, descontos, cancelamentos, auditoria e renderização das páginas. Os testes usam dados simulados, sem alterar o MongoDB existente.

## Acesso

Após iniciar a aplicação, acesse `http://localhost:8080`. O login utiliza uma conta já cadastrada no sistema.

## Perfis

- **ADMIN:** administra usuários e atribui perfis, consulta pedidos e visualiza auditoria. Não pode aplicar desconto nem cancelar pedidos.
- **GERENTE:** gerencia pedidos, aplica descontos, cancela pedidos com justificativa, consulta clientes e visualiza auditoria.
- **ATENDENTE:** cria e atualiza clientes, registra pedidos e acompanha seu andamento. Mantém o avanço das etapas permitido pelo fluxo existente, sem acesso a desconto, cancelamento ou auditoria.
