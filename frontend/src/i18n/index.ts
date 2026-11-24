import i18n from "i18next";
import { initReactI18next } from "react-i18next";

const resources = {
  it: {
    translation: {
      // CV view
      eyebrow: "DEV",
      experience: "Esperienze",
      projects: "Progetti principali",
      projectIndex: "PROGETTO / {{n}}",
      skills: "Competenze tecniche",
      education: "Formazione",
      certifications: "Certificazioni",
      languages: "Lingue",
      nativeSpeaker: "madrelingua",
      contact: "Contatti",
      download: "PDF portfolio",
      atsView: "Vista ATS",
      portfolioView: "Vista portfolio",
      switchLanguage: "Passa all'inglese",
      portraitAlt: "Foto di {{name}}",
      footerNote: "GRAZIE PER L'ATTENZIONE.",
      privacy:
        "Autorizzo il trattamento dei dati personali contenuti nel presente curriculum vitae ai sensi del D.Lgs. 196/2003 e del Regolamento UE 2016/679 (GDPR).",
      // App shell
      loading: "Caricamento…",
      landingTitle: "Il tuo CV, sempre aggiornato",
      landingIntro:
        "Inserisci una volta il tuo percorso professionale e genera CV mirati in italiano e inglese, in versione ATS e Portfolio.",
      landingPrivacy:
        "I tuoi dati sono visibili solo a te. Puoi esportarli o eliminarli in qualsiasi momento.",
      signIn: "Accedi",
      signOut: "Esci",
      signInFailed: "Accesso non riuscito. Riprova.",
      seeExample: "Vedi un esempio",
      greeting: "Ciao, {{name}}",
      importJson: "Importa JSON",
      exportJson: "Esporta JSON",
      deleteProfile: "Elimina profilo",
      confirmDelete: "Eliminare definitivamente il profilo? L'operazione non è reversibile.",
      confirmReplace: "Sostituire il profilo attuale con il contenuto del file?",
      invalidFile: "Il file non è un profilo valido (serve almeno header.fullName).",
      imported: "Profilo importato.",
      created: "Profilo creato.",
      deleted: "Profilo eliminato.",
      conflict: "Il profilo è stato modificato altrove. Ricarica la pagina e riprova.",
      unexpectedError: "Si è verificato un errore imprevisto.",
      noProfileTitle: "Non hai ancora un profilo",
      noProfileIntro:
        "Crea un profilo partendo dal tuo nome oppure importa un file JSON. L'editor completo arriva nella prossima versione.",
      fullNameLabel: "Nome e cognome",
      createProfile: "Crea profilo",
    },
  },
  en: {
    translation: {
      // CV view
      eyebrow: "DEV",
      experience: "Experience",
      projects: "Main projects",
      projectIndex: "PROJECT / {{n}}",
      skills: "Technical skills",
      education: "Education",
      certifications: "Certifications",
      languages: "Languages",
      nativeSpeaker: "native",
      contact: "Contact",
      download: "Portfolio PDF",
      atsView: "ATS view",
      portfolioView: "Portfolio view",
      switchLanguage: "Switch to Italian",
      portraitAlt: "Photo of {{name}}",
      footerNote: "THANK YOU FOR YOUR ATTENTION.",
      privacy:
        "I authorize the processing of the personal data contained in this CV pursuant to Legislative Decree 196/2003 and EU Regulation 2016/679 (GDPR).",
      // App shell
      loading: "Loading…",
      landingTitle: "Your CV, always up to date",
      landingIntro:
        "Enter your career history once and generate targeted CVs in Italian and English, in ATS and Portfolio versions.",
      landingPrivacy: "Your data is visible only to you. You can export or delete it at any time.",
      signIn: "Sign in",
      signOut: "Sign out",
      signInFailed: "Sign-in failed. Please try again.",
      seeExample: "See an example",
      greeting: "Hi, {{name}}",
      importJson: "Import JSON",
      exportJson: "Export JSON",
      deleteProfile: "Delete profile",
      confirmDelete: "Permanently delete your profile? This cannot be undone.",
      confirmReplace: "Replace your current profile with the file contents?",
      invalidFile: "The file is not a valid profile (header.fullName is required).",
      imported: "Profile imported.",
      created: "Profile created.",
      deleted: "Profile deleted.",
      conflict: "The profile was changed elsewhere. Reload the page and try again.",
      unexpectedError: "An unexpected error occurred.",
      noProfileTitle: "You don't have a profile yet",
      noProfileIntro:
        "Create a profile from your name or import a JSON file. The full editor comes in the next version.",
      fullNameLabel: "Full name",
      createProfile: "Create profile",
    },
  },
} as const;

void i18n.use(initReactI18next).init({
  resources,
  lng: "en",
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

export default i18n;
