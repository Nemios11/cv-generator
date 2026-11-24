import { useEffect } from "react";
import { useTranslation } from "react-i18next";

/** IT/EN switch; keeps <html lang> in sync with the selected language. */
export function LanguageToggle() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language.startsWith("it") ? "it" : "en";

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  return (
    <button
      type="button"
      className="link-button"
      aria-label={t("switchLanguage")}
      onClick={() => void i18n.changeLanguage(lang === "en" ? "it" : "en")}
    >
      {lang === "en" ? "IT" : "EN"}
    </button>
  );
}
