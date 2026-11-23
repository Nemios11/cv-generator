/** A text available in every supported language. */
export type Localized = { it: string; en: string };

export type Lang = keyof Localized;

export interface Profile {
  name: string;
  title: Localized;
  location: Localized;
  email?: string;
  phone?: string;
  website?: string;
  linkedin?: string;
  github?: string;
  /** Path of a photo served from /public. Optional: omit to hide the portrait. */
  photo?: string;
  /** Paragraphs are separated by a blank line. */
  summary: Localized;
}

export interface Experience {
  /** Stable identifier, unique across the list. */
  id: string;
  company: string;
  role: Localized;
  location: Localized;
  start: string;
  end: Localized;
  bullets: Localized[];
  technologies: string[];
}

export interface Project {
  id: string;
  name: Localized;
  description: Localized;
  impact: Localized[];
  technologies: string[];
}

export interface SkillGroup {
  id: string;
  name: Localized;
  items: Localized[];
}

export interface Education {
  id: string;
  qualification: Localized;
  institution: string;
  location: Localized;
  year: Localized;
}

export interface CvData {
  profile: Profile;
  experience: Experience[];
  projects: Project[];
  skillGroups: SkillGroup[];
  education: Education[];
}
