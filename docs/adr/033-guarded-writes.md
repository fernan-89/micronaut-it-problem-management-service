# ADR-033: Every Change Is a Guarded Write

## Status
Accepted

## Context
Two analysts working the same problem could silently overwrite each other (one records a root cause while the other cancels the problem).

## Decision
- The repository saves the state the aggregate reached together with its audit entry in **one atomic update, and only while the problem still has the status it had when it was loaded**. If someone else moved it in between, the write matches nothing and the caller gets 409 `ERR-PRB-00409` (read again and retry), never a lost update. Comments are an atomic `$push` and need no guard.
- Four compound indexes serve the real queries and are created at startup (fail-open, idempotent, `thinklab.mongo.create-indexes=false` turns them off): the work queue `(organisationId, status, priority)`, `(organisationId, assigneeId)`, and the multikey `(organisationId, relatedIncidentIds)` and `(organisationId, affectedAssetIds)`.

## Consequences
- Positive: no lost updates, no partial writes.
- Negative: the guard is on the status, so two edits that do not change it (two analyses recorded at the same instant, two assignments) are last-writer-wins, which is acceptable for fields meant to be overwritten.
