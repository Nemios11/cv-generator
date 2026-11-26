# Server MCP di deploy

Il requisito del corso:

> Expose deployment through MCP. Deploy, status, and rollback become tools, scoped per environment,
> so the agent's deployment powers are an allowlist rather than a shell script with credentials.

Questa cartella lo realizza: per ogni ambiente c'è un server MCP (`deploy-dev`, `deploy-staging`,
`deploy-prod`) che offre all'agent **tre strumenti e niente altro**. Le decisioni sono nell'
[ADR 0007](../../docs/adr/0007-rilasci-e-ambienti.md), gli ambienti in [../README.md](../README.md).

## Cos'è MCP, in breve
**MCP** (*Model Context Protocol*) è un protocollo aperto con cui un'applicazione AI (il *client*,
qui Claude Code) usa funzioni offerte da un altro programma (il *server*).
- Il server dichiara i suoi **strumenti** (*tools*): ciascuno ha un nome, una descrizione e lo
  **schema JSON** degli argomenti.
- Il modello sceglie quale strumento chiamare; il client invia la chiamata al server e il server
  restituisce il risultato.
- I messaggi sono JSON-RPC. Qui il trasporto è **Streamable HTTP**: una `POST /mcp` per ogni messaggio.

Il punto importante per la sicurezza: l'agent **non esegue** niente. Chiede al server di eseguire
un'operazione tra quelle previste, con argomenti che il server controlla.

## Allowlist contro "shell con credenziali"

| | Shell script con credenziali | Server MCP |
|---|---|---|
| Cosa ha l'agent | il socket del motore o un token cloud, e una shell | un URL e un token bearer |
| Cosa può fare | tutto ciò che la credenziale permette: cancellare volumi, avviare container qualsiasi, leggere segreti | `release`, `status`, `rollback` di **un** ambiente |
| Argomenti | qualunque stringa, anche `; rm -rf /` | validati: la versione deve essere 12 caratteri esadecimali |
| Dove sta la credenziale vera | nel container dell'agent | nel container del server, che l'agent non può aprire |
| Traccia | quella che lo script decide di scrivere | ogni chiamata nel log JSON e nella cronologia del rilascio |

Il modello resta lo stesso dell'[ADR 0006](../../docs/adr/0006-sandbox-agent.md): l'agent è codice non
fidato, quindi gli si dà il potere minimo e lo si esercita per suo conto in un posto che controlliamo.

## Come è fatto

```
 sandbox (rete cv-sandbox-internal, nessuna uscita)
 ┌──────────────────────────┐   POST /mcp + Bearer       ┌──────────────────────────────┐
 │ cv-sandbox-agent         │ ─────────────────────────▶ │ cv-deploy-dev  (deploy-dev)  │
 │ Claude Code              │   solo con --deploy dev    │ node src/server.ts           │
 │ .mcp.json:               │                            │  ├ release / status /        │
 │  deploy-dev     ─────────┤                            │  │ rollback                  │
 │  deploy-staging (no token)│ ──▶ 401                   │  └ execFile bash stack.sh dev│
 │  deploy-prod    (no token)│ ──▶ 401                   │ /state/dev  (solo dev)       │
 └──────────────────────────┘                            │ /var/run/docker.sock ◀───────┼── il vero potere
                                                         └──────────────┬───────────────┘
                                                                        │ Docker API (Podman in locale)
                                                                        ▼
                                                     ambiente cv-dev (api, web, keycloak, postgres, synthetic)
```

| File | Ruolo |
|---|---|
| `src/server.ts` | Il server: autenticazione, i tre strumenti, log di audit |
| `src/gate.ts` | Controllo del rilascio in produzione lato server (oggi rifiuta sempre, vedi sotto) |
| `src/client.ts` | Client minimo per chiamare un server a mano, come fa l'agent |
| `Containerfile` | Immagine: Node, gli script di deploy, CLI Docker e Compose verificati con checksum |
| `start.sh` | Avvia o ferma il server di un ambiente |
| `../../.mcp.json` | Dove Claude Code trova i tre server |

### Gli strumenti

| Strumento | Input | Output | Note |
|---|---|---|---|
| `release` | `version` (+ `ticket` in prod) | ID del rilascio, quando tutti i servizi sono sani | in prod serve un ticket autorizzato |
| `status` | nessuno | versione attiva e tasso di 5xx per endpoint, `breached` se uno supera l'1% | aspetta la fine della finestra di osservazione |
| `rollback` | nessuno | la versione di nuovo attiva | sempre consentito, anche in prod |

Ogni strumento esegue `deploy/stack.sh <env> …`, lo stesso comando che userebbe una persona. Quindi
il rollback dell'agent e il rollback a mano sono **lo stesso percorso**, ed è quello che si prova.

## I concetti, uno per uno

### 1. Un server per ambiente, un token per server
L'ambiente non è un argomento degli strumenti: è fissato all'avvio del server (`DEPLOY_ENV`). Per
dare all'agent il potere su staging gli si dà il token di `deploy-staging`, e solo quello. Il token
di produzione può restare fuori dal container.

`.mcp.json` dichiara sempre tutti e tre i server, con l'intestazione
`Authorization: Bearer ${DEPLOY_DEV_TOKEN:-}`. Claude Code sostituisce la variabile d'ambiente al
momento della connessione. `sandbox/agent.sh --deploy dev` passa nel container solo
`DEPLOY_DEV_TOKEN`; gli altri server rispondono `401`. Il `:-` evita un errore se la variabile manca:
la connessione semplicemente non viene autorizzata.

Il token (`openssl rand -hex 32`) è generato da `start.sh` al primo avvio in
`~/.config/cv-generator-deploy/<env>/mcp-token`. Il server lo confronta in **tempo costante**
(`timingSafeEqual` sugli hash), così il tempo di risposta non rivela quanti caratteri sono giusti.
Senza token il server non parte (*fail closed*).

### 2. Il potere vero resta nel server
Per fare un deploy serve parlare con il motore dei container. Il server ha il suo **socket**
(`/var/run/docker.sock`, che in locale è il socket di Podman). Chi ha il socket controlla tutti i
container: è la credenziale più potente del sistema. Per questo:
- il socket è montato **solo** nel container del server, mai in quello dell'agent (l'autotest della
  sandbox lo verifica: *no container engine socket*);
- il server è codice nostro, piccolo e revisionato, non un agent;
- il container del server è irrigidito come gli altri: sola lettura, nessuna capability, memoria
  limitata, nessuna uscita verso Internet (è solo sulla rete interna).

### 3. Argomenti validati, nessuna shell
`version` deve corrispondere a `^[0-9a-f]{12}$`, e lo controllano sia lo schema MCP sia `stack.sh`.
Il server avvia `bash stack.sh <env> release <version>` con `execFile`, cioè con gli argomenti in
una lista: nessuna shell li interpreta. Nella prova, la chiamata
`release {"version": "; rm -rf /"}` è stata rifiutata dallo schema prima di arrivare a qualunque
comando:
```
MCP error -32602: Input validation error: ... a version is the 12-character commit SHA
```

### 4. Un rilascio è finito quando funziona
`release` ritorna solo quando `compose up --wait` ha visto **tutti** i servizi sani. Se la nuova
versione non diventa sana, lo strumento restituisce un errore con le ultime righe del log, e la
cronologia registra `failed`. In quel caso la mossa giusta è `rollback`.

Rilasci e rollback di uno stesso ambiente sono **serializzati**: una coda nel server e un lock in
`stack.sh` (`mkdir`, che è atomico) impediscono due deploy contemporanei.

### 5. `status`: misurare dopo, non subito
Subito dopo un rilascio non si sa ancora niente: servono richieste reali. `status` quindi **aspetta**
che sia passata la finestra di osservazione dall'ultimo rilascio (`STATUS_WINDOW_SECONDS`, 300
secondi come nel corso; nelle prove 15–20 s), poi calcola per ogni endpoint:

```
tasso 5xx = risposte con stato 5xx / risposte totali
```

e segnala `breached: true` se un endpoint supera l'1% (`CV_STATUS_MAX_5XX_RATE`).

Da dove vengono i numeri:
- **Metriche dell'applicazione.** Spring Boot conta ogni richiesta nella metrica
  `http_server_requests_seconds_count`, con le etichette `uri` (il modello dell'endpoint, per esempio
  `/api/profile`) e `status`. Con `micrometer-registry-prometheus` le pubblica in formato Prometheus
  su `/actuator/prometheus`.
- **Porta di management separata.** Negli ambienti deployati l'actuator sta sulla porta `8081`
  (`MANAGEMENT_SERVER_PORT`), che non è pubblicata e non passa da nginx: dal browser non si vede.
  `stack.sh status` la legge con un `wget` eseguito *dentro* il container `web`, che è sulla stessa
  rete. Spring Security si applica anche a quella porta: una catena dedicata (`SecurityConfig`,
  `@Order(0)`) la lascia aperta solo per le richieste arrivate lì
  (test: `ManagementPortIntegrationTest`).
- **I contatori partono con la versione.** Un rilascio ricrea il container `api`, quindi i contatori
  ripartono da zero: misurano esattamente la versione attiva.
- **Traffico sintetico.** Un ambiente che nessuno usa non produce richieste, e senza richieste non c'è
  tasso. Il servizio `synthetic` di `compose.yaml` ottiene un token con il client di servizio
  `cv-synthetic` (*client credentials*) e ogni 2 secondi chiama `/api/me` e `/api/profile`
  **attraverso l'entry point**, come un utente. È un *synthetic monitor*: fa solo letture.

Esempio reale, dopo il rilascio di una versione difettosa:
```json
{"environment":"dev","version":"5a97f0e0d10f","windowSeconds":20,"max5xxRate":0.01,"breached":true,
 "endpoints":[{"endpoint":"/api/me","requests":10,"errors5xx":10,"rate5xx":1.0000},
              {"endpoint":"/api/profile","requests":10,"errors5xx":0,"rate5xx":0.0000}]}
```

### 6. `rollback`: tornare all'ultima versione buona
Il rollback non prende argomenti, perché la decisione "dove tornare" non va lasciata all'agent. La
regola, in `stack.sh`:
> la voce più recente della cronologia con esito `ok`, la cui versione non è quella attiva e non è
> già stata oggetto di un rollback.

La seconda condizione evita il ping-pong: senza di essa, due rollback di fila tornerebbero alla
versione difettosa. Cronologia reale della prova:

| tipo | versione | precedente | esito | chi |
|---|---|---|---|---|
| release | 9b1590d51826 | - | ok | operator |
| release | 5a97f0e0d10f | 9b1590d51826 | ok | operator |
| rollback | 9b1590d51826 | 5a97f0e0d10f | ok | operator |
| release | 5a97f0e0d10f | 9b1590d51826 | ok | agent via deploy-dev |
| rollback | 9b1590d51826 | 5a97f0e0d10f | ok | agent via deploy-dev |

Le prime tre righe sono comandi dati a mano, le ultime due chiamate MCP: stesso percorso, stessa
cronologia. La colonna "chi" registra chi ha agito.

### 7. Produzione: chiusa finché il controllo non esiste
In `deploy-prod` lo strumento `release` richiede anche `ticket`, il numero della issue *change*.
Il server lo verifica **lui stesso** (`src/gate.ts`), indipendentemente da eventuali hook del client:
un client modificato o senza hook non aggira il controllo. La verifica dell'autorizzazione del
release manager (etichetta `release-approved`) arriva nella parte 3. Fino ad allora il controllo
**rifiuta sempre** (*fail closed*): se non si può dimostrare che un rilascio in produzione è
autorizzato, non si fa. Prova:
```json
{"released": false, "reason": "ticket #7 for 9b1590d51826 cannot be verified yet: the production gate is not configured, so production releases are refused (fail closed)"}
```
`status` e `rollback` non passano dal controllo: in produzione tornare indietro deve essere sempre
possibile, ed è il motivo per cui il rollback è l'azione più provata.

### 8. Audit
Ogni chiamata produce una riga JSON nel log del server (`podman logs cv-deploy-dev`): richiesta,
esito, versione, ticket, motivo di un rifiuto, tentativi senza token valido (`auth.rejected`).
Insieme alla cronologia di `stack.sh` permette di ricostruire chi ha rilasciato cosa e quando.

## Uso

```bash
bash deploy/mcp/start.sh dev --build       # avvia deploy-dev (la prima volta genera il token)
bash deploy/mcp/start.sh dev --stop        # lo ferma

cd deploy/mcp && npm ci                    # una volta, per il client
node src/client.ts dev tools               # strumenti e schemi, come li vede l'agent
node src/client.ts dev status
node src/client.ts dev release '{"version":"9b1590d51826"}'
node src/client.ts dev rollback

# l'agent nella sandbox, con il potere di deploy solo su dev
bash sandbox/agent.sh --deploy dev -- claude -p "Rilascia <versione> in dev e verifica lo stato"
```
Dal PC il server è raggiungibile anche su `127.0.0.1:9280` (dev), `9380` (staging), `9480` (prod),
ma solo con Podman: Docker non pubblica le porte di un container collegato solo a una rete interna.

Claude Code nella sandbox chiede l'approvazione dei server di `.mcp.json` la prima volta, in modalità
interattiva. Nei job automatici l'approvazione e i permessi sugli strumenti si fissano con le
impostazioni gestite: è la parte 3.

## Limiti noti
- **Il socket non è limitato a un ambiente.** In locale tutti e tre gli ambienti sono sullo stesso
  motore: il server di dev, tecnicamente, potrebbe toccare i container di prod. Il limite per
  l'agent è nel server (l'ambiente è fisso e gli strumenti sono tre); in un sistema reale ogni
  ambiente avrebbe un cluster o un account suo, con credenziali valide solo lì.
- **Token a lunga durata.** Il token di un server si ruota cancellando `mcp-token` e riavviando il
  server. Token brevi (per esempio firmati per un solo job) sarebbero il passo successivo.
- **Chiamate lunghe.** `status` può durare più di 5 minuti. Il client deve accettare chiamate lunghe
  (il nostro client usa 30 minuti di timeout).
- **Lock orfano.** Se `stack.sh` viene ucciso con `kill -9`, la cartella `lock` resta e blocca il
  rilascio successivo, con un messaggio che dice quale cartella rimuovere.

## Domande per verificare di aver capito
1. Perché l'ambiente non è un argomento degli strumenti?
2. Cosa ottiene l'agent se legge tutte le variabili d'ambiente del suo container dopo
   `agent.sh --deploy dev`?
3. Perché il server usa `execFile` e non `exec`?
4. Perché `status` aspetta, invece di rispondere subito?
5. Perché serve il traffico sintetico?
6. Perché il rollback non prende la versione come argomento?
7. Cosa impedisce a due rollback di fila di tornare alla versione difettosa?
8. Perché il controllo della produzione sta anche nel server, oltre che nell'hook del client?
9. Cosa succede oggi a un `release` in prod, e perché è il comportamento giusto?
10. Qual è la credenziale più pericolosa di tutto il sistema, e dove si trova?

<details>
<summary>Risposte</summary>

1. Perché il potere sia dato per ambiente con il token: chi ha solo il token di dev non può
   chiedere un rilascio in prod, perché nessuno strumento di `deploy-dev` sa farlo.
2. Il token di `deploy-dev` e basta: niente socket, nessuna password degli ambienti, nessun token di
   staging o prod. Con quel token può chiamare tre strumenti su dev.
3. `exec` passa la riga di comando a una shell, che interpreterebbe `;`, `$( )` e simili.
   `execFile` passa gli argomenti come lista al programma, senza shell. In più la versione è
   validata prima.
4. Gli errori di una versione si vedono solo dopo che ha servito richieste. Misurare subito darebbe
   zero errori su zero richieste, cioè un falso "tutto bene".
5. Senza richieste non c'è un tasso da misurare. Il traffico sintetico rende `status` significativo
   anche in un ambiente senza utenti, come staging in CI.
6. La scelta della versione a cui tornare è una regola fissa e provata, non un'improvvisazione:
   l'agent decide solo *se* tornare indietro, non *dove*.
7. La regola esclude le versioni già oggetto di un rollback.
8. L'hook gira nel client, che è nell'ambiente dell'agent. Il server è l'ultimo punto prima
   dell'azione e non dipende dalla configurazione del client.
9. Viene rifiutato, perché l'autorizzazione non si può ancora verificare. Nel dubbio la produzione
   resta chiusa (*fail closed*), mentre rollback e stato restano disponibili.
10. Il socket del motore dei container: si trova solo nei container dei server MCP.
</details>
