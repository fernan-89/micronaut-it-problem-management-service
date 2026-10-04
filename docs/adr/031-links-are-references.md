# ADR-031: Links Are References, Held by the Problem

## Status
Accepted

## Context
A problem explains incidents, is fixed by changes and involves assets. Validating each id against the services that own them would make opening a problem depend on three other services being up, and a problem is exactly what gets opened while things are broken.

## Decision
- The problem stores `relatedIncidentIds`, `relatedChangeIds` and `affectedAssetIds` as ids. This service calls no other service (only the hash registry for sovereign ids). An id that does not exist is not refused.
- The reference lives **only on the problem**: the incident service is not changed, so an incident does not know which problem explains it. The question is answered from this side: `GET /retrieve?incidentId=` lists the problems that explain an incident, and `?assetId=` the ones involving an asset, both served by multikey indexes. The web app resolves the ids when it shows a problem.
- Each set takes at most 200 ids (400 above), and an update replaces the three sets (a link left out is removed).

## Consequences
- Positive: opening a problem never fails because another service is down; the incident service stays untouched.
- Negative: a mistyped id is accepted; a screen that shows an incident must ask this service for its problems (one extra call) instead of reading a field; there is no event telling an incident its problem was resolved (notification is a later journey).
