/**
 * Shapes of the backend REST API (see /v3/api-docs). Months use "YYYY-MM";
 * a missing end month means "ongoing". Ids are optional on write.
 */

export interface TextDto {
  it?: string;
  en?: string;
}

export interface HeaderDto {
  fullName: string;
  headline?: TextDto;
  location?: TextDto;
  email?: string | null;
  phone?: string | null;
  website?: string | null;
  linkedin?: string | null;
  github?: string | null;
  summary?: TextDto;
}

export interface ExperienceDto {
  id?: string;
  company: string;
  role: TextDto;
  location?: TextDto;
  start: string;
  end?: string | null;
  bullets?: TextDto[];
  technologies?: string[];
}

export interface ProjectDto {
  id?: string;
  name: TextDto;
  description?: TextDto;
  highlights?: TextDto[];
  technologies?: string[];
  url?: string | null;
}

export interface SkillGroupDto {
  id?: string;
  name: TextDto;
  items?: TextDto[];
}

export interface EducationDto {
  id?: string;
  qualification: TextDto;
  institution: string;
  location?: TextDto;
  start?: string | null;
  end?: string | null;
}

export interface CertificationDto {
  id?: string;
  name: TextDto;
  issuer: string;
  issued?: string | null;
  expires?: string | null;
  credentialUrl?: string | null;
}

export interface LanguageDto {
  id?: string;
  language: TextDto;
  level: "A1" | "A2" | "B1" | "B2" | "C1" | "C2" | "NATIVE";
}

export interface ProfileDto {
  header: HeaderDto;
  experiences?: ExperienceDto[];
  projects?: ProjectDto[];
  skillGroups?: SkillGroupDto[];
  education?: EducationDto[];
  certifications?: CertificationDto[];
  languages?: LanguageDto[];
}

export interface MeDto {
  id: string;
  username?: string;
  name?: string;
}

/** RFC 9457 problem details returned by the API on errors. */
export interface ProblemDto {
  title?: string;
  detail?: string;
  status?: number;
  field?: string;
}
