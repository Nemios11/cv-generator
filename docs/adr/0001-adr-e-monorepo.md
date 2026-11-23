# ADR 0001 — Registrare le decisioni architetturali e usare un monorepo

- **Stato:** accettata
- **Data:** 2026-11-23

## Contesto
Il progetto nasce da un prototipo frontend (React/Vite) con dati del CV inseriti nel codice.
Evolve in un'applicazione multi-utente con backend, autenticazione, import ed export.
Le decisioni vanno rese tracciabili e il codice di frontend, backend e infrastruttura
deve evolvere in modo coordinato.

## Decisione
- Le decisioni significative si registrano come ADR in `docs/adr/`, numerate e immutabili.
  Una decisione superata si sostituisce con un nuovo ADR che la richiama.
- Un unico repository GitHub **privato** (`Nemios11/cv-generator`) con questa struttura:
  - `frontend/`: il prototipo spostato ed evoluto, non riscritto.
  - `backend/`: progetto Maven multi-modulo.
  - `infra/`: configurazione dei servizi locali (Postgres, Keycloak).
  - `docs/`: ADR e documentazione.
- Ogni milestone si chiude con una PR dedicata che riporta riepilogo, decisioni e test eseguiti.

## Conseguenze
- Una sola CI valida frontend, backend e stack locale in modo coerente.
- Le modifiche trasversali (contratto API e client) stanno in una sola PR.
- I build dei due mondi restano indipendenti: `npm` in `frontend/`, Maven Wrapper in `backend/`.
