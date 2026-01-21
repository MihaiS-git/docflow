> This diagram uses Mermaid and renders automatically on GitHub / GitLab.
sequenceDiagram
    autonumber
    participant B as Browser
    participant N as Nginx
    participant K as Keycloak
    participant S as Spring Boot
    participant DB as Audit DB

    %% ===== SUCCESS PATH =====
    rect rgba(200, 255, 200, 0.3)
    Note over B,DB: SUCCESS / LOGOUT (Spring Security)
    B->>N: Login request
    N->>K: Redirect to Keycloak
    K->>B: Login form
    B->>K: Credentials
    K->>B: Authorization code
    B->>N: Callback with code
    N->>S: /login/oauth2/code
    S->>K: Token exchange
    K-->>S: ID / Access token
    S->>S: AuthenticationSuccessEvent
    S->>DB: Persist SUCCESS event<br/>(username, UA, IP, correlationId)
    S->>K: Fetch user details
    S->>DB: Update identity projection
    end

    %% ===== FAILURE PATH =====
    rect rgba(255, 220, 220, 0.3)
    Note over B,DB: FAILURE (Keycloak Admin Events)
    B->>N: Login request
    N->>K: Redirect to Keycloak
    K->>B: Login form
    B->>K: Invalid credentials
    K->>K: Authentication fails<br/>(pre-identity)
    K->>K: Emit admin event<br/>(LOGIN_ERROR / CLIENT_LOGIN_ERROR)
    S->>K: Poll admin events (scheduled)
    K-->>S: Failure event (no userId)
    S->>DB: Persist FAILURE event<br/>(username=UNKNOWN, UA=N/A)
    end
