# ADR-032: Staff Only, and the Priority Is the Analyst's Choice

## Status
Accepted

## Context
Incidents and service requests serve two audiences, so they narrow a `REQUESTER` to their own items (incident ADR-031, service-request ADR-031). A problem is root-cause work: internal, speculative while it is being investigated, and of no use to a requester until it is a known error with a workaround, which is the job of the knowledge base.

## Decision
- **Every route refuses a `REQUESTER`** with 403 `ERR-PRB-00403` (`X-Role`, set by the kit's `SecurityFilter` from the verified token when security is on). There is no self-service endpoint and no internal/public distinction in comments: all of it is staff-only.
- `X-Tenant-Id` is mandatory on every route and scopes every lookup; another tenant's problem is a 404.
- **The priority P1..P4 is chosen by the analyst and can be changed while the problem is open.** An incident derives its priority from impact and urgency, but a problem has no urgency of its own to derive from: the pressure comes from how many incidents it explains and how much they hurt, a judgement that is the analyst's. There is **no SLA**: investigation time is not something a target meaningfully measures, and a second configuration to maintain would not earn its keep.

## Consequences
- Positive: nothing speculative can leak to a requester; no derivation rule to explain; no SLA configuration.
- Negative: priorities are only as consistent as the analysts; no breach alert for a problem that sits untouched (a later alerting journey can read the audit trail).
