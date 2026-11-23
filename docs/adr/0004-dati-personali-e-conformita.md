# ADR 0004 — Dati personali, privacy e conformità

- **Stato:** accettata
- **Data:** 2026-11-23

## Contesto
L'applicazione tratta dati personali (CV, contatti, eventuale foto) di più utenti.
Si applicano il GDPR (Reg. UE 2016/679) e il D.Lgs. 196/2003 e successive modifiche.
I dati reali usati durante la prototipazione non devono mai finire nel repository.

## Decisione

### Nessun dato reale nel repository
- I dati reali stanno solo in `local/`, cartella esclusa da git.
  Nel repository ci sono soltanto dati di esempio **fittizi**: dominio `example.com` e marcatori `[ESEMPIO]`/`[SAMPLE]`.
- `scripts/check-private-data.sh` funziona sia come hook `pre-commit` (`.githooks/`) sia in CI. Blocca:
  - file sotto `local/` ed export di CV (PDF, DOCX, immagini JPEG, ZIP);
  - email con domini diversi da quelli riservati agli esempi (RFC 2606) e numeri di telefono;
  - in locale, i pattern privati elencati in `local/forbidden-patterns.txt`, un file che non lascia mai la macchina.
- Gli export generati (PDF/DOCX) e gli archivi importati sono esclusi da git.

### Privacy by design (art. 25 GDPR)
- **Nessuna risorsa di terze parti nel frontend:** font serviti dall'app, nessun CDN, analytics o tracker.
  Un test e2e lo verifica. In questo modo si evita il trasferimento dell'IP a terzi
  (cfr. LG München, 3 O 17493/20, sui Google Fonts).
- **Minimizzazione:** le API restituiscono solo i dati necessari (es. `/api/me` non espone l'email).
- **Diritti dell'interessato (pianificati):** export completo dei propri dati (portabilità, art. 20)
  e cancellazione dell'account con tutti i dati (art. 17), entro la prima release usabile da terzi.
- **Import LinkedIn (M4):** solo dall'archivio ZIP che l'utente scarica da LinkedIn con la funzione
  "Ottieni una copia dei tuoi dati". **Nessuno scraping**, vietato dai termini d'uso di LinkedIn.
  L'archivio si elabora e si scarta senza conservarlo.
- **Sicurezza:** porte locali esposte solo su `127.0.0.1`, credenziali di sviluppo sovrascrivibili via `.env`,
  header di sicurezza sulle API ed errori senza stack trace.

### Cosa non copre questo ADR
Le misure tecniche riducono il rischio ma non sostituiscono la valutazione legale.
**Prima di aprire il servizio a utenti terzi** servono:
- un'informativa privacy (artt. 13-14) e condizioni d'uso, verificate da un professionista;
- un registro dei trattamenti, se dovuto;
- la scelta del luogo di hosting (preferibilmente UE) e i relativi accordi (art. 28).

## Conseguenze
- Ogni sviluppatore deve attivare gli hook: `git config core.hooksPath .githooks`.
- Le nuove funzionalità che trattano dati personali aggiornano questo ADR o ne creano uno nuovo.
