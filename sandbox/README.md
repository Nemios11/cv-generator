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
                                                 │ /workspace = repo    │    │ default deny     │    github.com, api.github.com
                                                 │ local/ = vuota       │    └──────────────────┘    registry.npmjs.org
                                                 └──────────────────────┘     rete cv-sandbox-egress  repo.maven.apache.org
```

| File | Ruolo |
|---|---|
| `agent.sh` | Avvia proxy e container dell'agent; genera il token GitHub |
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
```bash
bash sandbox/agent.sh --selftest --no-github   # verifica isolamento e rete, senza token
bash sandbox/agent.sh --selftest               # verifica anche il token GitHub
bash sandbox/agent.sh                          # sessione interattiva di Claude Code
bash sandbox/agent.sh -- -p "compito"          # job non interattivo
bash sandbox/agent.sh --shell                  # shell nella sandbox
bash sandbox/agent.sh --build ...              # ricostruisce le immagini
```
Senza `--no-github` lo script genera un token nuovo a ogni avvio.

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
`claude setup-token` genera il token dell'abbonamento. Va impostato solo nella sessione del terminale
(`export CLAUDE_CODE_OAUTH_TOKEN=...`), mai in un file del repository.
È a lunga durata: in caso di fuga va revocato subito.

### 3. CI (workflow *Agent (sandboxed)*)
Nel repository GitHub:
- variabile `CV_AGENT_APP_CLIENT_ID`;
- segreti `CV_AGENT_APP_PRIVATE_KEY` (contenuto del `.pem`) e `CLAUDE_CODE_OAUTH_TOKEN`.

Il workflow si avvia a mano da *Actions → Agent (sandboxed) → Run workflow* con il testo del compito.
L'agent lavora su un branch `agent/run-<id>` e apre una pull request; il merge resta manuale.

## Limiti noti
- Nella sandbox i test d'integrazione con Testcontainers vengono saltati: l'agent non ha accesso al
  motore dei container, di proposito. Girano in CI.
- Repository privato su GitHub Free: niente protezione dei branch, quindi il divieto di push su `main`
  è solo nelle istruzioni del job (vedi ADR 0006).
