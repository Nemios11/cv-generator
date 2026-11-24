import type { MeDto, ProblemDto, ProfileDto } from "./types";

/** Error carrying the API problem details, if any. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDto;

  constructor(status: number, problem?: ProblemDto) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.status = status;
    this.problem = problem;
  }
}

export interface VersionedProfile {
  profile: ProfileDto;
  /** Opaque version for optimistic locking (ETag). */
  etag: string | null;
}

/**
 * Thin client for the backend. Calls are same-origin (/api) and authenticated
 * with the access token; the token is never persisted by this module.
 */
export class ApiClient {
  private readonly token: () => string | undefined;

  constructor(token: () => string | undefined) {
    this.token = token;
  }

  async me(): Promise<MeDto> {
    const res = await this.request("GET", "/api/me");
    return (await res.json()) as MeDto;
  }

  /** The caller's profile, or null if it has not been created yet. */
  async getProfile(): Promise<VersionedProfile | null> {
    const res = await this.request("GET", "/api/profile", undefined, undefined, [404]);
    if (res.status === 404) return null;
    return { profile: (await res.json()) as ProfileDto, etag: res.headers.get("ETag") };
  }

  async saveProfile(profile: ProfileDto, etag: string | null): Promise<VersionedProfile> {
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (etag) headers["If-Match"] = etag;
    const res = await this.request("PUT", "/api/profile", JSON.stringify(profile), headers);
    return { profile: (await res.json()) as ProfileDto, etag: res.headers.get("ETag") };
  }

  async deleteProfile(): Promise<void> {
    await this.request("DELETE", "/api/profile");
  }

  private async request(
    method: string,
    path: string,
    body?: string,
    headers: Record<string, string> = {},
    accepted: number[] = [],
  ): Promise<Response> {
    const token = this.token();
    const res = await fetch(path, {
      method,
      body,
      headers: { Accept: "application/json", ...headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      credentials: "omit",
      cache: "no-store",
    });
    if (!res.ok && !accepted.includes(res.status)) {
      let problem: ProblemDto | undefined;
      try {
        problem = (await res.json()) as ProblemDto;
      } catch {
        problem = undefined;
      }
      throw new ApiError(res.status, problem);
    }
    return res;
  }
}
