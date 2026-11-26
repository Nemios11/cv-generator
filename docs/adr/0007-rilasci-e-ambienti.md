# ADR 0007 — Rilasci e ambienti

- **Stato:** in corso (parte 1 di 4: immagini e ambienti)
- **Data:** 2026-11-26

## Contesto
L'agent deve poter rilasciare, controllare e annullare un rilascio con strumenti limitati per ambiente.
Lo stesso vale in Stage 6 (*Maintain*), dove il rollback scatta quando una banda di controllo viene
superata. I requisiti del corso sono:
- le scritture dell'agent arrivano come pull request, attraverso la protezione dei branch;
- deploy, stato e rollback sono strumenti MCP, uno per ambiente;
- l'autonomia è a livelli: libera in dev, intermedia in staging, autorizzata dal release manager in
  prod (con un hook che lo impone);
- il rollback è un solo comando, provato regolarmente in staging.

Prima di questa decisione non esisteva niente da rilasciare: il `compose.yaml` avviava solo Postgres
e Keycloak, e l'applicazione girava dall'IDE. Questa prima parte crea ciò che serve a tutto il resto:
**versioni** e **ambienti**.

## Decisione

### Versioni
- La versione è lo SHA del commit (12 caratteri). Un rilascio è formato da due immagini,
  `localhost/cv-generator-api:<versione>` e `localhost/cv-generator-web:<versione>`, etichettate con
  `org.opencontainers.image.revision`.
- Le immagini si costruiscono dal contenuto del commit (`git archive`), non dalla copia di lavoro:
  niente modifiche non committate e niente file ignorati (`local/`, `.env`).
- Sono immutabili: una versione già costruita non si ricostruisce. Un rollback riavvia immagini già
  provate.
- Build multi-stage: nell'immagine finale arrivano solo JRE + jar, oppure nginx + file statici.

### Ambienti
- `dev`, `staging` e `prod` usano lo stesso `deploy/compose.yaml` come progetti Compose separati
  (`cv-<env>`), ciascuno con container, rete e volume propri. Le differenze sono solo in
  `deploy/env/<env>.env`.
- Dove girano:
  - `dev` e `prod` sul PC, con Podman; `prod` è simulato e senza dati reali;
  - `staging` viene creato da zero sul runner della CI a ogni esecuzione.
- Le stesse immagini girano in tutti gli ambienti, quindi niente nella build dipende dall'ambiente.
  Il frontend usa un'*authority* OIDC relativa, che viene completata con l'origine della pagina.
- Un solo punto d'ingresso per ambiente: nginx su `127.0.0.1:<porta>`, che inoltra `/api/` all'API
  e il login a Keycloak. La console di amministrazione non è pubblicata. nginx risolve i nomi a ogni
  richiesta, così trova i container ricreati da un deploy o da un rollback.
- Le password sono generate per ambiente, fuori dal repository. Compose non ha valori di default per
  i segreti.
- I container dell'applicazione sono irrigiditi: non root, filesystem in sola lettura, nessuna
  capability, limiti di memoria.
- Un deploy è completato solo quando tutti i servizi sono sani (`compose up --wait` con gli healthcheck).

### Verifiche
| Cosa | Verifica |
|---|---|
| L'ambiente risponde come deve | `deploy/smoke.sh <env> [<versione>]` |
| Il login funziona attraverso l'entry point | `tests/auth.spec.ts` con `E2E_BASE_URL` |
| Tutto il percorso su ogni PR | job CI *Release images (build + staging deploy + smoke)* |

## Alternative considerate
- **Versioni semantiche (1.4.2):** vanno gestite a mano e non dicono quale codice contengono. Si
  possono aggiungere in seguito come etichette sopra lo SHA.
- **Costruire dalla copia di lavoro:** più comodo, ma un'immagine potrebbe contenere codice mai
  committato, o i dati di `local/`.
- **Un'immagine del frontend per ambiente:** contraddice "staging prova ciò che va in prod".
- **Configurazione del frontend scaricata all'avvio (`/config.json`):** funziona, ma l'*authority*
  relativa ottiene lo stesso risultato con un `if`.
- **Keycloak condiviso tra gli ambienti:** risparmia memoria, ma un problema in dev toccherebbe prod.
- **Kubernetes (kind, k3d):** più vicino a un'infrastruttura reale, ma molto più pesante per un PC con
  4 GB dedicati ai container. Compose basta per mostrare i concetti.
- **Registry (GHCR):** servirà per condividere le immagini tra macchine; oggi le immagini nascono e
  restano sulla macchina che le usa.

## Conseguenze
- Durante un deploy l'API non risponde per qualche secondo: il container viene sostituito, non
  affiancato.
- Il rollback riporta il codice, non lo schema del database. Le migrazioni devono restare compatibili
  con la versione precedente (modello *expand/contract*).
- Il realm di Keycloak viene importato solo su un database vuoto; dopo una modifica va ricreato
  l'ambiente (`destroy`).
- Il PC ha bisogno di memoria per due ambienti completi: circa 1,6 GB di limiti ciascuno.

## Parti successive
1. ~~Immagini e ambienti~~ (questo documento).
2. Server MCP di deploy per ambiente (`release`, `status`, `rollback`).
3. Livelli di autonomia: hook `PreToolUse` e controllo del ticket di produzione.
4. Rollback provato regolarmente in CI e watchdog della banda di controllo.
