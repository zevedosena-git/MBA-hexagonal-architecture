# Eventos

Aplicação Java 17 multi-módulo (`domain`, `application`, `infrastructure`) com Gradle.

## Subindo o projeto

A aplicação usa MySQL em `localhost:3306`, banco `events`, usuário `root` e senha `root`. Para subir o banco com o `docker-compose.yml` do projeto:

```
docker compose up -d
```

Se o banco já existia de uma versão anterior do projeto, recrie-o (`docker compose down -v` e `docker compose up -d`): o `ddl-auto=update` não altera colunas existentes, então a nova coluna `status` de eventos e o novo valor `CANCELLED` de ingressos não seriam aplicados ao schema antigo.

Para subir a API REST e o GraphQL (GraphiQL em `/graphiql`), na raiz do projeto:

```
gradlew.bat :infrastructure:bootRun     # Windows
./gradlew :infrastructure:bootRun       # Linux/macOS
```

## Rodando os testes

A suíte usa H2 em memória (perfil `test`), sem necessidade do MySQL:

```
gradlew.bat test     # Windows
./gradlew test       # Linux/macOS
```

## Cascata de cancelamento

O `CancelEventUseCase` chama `Event.cancel`, que registra o evento de domínio `EventCancelled` (type `event.cancelled`). Ao persistir o agregado, o `EventDatabaseRepository` grava esse evento na outbox. O `OutboxRelay` publica o conteúdo no `QueueGateway`, e o `ConsumerQueueGateway` roteia `event.cancelled` para o `CancelEventTicketsUseCase`, que busca os ingressos do evento via `TicketRepository.ticketsByEventId` e cancela cada um com `Ticket.cancel`. O agregado de Evento nunca toca o de Ingresso.
