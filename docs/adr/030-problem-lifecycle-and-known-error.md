# ADR-030: The Problem Lifecycle and the Known Error

## Status
Accepted

## Context
An incident is an interruption; a problem is the unknown cause behind one or several of them. Treating a problem as a long-lived incident loses what ITIL separates on purpose: the interruption is restored (perhaps with a workaround) long before the cause is fixed, and the people who hit the incident again need to be told what to do meanwhile.

## Decision
- Status `NEW -> UNDER_INVESTIGATION -> KNOWN_ERROR -> RESOLVED -> CLOSED`, plus `reopen` (RESOLVED back to UNDER_INVESTIGATION) and `cancel` (before it is resolved). No `DELETE`.
- **`KNOWN_ERROR` is a real state, not a label.** Declaring it needs both a **root cause** and a **workaround** already on record (`analysis/update`); otherwise the call is refused with 400. It means: the cause is understood, there is a way around it, and no permanent fix exists yet. It is what a knowledge base will serve to people who meet the incident again (the next slice of Journey 12).
- A problem can also be resolved directly from `UNDER_INVESTIGATION`. **Resolving always needs a root cause and the resolution** (the permanent fix); a problem never closes without knowing why it happened.
- A problem is **reopened with a reason** when the fix did not hold: the status returns to UNDER_INVESTIGATION, the earlier resolution is cleared (it was wrong) and a counter goes up; the audit trail keeps the old resolution text in its entries.
- Analysis can only be recorded while the problem is being investigated or is a known error, a field left out keeps its value and a given field must say something.

## Consequences
- Positive: the known-error state is explicit and enforceable; no problem is closed without a cause and a fix.
- Negative: there is no "root cause analysis method" structure (5 whys, fishbone): the analysis is two free-text fields. A problem cannot jump back from KNOWN_ERROR to NEW, and cannot be resolved without ever being investigated.
