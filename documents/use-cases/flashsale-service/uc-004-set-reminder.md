# UC-FLASHSALE-004: Customer Set Reminder

**Stable ID:** `UC-FLASHSALE-004`
**Actor:** Customer (BUYER)
**Priority:** MEDIUM
**Auth:** JWT (BUYER)

---

## Brief
A customer sets a reminder for an upcoming flash sale session. The system enforces one reminder per customer per session. When the session starts, the Notification Service sends an SSE push notification.

---

## Preconditions

| # | Condition |
|---|-----------|
| P1 | Actor is authenticated as BUYER |
| P2 | Session exists |
| P3 | Customer has not already set a reminder for this session (BR-FLASHSALE-006) |

---

## Main Flow

| Step | Actor | Action |
|------|-------|--------|
| 1 | Customer | Sends `POST /flash-sales/{id}/remind` (no request body) |
| 2 | System | Extracts `customer_id` from JWT |
| 3 | System | Validates session exists |
| 4 | System | Checks no existing reminder: `SELECT 1 FROM fs_reminders WHERE customer_id = :cid AND session_id = :sid` |
| 5 | System | Inserts row into `fs_reminders` |
| 6 | System | Returns `201 Created` with reminder ID |

---

## Alternate Flows

| # | Trigger | Action |
|---|---------|--------|
| A1 | Session not found | Return `404 SESSION_NOT_FOUND` |
| A2 | Reminder already exists | Return `409 REMINDER_ALREADY_SET` |

---

## Postconditions

| # | Condition |
|---|-----------|
| PC1 | `fs_reminders` row exists with `customer_id` and `session_id` |
| PC2 | Notification Service will push reminder at `start_time` |

---

## Cross-References

| Reference | Description |
|-----------|-------------|
| FR-FLASHSALE-011 | Customer set reminder |
| BR-FLASHSALE-006 | One reminder per customer per session |
| ENTITY-FLASHSALE-003 | FS_REMINDERS table |

---

## Related Use Cases

| UC | Relationship |
|----|-------------|
| UC-FLASHSALE-003 | Customer views sessions to find ones to follow |
| UC-FLASHSALE-006 | Reminder triggers when session starts |

---

*Generated: 2026-05-09*
