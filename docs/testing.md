# Testing with the shared test base

`services/test-support` gives every service real PostGIS, RabbitMQ and Redis containers for integration tests,
using the same images as `docker compose`. You need Docker Desktop running; CI runners already have Docker.

## 1. Add it to your service

In your service's `pom.xml`, under `<dependencies>` (no version needed, the root pom manages it):

```xml
<dependency>
    <groupId>com.swiftdrop</groupId>
    <artifactId>test-support</artifactId>
    <scope>test</scope>
</dependency>
```

## 2. Import the containers you need

| Config | Gives you | Typical owner |
|---|---|---|
| `PostgisContainerConfig` | Postgres 16 + PostGIS 3.5. Your Flyway migrations run in your schema | everyone with tables |
| `RabbitContainerConfig` | RabbitMQ 4.1, plus a `TestEvents` bean to publish events | M3, M4, M5, M6, M7, M8 |
| `RedisContainerConfig` | Redis 7.4 | M6 (idempotency), M7 (pub/sub) |

`@ServiceConnection` sets the datasource, `spring.rabbitmq.*` and `spring.data.redis.*` for you. Do not set them by hand.

```java
@SpringBootTest
@Import({PostgisContainerConfig.class, RabbitContainerConfig.class})
class StatusChangedFlowTest {

    @Autowired TestEvents events;
    @Autowired NotificationRepository notifications;

    @Test
    void statusChangeCreatesOneNotificationEvenIfRedelivered() {
        var payload = new ParcelStatusChanged(/* ... */);
        var sent = events.publish(EventTopology.PARCEL_STATUS_CHANGED, payload);
        events.publish(sent); // same eventId again: your consumer must skip it

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notifications.findByEventId(sent.eventId())).hasSize(1));
    }
}
```

`TestEvents` publishes the way the outbox relay does: the full envelope as JSON, routing key = event type,
`messageId` = `eventId`, persistent. It declares the `parcel.events` exchange if it is missing. Your consumer
declares its own queue, as in production.

## 3. Tips

- **Keep the same `@Import` list and properties across your test classes.** Spring caches one context per
  configuration, so identical tests share one set of containers. Each different configuration starts new ones.
- **Unit tests stay plain.** Use the containers for repository, consumer and end-to-end flow tests. Controller and
  authorisation tests are faster with `@WebMvcTest` and `jwt()` from spring-security-test (see any `PingControllerTest`).
- **Waiting for async consumers:** use Awaitility (`await().atMost(...)`), which comes with spring-boot-starter-test.
  Never `Thread.sleep`.
- **Running one test:** `mvn -pl services/<your-service> -am test -Dtest=YourTest -Dsurefire.failIfNoSpecifiedTests=false`
