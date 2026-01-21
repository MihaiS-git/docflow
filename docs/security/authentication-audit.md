*.Authentication Audit Events — Design & Semantics

This system records authentication activity from two different sources:
    1. Spring Security runtime events (SUCCESS / LOGOUT)
    2. Keycloak Admin events (FAILURE and selected security failures)
These sources have fundamentally different levels of context, which explains why audit records do not look symmetric.
This is intentional and correct.

Event Sources Overview
-----------------------------------------------------------------------------------------------------------------------------------------
Source	                Trigger	                                            Timing	                    Context
-----------------------------------------------------------------------------------------------------------------------------------------
Spring Security	        AuthenticationSuccessEvent, LogoutSuccessEvent	    Synchronous                 Full HTTP request context
Keycloak Admin API	    LOGIN_ERROR, CLIENT_LOGIN_ERROR, etc.	            Asynchronous (polling)	    Partial, security-grade metadata
-----------------------------------------------------------------------------------------------------------------------------------------


*.SUCCESS / LOGOUT Events (Spring Security)

Origin
    Emitted by Spring Security after successful authentication
    Request is fully authenticated
    Servlet request is still available

Available data
    Subject ID (Keycloak user UUID)
    User agent
    Client IP
    Correlation ID (X-Request-Id)
    Authentication provider

Example record
    result        = SUCCESS
    username      = 03bdf63a-60ee-4f91-afac-0a8457539161
    user_agent    = Mozilla/5.0 ...
    correlationId = b829dcba-a011-45b6-be32-19212fb2a545

Identity projection
    Triggered only on SUCCESS
    Uses subjectId to fetch user details from Keycloak
    Cached and refreshed periodically
This path is rich, deterministic, and request-scoped.


*.FAILURE Events (Keycloak Admin Pull)

Origin
    Pulled from Keycloak Admin Events API
    Represents authentication failures inside Keycloak
    Happens before authentication succeeds

Key difference
    In many failure scenarios, Keycloak does not know the user yet.

Examples:
    Wrong password
    Invalid username
    Disabled account
    Brute-force protection
    Client misconfiguration

In these cases:
    userId is null
    No authenticated principal exists
    No servlet request exists in Spring

Consequences (expected)
----------------------------------------------------------------------------------------
Field	                    Value	                            Reason
----------------------------------------------------------------------------------------
username	              UNKNOWN	                User not resolved at failure time
userAgent	                N/A	                    Not always provided in admin events
correlationId	    sessionId or generated UUID	    No request correlation possible
----------------------------------------------------------------------------------------

Example record
    result     = FAILURE
    username   = UNKNOWN
    userAgent  = N/A
    ip         = 172.18.0.1
This is correct audit behavior, not data loss.


*.Why FAILURE ≠ SUCCESS (by design)

Authentication failures are pre-identity events.
At failure time:
    No subject exists
    No user identity can be trusted
    Only network-level and protocol-level metadata is reliable
For audit and compliance purposes, this is preferable to guessing or retroactively assigning identities.


*.Correlation IDs and Sessions

    SUCCESS / LOGOUT events use X-Request-Id from the HTTP request
    FAILURE events use correlationId:
        Keycloak sessionId when available
        null otherwise

These identifiers allow:
    Correlation of repeated failures
    Brute-force detection
    Cross-event linking without identity assumptions


*.Identity Projection Behavior

Identity projection (user_identity_projection) is:
    Triggered only on SUCCESS
    Never triggered on FAILURE
    Cached and periodically refreshed
    Safe against deleted or missing users
This prevents:
    Polluting identity data with failed attempts
    Creating projections for non-existent users
    
*.Summary (Important)
    UNKNOWN username on FAILURE is expected and correct
    FAILURE events are intentionally sparse
    SUCCESS events are intentionally rich
    Both represent different security phases
    No data is missing or miswired
This asymmetry is a security feature, not a bug.


*.Auditor / Reviewer Note

This audit model aligns with:
    Zero-trust principles
    Pre-auth vs post-auth separation
    ISO 27001 / NIS2 expectations
    OWASP authentication logging guidance
