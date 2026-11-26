# ADR 0007 — Rilasci e ambienti

- **Stato:** in corso (parti 1 e 2 di 4: immagini e ambienti, deploy via MCP)
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
- Una versione ha quattro immagini: `api`, `web`, `db` (Postgres con lo script di init) e `idp`
  (Keycloak con il realm). Anche la configurazione fa parte della versione, e il compose non monta
  niente dal checkout.
- Le stesse immagini girano in tutti gli ambienti, quindi niente nella build dipende dall'ambiente.
  Il frontend usa un'*authority* OIDC relativa, che viene completata con l'origine della pagina.
- Un solo punto d'ingresso per ambiente: nginx su `127.0.0.1:<porta>`, che inoltra `/api/` all'API
  e il login a Keycloak. La console di amministrazione non è pubblicata. nginx risolve i nomi a ogni
  richiesta, così trova i container ricreati da un deploy o da un rollback.
- Le password sono generate per ambiente, fuori dal repository
  (`~/.config/cv-generator-deploy/<env>/`). Compose non ha valori di default per i segreti.
- I container dell'applicazione sono irrigiditi: non root, filesystem in sola lettura, nessuna
  capability, limiti di memoria.
- Un deploy è completato solo quando tutti i servizi sono sani (`compose up --wait` con gli healthcheck).

### Deploy come strumenti MCP (parte 2)
- `deploy/stack.sh` è l'unica implementazione di `release`, `status` e `rollback`, con la cronologia
  per ambiente. Le persone e il server MCP usano gli stessi comandi.
- Un server MCP per ambiente (`deploy/mcp`, TypeScript con l'SDK ufficiale, licenza MIT), in un
  container `cv-deploy-<env>`:
  - l'ambiente è fissato all'avvio;
  - token bearer proprio, confrontato in tempo costante; senza token il server non parte;
  - è collegato solo alla rete interna della sandbox, con l'alias `deploy-<env>`;
  - solo lui ha il socket del motore dei container, e monta solo lo stato del suo ambiente.
- Strumenti:
  - `release(version[, ticket])`: restituisce l'ID del rilascio quando tutti i servizi sono sani;
  - `status()`: aspetta la finestra di 5 minuti dall'ultimo rilascio, poi riporta il tasso di 5xx
    per endpoint e se la soglia dell'1% è superata;
  - `rollback()`: torna all'ultima versione buona che non sia già stata oggetto di un rollback e
    restituisce la versione di nuovo attiva.
- Gli argomenti sono validati (versione `^[0-9a-f]{12}$`) e passati senza shell (`execFile`).
  Rilasci e rollback di un ambiente sono serializzati. Ogni chiamata produce una riga di audit.
- `.mcp.json` dichiara i tre server. `sandbox/agent.sh --deploy <env>` passa all'agent solo i token
  degli ambienti indicati.
- Metriche:
  - l'API pubblica le metriche Prometheus sulla porta di management `8081`, interna all'ambiente;
  - una catena di Spring Security dedicata apre quella porta e nient'altro;
  - un client di servizio Keycloak (`cv-synthetic`) genera traffico sintetico di sola lettura
    attraverso l'entry point.
- Produzione: `release` richiede un ticket e il server lo verifica da sé. Finché la verifica della
  parte 3 non esiste, rifiuta sempre (*fail closed*). `status` e `rollback` sono sempre consentiti.

### Verifiche
| Cosa | Verifica |
|---|---|
| L'ambiente risponde come deve | `deploy/smoke.sh <env> [<versione>]` |
| Il login funziona attraverso l'entry point | `tests/auth.spec.ts` con `E2E_BASE_URL` |
| Metriche solo sulla porta di management | `ManagementPortIntegrationTest` |
| Tutto il percorso su ogni PR | job CI *Release images*: build, staging, smoke, login, `status` via MCP dalla rete della sandbox |

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
- **Socket del motore dato all'agent, con istruzioni su cosa non fare:** è la "shell con
  credenziali" che il requisito chiede di evitare.
- **Un solo server MCP con l'ambiente come argomento:** il potere non sarebbe più separato per
  token; un agent con accesso a dev potrebbe chiedere prod.
- **Server MCP sull'host:** l'agent è su una rete senza uscita e non potrebbe raggiungerlo. Un
  container sulla rete interna è raggiungibile solo dalla sandbox.
- **Logica di deploy nel server (TypeScript):** il rollback a mano e quello dell'agent sarebbero due
  percorsi diversi, e se ne proverebbe uno solo.
- **Prometheus e Alertmanager per lo stato:** più completi, ma un servizio in più per ambiente; per
  un tasso di errore per endpoint bastano i contatori dell'applicazione.

## Conseguenze
- Durante un deploy l'API non risponde per qualche secondo: il container viene sostituito, non
  affiancato.
- Il rollback riporta il codice, non lo schema del database. Le migrazioni devono restare compatibili
  con la versione precedente (modello *expand/contract*).
- Il realm di Keycloak viene importato solo su un database vuoto; dopo una modifica va ricreato
  l'ambiente (`destroy`).
- Il PC ha bisogno di memoria per due ambienti completi: circa 1,6 GB di limiti ciascuno.
- In locale i tre server MCP usano lo stesso motore dei container: il limite per ambiente è nel
  server, non nel socket. In un sistema reale ogni ambiente avrebbe credenziali sue.
- I token dei server MCP sono a lunga durata e si ruotano a mano.

## Parti successive
1. ~~Immagini e ambienti~~ (questo documento).
2. ~~Server MCP di deploy per ambiente (`release`, `status`, `rollback`)~~ (questo documento,
   guida in `deploy/mcp/README.md`).
3. Livelli di autonomia: hook `PreToolUse` e controllo del ticket di produzione.
4. Rollback provato regolarmente in CI e watchdog della banda di controllo.
