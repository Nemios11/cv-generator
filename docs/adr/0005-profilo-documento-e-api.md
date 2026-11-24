# ADR 0005 — CareerProfile come documento e API del profilo

- **Stato:** accettata
- **Data:** 2026-11-23

## Contesto
M1 introduce `CareerProfile`, la fonte unica da cui deriveranno tutte le varianti di CV.
Il profilo è un aggregato: contiene intestazione, esperienze, progetti, competenze, formazione,
certificazioni e lingue, e si legge e si modifica sempre nel suo insieme.
Ogni utente ne ha uno solo.

## Decisione

### Modello
- Il dominio (`cv-domain`) è fatto di record Java immutabili.
  Le invarianti si verificano nei costruttori e la violazione solleva `InvalidProfileException` con il percorso del campo:
  - campi obbligatori;
  - date coerenti (fine ≥ inizio, granularità mese `YearMonth`);
  - URL solo `http(s)`, email e telefono validi se presenti;
  - tag unici;
  - limiti di dimensione;
  - id unici in ogni collezione.
- Ogni testo è `LocalizedText {it, en}`. Basta una lingua per i campi obbligatori,
  così si può compilare il profilo in una lingua alla volta. La vista usa l'altra lingua come fallback.
- La foto non fa parte del modello. Se servirà, sarà una decisione separata (minimizzazione).

### Persistenza
- Una riga per utente nella tabella `career_profile`:
  - `owner_id` (claim `sub`) è chiave primaria e chiave d'isolamento;
  - `document` è un campo JSONB;
  - ci sono `schema_version` e `version` per il locking ottimistico.
- Lo schema è gestito da Flyway. L'accesso al database passa da JDBC puro (`JdbcClient`), senza ORM:
  un documento non ne ha bisogno.
- Il formato del documento coincide con quello dell'API (`ProfileDto`).
  Un cambio incompatibile incrementa `schema_version` e richiede una migrazione.

### API
- `GET | PUT | DELETE /api/profile` agiscono sempre e solo sul profilo dell'utente autenticato.
  Non esiste un id di profilo nell'URL, quindi un utente non può indirizzare il profilo di un altro.
- `PUT` crea il profilo (201) o lo sostituisce (200). Gli id delle voci mancanti vengono generati dal server.
- La concorrenza usa `ETag` e `If-Match`: con `If-Match` non aggiornato la risposta è **412**.
  Senza `If-Match` vince l'ultima scrittura, cosa utile per import e script.
- Gli errori sono RFC 9457 (`ProblemDetail`) con `field` (es. `experiences[0].period.end`)
  e non ripetono mai i valori inviati, che possono contenere dati personali.
- La descrizione OpenAPI è su `/v3/api-docs` e Swagger UI su `/swagger-ui.html`, con login PKCE sullo stesso realm.
  Sono pubbliche perché descrivono soltanto le API. Si disattivano con `API_DOCS_ENABLED=false`.
- Le modifiche granulari (singola esperienza) sono rimandate all'editor di M2, se serviranno.

### Frontend
- Il login usa `oidc-client-ts` + `react-oidc-context`, Authorization Code + PKCE (S256).
- La libreria conserva i token in `sessionStorage`: non sopravvivono alla chiusura della scheda.
  Restano però accessibili a un eventuale XSS. Mitigazioni:
  - nessuno script di terze parti;
  - token di accesso di breve durata (5 minuti).

  Il passaggio a un BFF resta in valutazione in M6.
- L'utente può già **esportare** il profilo in JSON (portabilità, art. 20 GDPR) ed **eliminarlo** (art. 17).
  La cancellazione dell'account Keycloak resta da collegare.

### Import dei dati del prototipo
- Lo script `npm run convert-prototype` converte i file del prototipo in un JSON importabile dall'app.
  Legge da `local/`, cartella esclusa da git, e scrive in `local/`, quindi i dati reali non passano mai da git o dalla CI.
- Stampa solo conteggi e avvisi, mai i contenuti.

## Conseguenze
- Il backend ora richiede PostgreSQL per avviarsi (in locale `compose.yaml`).
- I test d'integrazione usano Testcontainers. Dove manca un runtime di container vengono saltati,
  mentre in CI girano sempre.
- Modificare il formato JSON significa modificare insieme API, documento salvato e client.
