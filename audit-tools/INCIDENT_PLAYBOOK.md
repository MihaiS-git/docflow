# Incident Investigation Playbook — Suspicious Admin Activity

## Purpose

This playbook defines a repeatable process for investigating suspicious administrative behavior using database audit evidence.

The goal is to:

* identify the actor
* reconstruct the event timeline
* detect escalation attempts
* preserve reproducible evidence

Do **not** modify database data during investigation.

---

## Scenario

An administrator action appears suspicious or unauthorized.

Examples:

* unexpected role assignment
* user status change
* abnormal admin activity spike

---

## Step 1 — Identify admin activity

Run:

```bash
psql -f audit-tools/per_user/incident_user_admin.sql
```

Review:

* actor_user_id
* action_type
* timestamp
* request_id

Record all suspicious entries.

---

## Step 2 — Correlate authentication activity

Run:

```bash
psql -f audit-tools/per_user/incident_user_auth.sql
```

Check for:

* unfamiliar IP addresses
* unusual user agents
* repeated failures
* unexpected login timing

Record anomalies.

---

## Step 3 — Check denied operations

Run:

```bash
psql -f audit-tools/per_user/incident_user_denied.sql
```

Look for:

* escalation attempts
* forbidden operations
* repeated blocked requests

Record findings.

---

## Step 4 — Reconstruct full timeline

Run:

```bash
psql -f audit-tools/timeline/incident_timeline.sql
```

Sort by timestamp and build a sequence:

```
authentication → admin action → denied operation
```

This establishes the forensic narrative.

---

## Step 5 — Export evidence

Run:

```bash
./audit-tools/export_helpers/export.sh
```

Store exported CSV files securely.

Preserve:

* timestamps
* actor IDs
* request IDs

Do not alter evidence.

---

## Investigation Outcome

At completion, you should have:

* actor identity trace
* correlated login activity
* escalation evidence
* chronological timeline
* exportable audit artifacts

Store evidence according to retention policy.

---

## Important Rules

* Never modify audit tables
* Do not run destructive SQL
* Preserve raw evidence
* Document all observations

---

## Notes

This playbook provides a consistent, reproducible investigation process aligned with audit and compliance expectations.

All steps must be followed in order to maintain evidentiary integrity.
