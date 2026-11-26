# Rilasci e ambienti

Questa cartella trasforma un commit in una **versione** che si può installare (*deploy*) in un
**ambiente**: `dev`, `staging` o `prod`. È la base su cui poggiano gli strumenti di deploy dell'agent
(server MCP), i livelli di autonomia per ambiente e il rollback: le decisioni sono in
[ADR 0007](../docs/adr/0007-rilasci-e-ambienti.md).

```
 commit abc123…  ──deploy/build.sh──▶  immagini localhost/cv-generator-api:abc123def456
                                                 localhost/cv-generator-web:abc123def456
                                                              │
                          deploy/stack.sh <env> up abc123def456
                                                              ▼
 ┌─ ambiente (progetto Compose cv-<env>, rete propria) ──────────────────────────────────┐
 │                                                                                        │
 │  browser ──▶ 127.0.0.1:<porta> ──▶ web (nginx) ──┬── /            app React (statica) │
 │                                                  ├── /api/        api (Spring Boot)   │
 │                                                  └── /realms/     keycloak (login)    │
 │                                                                      │         │       │
 │                                                                      ▼         ▼       │
 │                                                                   postgres (volume)    │
 └────────────────────────────────────────────────────────────────────────────────────────┘
```

| File | Ruolo |
|---|---|
| `build.sh` | Costruisce le immagini di un commit; la versione è lo SHA |
| `stack.sh` | Avvia, ferma e ispeziona un ambiente |
| `smoke.sh` | Verifica dall'esterno che un ambiente risponda come deve |
| `compose.yaml` | Lo stack di un ambiente: stesso file per tutti |
| `env/<env>.env` | Ciò che distingue gli ambienti: nome, porta, opzioni. Nessun segreto |
| `lib.sh` | Funzioni comuni agli script |
| `../backend/Containerfile`, `../frontend/Containerfile` | Le due immagini |

## Uso

```bash
v=$(bash deploy/build.sh)               # immagini del commit corrente (HEAD); stampa la versione
bash deploy/stack.sh dev up "$v"         # installa quella versione in dev
bash deploy/smoke.sh dev "$v"            # verifica: tutte le righe devono essere PASS
bash deploy/stack.sh dev version         # versione in esecuzione
bash deploy/stack.sh dev ps              # container e stato di salute
bash deploy/stack.sh dev logs api        # log di un servizio
bash deploy/stack.sh dev down            # ferma; il database resta
bash deploy/stack.sh dev destroy         # ferma e cancella anche il database
```

L'app di un ambiente si apre su `http://localhost:<porta>` e si entra con l'utente di prova
`demo` / `demo-local-only-password` (dati fittizi). Per una versione precedente:
`bash deploy/build.sh <commit>` e poi `up` con la versione stampata.

| Ambiente | Porta | Dove gira | Swagger UI | Autonomia dell'agent (prossimi passi) |
|---|---|---|---|---|
| `dev` | 8280 | PC, Podman | sì | deploya liberamente |
| `staging` | 8380 | runner della CI, effimero (anche sul PC per prova) | sì | deploya dalla pipeline su `main` |
| `prod` | 8480 | PC, Podman (simulato, nessun dato reale) | no | prepara il rilascio, lo autorizza il release manager |

In locale servono Podman (macchina `cv-generator`) e `docker-compose`, che `podman compose` usa
dietro le quinte. In CI si usa Docker (`CONTAINER_ENGINE=docker`).

---

## I concetti, uno per uno

### 1. Una versione è un commit, e non cambia più
La versione è lo SHA del commit, abbreviato a 12 caratteri (`c20f9654a202`). Ne seguono tre
proprietà utili:
- **tracciabilità:** dalla versione in esecuzione si risale esattamente al codice
  (`git show c20f9654a202`). La versione è scritta anche nell'etichetta OCI
  `org.opencontainers.image.revision` di ogni immagine, ed è da lì che `stack.sh <env> version` la
  legge;
- **immutabilità:** se le immagini di una versione esistono già, `build.sh` non le ricostruisce. La
  stessa etichetta indica sempre lo stesso contenuto, quindi tornare a una versione precedente
  significa riavviare le **stesse** immagini già provate, non ricostruirle;
- **niente "versione 1.4.2" da inventare:** il numero lo dà git.

**Una trappola trovata nella prova.** All'inizio l'etichetta era scritta nel `Containerfile` come
`LABEL …revision="${CV_VERSION}"`, con la versione passata come `ARG`. Costruendo un secondo commit
con lo stesso codice del backend, Podman ha riusato dalla cache il passo `LABEL` del commit
precedente: l'immagine `16e8c07afa3a` diceva di essere `c20f9654a202`. La cache di buildah confronta
il testo dell'istruzione, non il valore dell'`ARG`. Ora l'etichetta è passata a `build --label`,
che si applica all'immagine finale e non passa dalla cache. L'errore l'ha trovato lo smoke test, che
controlla la versione in esecuzione: è il motivo per cui quel controllo esiste.

### 2. Si costruisce dal commit, non dalla cartella di lavoro
`build.sh` estrae i file del commit con `git archive` in una cartella temporanea e costruisce da lì.
Quindi:
- una modifica non committata **non** entra nell'immagine (lo script lo segnala);
- i file ignorati da git (`local/` con i dati personali, `.env`) non possono entrare, perché
  `git archive` contiene solo file tracciati;
- costruire una versione vecchia è uguale a costruire quella nuova: `build.sh <commit>`.

### 3. Build in due fasi (*multi-stage*)
Ogni `Containerfile` ha due `FROM`:
1. **build:** un'immagine con JDK e Maven (o Node) compila il codice;
2. **runtime:** un'immagine minima che riceve **solo il risultato** (il jar, oppure i file statici
   del frontend).

Compilatori, sorgenti e cache non arrivano in produzione, quindi l'immagine è più piccola e offre
meno superficie di attacco:
- API: circa 370 MB, cioè la JRE (331 MB) più il jar. La sola base JDK pesa 414 MB, a cui si
  aggiungerebbero Maven, la sua cache e i sorgenti;
- web: circa 57 MB con nginx, contro i 232 MB della sola base Node a cui si aggiungerebbe
  `node_modules`. La cache di Maven e
npm è montata solo durante la build (`RUN --mount=type=cache`) e non finisce nell'immagine.

I test non girano nella build: lo stesso commit li ha già passati nella CI (job *Backend* e
*Frontend*). La build produce, non verifica.

### 4. Lo stesso artefatto in tutti gli ambienti
Si costruisce **una volta** e si installa ovunque (*build once, deploy many*): staging prova
esattamente ciò che arriverà in prod. Perciò nell'immagine non c'è niente di specifico di un
ambiente; ciò che cambia arriva **all'avvio**, da variabili d'ambiente (`env/<env>.env` e i segreti).

Il caso difficile era il frontend. Vite scrive le variabili `VITE_*` nel JavaScript **al momento
della build**, e l'indirizzo di Keycloak era una di queste: con porte diverse per ambiente sarebbe
servita un'immagine per ambiente. La soluzione è l'*authority* relativa: l'immagine è costruita con
`VITE_OIDC_AUTHORITY=/realms/cv-generator` e `src/auth/config.ts` la completa nel browser con
l'origine della pagina (`http://localhost:8280/realms/cv-generator` in dev, `…:8480…` in prod).
Lo sviluppo con `npm run dev` resta com'era: senza variabile si usa Keycloak su `:8180`.

### 5. Un solo punto d'ingresso: nginx
Di ogni ambiente è pubblicata **una sola porta**, quella del container `web`, e solo su `127.0.0.1`.
nginx serve l'app e inoltra il resto:

| Percorso | Destinazione |
|---|---|
| `/` | file statici dell'app; i percorsi sconosciuti ricadono su `index.html` (app a pagina singola) |
| `/api/` | container `api` |
| `/realms/`, `/resources/` | Keycloak, solo pagine di login e relativi file |
| `/admin` | **non** inoltrato: la console di amministrazione di Keycloak non è raggiungibile |

Vantaggi:
- **stessa origine** per app, API e login: niente CORS da configurare e un solo indirizzo da conoscere;
- API, Keycloak e database **non sono esposti**: si raggiungono solo dalla rete interna dell'ambiente;
- **emittente dei token unico:** Keycloak conosce il proprio indirizzo pubblico (`KC_HOSTNAME`) e lo
  scrive nei token (`iss`). L'API verifica che l'emittente sia quello, ma scarica le chiavi di firma
  direttamente da `http://keycloak:8080` dentro la rete (`…JWK_SET_URI` in `compose.yaml`).

**Risoluzione dinamica dei nomi.** Normalmente nginx risolve `api` in un indirizzo IP una volta sola,
all'avvio. Quando un nuovo deploy o un rollback ricreano il container `api`, l'IP cambia e nginx
continuerebbe a chiamare quello vecchio. Per questo la configurazione usa un `resolver` (il DNS dei
container, scritto dall'entrypoint dell'immagine) e una variabile in `proxy_pass`: il nome viene
risolto di nuovo al massimo ogni 10 secondi. È verificato: con l'`api` ricreata da 10.89.3.4 a
10.89.3.7, nginx ha continuato a rispondere senza riavvio.

### 6. Ambienti isolati, stesso file Compose
`stack.sh` usa sempre `deploy/compose.yaml`, con un **nome di progetto** diverso (`-p cv-dev`). Per
Compose sono stack indipendenti: container (`cv-dev-api-1`, `cv-prod-api-1`), rete
(`cv-dev_default`) e volume del database (`cv-dev_pgdata`) separati. Un errore in dev non tocca i
dati di prod.

Il `compose.yaml` della radice del repository è un'altra cosa: avvia solo Postgres e Keycloak per
lanciare l'app dall'IDE.

### 7. Segreti fuori dal repository, diversi per ambiente
Nel repository non c'è nessuna password di un ambiente. Al primo avvio `stack.sh` genera password
casuali (`openssl rand`) in `~/.config/cv-generator-deploy/<env>.secrets`, con permessi `600`, e le
passa a Compose. Se mancano, `compose.yaml` si rifiuta di partire (`${VAR:?…}`): non esistono
password di default.

Attenzione: Postgres imposta la password **solo alla prima creazione del volume**. Se si cancella
il file dei segreti, l'ambiente va ricreato con `destroy`.

### 8. Container irrigiditi
Le stesse idee della sandbox dell'agent ([ADR 0006](../docs/adr/0006-sandbox-agent.md)), applicate
all'applicazione:
- utente non root (`10001` per l'API, `101` per nginx);
- filesystem in sola lettura (`read_only`) con `tmpfs` solo dove serve scrivere;
- nessuna capability (`cap_drop: [ALL]`), `no-new-privileges`;
- limiti di memoria; la JVM usa il 75% del limite e termina in caso di memoria esaurita invece di
  restare in uno stato instabile.

### 9. Quando un deploy è "finito"
Ogni servizio ha un **healthcheck**, e i `depends_on` con `service_healthy` impongono l'ordine:
postgres → keycloak → api → web. `stack.sh up` usa `compose up --wait`: ritorna solo quando **tutti** i
container sono sani, oppure fallisce. "Deploy completato" significa quindi "la nuova versione
risponde", non "i container sono partiti". Lo strumento `release` del server MCP si appoggerà a questo.

Per l'API il controllo è `/actuator/health/readiness`, cioè "pronta a ricevere traffico", che è più
dell'essere viva. Le immagini non contengono `curl`: il controllo apre la connessione con
`/dev/tcp` di bash. Va chiamato con `bash -c`, perché `/bin/sh` nell'immagine Ubuntu è dash e
`/dev/tcp` non lo conosce; è stato il primo errore della prova.

### 10. Verificare dall'esterno
`smoke.sh` si comporta come un utente: entry point, app, login con l'emittente giusto, API che
rifiuta senza token, console di amministrazione non pubblicata, versione attesa in esecuzione. Il
test end-to-end di login (`frontend/tests/auth.spec.ts`) gira anche contro un ambiente deployato con
`E2E_BASE_URL`:
```bash
cd frontend && E2E_AUTH=1 E2E_BASE_URL=http://localhost:8280 npx playwright test tests/auth.spec.ts
```
Questo prova il flusso completo: PKCE attraverso nginx, token, API, database.

### 11. In CI: staging effimero
Il job *Release images (build + staging deploy + smoke)* della CI, su ogni PR e su `main`:
1. costruisce le immagini del commit;
2. crea uno staging sul runner;
3. esegue lo smoke test e il login end-to-end;
4. distrugge tutto.

Lo staging non esiste tra un job e l'altro: ogni volta parte da zero, quindi non accumula stato
né derive di configurazione.

## Limiti noti
- **Breve interruzione durante il deploy.** Compose sostituisce il container: per qualche secondo
  l'API non risponde. Servirebbero due copie in parallelo (*blue/green*) o un orchestratore; per
  questo progetto basta.
- **Le migrazioni del database vanno solo avanti.** Flyway applica le migrazioni nuove all'avvio
  della nuova versione. Un rollback riporta il codice, **non** lo schema: le migrazioni devono quindi
  restare compatibili con la versione precedente (prima si aggiunge, si rimuove solo in un rilascio
  successivo, il modello *expand/contract*). Il rollback ne terrà conto.
- **Configurazione dal checkout.** Il realm di Keycloak e lo script di inizializzazione di Postgres
  sono letti dal repository, non dall'immagine. Keycloak importa il realm **solo sul database vuoto**:
  dopo averlo modificato serve `destroy` e poi `up`.
- **Nessun registry.** Le immagini restano nel motore locale (Podman sul PC, Docker sul runner).
  Un registry (per esempio GHCR) servirà per condividerle tra macchine.
- **Keycloak in `start-dev`** anche in prod: va bene per un ambiente simulato, non per uno reale
  (servirebbero HTTPS e `start` con la configurazione di produzione).

## Domande per verificare di aver capito
1. Perché la versione è lo SHA del commit e non un numero scelto a mano?
2. Cosa succede se lancio `build.sh` con una modifica non ancora committata?
3. Perché `local/` non può finire in un'immagine, anche per errore?
4. Perché l'indirizzo di Keycloak nel frontend è relativo?
5. Cosa succederebbe a nginx, senza `resolver`, dopo un rollback?
6. Perché solo il container `web` pubblica una porta?
7. Perché "`up` è terminato" significa che la versione funziona, e non solo che è partita?
8. Perché un rollback non annulla una migrazione del database?

<details>
<summary>Risposte</summary>

1. Collega la versione al codice esatto, è unica e non va gestita a mano; con immagini immutabili
   la stessa versione indica sempre lo stesso contenuto.
2. La modifica non entra: si costruisce dal commit con `git archive`. Lo script avvisa.
3. `git archive` contiene solo file tracciati, e `local/` è ignorata da git.
4. Perché l'immagine sia uguale in tutti gli ambienti: l'indirizzo si completa nel browser con
   l'origine della pagina, e ogni ambiente ha la sua porta.
5. Continuerebbe a inviare le richieste all'IP del container vecchio, che non esiste più: errori
   fino al riavvio di nginx.
6. Un solo punto d'ingresso: API, Keycloak e database restano sulla rete interna; meno superficie
   esposta e nessun CORS.
7. Per `--wait` e gli healthcheck: `up` ritorna con successo solo quando tutti i servizi, nell'ordine
   delle dipendenze, risultano sani.
8. Il rollback rimette le immagini precedenti; lo schema del database resta quello nuovo. Per questo
   le migrazioni devono essere compatibili con la versione precedente.
</details>
