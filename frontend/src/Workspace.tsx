import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "react-oidc-context";
import { ApiClient, ApiError, type VersionedProfile } from "./api/client";
import { toCvData } from "./api/toCvData";
import type { MeDto, ProfileDto } from "./api/types";
import { CvView } from "./CvView";

type Loaded = { me: MeDto; profile: VersionedProfile | null };

/** The signed-in area: the user's own profile, import/export and deletion. */
export function Workspace() {
  const auth = useAuth();
  const { t } = useTranslation();
  const [state, setState] = useState<Loaded | null>(null);
  const [message, setMessage] = useState<{ kind: "error" | "info"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const accessToken = auth.user?.access_token;
  const api = useMemo(() => new ApiClient(() => accessToken), [accessToken]);

  const describe = useCallback(
    (error: unknown): string => {
      if (error instanceof ApiError) {
        if (error.status === 412) return t("conflict");
        if (error.problem?.field) return `${error.message} (${error.problem.field})`;
        return error.message;
      }
      return t("unexpectedError");
    },
    [t],
  );

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.me(), api.getProfile()])
      .then(([me, profile]) => {
        if (!cancelled) setState({ me, profile });
      })
      .catch((error: unknown) => {
        if (!cancelled) setMessage({ kind: "error", text: describe(error) });
      });
    return () => {
      cancelled = true;
    };
  }, [api, describe]);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setMessage(null);
    try {
      await action();
    } catch (error) {
      setMessage({ kind: "error", text: describe(error) });
    } finally {
      setBusy(false);
    }
  }

  function save(profile: ProfileDto, etag: string | null, info: string) {
    return run(async () => {
      const saved = await api.saveProfile(profile, etag);
      setState((s) => (s ? { ...s, profile: saved } : s));
      setMessage({ kind: "info", text: info });
    });
  }

  async function onImport(file: File) {
    let parsed: unknown;
    try {
      parsed = JSON.parse(await file.text());
    } catch {
      setMessage({ kind: "error", text: t("invalidFile") });
      return;
    }
    const candidate = parsed as Partial<ProfileDto>;
    if (!candidate || typeof candidate !== "object" || typeof candidate.header?.fullName !== "string") {
      setMessage({ kind: "error", text: t("invalidFile") });
      return;
    }
    const current = state?.profile;
    if (current && !window.confirm(t("confirmReplace"))) return;
    await save(candidate as ProfileDto, current?.etag ?? null, t("imported"));
  }

  function onExport() {
    if (!state?.profile) return;
    const blob = new Blob([JSON.stringify(state.profile.profile, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "cv-profile.json";
    link.click();
    URL.revokeObjectURL(url);
  }

  function onDelete() {
    if (!window.confirm(t("confirmDelete"))) return;
    void run(async () => {
      await api.deleteProfile();
      setState((s) => (s ? { ...s, profile: null } : s));
      setMessage({ kind: "info", text: t("deleted") });
    });
  }

  function onCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const fullName = String(new FormData(event.currentTarget).get("fullName") ?? "").trim();
    if (fullName) void save({ header: { fullName } }, null, t("created"));
  }

  const greetingName = state?.me.name || state?.me.username || "";

  return (
    <>
      <div className="app-bar">
        <span>{state ? t("greeting", { name: greetingName }) : t("loading")}</span>
        <div className="app-actions">
          <button type="button" disabled={busy || !state} onClick={() => fileInput.current?.click()}>
            {t("importJson")}
          </button>
          <input
            ref={fileInput}
            type="file"
            accept="application/json,.json"
            hidden
            data-testid="import-file"
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (file) void onImport(file);
            }}
          />
          <button type="button" disabled={busy || !state?.profile} onClick={onExport}>
            {t("exportJson")}
          </button>
          <button type="button" disabled={busy || !state?.profile} onClick={onDelete}>
            {t("deleteProfile")}
          </button>
          <button type="button" onClick={() => void auth.signoutRedirect()}>
            {t("signOut")}
          </button>
        </div>
      </div>
      {message ? (
        <p className={`notice ${message.kind}`} role={message.kind === "error" ? "alert" : "status"}>
          {message.text}
        </p>
      ) : null}
      {state && state.profile ? <CvView data={toCvData(state.profile.profile)} /> : null}
      {state && !state.profile ? (
        <main className="landing">
          <h1>{t("noProfileTitle")}</h1>
          <p className="summary">{t("noProfileIntro")}</p>
          <form className="create-form" onSubmit={onCreate}>
            <label htmlFor="fullName">{t("fullNameLabel")}</label>
            <input id="fullName" name="fullName" required maxLength={200} autoComplete="name" />
            <button type="submit" className="primary" disabled={busy}>
              {t("createProfile")}
            </button>
          </form>
        </main>
      ) : null}
    </>
  );
}
