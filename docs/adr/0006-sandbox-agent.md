# ADR 0006 — Sandbox per i job degli agent

- **Stato:** accettata
- **Data:** 2026-11-24

## Contesto
Parte del lavoro sul progetto è svolto da agent (Claude Code), in locale e in CI.
Un agent esegue comandi arbitrari, quindi va trattato come codice non fidato. Il requisito è:

> L'esecuzione avviene in un ambiente isolato (sandbox). I job degli agent vengono eseguiti
> all'interno di container, soggetti a una policy di rete e dotati di token a breve durata e con
> ambito limitato; per impostazione predefinita, non dispongono di credenziali per l'ambiente di produzione.

Vincoli del progetto che restano validi:
- i dati personali in `local/` non devono mai uscire dalla macchina (ADR 0004);
- tutto gratuito e open source;
- nessuna risorsa aziendale (CA, credenziali, configurazioni).

## Decisione

### 1. Container isolato
- In locale si usa Podman 6.2 sulla macchina dedicata `cv-generator` (WSL), creata **senza** importare
  la CA aziendale. In CI si usa Docker sul runner effimero. Lo stesso script `sandbox/agent.sh`
  funziona con entrambi (`CONTAINER_ENGINE`).
- L'immagine `sandbox/Containerfile` contiene solo la toolchain (Java 25, Node 22, git, gh) e
  Claude Code in versione fissata. Il binario `gh` è verificato contro i checksum ufficiali.
  Nell'immagine non ci sono credenziali.
- Opzioni del container:
  - utente non root, nessuna capability (`--cap-drop ALL`), `no-new-privileges`;
  - filesystem radice in sola lettura, home e `/tmp` su tmpfs;
  - limiti di memoria, CPU e processi;
  - nessun socket del motore dei container (l'agent non può avviare altri container).
- **Il repository dell'host non viene montato.** Il container clona il repository da GitHub, con il
  token breve, in un volume anonimo che viene eliminato insieme al container (`--ref` sceglie il
  branch, di default `main`). La cartella `local/` è ignorata da git, quindi nel clone non esiste.
- Solo senza token GitHub (`--no-github`, usato per l'autotest in CI) si monta il repository dell'host,
  **in sola lettura**, con `local/` coperta da un tmpfs vuoto in sola lettura. Con Podman serve
  l'opzione `notmpcopyup`: senza, Podman copia nel tmpfs il contenuto della cartella coperta, cioè
  proprio i dati personali.
- Non si montano `~/.ssh`, `~/.gitconfig`, le credenziali di gh, di Claude o del motore dei container.
- I commit dell'agent hanno l'identità del bot della GitHub App (`<app>[bot]`), in locale come in CI.

#### Perché il clone: cosa ha mostrato la prima prova reale
Nella prima versione il repository dell'host era montato in lettura e scrittura. La prima prova con
token reali ha funzionato, ma ha mostrato tre problemi che l'autotest non controllava:
1. l'agent aveva cambiato il branch della copia di lavoro dell'utente e scritto in `.git/config`.
   Con un repository montato in scrittura un agent manipolato può modificare gli hook
   (`core.hooksPath=.githooks`) o la configurazione git, che poi **vengono eseguiti sull'host** al
   commit successivo dell'utente: un'uscita dalla sandbox;
2. i commit risultavano dell'utente, perché valeva la configurazione git del repository montato;
3. l'agent partiva dal branch su cui si trovava l'utente, non da `main`.

Il clone in un volume usa e getta risolve i tre problemi e rende superflua la copertura di `local/`.

### 2. Policy di rete
- L'agent è collegato solo alla rete `cv-sandbox-internal`, creata con `--internal`: non ha nessuna
  route verso l'esterno, né per nome né per IP.
- L'unica uscita è il proxy `cv-sandbox-proxy` (tinyproxy), collegato alla rete interna e a quella
  esterna. Il proxy applica una **allowlist con negazione di default** (`FilterDefaultDeny`) e apre
  tunnel TLS solo verso la porta 443:
  - `api.anthropic.com` (Claude Code);
  - `github.com`, `api.github.com` (git e pull request);
  - `registry.npmjs.org`, `repo.maven.apache.org` (dipendenze).
- L'isolamento non dipende dalla collaborazione del codice: un programma che ignora `HTTPS_PROXY`
  semplicemente non esce.
- Telemetria, error reporting e aggiornamenti automatici di Claude Code sono disattivati.

### 3. Token a breve durata e con ambito limitato
- **GitHub:** token di installazione di una GitHub App privata (`cv-generator-agent`).
  - Scade dopo **1 ora** (lo impone GitHub).
  - Vale solo per `Nemios11/cv-generator` e ha solo `contents: write` e `pull_requests: write`.
    Non ha `workflows`, quindi l'agent non può modificare `.github/workflows`, né amministrazione o segreti.
  - In locale lo genera `sandbox/github-app-token.sh` **sull'host**. La chiave privata dell'App
    resta fuori dal repository (`~/.config/cv-generator-agent/`) e non entra mai nel container.
  - In CI lo genera `actions/create-github-app-token`, che lo revoca a fine job.
- Il `GITHUB_TOKEN` del workflow dell'agent è in sola lettura (`contents: read`) e le credenziali
  git non restano nel checkout (`persist-credentials: false`).
- **Claude:** il token dell'abbonamento (`CLAUDE_CODE_OAUTH_TOKEN`, da `claude setup-token`).
  È una credenziale a **lunga durata**: è un'eccezione accettata (vedi Conseguenze).

### 4. Nessuna credenziale di produzione per impostazione predefinita
- Oggi non esiste un ambiente di produzione. La regola vale già: eventuali segreti di produzione
  andranno in un GitHub Environment protetto, con approvazione manuale.
- Il workflow `agent.yml`:
  - non dichiara nessun `environment`, quindi quei segreti non gli sono mai accessibili;
  - parte solo a mano (`workflow_dispatch`), mai da eventi esterni come `pull_request_target`;
  - ha permessi di default vuoti (`permissions: {}`).
- Nel container entrano solo le variabili passate esplicitamente da `agent.sh`: il token GitHub e
  la sua scadenza, l'identità dei commit e il token di Claude, quest'ultimo solo quando il comando
  eseguito è `claude`.

### Verifiche automatiche
| Parte del requisito | Verifica |
|---|---|
| Container isolato | `sandbox/selftest.sh` §1: utente non root, nessuna capability, `no-new-privileges`, radice in sola lettura, nessun socket; con token: workspace clonato senza mount dell'host, `local/` assente, commit a nome del bot; senza token: repository in sola lettura, `local/` vuota |
| Policy di rete | §2: uscita diretta bloccata per nome e per IP; host in allowlist raggiungibile; `example.com` rifiutato; porte diverse da 443 rifiutate |
| Token brevi e limitati | §3: il token GitHub è d'installazione, vale per un solo repository, scade entro 60 minuti e non agisce come utente |
| Niente produzione | §4: nessuna variabile di produzione o cloud, nessun file di credenziali; `sandbox/check-agent-workflow.sh` controlla permessi, segreti ed environment del workflow |

Il job `sandbox` della CI esegue su ogni pull request il controllo del workflow e l'autotest senza
token. Il workflow `agent.yml` esegue l'autotest con il token reale prima di ogni job dell'agent.

## Alternative considerate
- **Sandbox integrata di Claude Code** (`/sandbox`): non è disponibile su Windows nativo e isola il
  solo processo di Claude, non l'intero job. Resta utilizzabile dentro il container come ulteriore livello.
- **Firewall iptables nel container** (come il devcontainer di riferimento di Anthropic): richiede
  `NET_ADMIN` nel container dell'agent. Il proxy su rete interna non richiede capability.
- **PAT fine-grained**: dura almeno un giorno e va rinnovato a mano. La GitHub App dà token di un'ora
  generati al momento.
- **Montare il socket di Podman** per i test d'integrazione: darebbe all'agent il controllo del
  motore dei container, cioè un'uscita dalla sandbox. I test Testcontainers quindi si saltano nella
  sandbox e girano in CI.

## Conseguenze
- `CLAUDE_CODE_OAUTH_TOKEN` dura a lungo. Mitigazioni:
  - entra solo nel container dell'agent e nel segreto del workflow `agent.yml`;
  - il proxy permette di raggiungere solo `api.anthropic.com` e GitHub;
  - va revocato subito in caso di fuga.

  Il proxy filtra l'**host**, non l'account né il contenuto del tunnel TLS. Un agent manipolato
  (prompt injection) potrebbe quindi usare credenziali dell'attaccante, scritte nelle istruzioni
  malevole, per inviare dati verso un host consentito, per esempio un repository dell'attaccante
  su `api.github.com` o un pacchetto su `registry.npmjs.org`. L'allowlist riduce i canali di
  esfiltrazione ma non li chiude: per chiuderli servirebbe un proxy che ispeziona il TLS. Per
  questo la difesa principale resta tenere nel container il minor numero possibile di segreti,
  tutti a ambito limitato.

  Per arrivare a credenziali brevi anche qui servirebbe una chiave API con `apiKeyHelper` o un gateway.
- Con un repository privato su piano GitHub Free le regole di protezione dei branch non sono
  disponibili. Quindi `contents: write` permetterebbe un push su `main`. Le istruzioni del job lo
  vietano e ogni modifica passa da una pull request, ma il vincolo tecnico richiederebbe GitHub Pro
  o un repository pubblico.
- L'agent vede solo ciò che è già su GitHub: le modifiche locali non ancora pushate non gli arrivano.
  È lo stesso modello della CI.
- Le cache di Maven e npm (artefatti pubblici) persistono in volumi Podman in locale; in CI no.
- Nella sandbox i test d'integrazione con Testcontainers vengono saltati.
- Aggiungere un host all'allowlist è una modifica di `sandbox/proxy/allowlist`, revisionata in una pull request.
