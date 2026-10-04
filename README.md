# micronaut-it-problem-management-service

BIAN-aligned Service Domain **it-problem-management** (Control Record: `Problem`), port `8100`.

The unknown cause behind one or more incidents, worked by IT staff until it is understood and fixed for good. The third service of the
ITSM core (Journey 12), after incidents and service requests. A problem has a title, a description, a **priority** P1..P4 chosen by the
analyst, links to the incidents it explains, the changes that fix it and the assets involved, an assignee, a **root cause**, a
**workaround**, the permanent **resolution**, staff comments and a status that follows an ITIL-style lifecycle.

## What it guarantees, and what it does not

- **The known error is a state** (ADR-030): `KNOWN_ERROR` needs both a root cause and a workaround on record, and it is what a knowledge
  base will serve to people who meet the incident again. A problem is never resolved without a root cause and the resolution, and it is
  reopened, with a reason, when the fix did not hold.
- **Links are references, held by the problem** (ADR-031): incident, change and asset ids are stored, not validated, so opening a problem
  never fails because another service is down. The incident service is not changed; ask this one which problems explain an incident with
  `GET /retrieve?incidentId=`.
- **Staff only** (ADR-032): every route refuses a `REQUESTER` with 403 `ERR-PRB-00403`. The priority is the analyst's choice and there
  is no SLA.
- **No lost updates** (ADR-033): every change is one guarded write that also appends its audit entry; two analysts moving the same
  problem cannot both win (the loser gets 409 and retries).
- **Not here yet:** a knowledge base that serves the known errors (the next slice), notifications to the incidents when their problem
  is resolved, root cause analysis methods, SLA or breach alerts, events, and external ticketing connectors.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call and scopes it (another tenant's problem answers 404); `X-Executor` is mandatory on the actions;
`X-Role` is optional and, with platform security on, comes from the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-problem-management/v1/initiate` `{"title":"Switch drops packets","description":"Intermittent, floor 3","priority":"P2","relatedIncidentIds":["<uuid>"],"relatedChangeIds":[],"affectedAssetIds":["<uuid>"]}` |
| retrieve | `GET /it-problem-management/v1/{id}/retrieve` |
| retrieve (collection) | `GET /it-problem-management/v1/retrieve?status=&priority=&assigneeId=&incidentId=&assetId=&openOnly=` |
| update | `PUT /it-problem-management/v1/{id}/update` (title, description, priority and the three sets of links; while open) |
| assignment/update | `PUT /it-problem-management/v1/{id}/assignment/update` `{"assigneeId":"<uuid>"}` |
| analysis/update | `PUT /it-problem-management/v1/{id}/analysis/update` `{"rootCause":"Bad firmware","workaround":"Reboot weekly"}` (either one; while investigating or a known error) |
| control/investigate | `PUT /it-problem-management/v1/{id}/control/investigate` (NEW -> UNDER_INVESTIGATION) |
| control/known-error | `PUT /it-problem-management/v1/{id}/control/known-error` (UNDER_INVESTIGATION -> KNOWN_ERROR; needs a root cause and a workaround) |
| control/resolve | `PUT /it-problem-management/v1/{id}/control/resolve` `{"resolution":"Upgraded to 2.2"}` (UNDER_INVESTIGATION or KNOWN_ERROR -> RESOLVED; needs the root cause) |
| control/close | `PUT /it-problem-management/v1/{id}/control/close` (RESOLVED -> CLOSED, terminal) |
| control/reopen | `PUT /it-problem-management/v1/{id}/control/reopen` `{"reason":"Still dropping packets"}` (RESOLVED -> UNDER_INVESTIGATION) |
| control/cancel | `PUT /it-problem-management/v1/{id}/control/cancel` (before it is resolved; terminal) |
| comment/initiate | `POST /it-problem-management/v1/{id}/comment/initiate` `{"text":"..."}` |
| audit-log/retrieve | `GET /it-problem-management/v1/{id}/audit-log/retrieve` |

```text
NEW -> UNDER_INVESTIGATION -> KNOWN_ERROR -+
              |                            +-> RESOLVED -> CLOSED (terminal)       RESOLVED --reopen--> UNDER_INVESTIGATION
              +----------------------------+
NEW | UNDER_INVESTIGATION | KNOWN_ERROR --cancel--> CANCELLED (terminal)
```

```bash
curl "http://localhost:8100/it-problem-management/v1/retrieve?incidentId=<incidentId>" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: <userId>"
# [{"title":"Switch drops packets","priority":"P2","status":"KNOWN_ERROR","rootCause":"Bad firmware","workaround":"Reboot weekly",...}]
```

Do not put personal data in a title, a description, an analysis or a comment: they are stored with the problem.

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-PRB-00403` | 403 | A REQUESTER used the problem service (ADR-032) |
| `ERR-PRB-00404` | 404 | Problem not found (another tenant's answers the same) |
| `ERR-PRB-00409` | 409 | Illegal transition, a change on a closed or cancelled problem, or the problem changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure (blank title, a known error without root cause and workaround, a resolution or reason missing...) |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 the problem lifecycle and the known error · 031 links are references · 032 staff only, the priority is the analyst's choice ·
033 guarded writes.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
