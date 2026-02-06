# Audit Tools README

This directory contains investigation and audit tooling used to inspect evidence, reconstruct incidents, and export reproducible artifacts. All scripts are designed to be **read-only**, safe to execute during audits, and focused on operational incident response.

The structure mirrors the folder layout for easy navigation.

---

## activity/

General audit evidence inspection — answers:

> *“What happened in the system?”*

* **admin_actions.sql**
  Displays privileged or administrative operations.

* **auth_activity.sql**
  Shows login/logout and authentication evidence.

* **denied_operations.sql**
  Lists blocked or forbidden security actions.

* **onboarding_activity.sql**
  Tracks invitation and onboarding audit events.

---

## anomaly/

Detection queries — answers:

> *“Does anything look suspicious?”*

* **anomaly_admin_spike.sql**
  Detects unusual bursts of administrative activity.

* **anomaly_denied_spike.sql**
  Highlights spikes in blocked or forbidden actions.

* **anomaly_failed_logins.sql**
  Identifies repeated authentication failures.

---

## export/

Evidence extraction templates — answers:

> *“How do we export audit artifacts?”*

* **export_auth_csv.sql**
  CSV export template for authentication evidence.

---

## export_helpers/

Operational helpers for evidence handling.

* **export.sh**
  Script to automate repeatable audit exports.

---

## per_user/

Targeted incident investigation — answers:

> *“What did this specific actor do?”*

* **incident_user_admin.sql**
  Per-user administrative activity trace.

* **incident_user_auth.sql**
  Per-user authentication history.

* **incident_user_denied.sql**
  Per-user blocked or denied actions.

---

## snapshots/

Current system state verification — answers:

> *“What is the current integrity state?”*

* **tenant_snapshot.sql**
  Tenant configuration and integrity snapshot.

* **user_snapshot.sql**
  User account and status snapshot.

---

## timeline/

Forensic reconstruction — answers:

> *“What happened, and in what order?”*

* **incident_timeline.sql**
  Chronological audit timeline for incident analysis.

---

## INCIDENT_PLAYBOOK.md

Step-by-step investigation workflow describing how to use these tools during an incident.

---

## Usage principles

* Scripts are **read-only** — no data modification.
* Evidence exports must remain unaltered.
* Always record timestamps and context when investigating.
* Maintain reproducibility during audits.

---

## Purpose

This toolkit enables:

* audit inspection
* incident investigation
* anomaly detection
* forensic reconstruction
* evidence export

It provides practical, engineering-focused audit capability without unnecessary compliance overhead.
