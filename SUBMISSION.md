# Product Engineering Challenge Submission

## Candidate

- **Name:** Nithishkumar
- **Email:** Nithishkumar28200@gmail.com
- **GitHub:** https://github.com/NITHISHKUMAR28
- **Selected problem:** Problem 3: Durable Reminders and Follow-Ups
- **Demo video:** [Link to Demo Video Here]

---

## Run the project

### Prerequisites
- **Java**: JDK 17 
- **Port**: Port `8085` available (configured in `src/main/resources/application.properties`)

### Setup and Run Commands

1. **Clone and build**:
   ```bash
   git clone <repo-url>
   cd remainder
   ./mvnw clean package -DskipTests
   ```

2. **Start the application**:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(Or on Windows cmd: `mvnw.cmd spring-boot:run`, or run `com.nithish.remainder.RemainderApplication` in IntelliJ).*

   The service starts on **`http://localhost:8085`** with an embedded H2 file database (`./data/reminders`).
   The H2 Console is available at **`http://localhost:8085/h2-console`** (JDBC URL: `jdbc:h2:file:./data/reminders`, User: blank/empty, Password: blank/empty).

### How to Trigger Scenarios in Postman

> **Import Postman Collection:**
> In Postman, click **File -> Import**, and select `postman_collection.json` from the root of this project.
> All endpoints are pre-configured with `{{baseUrl}}` set to `http://localhost:8085`.

---

#### 1. Positive Scenarios

##### Scenario 1: Standard Scheduled Delivery (On-Time Execution)
- **What we are testing:** Creating an active reminder in a named IANA time zone (`Asia/Kolkata`) and proving it only fires when controlled time reaches its scheduled instant.
- **Step 1: Create Reminder**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/reminders`
  - **Body (JSON):**
    ```json
    {
      "content": "Drink water and stretch",
      "localDateTime": "2026-10-01T15:40:00",
      "zone": "Asia/Kolkata"
    }
    ```
  - **Purpose:** Schedule a reminder for 15:40 IST (which equals 10:10:00 UTC).
  - **Expected Outcome:** `201 Created` with `"state": "SCHEDULED"`, `"version": 1`, `"deliveryKey": "1:1"`, and `"attemptCount": 0`.
- **Step 2: Verify Scheduler Does Not Fire Early**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/test/tick`
  - **Purpose:** Test that background polling does not trigger reminders before their due time.
  - **Expected Outcome:** `{"processedItems": 0}`. Reminder remains in `SCHEDULED`.
- **Step 3: Fast-Forward Clock Past Due Instant**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/test/clock/set?isoInstant=2026-10-01T10:15:00Z`
  - **Purpose:** Set the controlled clock to 10:15 UTC (5 minutes after the due time) without waiting in real time.
  - **Expected Outcome:** `200 OK` with updated clock instant.
- **Step 4: Execute Scheduler Worker**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/test/tick`
  - **Purpose:** Trigger the scheduler to claim and dispatch due work.
  - **Expected Outcome:** `{"processedItems": 1}`.
- **Step 5: Verify Final Delivery State**
  - **Method:** `GET`
  - **URL:** `http://localhost:8085/api/reminders/1`
  - **Purpose:** Confirm database durability, state transition, and execution history.
  - **Expected Outcome:** `200 OK` with `"state": "DELIVERED"`, `"attemptCount": 1`, and attempt outcome `"SUCCESS"`.

##### Scenario 2: Edit Content & Reschedule Before Execution
- **What we are testing:** Proving that changing time or content increments the version number ($v1 \to v2$), generates a new delivery key, and cancels the old schedule.
- **Request:**
  - **Method:** `PUT`
  - **URL:** `http://localhost:8085/api/reminders/1`
  - **Body (JSON):**
    ```json
    {
      "content": "Drink water and stretch [RESCHEDULED]",
      "localDateTime": "2026-10-01T16:00:00",
      "zone": "Asia/Kolkata",
      "expectedVersion": 1
    }
    ```
- **Purpose:** Reschedule the reminder 20 minutes later under safe optimistic concurrency control.
- **Expected Outcome:** `200 OK` with `"version": 2`, updated `"deliveryKey": "1:2"`, and `"state": "SCHEDULED"`. The old scheduled time will no longer fire.

---

#### 2. Negative Scenarios

##### Negative Scenario 1: Nonexistent Local Time (Daylight Saving Gap)
- **What we are testing:** Proving the service enforces strict time zone rules and refuses to silently guess nonexistent times during the Spring Forward transition.
- **Request:**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/reminders`
  - **Body (JSON):**
    ```json
    {
      "content": "Test nonexistent time",
      "localDateTime": "2026-03-08T02:30:00",
      "zone": "America/New_York"
    }
    ```
- **Purpose:** On March 8, 2026, clocks in New York jump from 02:00 straight to 03:00. The local time 02:30 does not exist.
- **Expected Outcome:** `400 Bad Request` with error:
  `"The requested local time does not exist in zone America/New_York due to daylight saving gap"`.
  *Why:* Conversational systems must reject nonexistent times explicitly rather than delivering an hour off.

##### Negative Scenario 2: Stale Version Conflict (Concurrent Edit Protection)
- **What we are testing:** Protecting against lost updates and race conditions when two clients or operations edit the same reminder.
- **Request:**
  - **Method:** `PUT`
  - **URL:** `http://localhost:8085/api/reminders/1`
  - **Body (JSON):**
    ```json
    {
      "content": "Stale conflicting update",
      "localDateTime": "2026-10-01T16:30:00",
      "zone": "Asia/Kolkata",
      "expectedVersion": 1
    }
    ```
- **Purpose:** Attempt to update a reminder using stale version `1` when the reminder is already at version `2`.
- **Expected Outcome:** `409 Conflict` with error:
  `"Reminder 1 has version 2, but expected version 1"`.
  *Why:* Guarantees safe atomic updates without overwriting newer user changes.

##### Negative Scenario 3: Modifying or Cancelling Terminal Reminders
- **What we are testing:** Enforcing terminal state immutability. Once a reminder is `DELIVERED`, `CANCELLED`, or `FAILED`, it cannot be modified or re-cancelled.
- **Request:**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/reminders/1/cancel?expectedVersion=2`
- **Purpose:** Attempt to cancel a reminder that has already been delivered.
- **Expected Outcome:** `400 Bad Request` with error:
  `"Cannot cancel reminder in state: DELIVERED"`.
  *Why:* Completed records must remain permanent historical audit trails.

---

#### 3. Failure and Recovery Scenarios

##### Scenario A: Process Crash & Restart Recovery (AC2)
- **What we are testing:** Demonstrating that overdue reminders survive an abrupt server crash and execute promptly upon restart.
- **Step 1:** Create a reminder scheduled for `2026-10-01T10:10:00Z`.
- **Step 2:** Stop the Spring Boot application in IntelliJ (simulating a crash while the reminder is pending).
- **Step 3:** Start the application again.
- **Step 4:** Set the clock past the due time:
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/test/clock/set?isoInstant=2026-10-01T10:15:00Z`
- **Step 5:** Trigger the worker tick:
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/test/tick`
- **Expected Outcome:** Returns `{"processedItems": 1}`. The server scanned the database upon restart, identified the overdue item, claimed it, and delivered it.

##### Scenario B: Temporary Downstream Network Failure & Bounded Backoff (AC3)
- **What we are testing:** Simulating a transient 503 error on the notification destination, verifying bounded retries with delay, and demonstrating eventual delivery.
- **Step 1: Set Clock Past Due Time**
  - `POST http://localhost:8085/api/test/clock/set?isoInstant=2026-10-01T10:15:00Z`
- **Step 2: Tell Destination to Simulate 1 Temporary Failure**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/test/simulate-failure/temporary?count=1`
  - **Expected Outcome:** `{"simulatedTemporaryFailures": 1, "status": "configured"}`.
- **Step 3: Trigger First Delivery Attempt**
  - **Method:** `POST`
  - **URL:** `http://localhost:8085/api/test/tick`
- **Step 4: Inspect Reminder History**
  - **Method:** `GET`
  - **URL:** `http://localhost:8085/api/reminders/1`
  - **Expected Outcome:**
    - State is **`SCHEDULED`** (not dropped or prematurely failed).
    - `attemptCount: 1`.
    - Attempt #1 logs outcome `"TEMPORARY_FAILURE"` with reason `"Simulated global temporary failure"`.
    - Next attempt is scheduled 10 seconds in the future.
- **Step 5: Advance Past 10-Second Backoff & Retry**
  - Advance clock: `POST http://localhost:8085/api/test/clock/advance?seconds=15`
  - Trigger tick: `POST http://localhost:8085/api/test/tick`
- **Step 6: Confirm Eventual Success**
  - Inspect reminder: `GET http://localhost:8085/api/reminders/1`
  - **Expected Outcome:** State transitions to **`DELIVERED`** with `attemptCount: 2`. Attempt #1 shows `TEMPORARY_FAILURE` and Attempt #2 shows `SUCCESS`.

##### Scenario C: Duplicate Execution Defense (AC4)
- **What we are testing:** Proving that if a worker or scheduler fires twice for the same occurrence, the destination records only one logical notification.
- **Execution:** Trigger duplicate dispatch on an already delivered item.
- **Expected Outcome:** Caught and deduplicated by the database unique constraint on `notification(delivery_key)`. The destination's notification count remains strictly 1.

---

## Run the tests

Run the complete automated test suite using Maven:

```text
mvnw.cmd clean test
```

*(On Linux / macOS)*:
```text
./mvnw clean test
```

*Note: Tests run against an isolated in-memory H2 database (`jdbc:h2:mem:testdb`) configured in `src/test/resources/application.properties`, ensuring tests never lock or pollute the local file database.*

---

## Acceptance scenarios and verification

All acceptance scenarios (AC1–AC7) and the verification benchmark (AC8/AC9) are **100% complete and passing**:

1. **AC1: Scheduled delivery** (`ac1_scheduledDelivery_deliversWhenClockReachesInstant`):
   - Items remain in `SCHEDULED` until the injected `MutableClock` reaches the calculated instant.
   - Transitions to `DELIVERED` with execution attempt logged in `delivery_attempt` and destination notification stored.

2. **AC2: Restart recovery** (`ac2_restartRecovery_processesOverdueItemsAfterRestart`):
   - Items that become due while the application is offline are identified on startup via index query `(state = 'SCHEDULED' AND next_attempt_at <= now) OR (state = 'RUNNING' AND lease_until <= now)`.

3. **AC3: Temporary failure & retry** (`ac3_temporaryFailureAndRetryExhaustion`):
   - Simulates transient downstream errors. Worker logs attempt, computes backoff (`attempt 1: +10s`, `attempt 2: +30s`), and retries up to 3 bounded attempts.
   - If retries fail 3 times, item enters terminal `FAILED`.

4. **AC4: Duplicate execution / Idempotency** (`ac4_duplicateExecution_destinationObservesSingleLogicalNotification`):
   - Re-executing delivery on an already delivered item does not dispatch a second notification.
   - Database unique constraint on `notification(delivery_key)` prevents double delivery at the boundary.

5. **AC5: Edit before execution** (`ac5_editBeforeExecution_updatesVersionAndReschedules`):
   - Editing content/time increments `version` ($1 \to 2$) and updates `deliveryKey = id:version`.
   - Superseded schedules are invalidated; stale edits with mismatched `expectedVersion` are rejected with `StaleVersionException`.

6. **AC6: Cancellation** (`ac6_cancellationBeforeExecution_cancelsCleanlyWithoutDelivery`):
   - Active reminders are marked `CANCELLED`. Workers ignore cancelled reminders, ensuring zero notifications.

7. **AC7: Time-zone boundary & DST** (`ac7_timeZoneBoundaries_handlesMultipleZonesAndDstSafely`):
   - Tests both `Asia/Kolkata` (fixed +05:30) and `America/New_York` (DST transitions).
   - Rejects nonexistent times (Spring Forward 1-hour gap) and ambiguous times (Fall Back 1-hour overlap) deterministically with `IllegalArgumentException`.

---

### Problem-Specific Verification Benchmark

#### How to run:

**Option 1: Via Postman**
- Open Postman, navigate to folder: **3. Verification Benchmark**
- Select: **Run 20-Item Verification Benchmark**
- Click **Send** (`POST http://localhost:8085/api/test/benchmark/run`)

**Option 2: Via Automated Test**
```text
mvnw.cmd test -Dtest=ReminderWorkflowTests#benchmark_twentyItemsAcrossTwoZones_demonstratesZeroDuplicateDeliveriesAndFullSettlement
```

#### Observed Result:
```json
{
  "totalCreated": 20,
  "deliveredCount": 13,
  "cancelledCount": 3,
  "failedCount": 4,
  "totalNotificationsDelivered": 13,
  "distinctDeliveryKeysInNotifications": 13,
  "zeroDuplicateDeliveries": true,
  "allSettled": true,
  "details": {
    "normalDelivered": 6,
    "editedDelivered": 4,
    "tempFailureRecoveredDelivered": 3,
    "cancelled": 3,
    "permanentFailed": 2,
    "exhaustedFailed": 2,
    "duplicateExecutionSimulatedOnId": 1,
    "duplicateExecutionProducedDuplicateNotification": false
  }
}
```

#### Explanation of Every Variable in the Result:

- **`totalCreated: 20`**
  Total number of reminders created in the benchmark across two time zones (`Asia/Kolkata` and `America/New_York`).
- **`deliveredCount: 13`**
  Total reminders that reached the final `DELIVERED` state (6 standard + 4 edited/rescheduled + 3 recovered from temporary errors).
- **`cancelledCount: 3`**
  Total reminders that were cancelled by the user before their due time, reaching the final `CANCELLED` state.
- **`failedCount: 4`**
  Total reminders that reached the final `FAILED` state (2 non-retryable bad payloads + 2 where all 3 retry attempts were exhausted).
- **`totalNotificationsDelivered: 13`**
  The actual count of notifications received and stored by the destination. Notice it equals `deliveredCount` (13 == 13) with zero extras.
- **`distinctDeliveryKeysInNotifications: 13`**
  The number of unique delivery keys (`id:version`) stored in the destination. Confirms every notification is distinct and unique.
- **`zeroDuplicateDeliveries: true`**
  The core idempotency proof! Confirms that even with duplicate triggers and worker retries, **no duplicate notification was ever delivered**.
- **`allSettled: true`**
  Confirms every single one of the 20 items reached a terminal state (`DELIVERED`, `CANCELLED`, or `FAILED`) with 0 items stuck in `RUNNING` or `SCHEDULED`.
- **`details.normalDelivered: 6`**
  Count of straightforward reminders that delivered on time without user edits or errors.
- **`details.editedDelivered: 4`**
  Count of reminders where the user changed time/content before delivery; all 4 delivered at their updated time under version 2.
- **`details.tempFailureRecoveredDelivered: 3`**
  Count of reminders that initially experienced downstream network errors (503), underwent backoff retries, and successfully delivered.
- **`details.cancelled: 3`**
  Count of reminders where the user cancelled prior to delivery; zero notifications were sent.
- **`details.permanentFailed: 2`**
  Count of reminders with non-retryable permanent failures that terminated immediately to avoid wasting retry resources.
- **`details.exhaustedFailed: 2`**
  Count of reminders that failed 3 consecutive retry attempts and terminated in `FAILED`.
- **`details.duplicateExecutionSimulatedOnId: 1`**
  Identifies reminder #1 where the test intentionally triggered delivery a second time after it was already completed.
- **`details.duplicateExecutionProducedDuplicateNotification: false`**
  Confirms the duplicate delivery attack was rejected by the database unique constraint and ignored by the notifier. No second notification was produced.


---

## Architecture and data flow

```
  [REST API / Client] 
           │
           ▼
   [ReminderService] ──(Pessimistic / Optimistic Lock)──► [Database (H2/SQL)]
           ▲                                               ├── scheduled_item
           │                                               ├── delivery_attempt
           │                                               └── notification (UNIQUE: delivery_key)
  [SchedulerService] ◄── [MutableClock (Injected)]
           │
           ▼ (Atomic Row Lease Claim: state = RUNNING, leaseUntil = now + 30s)
    [ClaimService]
           │
           ▼
   [DeliveryService] ──(Idempotency check via deliveryKey)──► [FakeNotifier]
```

### Core Data Flow:
1. **Creation**: `POST /api/reminders` $\to$ `TimeResolver` converts `(localDateTime, zone)` to UTC `Instant` using `ZoneRules`. Persists `ScheduledItem` in state `SCHEDULED` with `version = 1`, `deliveryKey = id:1`.
2. **Discovery & Lease Claim**: Background scheduler (or `/api/test/tick`) runs `ClaimService.claimDueItems()`. Uses `SELECT ... FOR UPDATE SKIP LOCKED` semantics, transitions row to `RUNNING` with `leaseUntil = now + 30s`.
3. **Dispatch & Idempotency**: `DeliveryService` invokes `Notifier.send(deliveryKey, content)`. The destination checks `deliveryKey` and inserts into the `notification` table guarded by a database unique constraint.
4. **Settlement**: Upon success, state becomes `DELIVERED`. Upon temporary error, backoff delay is scheduled (`state = SCHEDULED`). Upon permanent error or retry limit (3), state becomes `FAILED`.

---

## Technology choices

- **Language & Runtime**: Java 17 + Spring Boot 4.1 / Spring Framework 7.
  - *Why*: Strong typing, mature concurrency primitives, and built-in transaction management (`@Transactional`).
- **Persistence**: Spring Data JPA / Hibernate ORM with H2 (File-mode for dev durability, In-memory for fast unit test isolation).
  - *Alternatives considered*: PostgreSQL (ideal for production, but H2 ensures zero external dependencies for local reviewer execution).
- **Time Handling**: `java.time.Clock` abstraction + `ZoneRules`.
  - *Why*: Java 8+ `java.time` is standard, robust, and correctly implements the complete IANA tz database.
- **Idempotency Strategy**: Application-level delivery key checks combined with relational Unique Constraints (`UNIQUE(delivery_key)`).
  - *Trade-off accepted*: A relational table constraint requires DB write overhead, but provides unbreakable consistency against concurrent duplicate deliveries.

---

## Important decisions

1. **Unique Scheduled Occurrence as `id:version`**:
   Rather than treating a reminder as a static mutable record, each version represents a distinct logical occurrence. Rescheduling increments `version` ($1 \to 2$), which generates a new delivery key `id:2`. This ensures that any in-flight execution for version 1 is automatically recognized as stale and cannot satisfy or collide with version 2.

2. **Strict Rejection Policy for DST Gaps and Overlaps**:
   When converting local times during Daylight Saving boundaries:
   - **Nonexistent times** (Spring Forward 1-hour gap): Rejects immediately with clear error rather than silently rounding forward.
   - **Ambiguous times** (Fall Back 1-hour overlap): Rejects immediately rather than guessing between Standard and Daylight time offsets.
   This prevents conversational assistants from making silent, incorrect promises.

3. **Short Leases + Row Locking for Worker Coordination**:
   To prevent duplicate workers from claiming the same row, items are claimed under row locks with an explicit `leaseUntil = now + 30s`. If a node crashes mid-execution, the lease expires and any healthy worker recovers the item on the next tick.

---

## Assumptions and limitations

### Assumptions:
- **Client supplies `expectedVersion`**: For updates and cancellations, optimistic concurrency requires the caller to specify the expected version.
- **Injected Clock**: Development, tests, and benchmark run via `MutableClock` for deterministic evaluation.

### Limitations:
- Single-node scheduler polling (distributed cluster leader election or partitioned queues like Kafka/RabbitMQ would be used in multi-datacenter production).
- Local notifications stored in database table instead of actual SMS/Push networks.

---

## Production and scale

### What the Prototype Does Now:
- Durable H2 storage with indexed polling on `(state, next_attempt_at)`.
- Pessimistic row locking (`FOR UPDATE`) to prevent race conditions.
- Bounded retries (max 3) with exponential backoff.

### Production Improvements at Scale:
1. **Partitioned Polling / Sharding**: Replace table polling with partitioned database buckets or Redis sorted sets (`ZADD/ZRANGEBYSCORE`) to handle millions of items without database query bottlenecks.
2. **Distributed Lock / Queue Handoff**: Once claimed, push reminder payloads into a durable message broker (e.g. Apache Kafka or AWS SQS) with dead-letter queues.
3. **Database Engine**: Transition from H2 to PostgreSQL or AWS Aurora with read-replicas.
4. **Multi-Tenancy & Auth**: Add tenant isolation (`tenant_id`), OAuth2 JWT authentication, and rate limiting per user.

---

## AI usage

- **AI Tools Used**: Antigravity AI assistant.
- **Contribution**:
  - Assisted in scaffolding boilerplate entity models, DTOs, and exception handlers.
  - Formulated edge-case test fixtures for IANA DST boundaries (Spring Forward / Fall Back dates).
  - Assisted in generating the Postman collection JSON structure.
- **Review and Validation**:
  - All generated code, database constraints, state transition logic, and unit tests were reviewed, compiled with Java 17, and executed through Maven test runs to guarantee correctness.

---

## Credibility note

- **Problem Solved**: Built and shipped a high-reliability distributed event and webhook dispatching engine ensuring at-least-once delivery with end-to-end deduplication for critical financial workflows.
- **Personal Contribution**: Designed the delivery state machine, idempotency boundary with hash-chained delivery keys, and bounded retry policy with jittered backoff.
- **Scale / Complexity**: Handled over 5 million daily transactional callbacks with strict sub-second latency SLAs and zero duplicate ledger events across worker restarts.
- **Difficult Decision**: Chose relational database unique constraints over distributed cache locks (Redis) for final delivery commits, sacrificing a few milliseconds of throughput to achieve absolute financial idempotency.
