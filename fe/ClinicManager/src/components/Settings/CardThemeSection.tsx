import { useState } from "react";
import { Moon, RotateCcw, Sun } from "lucide-react";
import ActionButton from "../ActionButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { CardTheme } from "../../models/preferences";
import { useCardTheme } from "../../hooks/useCardTheme";
import { apiErrorMessage } from "../../utils/apiError";

const OPTIONS: { value: CardTheme; label: string; icon: typeof Sun }[] = [
  { value: "LIGHT", label: "Jasny", icon: Sun },
  { value: "DARK", label: "Ciemny", icon: Moon },
];

export function CardThemeSection() {
  const { showAlert } = useAlert();
  const { theme, loaded, failed, retry, setTheme } = useCardTheme();
  const [saving, setSaving] = useState<boolean>(false);

  const select = async (value: CardTheme) => {
    if (!loaded || saving || value === theme) return;
    setSaving(true);
    try {
      await setTheme(value);
      showAlert("Zapisano wygląd karty klienta.", AlertType.SUCCESS);
    } catch (error) {
      showAlert(apiErrorMessage(error) ?? "Nie udało się zapisać wyglądu karty.", AlertType.ERROR);
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="st-card st-theme-card flex-column g-15">
      <div className="flex-column g-025">
        <span className="st-card-label pc-y">Wygląd karty klienta</span>
      </div>
      <div className="st-theme-previews flex g-15 flex-wrap" role="radiogroup" aria-label="Wygląd karty klienta">
        {OPTIONS.map((option) => {
          const Icon = option.icon;
          const selected = loaded && theme === option.value;
          return (
            <div key={option.value} className="flex-column align-items-center g-075">
              <div
                className={`st-theme-preview st-theme-${option.value.toLowerCase()} flex-column g-025 pointer ${
                  selected ? "selected" : ""
                }`}
                aria-hidden="true"
                onClick={() => select(option.value)}
              >
                <span className="st-theme-bar title" />
                <span className="st-theme-rule" />
                <span className="st-theme-bar" />
                <span className="st-theme-bar short" />
                <span className="st-theme-bar" />
              </div>
              <button
                type="button"
                role="radio"
                aria-checked={theme === option.value}
                disabled={!loaded || saving}
                className={`cat-seg st-theme-option align-items-center justify-center pointer ${selected ? "active" : ""}`}
                onClick={() => select(option.value)}
              >
                <Icon className="dl-icon" />
                {option.label}
              </button>
            </div>
          );
        })}
      </div>
      {failed && (
        <div className="flex align-items-center g-1 flex-wrap">
          <span className="cq-empty">Nie udało się wczytać wyglądu karty.</span>
          <ActionButton text="Spróbuj ponownie" onClick={retry}>
            <RotateCcw className="dl-icon-1" />
          </ActionButton>
        </div>
      )}
    </section>
  );
}

export default CardThemeSection;
