import type { AuthProviderProps } from "react-oidc-context";

const configuredAuthority = import.meta.env.VITE_OIDC_AUTHORITY ?? "http://localhost:8180/realms/cv-generator";

/**
 * OpenID Connect configuration: Authorization Code Flow with PKCE (S256, the
 * oidc-client-ts default) against the Keycloak realm. Values can be overridden
 * at build time with VITE_OIDC_AUTHORITY and VITE_OIDC_CLIENT_ID.
 *
 * A relative authority (e.g. "/realms/cv-generator", used by the container
 * image) is resolved against the page origin: Keycloak sits behind the same
 * entry point as the app, so one build works in every environment.
 */
export const oidcConfig: AuthProviderProps = {
  authority: configuredAuthority.startsWith("/")
    ? `${window.location.origin}${configuredAuthority}`
    : configuredAuthority,
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? "cv-frontend",
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: "code",
  scope: "openid profile",
  // Remove ?code=&state= from the address bar after the login redirect.
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
