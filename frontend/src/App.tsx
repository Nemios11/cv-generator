import { useTranslation } from "react-i18next";
import { AuthProvider, useAuth } from "react-oidc-context";
import { oidcConfig } from "./auth/config";
import { CvView } from "./CvView";
import { sampleCv } from "./data/sample";
import { LanguageToggle } from "./LanguageToggle";
import { Workspace } from "./Workspace";

/**
 * Routes:
 *  - /demo  the CV rendered from fictional sample data, no login required;
 *  - /      sign-in, then the user's own profile.
 */
export default function App() {
  if (window.location.pathname.startsWith("/demo")) {
    return <CvView data={sampleCv} />;
  }
  return (
    <AuthProvider {...oidcConfig}>
      <Home />
    </AuthProvider>
  );
}

function Home() {
  const auth = useAuth();
  const { t } = useTranslation();

  if (auth.isLoading || auth.activeNavigator) {
    return (
      <p className="status" role="status">
        {t("loading")}
      </p>
    );
  }
  if (!auth.isAuthenticated) {
    return <Landing error={auth.error ? t("signInFailed") : undefined} />;
  }
  return <Workspace />;
}

function Landing({ error }: { error?: string }) {
  const auth = useAuth();
  const { t } = useTranslation();

  return (
    <main className="landing">
      <div className="landing-top">
        <span className="brand">CV Generator</span>
        <LanguageToggle />
      </div>
      <h1>{t("landingTitle")}</h1>
      <p className="summary">{t("landingIntro")}</p>
      {error ? (
        <p className="notice error" role="alert">
          {error}
        </p>
      ) : null}
      <div className="landing-actions">
        <button type="button" className="primary" onClick={() => void auth.signinRedirect()}>
          {t("signIn")}
        </button>
        <a href="/demo">{t("seeExample")}</a>
      </div>
      <p className="privacy">{t("landingPrivacy")}</p>
    </main>
  );
}
