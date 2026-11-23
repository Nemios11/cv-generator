# ADR 0002 — Stack tecnologico (solo software libero e gratuito)

- **Stato:** accettata
- **Data:** 2026-11-23

## Contesto
Requisito vincolante: ogni componente deve essere gratuito, con licenza compatibile con l'uso previsto,
senza costi di licenza né vincoli copyleft forti sul codice dell'applicazione.

## Decisione

| Area | Scelta | Licenza |
|---|---|---|
| Runtime backend | Java 25 (Eclipse Temurin) | GPLv2 + Classpath Exception |
| Framework | Spring Boot 4.2 (Spring Security, Spring MVC) | Apache-2.0 |
| Build backend | Maven 3.10 tramite Maven Wrapper | Apache-2.0 |
| Database | PostgreSQL 18 | PostgreSQL License |
| Identity provider | Keycloak 26 | Apache-2.0 |
| Frontend | React 19, TypeScript, Vite, i18next | MIT |
| Font | Inter, distribuito con l'app (`@fontsource/inter`) | OFL-1.1 |
| Test e2e | Playwright | Apache-2.0 |
| CI | GitHub Actions (quota gratuita dei repository privati) | — |

Struttura backend:
- `cv-domain`: modello di dominio in Java puro, senza Spring né annotazioni di persistenza.
- `cv-app`: applicazione Spring Boot con adattatori web, sicurezza e persistenza.

Altri moduli (export, import LinkedIn) si aggiungeranno quando servono.

**Librerie escluse:** quelle con licenza AGPL o commerciale, per esempio iText 7+ per i PDF.
Per l'export (M3) si valuteranno alternative Apache/LGPL come Apache PDFBox, OpenHTMLtoPDF
e Apache POI (DOCX).

## Conseguenze
- Serve un JDK 25 in locale; l'enforcer di Maven blocca il build con versioni precedenti.
- Ogni nuova dipendenza va verificata sulla licenza prima di essere aggiunta.
- Il minutaggio gratuito di GitHub Actions è limitato: i job restano snelli e usano la cache.
