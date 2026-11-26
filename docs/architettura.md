# Architettura e sviluppo locale

Questo documento spiega com'è costruita l'applicazione e come avviarla in locale.
Le scelte e le loro motivazioni sono negli [ADR](adr/).

## Panoramica

```
┌──────────────────────── Browser ────────────────────────┐
│  React app (HTML + JS + CSS)                            │
└───────┬───────────────────────────────────┬─────────────┘
        │ pagine e asset                    │ /api/... + token JWT
        ▼                                   ▼
┌───────────────┐   proxy /api    ┌──────────────────────┐
│ Vite dev      │ ──────────────► │ Spring Boot (cv-app) │
│ server :5173  │                 │ :8080                │
└───────────────┘                 └──────────┬───────────┘
                                             │ chiavi pubbliche per verificare il token
                                             ▼
                                  ┌──────────────────────┐
                                  │ Keycloak :8180       │  login, utenti, token
                                  └──────────┬───────────┘
                                             ▼
                                  ┌──────────────────────┐
                                  │ PostgreSQL :5432     │  database (dati applicativi da M1)
                                  └──────────────────────┘
```

Il sistema è composto da **due applicazioni separate** che comunicano via HTTP:
- il **frontend** è l'interfaccia che gira nel browser;
- il **backend** è un'API REST che gestisce dati e sicurezza.

Keycloak (identity provider) e PostgreSQL sono servizi esterni, avviati in locale con `compose.yaml`.

| Cartella | Contenuto |
|---|---|
| `frontend/` | Applicazione React + TypeScript, compilata con Vite |
| `backend/` | Progetto Maven multi-modulo, Java 25 + Spring Boot 4.2 |
| `infra/` | Configurazione di Keycloak (realm) e Postgres (script di init) |
| `compose.yaml` | Stack locale: Postgres + Keycloak |
| `scripts/` | Controllo anti-dati personali usato da hook e CI |
| `sandbox/` | Sandbox dei job degli agent ([guida](../sandbox/README.md), [ADR 0006](adr/0006-sandbox-agent.md)) |
| `local/` | **Esclusa da git:** dati personali reali, solo sulla propria macchina |

---

## Backend

```
backend/
├── pom.xml              ← POM padre: eredita da spring-boot-starter-parent, Java 25, elenco moduli
├── mvnw / mvnw.cmd      ← Maven Wrapper: scarica e usa Maven 3.10.2, uguale per tutti
├── cv-domain/           ← modulo Java puro (regole di business), nessuna dipendenza da Spring
│   ├── UserId.java
│   └── profile/         ← CareerProfile e voci (Experience, Project, ...), invarianti nei costruttori
└── cv-app/              ← modulo Spring Boot, dipende da cv-domain
    ├── CvGeneratorApplication.java   ← main()
    ├── security/SecurityConfig.java  ← chi può chiamare cosa
    ├── me/MeController.java          ← GET /api/me
    ├── profile/
    │   ├── ProfileController.java    ← GET/PUT/DELETE /api/profile (ETag / If-Match)
    │   ├── ProfileService.java       ← casi d'uso, sempre sul profilo dell'utente autenticato
    │   ├── ProfileStore.java         ← porta di persistenza
    │   ├── JdbcProfileStore.java     ← adattatore PostgreSQL (documento JSONB)
    │   ├── ProfileDto.java           ← formato JSON dell'API e del documento salvato
    │   └── ProfileMapper.java        ← DTO ↔ dominio, con percorso del campo negli errori
    ├── web/                          ← errori RFC 9457, configurazione OpenAPI
    └── resources/
        ├── application.yml           ← configurazione (porta, database, issuer Keycloak, ...)
        └── db/migration/             ← migrazioni Flyway (V1__career_profile.sql, ...)
```

Il flusso di una richiesta segue questi livelli:
1. **Controller (web):** autentica, converte il JSON.
2. **Service (casi d'uso):** applica le regole applicative.
3. **Dominio:** valida le invarianti.
4. **Store (persistenza):** salva su Postgres.

Il dominio non conosce né HTTP né il database.

- `./mvnw package` compila prima `cv-domain` e poi `cv-app`. Produce un **jar eseguibile**
  (`cv-app/target/cv-app-<versione>.jar`) che contiene già Tomcat: si avvia con `java -jar`.
- `cv-domain` non dipende da Spring né da annotazioni di persistenza: il dominio resta testabile
  in isolamento e indipendente dal framework.
- Il POM padre imposta `spring-boot.run.skip=true`, che `cv-app` riporta a `false`.
  Così `spring-boot:run` lanciato sul reactor avvia solo il modulo che ha un `main`.
- La configurazione è in `application.yml`. I valori che cambiano tra ambienti si leggono da
  variabili d'ambiente con un default per lo sviluppo:

  | Variabile | Default |
  |---|---|
  | `OIDC_ISSUER_URI` | `http://localhost:8180/realms/cv-generator` |
  | `OIDC_AUDIENCE` | `cv-api` |
  | `DB_URL` | `jdbc:postgresql://localhost:5432/cvgenerator` |
  | `DB_USER` / `DB_PASSWORD` | credenziali di sviluppo di `compose.yaml` |
  | `API_DOCS_ENABLED` | `true` (OpenAPI e Swagger UI) |
  | `PORT` | `8080` |

### API
| Metodo e percorso | Descrizione |
|---|---|
| `GET /api/me` | Identità dell'utente (dal token) |
| `GET /api/profile` | Il proprio profilo (404 se non esiste); header `ETag` con la versione |
| `PUT /api/profile` | Crea (201) o sostituisce (200) il profilo; con `If-Match` evita di sovrascrivere modifiche concorrenti (412) |
| `DELETE /api/profile` | Elimina definitivamente il profilo |

La documentazione interattiva è su http://localhost:8080/swagger-ui.html: il pulsante *Authorize*
fa il login su Keycloak. La descrizione OpenAPI è su `/v3/api-docs`.
Le scelte di modello e API sono in [ADR 0005](adr/0005-profilo-documento-e-api.md).

### Sicurezza
- Il backend è un **OAuth2 Resource Server stateless**. Ogni richiesta sotto `/api` deve avere
  l'header `Authorization: Bearer <token>`. Il token deve essere firmato da Keycloak, avere l'issuer
  configurato e come audience `cv-api`.
- Non usa sessioni né cookie, quindi non c'è esposizione a CSRF.
- Sono pubblici solo `/actuator/health` e la documentazione delle API; ogni altro percorso fuori da `/api` è negato.

---

## Frontend

Il frontend usa strumenti diversi ma con gli stessi ruoli di quelli del backend:

| Concetto | Backend (Java) | Frontend (JS/TS) |
|---|---|---|
| Runtime per gli strumenti | JDK | **Node.js** |
| Gestore di dipendenze e build | Maven | **npm** |
| Descrittore del progetto | `pom.xml` | **`package.json`** |
| Versioni esatte risolte | — | **`package-lock.json`**: fissa ogni versione, anche quelle transitive |
| Cartella delle dipendenze | `~/.m2/repository` (globale) | **`node_modules/`** (dentro il progetto) |
| Repository remoto | Maven Central | registry npm |
| Compilatore e controllo tipi | `javac` | **TypeScript** (`tsc`) |
| Packaging | Spring Boot Maven plugin | **Vite** (bundler) |
| Output del build | `target/*.jar` | **`dist/`** (file statici) |
| Comandi | goal Maven (`package`, `verify`) | **script** di `package.json` (`npm run build`, ...) |
| Test | JUnit | Playwright (e2e nel browser) |

### `package.json`
```json
"scripts": {
  "dev":      "vite",                        // server di sviluppo con hot reload
  "build":    "tsc -b && vite build",        // controllo tipi + build di produzione
  "preview":  "vite preview",                // serve la build di produzione in locale
  "pdf":      "tsx scripts/generate-pdf.ts", // export PDF del prototipo
  "test:e2e": "playwright test"              // test end-to-end nel browser
}
```
- `dependencies` finiscono nel codice che arriva al browser, come lo scope `compile` di Maven.
- `devDependencies` servono solo a sviluppo e build (Vite, TypeScript, Playwright), come gli scope `test`/`provided`.

### Struttura
```
frontend/
├── index.html           ← unica pagina HTML; carica /src/main.tsx
├── src/
│   ├── main.tsx         ← punto d'ingresso: monta React nel <div id="root">, carica font, CSS e i18n
│   ├── App.tsx          ← percorsi: /demo (esempio, senza login) e / (login, poi il proprio profilo)
│   ├── auth/config.ts   ← configurazione OIDC (Keycloak, PKCE)
│   ├── Workspace.tsx    ← area riservata: profilo, import/export JSON, eliminazione
│   ├── CvView.tsx       ← il componente che disegna il CV (toolbar, sezioni, footer)
│   ├── api/             ← client HTTP, tipi dell'API, conversione profilo → vista
│   ├── data/types.ts    ← tipi della vista del CV
│   ├── data/sample.ts   ← dati di esempio fittizi (pagina /demo e test)
│   ├── i18n/index.ts    ← testi dell'interfaccia in IT/EN
│   ├── styles.css       ← stile del CV: portfolio, ATS, stampa
│   └── app-shell.css    ← stile di pagina iniziale e area riservata
├── scripts/             ← export PDF del prototipo, conversione dei dati del prototipo
├── vite.config.ts       ← plugin React, proxy /api → backend (anche in preview)
├── tsconfig*.json       ← opzioni del compilatore TypeScript (strict, ...)
├── postcss.config.js    ← pipeline CSS (Tailwind)
└── playwright.config.ts ← configurazione dei test e2e
```
I file `.tsx` sono TypeScript con JSX, la sintassi tipo HTML che React trasforma in elementi della pagina.

### Modalità sviluppo e produzione
- **Sviluppo (`npm run dev`):** Vite avvia un server su `:5173` che non crea alcun pacchetto.
  - Traduce al volo i file che il browser richiede e aggiorna la pagina a ogni salvataggio (*hot reload*).
  - Inoltra le chiamate a `/api/...` al backend (`:8080`) tramite il proxy in `vite.config.ts`.
    Il browser vede una sola origine, quindi non serve configurare CORS.
- **Produzione (`npm run build`):**
  1. `tsc -b` controlla i tipi e si ferma sugli errori.
  2. `vite build` unisce e minimizza tutto in `dist/`: un `index.html`, JS, CSS e font.

  Sono **file statici**: in produzione Node.js non serve, basta un qualunque web server.

### Privacy nel frontend
- Nessuna risorsa di terze parti: il font Inter è distribuito con l'app (`@fontsource/inter`) e non ci sono CDN né tracker.
  Un test e2e fallisce se la pagina fa richieste verso domini esterni.

---

## Avvio in locale

### Requisiti
- JDK 25 (es. Eclipse Temurin), con `JAVA_HOME` che punta alla sua cartella
- Node.js 22+ e npm
- Per lo stack completo: Docker o Podman con Compose

### Preparazione (una volta)
```bash
git config core.hooksPath .githooks     # attiva il controllo anti-dati personali sui commit

cd frontend
npm ci                                  # installa node_modules esattamente come da package-lock.json
npx playwright install chromium         # solo per i test e2e
```
Si usa `npm ci` e non `npm install`: installa le versioni del lock senza modificarlo.

### Avvio quotidiano

**Keycloak e Postgres** (terminale 0, da M1 sempre necessari: il backend si avvia solo con il database):
```bash
docker compose up -d --wait             # oppure: podman compose up -d
```
- Console di Keycloak: http://localhost:8180/admin (`admin` / `admin-dev-only`).
- Utente di sviluppo: `demo` / `demo-local-only-password`. Dalla pagina di login ci si può anche registrare.
- Per cambiare le credenziali predefinite copiare `.env.example` in `.env`.
- Il realm viene importato **solo al primo avvio**. Dopo una modifica a
  `infra/keycloak/realm-cv-generator.json` va ricreato lo stack con `docker compose down -v`,
  che cancella anche i dati locali.

**Backend** (terminale 1):
```powershell
cd backend
$env:JAVA_HOME = "<cartella del JDK 25>"     # Linux/macOS: export JAVA_HOME=...
.\mvnw.cmd spring-boot:run -pl cv-app -am    # Linux/macOS: ./mvnw ...
```
`-pl cv-app -am` avvia il modulo `cv-app` dopo aver compilato i moduli da cui dipende.
Il backend è pronto quando compare `Started CvGeneratorApplication`, su http://localhost:8080.

**Frontend** (terminale 2):
```bash
cd frontend
npm run dev                             # http://localhost:5173
```
- http://localhost:5173 mostra la pagina iniziale: *Accedi* porta al login su Keycloak.
- http://localhost:5173/demo mostra il CV con i dati di esempio, senza login.

Prima va avviato lo stack (Postgres), poi il backend. Il frontend si può avviare in qualsiasi momento.

### Importare i dati del prototipo
I dati reali del prototipo restano in `local/prototype-data/`, cartella esclusa da git.
Per caricarli nel proprio profilo:
```bash
cd frontend
npm run convert-prototype        # scrive ../local/profile-import.json (stampa solo conteggi)
```
Poi, nell'app, dopo il login: **Importa JSON** → scegliere `local/profile-import.json`.
Con **Esporta JSON** si scarica il profilo, con **Elimina profilo** lo si cancella.

### Verifiche rapide
```bash
curl http://localhost:8080/actuator/health   # {"status":"UP",...}
curl -i http://localhost:8080/api/me          # 401: serve un token
```
Per ottenere un token di prova senza frontend:
1. Nella console di Keycloak, realm `cv-generator`, aprire Clients → `cv-frontend` → **Client scopes** → **Evaluate**.
2. Scegliere l'utente `demo` e poi **Generated access token**.
3. Chiamare:
   ```bash
   curl -H "Authorization: Bearer <token>" http://localhost:8080/api/me
   ```

In alternativa si può usare Swagger UI (http://localhost:8080/swagger-ui.html → *Authorize*).

### Da IntelliJ IDEA
- **Backend:** aprire la cartella radice e importare `backend/pom.xml` come progetto Maven.
  Impostare il JDK 25 in *Project Structure → SDK* ed eseguire `CvGeneratorApplication`.
- **Frontend:** in IntelliJ Ultimate gli script di `package.json` hanno il pulsante ▶ accanto.
  Altrimenti usare il terminale integrato.

### Test
```bash
cd backend  && ./mvnw verify          # dominio, API e sicurezza; integrazione su Postgres se c'è Docker/Podman
cd frontend && npm run test:e2e       # build + test Playwright (il login e2e richiede E2E_AUTH=1 e lo stack)
bash scripts/check-private-data.sh    # controllo anti-dati personali sui file in staging
```
I test d'integrazione (Testcontainers) e il test di login end-to-end girano sempre in CI, nel job `stack`.

### Agent in sandbox
I job degli agent non girano sull'host ma in un container Podman (in CI Docker) collegato solo a una
rete interna; l'unica uscita è un proxy con allowlist. Il token GitHub dura un'ora e vale solo per
questo repository. Dettagli in [sandbox/README.md](../sandbox/README.md) e nell'[ADR 0006](adr/0006-sandbox-agent.md).
Nella sandbox `./mvnw verify` salta i test Testcontainers, perché l'agent non ha accesso al motore dei container.

### Ambienti deployati (dev, staging, prod)
Oltre all'avvio dall'IDE, l'applicazione si può installare come **versione** (lo SHA del commit) in un
ambiente completo: Postgres, Keycloak, API e nginx in container, dietro un solo punto d'ingresso
(`http://localhost:8280` per dev). Dettagli in [deploy/README.md](../deploy/README.md) e
nell'[ADR 0007](adr/0007-rilasci-e-ambienti.md).

---

## Flusso di autenticazione

1. Il frontend reindirizza a Keycloak, dove l'utente inserisce le credenziali.
   L'applicazione non vede né conserva mai la password.
2. Keycloak rimanda al frontend un codice monouso, che il frontend scambia con un **access token JWT**
   (Authorization Code Flow con **PKCE**: protegge lo scambio da intercettazioni).
3. Il frontend allega il token a ogni chiamata `/api/...`.
4. Il backend verifica firma, issuer e audience del token.
   Ricava l'utente dal claim `sub` (`UserId`) e restituisce **solo i dati di quell'utente**.
