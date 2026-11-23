import type { AuthProviderProps } from "react-oidc-context";

/**
 * OpenID Connect configuration: Authorization Code Flow with PKCE (S256, the
 * oidc-client-ts default) against the Keycloak realm. Values can be overridden
 * at build time with VITE_OIDC_AUTHORITY and VITE_OIDC_CLIENT_ID.
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? "http://localhost:8180/realms/cv-generator",
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
