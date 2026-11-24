import type { CvData, Localized } from "../data/types";
import type { ProfileDto, TextDto } from "./types";

const PRESENT: Localized = { it: "oggi", en: "Present" };

function loc(text?: TextDto | null): Localized {
  return { it: text?.it ?? "", en: text?.en ?? "" };
}

/** "2024-07" → "07/2024"; null/undefined → "". */
export function displayMonth(month?: string | null): string {
  if (!month) return "";
  const [year, mm] = month.split("-");
  return mm ? `${mm}/${year}` : year;
}

/** Converts the API profile into the view model rendered by CvView. */
export function toCvData(p: ProfileDto): CvData {
  const h = p.header;
  return {
    profile: {
      name: h.fullName,
      title: loc(h.headline),
      location: loc(h.location),
      email: h.email ?? undefined,
      phone: h.phone ?? undefined,
      website: h.website ?? undefined,
      linkedin: h.linkedin ?? undefined,
      github: h.github ?? undefined,
      summary: loc(h.summary),
    },
    experience: (p.experiences ?? []).map((e, i) => ({
      id: e.id ?? `exp-${i}`,
      company: e.company,
      role: loc(e.role),
      location: loc(e.location),
      start: displayMonth(e.start),
      end: e.end ? { it: displayMonth(e.end), en: displayMonth(e.end) } : PRESENT,
      bullets: (e.bullets ?? []).map(loc),
      technologies: e.technologies ?? [],
    })),
    projects: (p.projects ?? []).map((x, i) => ({
      id: x.id ?? `prj-${i}`,
      name: loc(x.name),
      description: loc(x.description),
      impact: (x.highlights ?? []).map(loc),
      technologies: x.technologies ?? [],
    })),
    skillGroups: (p.skillGroups ?? []).map((s, i) => ({
      id: s.id ?? `sk-${i}`,
      name: loc(s.name),
      items: (s.items ?? []).map(loc),
    })),
    education: (p.education ?? []).map((e, i) => {
      const when = displayMonth(e.end ?? e.start);
      return {
        id: e.id ?? `edu-${i}`,
        qualification: loc(e.qualification),
        institution: e.institution,
        location: loc(e.location),
        year: { it: when, en: when },
      };
    }),
    certifications: (p.certifications ?? []).map((c, i) => ({
      id: c.id ?? `cert-${i}`,
      name: loc(c.name),
      issuer: c.issuer,
      issued: displayMonth(c.issued) || undefined,
    })),
    languages: (p.languages ?? []).map((l, i) => ({
      id: l.id ?? `lang-${i}`,
      language: loc(l.language),
      level: l.level,
    })),
  };
}
