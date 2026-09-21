# Postman Testing Guide & API Reference

You can import `postman_collection.json` directly into Postman (**File -> Import -> postman_collection.json**), or use the URLs and request bodies documented below.

Base URL: `http://localhost:8085` (as configured in `server.port=8085`)

---

## 1. Quick Verification: Run 20-Item Benchmark

Run the entire benchmark with one call to verify all acceptance scenarios:

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/test/benchmark/run`
- **Body**: None
- **Expected Response (200 OK)**:
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

---

## 2. Core Reminder Endpoints

### 1. Create Reminder (Asia/Kolkata)
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/reminders`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "content": "Drink water and stretch",
  "localDateTime": "2026-10-01T15:30:00",
  "zone": "Asia/Kolkata"
}
```
- **Expected Response (201 Created)**:
```json
{
  "id": 1,
  "content": "Drink water and stretch",
  "localDateTime": "2026-10-01T15:30:00",
  "zone": "Asia/Kolkata",
  "scheduledInstant": "2026-10-01T10:00:00Z",
  "state": "SCHEDULED",
  "version": 1,
  "nextAttemptAt": "2026-10-01T10:00:00Z",
  "attemptCount": 0,
  "deliveryKey": "1:1",
  "attempts": []
}
```

---

### 2. Create Reminder (America/New_York)
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/reminders`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "content": "Team catch-up sync",
  "localDateTime": "2026-10-01T06:00:00",
  "zone": "America/New_York"
}
```
- **Expected Response (201 Created)**:
```json
{
  "id": 2,
  "content": "Team catch-up sync",
  "localDateTime": "2026-10-01T06:00:00",
  "zone": "America/New_York",
  "scheduledInstant": "2026-10-01T10:00:00Z",
  "state": "SCHEDULED",
  "version": 1,
  "nextAttemptAt": "2026-10-01T10:00:00Z",
  "attemptCount": 0,
  "deliveryKey": "2:1",
  "attempts": []
}
```

---

### 3. Get Reminder by ID (Inspecting State & Attempts)
- **Method**: `GET`
- **URL**: `http://localhost:8080/api/reminders/1`
- **Expected Response (200 OK)**:
```json
{
  "id": 1,
  "content": "Drink water and stretch",
  "localDateTime": "2026-10-01T15:30:00",
  "zone": "Asia/Kolkata",
  "scheduledInstant": "2026-10-01T10:00:00Z",
  "state": "SCHEDULED",
  "version": 1,
  "nextAttemptAt": "2026-10-01T10:00:00Z",
  "attemptCount": 0,
  "deliveryKey": "1:1",
  "attempts": []
}
```

---

### 4. Get All Reminders (With Optional State Filter)
- **Method**: `GET`
- **URLs**:
  - `http://localhost:8080/api/reminders`
  - `http://localhost:8080/api/reminders?state=SCHEDULED`
  - `http://localhost:8080/api/reminders?state=DELIVERED`
  - `http://localhost:8080/api/reminders?state=CANCELLED`
  - `http://localhost:8080/api/reminders?state=FAILED`

---

### 5. Update Reminder (Edit Content & Reschedule)
- **Method**: `PUT`
- **URL**: `http://localhost:8080/api/reminders/1`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "content": "Drink water and stretch [UPDATED TIME]",
  "localDateTime": "2026-10-01T16:00:00",
  "zone": "Asia/Kolkata",
  "expectedVersion": 1
}
```
- **Expected Response (200 OK)**:
```json
{
  "id": 1,
  "content": "Drink water and stretch [UPDATED TIME]",
  "localDateTime": "2026-10-01T16:00:00",
  "zone": "Asia/Kolkata",
  "scheduledInstant": "2026-10-01T10:30:00Z",
  "state": "SCHEDULED",
  "version": 2,
  "nextAttemptAt": "2026-10-01T10:30:00Z",
  "attemptCount": 0,
  "deliveryKey": "1:2",
  "attempts": []
}
```
*(Notice version is now `2` and deliveryKey is `1:2`, superseding the previous execution!)*

---

### 6. Cancel Reminder
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/reminders/1/cancel?expectedVersion=2`
- **Expected Response (200 OK)**:
```json
{
  "id": 1,
  "content": "Drink water and stretch [UPDATED TIME]",
  "localDateTime": "2026-10-01T16:00:00",
  "zone": "Asia/Kolkata",
  "scheduledInstant": "2026-10-01T10:30:00Z",
  "state": "CANCELLED",
  "version": 2,
  "nextAttemptAt": null,
  "attemptCount": 0,
  "deliveryKey": "1:2",
  "attempts": []
}
```

---

## 3. Clock & Scheduler Control (Deterministic Demo Flow)

### 1. View Current Controlled Clock Instant
- **Method**: `GET`
- **URL**: `http://localhost:8080/api/test/clock`
- **Response**:
```json
{
  "instant": "2026-10-01T10:00:00Z"
}
```

### 2. Set Clock to Specific Time
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/test/clock/set?isoInstant=2026-10-01T10:00:00Z`

### 3. Advance Clock by Minutes / Seconds
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/test/clock/advance?minutes=15`
*(or `?seconds=30`)*

### 4. Trigger Scheduler Tick (Process Due Items)
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/test/tick`
- **Response**:
```json
{
  "processedItems": 1,
  "currentClock": "2026-10-01T10:15:00Z"
}
```

---

## 4. Suggested Demo Video Flow

1. **Step 1: Create a Reminder**
   - Call `POST /api/reminders` with a future time (e.g. 15 minutes ahead).
   - Show response with state `SCHEDULED`, `version = 1`, and `deliveryKey = 1:1`.
2. **Step 2: Advance Clock and Deliver**
   - Call `POST /api/test/clock/advance?minutes=15`.
   - Call `POST /api/test/tick`.
   - Call `GET /api/reminders/1` -> state is now `DELIVERED`, and `attempts` shows 1 successful attempt.
3. **Step 3: Edit and Versioning**
   - Create a reminder. Call `PUT /api/reminders/{id}` with `expectedVersion: 1`.
   - Show `version` increments to `2` and `deliveryKey` changes to `{id}:2`.
4. **Step 4: Cancellation**
   - Call `POST /api/reminders/{id}/cancel?expectedVersion=2`.
   - Show state transitions to `CANCELLED`.
5. **Step 5: Verification Benchmark**
   - Call `POST /api/test/benchmark/run`.
   - Show the returned summary: 20 items created, 13 delivered, 3 cancelled, 4 failed, and `zeroDuplicateDeliveries: true`.
