import type { CvData } from "./types";

/**
 * FICTIONAL sample data used for development, tests and demos.
 *
 * Every person, company and contact below is invented. Real CV data must never
 * be committed: from M1 it is entered through the application and stored per
 * user in the backend.
 */
export const sampleCv: CvData = {
  profile: {
    name: "Alex Example",
    title: { it: "Senior Software Developer", en: "Senior Software Developer" },
    location: { it: "Città di Esempio, Italia", en: "Sample City, Italy" },
    email: "alex@example.com",
    website: "https://example.com",
    summary: {
      it: "[ESEMPIO] Sviluppatore backend con esperienza in architetture a microservizi, API REST e integrazione tra sistemi.\n\n[ESEMPIO] Attenzione alla qualità del codice, alla manutenibilità e alla collaborazione nel team.",
      en: "[SAMPLE] Backend developer experienced in microservice architectures, REST APIs and system integration.\n\n[SAMPLE] Focused on code quality, maintainability and team collaboration.",
    },
  },
  experience: [
    {
      id: "exp-1",
      company: "Acme Software",
      role: { it: "Senior Software Developer", en: "Senior Software Developer" },
      location: { it: "Città di Esempio", en: "Sample City" },
      start: "01/2023",
      end: { it: "oggi", en: "Present" },
      bullets: [
        {
          it: "[ESEMPIO] Progetto e sviluppo microservizi per un portale di vendita, dall'analisi dei requisiti all'implementazione.",
          en: "[SAMPLE] I design and build microservices for a sales portal, from requirements analysis to implementation.",
        },
        {
          it: "[ESEMPIO] Ho guidato la migrazione a una nuova versione LTS di Java e del framework applicativo.",
          en: "[SAMPLE] I led the migration to a new Java LTS and application framework version.",
        },
      ],
      technologies: ["Java", "Spring Boot", "Kubernetes", "PostgreSQL"],
    },
    {
      id: "exp-2",
      company: "Example Consulting",
      role: { it: "Software Developer", en: "Software Developer" },
      location: { it: "Città di Esempio", en: "Sample City" },
      start: "03/2019",
      end: { it: "12/2022", en: "12/2022" },
      bullets: [
        {
          it: "[ESEMPIO] Sviluppo di componenti di integrazione basati su messaggistica asincrona.",
          en: "[SAMPLE] Built integration components based on asynchronous messaging.",
        },
      ],
      technologies: ["Java", "Apache Kafka", "MongoDB"],
    },
  ],
  projects: [
    {
      id: "prj-1",
      name: { it: "[ESEMPIO] Composizione offerte", en: "[SAMPLE] Offer composition" },
      description: {
        it: "Servizio backend che compone un'offerta commerciale a partire da un catalogo esterno.",
        en: "Backend service that composes a commercial offer from an external catalog.",
      },
      impact: [
        {
          it: "Offerte coerenti con il catalogo e verificate dai servizi esterni.",
          en: "Offers consistent with the catalog and checked by external services.",
        },
      ],
      technologies: ["Java", "REST"],
    },
    {
      id: "prj-2",
      name: { it: "[ESEMPIO] Migrazione dati", en: "[SAMPLE] Data migration" },
      description: {
        it: "Trasferimento di dati storici verso un nuovo archivio con verifica di coerenza.",
        en: "Transfer of historical data to a new store with consistency checks.",
      },
      impact: [
        {
          it: "Meccanismi di confronto automatico tra sorgente e destinazione.",
          en: "Automated comparison between source and target.",
        },
      ],
      technologies: ["Apache Kafka", "MongoDB"],
    },
  ],
  skillGroups: [
    {
      id: "sk-lang",
      name: { it: "Linguaggi", en: "Languages" },
      items: [
        { it: "Java", en: "Java" },
        { it: "TypeScript", en: "TypeScript" },
        { it: "SQL", en: "SQL" },
      ],
    },
    {
      id: "sk-backend",
      name: { it: "Backend", en: "Backend" },
      items: [
        { it: "Spring Boot", en: "Spring Boot" },
        { it: "Quarkus", en: "Quarkus" },
      ],
    },
  ],
  education: [
    {
      id: "edu-1",
      qualification: { it: "[ESEMPIO] Laurea in Informatica", en: "[SAMPLE] BSc in Computer Science" },
      institution: "Example University",
      location: { it: "Città di Esempio", en: "Sample City" },
      year: { it: "2018", en: "2018" },
    },
  ],
};
