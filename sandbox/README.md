# Sandbox per i job degli agent

I job degli agent (Claude Code) girano in un container isolato, con una policy di rete a
allowlist, token GitHub di un'ora limitati a questo repository e nessuna credenziale di
produzione. Le motivazioni sono in [ADR 0006](../docs/adr/0006-sandbox-agent.md).

```
 host (Windows, Git Bash)                        macchina Podman cv-generator
 ─────────────────────────                       ─────────────────────────────────────────────
 github-app-token.sh ──token 1h──┐               rete cv-sandbox-internal (--internal, senza uscita)
 (chiave privata: resta qui)     │               ┌──────────────────────┐    ┌──────────────────┐
                                 └─ agent.sh ──▶ │ cv-sandbox-agent     │──▶ │ cv-sandbox-proxy │──▶ allowlist:
                                                 │ non root, read-only  │    │ tinyproxy        │    api.anthropic.com
                                                 │ /workspace = clone   │    │ default deny     │    github.com, api.github.com
                                                 │ da GitHub (volume)   │    └──────────────────┘    registry.npmjs.org
                                                 └──────────────────────┘     rete cv-sandbox-egress  repo.maven.apache.org
```

| File | Ruolo |
|---|---|
| `agent.sh` | Avvia proxy e container dell'agent; genera il token GitHub |
| `entry.sh` | Punto di ingresso del container: clona il repository da GitHub nel workspace |
| `Containerfile` | Immagine dell'agent: Java 25, Node 22, git, gh, Claude Code |
| `proxy/` | Proxy di egress con allowlist |
| `github-app-token.sh` | Token GitHub App di 1 ora, solo per questo repository (gira sull'host) |
| `selftest.sh` | Autotest eseguito dentro il container |
| `check-agent-workflow.sh` | Controllo della policy del workflow `agent.yml` (in CI) |

## Requisiti
- Podman ≥ 6.2 con la macchina `cv-generator` come connessione di default
  (le versioni precedenti su WSL non avviano i container: `controller 'pids' is not available`).
- Git Bash, che c'è già con Git per Windows.

## Uso
```
bash sandbox/agent.sh [opzioni] -- <comando> [argomenti...]
```
Tutto quello che segue `--` è il comando eseguito nel container, **esattamente come è scritto**: lo
script non aggiunge niente. Prima di `--` vanno solo le opzioni dello script.

```bash
bash sandbox/agent.sh --selftest --no-github                   # verifica isolamento e rete, senza token
bash sandbox/agent.sh --selftest                               # verifica anche il token GitHub
bash sandbox/agent.sh -- claude                                # sessione interattiva di Claude Code
bash sandbox/agent.sh -- claude -p "compito" --max-turns 20    # job non interattivo
bash sandbox/agent.sh --ref <branch> -- claude -p "compito"    # parte da un branch diverso da main
bash sandbox/agent.sh -- bash                                  # shell nella sandbox
bash sandbox/agent.sh --no-github -- bash -c "ls -la"          # un comando, senza token GitHub
bash sandbox/agent.sh --build -- ...                           # ricostruisce prima le immagini
```

| Opzione | Effetto |
|---|---|
| `--ref <branch>` | Branch da cui parte il clone (default `main`) |
| `--no-github` | Nessun token GitHub; il repository del PC è montato in sola lettura |
| `--build` | Ricostruisce le immagini prima di partire |
| `--selftest` | Esegue l'autotest al posto di un comando |

Il token di Claude entra nel container solo quando il comando è `claude`: una shell o l'autotest non
lo ricevono.
Senza `--no-github` lo script genera un token nuovo a ogni avvio e il container **clona il repository
da GitHub** in un volume che viene eliminato a fine esecuzione. Il repository del PC non viene montato:
l'agent non può toccarne `.git`, gli hook, i branch o `local/`, e i suoi commit sono a nome del bot
della GitHub App. L'agent vede solo ciò che è già su GitHub. Con `--no-github` il repository del PC è
montato in sola lettura, con `local/` nascosta.

## Configurazione (una volta)

### 1. GitHub App per i token brevi
1. Su GitHub: **Settings → Developer settings → GitHub Apps → New GitHub App**.
   - Nome: per esempio `cv-generator-agent-<suffisso>` (deve essere unico su GitHub).
   - Homepage URL: l'URL del repository.
   - **Webhook:** disattivato (togliere la spunta *Active*).
   - **Repository permissions:** *Contents* → Read and write, *Pull requests* → Read and write.
     Nient'altro (*Metadata* in lettura viene aggiunto da GitHub).
   - *Where can this GitHub App be installed?* → **Only on this account**.
2. Dopo la creazione annotare il **Client ID** (`Iv23…`) e generare una **private key** (`.pem`).
3. **Install App** → *Only select repositories* → `cv-generator`.
4. Salvare la chiave **fuori dal repository**, per esempio in `~/.config/cv-generator-agent/agent-app.pem`,
   e creare `~/.config/cv-generator-agent/app.env`:
   ```bash
   CV_AGENT_APP_CLIENT_ID=Iv23...
   CV_AGENT_APP_KEY="$HOME/.config/cv-generator-agent/agent-app.pem"
   ```
5. Prova: `bash sandbox/agent.sh --selftest`. La sezione 3 deve mostrare il token limitato a un
   repository e la sua scadenza.

### 2. Token di Claude Code
`claude setup-token`, lanciato **sull'host** in un terminale separato (non nel container e non
con `!` dentro Claude Code, altrimenti il token finisce nella conversazione), genera il token
dell'abbonamento. `agent.sh` lo prende, in quest'ordine:
1. dalla variabile `CLAUDE_CODE_OAUTH_TOKEN`, se è impostata;
2. dal file `~/.config/cv-generator-agent/claude-token` (un'unica riga con il solo token), fuori dal
   repository. Per crearlo senza che il token compaia a schermo:
   ```bash
   read -rs t && printf '%s' "$t" > ~/.config/cv-generator-agent/claude-token && unset t
   ```

Il file non è cifrato e su Windows è leggibile da ogni programma dell'utente: è lo stesso livello di
rischio della chiave della GitHub App. Il token è a lunga durata: in caso di fuga va revocato subito.

### 3. CI (workflow *Agent (sandboxed)*)
Nel repository GitHub:
- variabile `CV_AGENT_APP_CLIENT_ID`;
- segreti `CV_AGENT_APP_PRIVATE_KEY` (contenuto del `.pem`) e `CLAUDE_CODE_OAUTH_TOKEN`.

Il workflow si avvia a mano da *Actions → Agent (sandboxed) → Run workflow* con il testo del compito.
L'agent lavora su un branch `agent/run-<id>` e apre una pull request; il merge resta manuale.

## Limiti noti
- Nella sandbox i test d'integrazione con Testcontainers vengono saltati: l'agent non ha accesso al
  motore dei container, di proposito. Girano in CI.
- Il token dell'App ha `contents: write`, ma non può scrivere su `main`: il ruleset del repository
  accetta solo pull request con la CI verde, senza eccezioni (vedi ADR 0006, Conseguenze).
