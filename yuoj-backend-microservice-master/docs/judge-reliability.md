# Reliable judge task delivery

## Delivery flow

1. The question service stores `question_submit` and `judge_task_outbox` in one local transaction.
2. `JudgeTaskOutboxPublisher` atomically claims committed outbox rows and publishes them with RabbitMQ publisher confirms and mandatory returns enabled.
3. A confirmed event is marked `SENT`. A failed or timed-out publish is returned to `PENDING` with capped exponential backoff.
4. The judge consumer changes a submission from `WAITING` to `RUNNING` with a conditional database update. Only the consumer that updates one row may invoke the sandbox.
5. Failed executions are returned to `WAITING` and routed through 5, 10, and 20 second delay queues.
6. After three failed retries, the submission is marked `FAILED` and the message is rejected into `judge.dead.queue`.

Publisher confirms do not remove the possibility of a duplicate publish when the service crashes after broker acknowledgement but before marking the outbox row sent. The conditional `WAITING -> RUNNING` update is therefore required even when the publisher is reliable.

## Database migration

New databases execute `mysql-init/z_20260720_judge_outbox.sql` automatically after `create_table.sql`.

For an existing MySQL volume, execute the migration once manually:

```shell
mysql -uroot -p yuoj < mysql-init/z_20260720_judge_outbox.sql
```

The migration is idempotent: the outbox table uses `CREATE TABLE IF NOT EXISTS`, and the composite submission index is added only when absent.

## RabbitMQ topology

- Main queue: `judge.code.queue`
- Retry queues: `judge.retry.queue.1`, `judge.retry.queue.2`, `judge.retry.queue.3`
- Dead-letter queue: `judge.dead.queue`
- Maximum consumer retries: 3

The topology uses new names, so it does not conflict with the legacy `code_queue` that may already exist in a persistent RabbitMQ volume.

## Verification

Build production code:

```shell
mvn -pl yuoj-backend-question-service,yuoj-backend-judge-service -am -DskipTests package
```

Run the focused reliability tests:

```shell
mvn -pl yuoj-backend-question-service,yuoj-backend-judge-service -am \
  -Dtest=JudgeTaskOutboxPublisherTest,JudgeRabbitMqConfigTest,MyMessageConsumerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```
