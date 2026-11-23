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
| `local/` | **Esclusa da git:** dati personali reali, solo sulla propria macchina |

---

## Backend

```
backend/
├── pom.xml              ← POM padre: eredita da spring-boot-starter-parent, Java 25, elenco moduli
├── mvnw / mvnw.cmd      ← Maven Wrapper: scarica e usa Maven 3.10.2, uguale per tutti
├── cv-domain/           ← modulo Java puro (regole di business), nessuna dipendenza da Spring
│   └── UserId.java
└── cv-app/              ← modulo Spring Boot, dipende da cv-domain
    ├── CvGeneratorApplication.java   ← main()
    ├── security/SecurityConfig.java  ← chi può chiamare cosa
    ├── me/MeController.java          ← GET /api/me
    └── resources/application.yml     ← configurazione (porta, issuer Keycloak, ...)
```

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
  | `PORT` | `8080` |

### Sicurezza
- Il backend è un **OAuth2 Resource Server stateless**. Ogni richiesta sotto `/api` deve avere
  l'header `Authorization: Bearer <token>`. Il token deve essere firmato da Keycloak, avere l'issuer
  configurato e come audience `cv-api`.
- Non usa sessioni né cookie, quindi non c'è esposizione a CSRF.
- È pubblico solo `/actuator/health`; ogni altro percorso fuori da `/api` è negato.

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
│   ├── App.tsx          ← il componente che disegna il CV (toolbar, sezioni, footer)
│   ├── data/types.ts    ← tipi del modello dati
│   ├── data/sample.ts   ← dati di esempio fittizi (da M1 arriveranno dall'API)
│   ├── i18n/index.ts    ← testi dell'interfaccia in IT/EN
│   └── styles.css       ← stile: portfolio, ATS, stampa
├── vite.config.ts       ← plugin React, proxy /api → backend
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

**Keycloak e Postgres** (opzionale finché il frontend non ha il login, cioè fino a M1):
```bash
docker compose up -d --wait             # oppure: podman compose up -d
```
- Console di Keycloak: http://localhost:8180/admin (`admin` / `admin-dev-only`).
- Utente di sviluppo: `demo` / `demo-local-only-password`.
- Per cambiare le credenziali predefinite copiare `.env.example` in `.env`.

I processi sono indipendenti e si possono avviare in qualsiasi ordine.

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

### Da IntelliJ IDEA
- **Backend:** aprire la cartella radice e importare `backend/pom.xml` come progetto Maven.
  Impostare il JDK 25 in *Project Structure → SDK* ed eseguire `CvGeneratorApplication`.
- **Frontend:** in IntelliJ Ultimate gli script di `package.json` hanno il pulsante ▶ accanto.
  Altrimenti usare il terminale integrato.

### Test
```bash
cd backend  && ./mvnw verify          # unit test e test di sicurezza delle API
cd frontend && npm run test:e2e       # build + test Playwright
bash scripts/check-private-data.sh    # controllo anti-dati personali sui file in staging
```

---

## Flusso di autenticazione (da M1)

1. Il frontend reindirizza a Keycloak, dove l'utente inserisce le credenziali.
   L'applicazione non vede né conserva mai la password.
2. Keycloak rimanda al frontend un codice monouso, che il frontend scambia con un **access token JWT**
   (Authorization Code Flow con **PKCE**: protegge lo scambio da intercettazioni).
3. Il frontend allega il token a ogni chiamata `/api/...`.
4. Il backend verifica firma, issuer e audience del token.
   Ricava l'utente dal claim `sub` (`UserId`) e restituisce **solo i dati di quell'utente**.
