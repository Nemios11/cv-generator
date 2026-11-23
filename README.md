# CV Generator

Applicazione multi-utente per creare e mantenere il proprio profilo professionale e generare CV
mirati in italiano e inglese: un template **ATS** a una colonna e un template **Portfolio**,
esportabili in PDF e DOCX.

> **Dati personali:** il repository contiene solo dati di esempio fittizi.
> I dati reali si inseriscono dall'applicazione (da M1) e non vanno mai salvati nei file del progetto.
> Vedi [ADR 0004](docs/adr/0004-dati-personali-e-conformita.md).

Come è costruita e come si avvia in locale: [docs/architettura.md](docs/architettura.md).

## Struttura

| Cartella | Contenuto |
|---|---|
| `frontend/` | React 19 + TypeScript + Vite (prototipo evoluto) |
| `backend/` | Maven multi-modulo, Java 25, Spring Boot 4.2 (`cv-domain`, `cv-app`) |
| `infra/` | Postgres e Keycloak per lo sviluppo locale |
| `docs/adr/` | Decisioni architetturali |
| `local/` | **Esclusa da git:** dati personali e pattern privati, solo sulla propria macchina |

## Requisiti
- JDK 25 (es. Eclipse Temurin) con `JAVA_HOME` impostato
- Node.js 22+ e npm
- Docker o Podman con Compose, per lo stack locale

## Primo avvio

```bash
# Attiva l'hook che blocca i dati personali nei commit
git config core.hooksPath .githooks

# Stack locale: Postgres (5432) e Keycloak (http://localhost:8180)
docker compose up -d --wait

# Backend su http://localhost:8080
cd backend && ./mvnw spring-boot:run -pl cv-app -am

# Frontend su http://localhost:5173 (le chiamate /api passano dal proxy di Vite)
cd frontend && npm ci && npm run dev
```

Utente di sviluppo nel realm Keycloak: `demo` / `demo-local-only-password`, valido solo in locale.
Le credenziali predefinite dello stack si sovrascrivono copiando `.env.example` in `.env`.

## Test

```bash
cd backend && ./mvnw verify                               # unit test e test di sicurezza delle API
cd frontend && npx playwright install chromium && npm run test:e2e
bash scripts/check-private-data.sh                        # controllo dati personali sui file in staging
```

La CI (`.github/workflows/ci.yml`) esegue tutti questi controlli più uno smoke test dello stack:
avvia Compose, verifica Keycloak e controlla che `/api/me` rifiuti le richieste senza token valido.

## Export PDF del prototipo

```bash
cd frontend && npm run build && npm run preview   # in un terminale
cd frontend && npm run pdf                        # in un altro: dist/developer-cv.pdf
```

Da M3 l'export (PDF/DOCX) passa al backend, con varianti per lingua e template.

## Roadmap

| # | Milestone |
|---|---|
| M0 | Setup repository: monorepo, Maven multi-modulo, Compose, `/api/me` protetto, CI, ADR |
| M1 | Login PKCE nel frontend, `CareerProfile` per utente, CRUD REST + OpenAPI, import dei dati del prototipo |
| M2 | Editor del profilo nel frontend (IT/EN, validazione) |
| M3 | `CvVariant`, anteprima HTML, PDF e DOCX del template ATS |
| M4 | Import da ZIP dell'export dati LinkedIn con revisione dei conflitti |
| M5 | Template Portfolio in PDF/DOCX, varianti per ruolo, accessibilità |
| M6 | Opzionale: import via API DMA, login social, BFF, container e deploy |
