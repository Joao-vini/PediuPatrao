# Preparação do Dashboard Administrativo

> Histórico da preparação inicial. A implementação posterior das transações e
> do dashboard está em [dashboard-administrativo.md](dashboard-administrativo.md),
> com validações finais em [evidencias-dashboard.md](evidencias-dashboard.md).
> Atualização: a validação de rollback em replica set descartável foi executada
> nesta continuidade, após localizar mongod fora do PATH. Resultados finais nos
> documentos acima. O texto abaixo preserva o histórico da preparação inicial.

Verificação em 05/11/2026. Git e Maven estão na pasta interna `PediuPatrao`.
Base: `main`, commit `3bcb4c204b7f10adef103d1ad21275b82e996f78`.
Árvore inicialmente limpa; `git fsck --full` sem erros. Consulta `git ls-remote`
confirmou `origin/main` no mesmo commit, sem pull. `origin` aponta para
Joao-vini/PediuPatrao e `upstream` para ATRAHProducoes/pediupatrao.
Branch de trabalho: `feature/dashboard-administrativo`, criada da main atual.
Não foi criada develop: esta preparação segue a base explicitamente solicitada.

## Ambiente e validação

O pom exige Java 21; o terminal utiliza Temurin 21.0.12.1. Maven Wrapper 3.9.9.
Os 70 testes originais passaram; após as correções, 76 testes passaram, sem
falhas, erros ou testes ignorados (seis regressões adicionadas). Testes são unitários Mockito e slices WebMvcTest
com serviços/repositórios simulados. A aplicação completa não foi iniciada.
Execução offline, com autoconfigurações MongoDB excluídas explicitamente:

```powershell
.\mvnw.cmd -o "-Dmaven.repo.local=C:\Users\Joaozinho\.m2\repository" test "-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
```

## Diagnóstico e correções iniciais

1. **Administrador fixo:** DataInitializer criava conta com senha literal e
   promovia uma conta existente de mesmo nome. Agora o bootstrap vem desativado
   e requer `APP_BOOTSTRAP_ADMIN_ENABLED=true`, `APP_BOOTSTRAP_ADMIN_USERNAME`
   e `APP_BOOTSTRAP_ADMIN_PASSWORD` externos. A senha passa pelo PasswordEncoder.
   Contas existentes não são alteradas. Não versionar valores dessas variáveis.
   A correção não troca credenciais já persistidas: avaliar sua substituição
   pelo fluxo administrativo em ambiente autorizado antes de uso real.
2. **Pedido e auditoria:** criar, avançar status, aplicar desconto e cancelar
   salvam o pedido antes da auditoria. Se a auditoria falhar, o pedido pode ficar
   alterado sem registro. Excluir faz o inverso: pode haver evento de exclusão
   sem exclusão efetiva. Não há atomicidade entre essas coleções. Reordenar saves
   ou tentar compensar manualmente não resolve falhas e concorrência.
   Pendente escolher e validar transação MongoDB com replica set ou outbox
   embutida no pedido. Não foi adicionado @Transactional isoladamente, pois
   não garantiria atomicidade sem transaction manager e infraestrutura adequada.
3. **Descontos:** regras existentes restringem ao GERENTE, percentual até 20%,
   antes da saída/retirada; criação exige também credenciais de gerente. A rota
   de desconto em pedido existente usa o perfil da sessão, sem segunda senha.
   Essas regras foram preservadas. A revalidação agora rejeita gerente inativo,
   inclusive para autorização de criação e cancelamento. Teste cobre a rejeição
   antes de consultar produtos ou gravar pedidos/auditoria.
4. **Nulos na auditoria:** mapaSeguro preservava valores nulos, incompatíveis
   com Map.copyOf. Agora usa mapa sanitizado novo com visão imutável que aceita
   nulos. Preserva ausência de valor e filtragem de campos sensíveis. Teste cobre
   nulos, imutabilidade e independência do mapa recebido.

## Antes do dashboard

Resolver atomicidade pedido/auditoria e testar falhas e concorrência em MongoDB
descartável, isolado e sem credenciais reais; revisar o bootstrap administrativo
no ambiente de uso. Somente após estabilização iniciar o dashboard. Não houve
merge, push, deploy, mudança de main ou operação sobre banco real.
