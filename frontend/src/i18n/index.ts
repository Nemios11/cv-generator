import i18n from "i18next";
import { initReactI18next } from "react-i18next";

const resources = {
  it: {
    translation: {
      eyebrow: "DEV",
      experience: "Esperienze",
      projects: "Progetti principali",
      projectIndex: "PROGETTO / {{n}}",
      skills: "Competenze tecniche",
      education: "Formazione",
      contact: "Contatti",
      download: "PDF portfolio",
      atsView: "Vista ATS",
      portfolioView: "Vista portfolio",
      switchLanguage: "Passa all'inglese",
      portraitAlt: "Foto di {{name}}",
      footerNote: "GRAZIE PER L'ATTENZIONE.",
      privacy:
        "Autorizzo il trattamento dei dati personali contenuti nel presente curriculum vitae ai sensi del D.Lgs. 196/2003 e del Regolamento UE 2016/679 (GDPR).",
    },
  },
  en: {
    translation: {
      eyebrow: "DEV",
      experience: "Experience",
      projects: "Main projects",
      projectIndex: "PROJECT / {{n}}",
      skills: "Technical skills",
      education: "Education",
      contact: "Contact",
      download: "Portfolio PDF",
      atsView: "ATS view",
      portfolioView: "Portfolio view",
      switchLanguage: "Switch to Italian",
      portraitAlt: "Photo of {{name}}",
      footerNote: "THANK YOU FOR YOUR ATTENTION.",
      privacy:
        "I authorize the processing of the personal data contained in this CV pursuant to Legislative Decree 196/2003 and EU Regulation 2016/679 (GDPR).",
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
