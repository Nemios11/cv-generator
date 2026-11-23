# Definitive prompt for Cursor Agent

Agisci come un team composto da: Senior Frontend Engineer, Product Designer specializzato in personal branding tech, CV strategist per il mercato internazionale e technical recruiter con esperienza nella selezione di Senior Software Developer.

## Obiettivo
Completa e porta a qualità professionale il progetto `Developer CV Generator`: un generatore bilingue (italiano/inglese) del mio curriculum e portfolio professionale, con due presentazioni sincronizzate:
1. **Developer Portfolio CV**: distintivo, curato e adatto a recruiter e hiring manager.
2. **ATS CV**: lineare, semanticamente corretto, a una colonna e facilmente parsabile dai sistemi di selezione.

Il posizionamento è **Senior Software Developer** e il target sono aziende internazionali. Il tono deve comunicare seniority attraverso ownership, decisioni tecniche, affidabilità, collaborazione, mentoring, architettura e risultati dimostrabili. Non attribuirmi automaticamente competenze, seniority, risultati, certificazioni, numeri o responsabilità non presenti nei dati forniti.

## Prima fase: audit e piano
- Esamina l'intero repository prima di modificare file.
- Identifica problemi, dipendenze, lacune e rischi.
- Presenta un piano breve e implementabile.
- Mantieni lo stack esistente: React, TypeScript, Vite, Tailwind CSS, i18next, Lucide React e Playwright, salvo motivazione tecnica concreta.

## Design direction
Crea un'estetica Developer Portfolio premium: editoriale, contemporanea, sobria, con gerarchia tipografica forte, spazio bianco, palette avorio/bianco, navy/antracite e un solo accento blu elettrico. Evita dashboard look, barre di skill con percentuali arbitrarie, progress bar, grafici decorativi, icone ridondanti, animazioni inutili e layout sovraccarichi. Deve sembrare il portfolio di un professionista esperto, non un template generico.

Il risultato deve essere responsive, accessibile, veloce e leggibile. Usa HTML semantico, contrasto adeguato, focus visibile, navigazione da tastiera e testi alternativi dove necessari.

## Contenuti e strategia editoriale
Prevedi sezioni configurabili:
- Header con nome, titolo professionale, località, email e link professionali.
- Professional Summary breve e specifico.
- Core Competencies / Engineering Strengths.
- Professional Experience in ordine cronologico inverso.
- Selected Projects con contesto, ruolo personale, scelte tecniche e risultati.
- Technical Skills raggruppate per categorie, senza autovalutazioni numeriche.
- Education e Certifications.
- Languages.
- Optional: Open Source, Talks, Publications, Leadership e Mentoring.

Per ogni esperienza, favorisci bullet che esprimano: azione + contesto/problema + contributo/decisione + risultato. Chiedi dati mancanti invece di inventarli. Distingui chiaramente responsabilità individuali e risultati del team. Evita buzzword non supportate, frasi generiche e ripetizioni.

## Multilingua
- Italiano e inglese devono essere versioni curate, non traduzioni letterali meccaniche.
- Tutti i testi UI e i contenuti professionali devono avere equivalenti IT/EN.
- Il cambio lingua non deve alterare layout, ordine o contenuto sostanziale.
- Imposta correttamente `lang` sul documento e i metadati.
- Mantieni date, titoli e terminologia professionale coerenti con il mercato di destinazione.

## Dati e architettura
- Mantieni contenuti separati dalla presentazione e fortemente tipizzati.
- Crea uno schema dati coerente per profilo, esperienze, progetti, competenze, formazione, certificazioni e lingue.
- Prevedi campi opzionali e sezioni nascondibili quando vuote.
- Evita duplicazioni tra Portfolio e ATS: entrambe le viste devono derivare dalla stessa fonte dati.
- Non esporre dati personali fittizi nel risultato finale: i placeholder devono essere chiaramente marcati e facili da sostituire.
- Aggiungi validazione dei dati e messaggi utili per campi mancanti.

## PDF e stampa
- PDF A4, testo selezionabile e ricercabile, non rasterizzato.
- CSS print dedicato con margini, tipografia e page-break controllati.
- Nascondi toolbar e controlli interattivi in stampa.
- Evita righe orfane, titoli isolati, contenuti tagliati e blocchi spezzati in modo innaturale.
- Verifica il PDF con Playwright/Chromium e controlla più pagine e contenuti lunghi.
- Non dichiarare il CV ATS-compliant solo perché è semplice: verifica ordine DOM, testo estratto, heading, link e assenza di contenuti essenziali in immagini o canvas.

## Funzionalità
- Switch lingua IT/EN.
- Switch Portfolio/ATS.
- Anteprima responsive.
- Stampa/esportazione PDF.
- Dati centralizzati e documentati.
- Possibilità di aggiungere in futuro più versioni mirate per ruoli diversi senza duplicare il profilo.

## Qualità e test
- TypeScript strict senza `any` ingiustificati.
- Componenti riutilizzabili e responsabilità chiare.
- Nessun errore in console, warning React o link rotti.
- Test per cambio lingua, cambio template, sezioni vuote, dati lunghi e rendering PDF.
- Esegui `npm run build` e i test; correggi ogni errore.
- Controlla il layout a desktop, tablet, mobile e in stampa A4.

## Modalità di lavoro
Procedi per milestone: (1) audit e schema dati, (2) design system e layout, (3) contenuti e i18n, (4) template ATS, (5) PDF e test, (6) rifinitura. Dopo ogni milestone riepiloga file modificati, scelte e test eseguiti. Non fermarti a una proposta grafica: implementa il risultato funzionante.

## Criteri di accettazione
Il progetto è pronto quando posso modificare una sola fonte dati, visualizzare Portfolio e ATS in italiano e inglese, stampare PDF A4 puliti, e il contenuto non inventa alcun fatto sul mio percorso. Aggiorna README con setup, modifica dati, build, test ed export PDF.

Prima di scrivere contenuti definitivi sul mio percorso professionale, chiedimi le informazioni mancanti in modo strutturato e non riempire i vuoti con supposizioni.
