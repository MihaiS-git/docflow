## Session Security & Authentication Audit — Implemented Design

### Session lifetime, inactivity timeout, and concurrent session limits

Session security is enforced **jointly** by the identity provider (**Keycloak**) and the application (**Spring Boot**), with **clear separation of responsibilities**.

**Identity Provider (Keycloak)**

* SSO Session Idle Timeout (realm-level)
* SSO Session Max Lifetime (realm-level)
* Login timeout and action token lifetimes
* Central SSO behavior across browser sessions

**Application (Spring Boot)**

* Absolute session lifetime (hard cap, server-side)
* Concurrent session limit per user
* Session fixation protection
* Immediate invalidation and rejection of expired sessions

This dual-layer model ensures:

* Centralized SSO governance (IdP)
* Deterministic, auditable enforcement at the application boundary

---

## Request Boundary Enforcement

The application enforces **eligibility and authorization at the request boundary**, independently of the identity provider.

This includes:

* User lifecycle checks (ACTIVE / LOCKED / DISABLED)
* Tenant lifecycle checks (ACTIVE / SUSPENDED)
* Session validity (absolute timeout)
* Concurrent session enforcement
* Role-based authorization

No request reaches controllers unless **all boundary conditions are satisfied**.

---

## Authentication Audit Events — Design & Semantics

This system records authentication activity from **two distinct sources**, each representing a different security phase.

These sources are **intentionally asymmetric**.

### Event Sources Overview

```
-----------------------------------------------------------------------------------------------------------------------------------------
Source                  Trigger                                         Timing                  Context
-----------------------------------------------------------------------------------------------------------------------------------------
Spring Security          AuthenticationSuccessEvent, LogoutSuccessEvent  Synchronous             Full HTTP request context
Keycloak Admin API       LOGIN_ERROR, CLIENT_LOGIN_ERROR, etc.            Asynchronous (polling)  Partial, security-grade metadata
-----------------------------------------------------------------------------------------------------------------------------------------
```

---

## SUCCESS / LOGOUT Events (Spring Security)

### Origin

* Emitted by Spring Security after successful authentication or logout
* Request is fully authenticated
* Servlet request context is available

### Available data

* Subject ID (Keycloak user UUID)
* User agent
* Client IP
* Correlation ID (`X-Request-Id`)
* Authentication provider

### Example record

```
result        = SUCCESS
username      = 03bdf63a-60ee-4f91-afac-0a8457539161
user_agent    = Mozilla/5.0 ...
correlationId = b829dcba-a011-45b6-be32-19212fb2a545
```

### Identity projection

* Triggered **only on SUCCESS**
* Uses `subjectId` to fetch user details from Keycloak
* Stored locally and refreshed periodically

This path is **rich, deterministic, and request-scoped**.

---

## FAILURE Events (Keycloak Admin Pull)

### Origin

* Pulled from Keycloak Admin Events API
* Represents authentication failures **inside Keycloak**
* Occurs **before authentication succeeds**

### Key difference

In many failure scenarios, **Keycloak does not know the user yet**.

### Examples

* Wrong password
* Invalid username
* Disabled account
* Brute-force protection
* Client misconfiguration

In these cases:

* `userId` is null
* No authenticated principal exists
* No servlet request exists in Spring

---

### Consequences (Expected)

```
----------------------------------------------------------------------------------------
Field              Value                         Reason
----------------------------------------------------------------------------------------
username            UNKNOWN                      User not resolved at failure time
userAgent           N/A                          Not always provided in admin events
correlationId       sessionId or generated UUID  No request correlation possible
----------------------------------------------------------------------------------------
```

### Example record

```
result     = FAILURE
username   = UNKNOWN
userAgent  = N/A
ip         = 172.18.0.1
```

This is **correct audit behavior**, not data loss.

---

## Why FAILURE ≠ SUCCESS (By Design)

Authentication failures are **pre-identity events**.

At failure time:

* No subject exists
* No user identity can be trusted
* Only network-level and protocol-level metadata is reliable

For audit and compliance purposes, this is **preferable to guessing or retroactively assigning identities**.

---

## Correlation IDs and Sessions

* **SUCCESS / LOGOUT** events use `X-Request-Id` from the HTTP request
* **FAILURE** events use:

  * Keycloak `sessionId` when available
  * Generated identifier otherwise

This allows:

* Correlation of repeated failures
* Brute-force detection
* Cross-event linking without identity assumptions

---

## Identity Projection Behavior

The identity projection (`user_identity_projection`) is:

* Triggered **only on SUCCESS**
* Never triggered on FAILURE
* Cached and periodically refreshed
* Safe against deleted or missing users

This prevents:

* Polluting identity data with failed attempts
* Creating projections for non-existent users

---

## Session Security Enforcement (Application)

The application enforces the following **independently of Keycloak**:

* **Absolute session timeout**
  Sessions are invalidated server-side once the maximum lifetime is reached, regardless of activity.

* **Concurrent session limit**
  A configurable maximum number of active sessions per user is enforced.

* **Session fixation protection**
  Session ID is rotated on authentication.

* **Hard stop on expiration**
  Expired sessions are invalidated immediately and rejected with `401 Unauthorized`.

This ensures deterministic behavior even when SSO sessions remain valid at the IdP.

---

## Summary (Important)

* `UNKNOWN` username on FAILURE is expected and correct
* FAILURE events are intentionally sparse
* SUCCESS events are intentionally rich
* Both represent different security phases
* No data is missing or miswired
* Session enforcement exists at both IdP and application levels

This asymmetry is a **security feature**, not a bug.

---

## Auditor / Reviewer Note

This audit and session model aligns with:

* Zero-trust principles
* Pre-auth vs post-auth separation
* ISO 27001 access control and logging requirements
* NIS2 authentication and session management expectations
* OWASP authentication logging guidance
