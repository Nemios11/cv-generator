/**
 * Converts the CV data files of the original prototype (profile.ts,
 * experience.ts, projects.ts, skills.ts, education.ts) into a profile JSON that
 * can be imported from the app ("Import JSON").
 *
 * The input and output live in the git-ignored local/ folder by default, so
 * personal data never enters the repository or CI:
 *
 *   npm run convert-prototype                      # ../local/prototype-data → ../local/profile-import.json
 *   npm run convert-prototype -- <inputDir> <outputFile>
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import type { ProfileDto, TextDto } from '../src/api/types';

type Localized = { it: string; en: string };

interface PrototypeModules {
  profile: {
    name: string;
    title?: string;
    location?: string;
    email?: string;
    numeroDiTelefono?: string;
    phone?: string;
    website?: string;
    linkedin?: string;
    github?: string;
    summary?: Localized;
  };
  experience: {
    company: string;
    role: Localized;
    location?: Localized;
    start: string;
    end?: Localized;
    bullets?: Localized[];
    technologies?: string[];
  }[];
  projects: { name: Localized; description?: Localized; impact?: Localized[]; technologies?: string[] }[];
  skillGroups: { name: Localized; items: Localized[] }[];
  education: { qualification: Localized; institution: string; location?: Localized; year?: Localized }[];
}

const [inputArg, outputArg] = process.argv.slice(2);
const inputDir = resolve(inputArg ?? '../local/prototype-data');
const outputFile = resolve(outputArg ?? '../local/profile-import.json');

async function load<T>(file: string, exportName: string): Promise<T> {
  const module = (await import(pathToFileURL(resolve(inputDir, file)).href)) as Record<string, unknown>;
  if (!(exportName in module)) throw new Error(`${file} does not export "${exportName}"`);
  return module[exportName] as T;
}

/** "07/2024" → "2024-07"; anything else (e.g. "oggi", "Present") → null. */
function month(value?: string): string | null {
  const match = value?.trim().match(/^(\d{1,2})\/(\d{4})$/);
  return match ? `${match[2]}-${match[1].padStart(2, '0')}` : null;
}

const text = (value?: Localized): TextDto | undefined => (value ? { it: value.it, en: value.en } : undefined);
const same = (value?: string): TextDto | undefined => (value ? { it: value, en: value } : undefined);
const orNull = (value?: string): string | null => (value && value.trim() ? value.trim() : null);

const warnings: string[] = [];

const p: PrototypeModules = {
  profile: await load('profile.ts', 'profile'),
  experience: await load('experience.ts', 'experience'),
  projects: await load('projects.ts', 'projects'),
  skillGroups: await load('skills.ts', 'skillGroups'),
  education: await load('education.ts', 'education'),
};

const profile: ProfileDto = {
  header: {
    fullName: p.profile.name,
    headline: same(p.profile.title),
    location: same(p.profile.location),
    email: orNull(p.profile.email),
    phone: orNull(p.profile.phone ?? p.profile.numeroDiTelefono),
    website: orNull(p.profile.website),
    linkedin: orNull(p.profile.linkedin),
    github: orNull(p.profile.github),
    summary: text(p.profile.summary),
  },
  experiences: p.experience.map((e, i) => {
    const start = month(e.start);
    if (!start) warnings.push(`experience[${i}]: start "${e.start}" is not MM/YYYY`);
    return {
      company: e.company,
      role: text(e.role)!,
      location: text(e.location),
      start: start ?? '',
      end: month(e.end?.en) ?? month(e.end?.it),
      bullets: (e.bullets ?? []).map((b) => text(b)!),
      technologies: e.technologies ?? [],
    };
  }),
  projects: p.projects.map((x) => ({
    name: text(x.name)!,
    description: text(x.description),
    highlights: (x.impact ?? []).map((h) => text(h)!),
    technologies: x.technologies ?? [],
  })),
  skillGroups: p.skillGroups.map((s) => ({ name: text(s.name)!, items: s.items.map((it) => text(it)!) })),
  education: p.education.map((e, i) => {
    const end = month(e.year?.en) ?? month(e.year?.it);
    if (e.year && !end) warnings.push(`education[${i}]: date "${e.year.en}" is not MM/YYYY and was dropped`);
    return {
      qualification: text(e.qualification)!,
      institution: e.institution,
      location: text(e.location),
      end,
    };
  }),
  certifications: [],
  languages: [],
};

mkdirSync(dirname(outputFile), { recursive: true });
writeFileSync(outputFile, `${JSON.stringify(profile, null, 2)}\n`, 'utf8');

// Counts only: never print the personal data itself.
console.log(`Written ${outputFile}`);
console.log(
  `experiences=${profile.experiences!.length} projects=${profile.projects!.length} ` +
    `skillGroups=${profile.skillGroups!.length} education=${profile.education!.length}`,
);
for (const w of warnings) console.warn(`warning: ${w}`);
console.log('Certifications and spoken languages are not in the prototype: add them in the app (M2).');
