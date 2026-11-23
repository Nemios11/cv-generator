import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Mail, Phone, Globe, MapPin, Printer } from "lucide-react";
import type { CvData, Lang, Localized } from "./data/types";

function Github({ size = 24 }: { size?: number }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
      <path d="M9 18c-4.51 2-5-2-7-2" />
    </svg>
  );
}

function Linkedin({ size = 24 }: { size?: number }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z" />
      <rect width="4" height="12" x="2" y="9" />
      <circle cx="4" cy="4" r="2" />
    </svg>
  );
}

/** Splits a text into paragraphs on blank lines (or single newlines). */
function paragraphs(text: string): string[] {
  return text
    .split(/\n\s*\n|\n/)
    .map((p) => p.trim())
    .filter(Boolean);
}

/** Renders a CV (portfolio or ATS presentation) from view data. */
export function CvView({ data }: { data: CvData }) {
  const { profile, experience, projects, skillGroups, education } = data;
  const certifications = data.certifications ?? [];
  const languages = data.languages ?? [];
  const { t, i18n } = useTranslation();
  const [template, setTemplate] = useState<"portfolio" | "ats">("portfolio");
  const [activeJob, setActiveJob] = useState(0);
  const [printQueued, setPrintQueued] = useState(false);
  const lang: Lang = i18n.language.startsWith("it") ? "it" : "en";
  /** Text in the current language, falling back to the other one when missing. */
  const tx = (text: Localized): string => text[lang] || text[lang === "it" ? "en" : "it"];

  useEffect(() => {
    document.body.dataset.template = template;
  }, [template]);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.title = `${profile.name} — ${tx(profile.title)}`;
  }, [lang, profile]);

  useEffect(() => {
    if (!printQueued || template !== "portfolio") return;
    const id = window.setTimeout(() => {
      window.print();
      setPrintQueued(false);
    }, 40);
    return () => window.clearTimeout(id);
  }, [printQueued, template]);

  return (
    <main className={`page ${template}`}>
      <nav className="toolbar">
        <div className="brand">{profile.name}</div>
        <div className="actions">
          <span className="where">
            <MapPin size={14} aria-hidden="true" />
            {tx(profile.location)}
          </span>
          <button
            type="button"
            aria-label={t("switchLanguage")}
            onClick={() => i18n.changeLanguage(lang === "en" ? "it" : "en")}
          >
            {lang === "en" ? "IT" : "EN"}
          </button>
          <button
            type="button"
            onClick={() =>
              setTemplate(template === "portfolio" ? "ats" : "portfolio")
            }
          >
            {template === "portfolio" ? t("atsView") : t("portfolioView")}
          </button>
          <button
            type="button"
            className="primary"
            onClick={() => {
              if (template !== "portfolio") setTemplate("portfolio");
              setPrintQueued(true);
            }}
          >
            <Printer size={15} aria-hidden="true" />
            {t("download")}
          </button>
        </div>
      </nav>
      <header className="hero">
        <img className="hero-glow" src="/hero-glow.png" alt="" />
        <div className="hero-copy">
          <p className="eyebrow">{t("eyebrow")}</p>
          <h1>{profile.name}</h1>
          <h2>{tx(profile.title)}</h2>
          {paragraphs(tx(profile.summary)).map((p) => (
            <p className="summary" key={p}>
              {p}
            </p>
          ))}
          <div className="contact">
            {profile.email ? (
              <a href={`mailto:${profile.email}`}>
                <Mail size={15} aria-hidden="true" />
                {profile.email}
              </a>
            ) : null}
            {profile.phone ? (
              <a href={`tel:${profile.phone.replace(/\s+/g, "")}`}>
                <Phone size={15} aria-hidden="true" />
                {profile.phone}
              </a>
            ) : null}
            {profile.linkedin ? (
              <a href={profile.linkedin}>
                <Linkedin size={15} />
                LinkedIn
              </a>
            ) : null}
            {profile.github ? (
              <a href={profile.github}>
                <Github size={15} />
                GitHub
              </a>
            ) : null}
            {profile.website ? (
              <a href={profile.website}>
                <Globe size={15} aria-hidden="true" />
                Portfolio
              </a>
            ) : null}
          </div>
        </div>
        {profile.photo ? (
          <img
            className="portrait"
            src={profile.photo}
            alt={t("portraitAlt", { name: profile.name })}
          />
        ) : null}
      </header>
      {experience.length > 0 ? (
        <section>
          <div className="section-head">
            <span>01.</span>
            <h3>{t("experience")}</h3>
            <span className="rule" />
          </div>
          <div className="work">
            <div className="work-tabs" role="tablist" aria-label={t("experience")}>
              {experience.map((job, i) => (
                <button
                  key={job.id}
                  type="button"
                  role="tab"
                  id={`job-tab-${job.id}`}
                  aria-selected={activeJob === i}
                  aria-controls={`job-panel-${job.id}`}
                  className={activeJob === i ? "is-active" : undefined}
                  onClick={() => setActiveJob(i)}
                >
                  {job.company}
                </button>
              ))}
            </div>
            <div className="work-panels">
              {experience.map((job, i) => (
                <article
                  className={activeJob === i ? "entry is-active" : "entry"}
                  key={job.id}
                  role="tabpanel"
                  id={`job-panel-${job.id}`}
                  aria-labelledby={`job-tab-${job.id}`}
                >
                  <h4>
                    {tx(job.role)} <span>@ {job.company}</span>
                  </h4>
                  <p className="muted">
                    {[`${job.start} — ${tx(job.end)}`, tx(job.location)]
                      .filter(Boolean)
                      .join(" · ")}
                  </p>
                  <ul>
                    {job.bullets.map((b, j) => (
                      <li key={j}>{tx(b)}</li>
                    ))}
                  </ul>
                  <div className="tags">
                    {job.technologies.map((x) => (
                      <span key={x}>{x}</span>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          </div>
        </section>
      ) : null}
      {projects.length > 0 ? (
        <section>
          <div className="section-head">
            <span>02.</span>
            <h3>{t("projects")}</h3>
            <span className="rule" />
          </div>
          <div className="project-grid">
            {projects.map((p, i) => (
              <article className="project" key={p.id}>
                <div className="project-index">
                  {t("projectIndex", { n: String(i + 1).padStart(2, "0") })}
                </div>
                <h4>{tx(p.name)}</h4>
                <p>{tx(p.description)}</p>
                {p.impact.map((part, j) => (
                  <p key={j}>{tx(part)}</p>
                ))}
                <div className="tags">
                  {p.technologies.map((x) => (
                    <span key={x}>{x}</span>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
      ) : null}
      {skillGroups.length > 0 ? (
        <section>
          <div className="section-head">
            <span>03.</span>
            <h3>{t("skills")}</h3>
            <span className="rule" />
          </div>
          <div className="skills-grid">
            {skillGroups.map((g) => (
              <div className="skill" key={g.id}>
                <h4>{tx(g.name)}</h4>
                <div className="skill-items">
                  {g.items.map((item, j) => (
                    <span key={j}>{tx(item)}</span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>
      ) : null}
      {education.length > 0 ? (
        <section>
          <div className="section-head">
            <span>04.</span>
            <h3>{t("education")}</h3>
            <span className="rule" />
          </div>
          {education.map((e) => (
            <article className="study" key={e.id}>
              <time>{tx(e.year)}</time>
              <div>
                <h4>{tx(e.qualification)}</h4>
                <p className="muted">
                  {[e.institution, tx(e.location)].filter(Boolean).join(" · ")}
                </p>
              </div>
            </article>
          ))}
        </section>
      ) : null}
      {certifications.length > 0 ? (
        <section>
          <div className="section-head">
            <span>05.</span>
            <h3>{t("certifications")}</h3>
            <span className="rule" />
          </div>
          {certifications.map((c) => (
            <article className="study" key={c.id}>
              <time>{c.issued ?? ""}</time>
              <div>
                <h4>{tx(c.name)}</h4>
                <p className="muted">{c.issuer}</p>
              </div>
            </article>
          ))}
        </section>
      ) : null}
      {languages.length > 0 ? (
        <section>
          <div className="section-head">
            <span>06.</span>
            <h3>{t("languages")}</h3>
            <span className="rule" />
          </div>
          <div className="skill-items">
            {languages.map((l) => (
              <span key={l.id}>
                {tx(l.language)} — {l.level === "NATIVE" ? t("nativeSpeaker") : l.level}
              </span>
            ))}
          </div>
        </section>
      ) : null}
      <footer>
        <div className="footer-bar">
          <span>
            © {new Date().getFullYear()} {profile.name}
          </span>
          <span>{t("footerNote")}</span>
        </div>
        <p className="privacy">{t("privacy")}</p>
      </footer>
    </main>
  );
}
