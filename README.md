# NotifyQueue

A Java 17 local notification scheduler with duplicate detection, bounded retained events, exponential retry delays, dead-letter outcomes, and manual redrive. An injected transport lets tests simulate outages without sending email or using external services.

## Run

Install a JDK 17 or newer. No external libraries are required.

```sh
git clone https://github.com/priyamukkapati/NotifyQueue.git
cd NotifyQueue
sh run.sh test
sh run.sh demo
sh run.sh serve
```

`serve` runs the small console example; it does not start a network server. The example fails its first simulated delivery and then succeeds on retry.

## API inside the process

| Operation | Behavior |
| --- | --- |
| `enqueue(event, now)` | Adds a new event; an identical ID/payload retry returns false |
| `tick(now, transport)` | Processes one due event; returns false when none is due |
| `outcome(id)` | Returns an immutable status and attempt-count snapshot |
| `redrive(id, now)` | Reschedules only a dead event and resets its attempt budget |

Conflicting payloads with the same event ID are rejected. For three attempts and a 100 ms base delay, failures at time 0 and 100 are retried at time 100 and 300; the third failure becomes DEAD. A priority queue orders equal deadlines by event ID. Time is an explicit input so tests need no sleep. Callers must advance time monotonically.

The retention limit includes pending, delivered, and dead events. Delivered IDs are retained to prevent duplicates; this prototype does not automatically evict them. Once the limit is reached, new IDs are rejected.

## Reliability proof

`sh run.sh demo` enqueues 1,000 unique events and 1,000 duplicate submissions. Every event fails its first delivery; 10% fail permanently. Advancing virtual time through three deadlines produces:

```text
accepted=1000 duplicates=1000 delivered=900 dead=100 transport_attempts=2100 pending=0 elapsed_ms=22.47
```

Recorded locally on 2026-10-14 using OpenJDK 17.0.20. The elapsed time measures CPU work with a fake transport and virtual clock; it excludes real retry waits and network calls. It is not email-provider throughput or an exactly-once delivery claim.

`sh run.sh test` passes 13 checks for deduplication, conflicts, retry deadlines, dead-letter behavior, redrive, capacity, stable ordering, concurrent enqueue, and overflow safety. CI is configured to run tests and the invariant demo.

## Design choices and limitations

The injected transport and immutable outcomes separate scheduling from delivery implementation. Synchronization makes enqueue and tick consistent in one instance. The transport runs while holding the queue lock, so a slow transport blocks other operations. This makes concurrency behavior easy to inspect but is not a production worker design.

Events and deduplication state are entirely in memory and disappear on restart. There is no HTTP API, persistent outbox, broker, email provider, worker lease, jitter, retry classification, or cross-process coordination. A delivery that succeeds remotely but reports an error can be retried and duplicated; the recipient/provider needs its own idempotency support. There is no exactly-once guarantee.

## Next proof

Persist events and outcomes in a database, introduce worker leases, and test a crash after a provider accepts a request but before acknowledgement is stored. Use provider idempotency keys and report both attempts and unique deliveries. Add jitter and separate transient failures from permanent failures.

## Resume description

Java notification scheduler with bounded event retention, exponential backoff, dead-letter handling, and redrive; a deterministic outage demo deduplicated 1,000 repeated submissions and classified 900 deliveries and 100 permanent failures.
