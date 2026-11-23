# ADR 0003 — Multi-utente con login già nell'MVP

- **Stato:** accettata
- **Data:** 2026-11-23

## Contesto
I dati del CV sono dati personali. L'applicazione deve gestire più utenti fin dall'inizio,
con isolamento completo dei dati tra un utente e l'altro.

## Decisione
- **Identity provider:** Keycloak (self-hosted, gratuito), un realm `cv-generator`.
  L'applicazione non gestisce mai password.
- **Frontend:** client OIDC pubblico `cv-frontend`, Authorization Code Flow con **PKCE (S256)**.
  Implicit flow e password grant sono disattivati.
- **Backend:** OAuth2 Resource Server stateless. Valida firma, issuer e **audience `cv-api`**
  del token JWT. Niente sessioni né cookie, quindi nessuna esposizione CSRF.
- **Isolamento:** ogni dato appartiene a un `UserId`, cioè il claim `sub` del token.
  Ogni query di accesso ai dati è filtrata per proprietario.
  Il controllo si verifica con test dedicati da M1.
- **Superficie minima:** è pubblico solo `/actuator/health`; tutto ciò che sta sotto `/api` richiede
  autenticazione e ogni altro percorso viene negato.
- Il BFF (token solo lato server) è rimandato a M6 come valutazione.

## Conseguenze
- In sviluppo serve Keycloak in esecuzione (`compose.yaml`), mentre i test del backend non ne hanno
  bisogno: il `JwtDecoder` è simulato.
- L'utente demo del realm (`demo@example.com`) esiste solo per lo sviluppo locale.
