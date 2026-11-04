import { useState, useEffect, useCallback } from "react";
import UnsavedChangesGuard from "../UnsavedChangesGuard";
import { Save } from "lucide-react";
import { useAlert } from "../Alert/AlertProvider";
import AppSettingsService from "../../services/AppSettingsService";
import { AppSettings, NewAppSettings } from "../../models/app_settings";
import NavigationBar from "../NavigationBar";
import { Slider } from "../Slider";
import ActionButton from "../ActionButton";
import { AlertType } from "../../models/alert";
import { validateSettingsForm } from "../../utils/validators";
import DevicesSection from "./DevicesSection";
import CardThemeSection from "./CardThemeSection";
import { useRoles } from "../../hooks/useRoles";

type SettingsTab = "general" | "app" | "devices";

const EMPTY_SETTINGS: NewAppSettings = {
  voucherExpiryTime: 0,
  visitAbsenceRate: 0,
  visitVipRate: 0,
  boostNetRate: 0,
  googleReviewDiscount: 0,
  booksyHappyHours: 0,
};

interface SettingDefinition {
  key: keyof NewAppSettings;
  label: string;
  description: string;
  min: number;
  max: number;
  unit: string;
}

const SETTINGS: SettingDefinition[] = [
  {
    key: "voucherExpiryTime",
    label: "Okres ważności Vouchera",
    description:
      "Podczas tworzenia nowego Vouchera, na podstawie podanej wartości, program przeliczy datę ważności względem daty utworzenia.",
    min: 0,
    max: 36,
    unit: " msc",
  },
  {
    key: "visitAbsenceRate",
    label: "Przelicznik za nieodbytą wizytę",
    description:
      "Tworząc nową Wizytę z nieobecnością (<24h), wartość Wizyty będzie przemnożona przez wskazaną wartość tworząc nowy Dług Klienta.",
    min: 0,
    max: 100,
    unit: " %",
  },
  {
    key: "visitVipRate",
    label: "Przelicznik wizyty VIP",
    description:
      "Podczas tworzenia Wizyty VIP program automatycznie przeliczy koszt Wizyty w oparciu o ustawioną wartość.",
    min: 100,
    max: 200,
    unit: " %",
  },
  {
    key: "boostNetRate",
    label: "Prowizja Netto Boost",
    description:
      "Prowizja Netto od pierwszej Wizyty nowego Klienta pozyskanego przez Boost. Podatek VAT zostanie automatycznie przeliczony. Zmiana wartości obowiązuje od 1. dnia miesiąca, w którym ją wprowadzasz (premie, statysyki).",
    min: 0,
    max: 100,
    unit: " %",
  },
  {
    key: "googleReviewDiscount",
    label: "Rabat za opinię Google",
    description:
      "Podczas tworzenia Wizyty wraz z wykorzystaniem Rabatu za pozostawienie Opinii Google, program automatycznie przeliczy zniżkę i wartość końcową Wizyty.",
    min: 0,
    max: 100,
    unit: " %",
  },
  {
    key: "booksyHappyHours",
    label: "Rabat Booksy Happy Hours",
    description:
      "Podczas tworzenia Wizyty wraz z wykorzystaniem Rabatu Happy Hours, program automatycznie przeliczy zniżkę i wartość końcową Wizyty.",
    min: 0,
    max: 100,
    unit: " %",
  },
];

export function SettingsDashboard() {
  const { isCompany } = useRoles();
  const [tab, setTab] = useState<SettingsTab>("general");
  const [initialSettings, setInitialSettings] = useState<AppSettings>({
    id: 0,
    ...EMPTY_SETTINGS,
  });
  const [settings, setSettings] = useState<NewAppSettings>(EMPTY_SETTINGS);
  const { showAlert } = useAlert();

  const fetchSettings = async () => {
    AppSettingsService.getSettings()
      .then((data) => {
        setSettings(data);
        setInitialSettings(data);
      })
      .catch((error) => {
        setSettings(EMPTY_SETTINGS);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching settings:", error);
      });
  };

  const handleSettingChange = useCallback(
    (key: keyof NewAppSettings, value: number) => {
      setSettings((prev) => ({
        ...prev,
        [key]: value,
      }));
    },
    []
  );

  const handleSaveSettings = useCallback(async () => {
    let error;
    if (initialSettings.id != 0) {
      error = validateSettingsForm(settings, initialSettings);
    }
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      await AppSettingsService.updateSettings(
        settings as AppSettings | NewAppSettings
      );
      showAlert("Ustawienia pomyślnie zaktualizowane!", AlertType.SUCCESS);
      fetchSettings();
    } catch (error) {
      showAlert("Błąd aktualizacji ustawień!", AlertType.ERROR);
    }
  }, [settings, showAlert, initialSettings]);

  useEffect(() => {
    if (isCompany) fetchSettings();
  }, [isCompany]);

  useEffect(() => {
    if (!isCompany && tab !== "general") setTab("general");
  }, [isCompany, tab]);

  const isDirty = (Object.keys(settings) as (keyof NewAppSettings)[]).some(
    (key) => settings[key] !== initialSettings[key]
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <UnsavedChangesGuard when={isDirty} />
        <NavigationBar showSearchbar={false} />

        <div className="st-content width-93 flex-column f-1 min-height-0 g-1 mt-1 mb-2">
          <div className="flex align-items-center space-between g-1 flex-wrap">
            <div className="cat-seg-group flex align-items-center width-fit-content g-025">
              <button
                type="button"
                className={`cat-seg align-items-center pointer ${tab === "general" ? "active" : ""}`}
                onClick={() => setTab("general")}
              >
                Ustawienia ogólne
              </button>
              {isCompany && (
                <>
                  <button
                    type="button"
                    className={`cat-seg align-items-center pointer ${tab === "app" ? "active" : ""}`}
                    onClick={() => setTab("app")}
                  >
                    Ustawienia aplikacji
                  </button>
                  <button
                    type="button"
                    className={`cat-seg align-items-center pointer ${tab === "devices" ? "active" : ""}`}
                    onClick={() => setTab("devices")}
                  >
                    Urządzenia gabinetu
                  </button>
                </>
              )}
            </div>
            {tab === "app" && (
              <ActionButton text={"Zapisz"} onClick={handleSaveSettings} className={isDirty ? "ag-btn ag-yellow" : ""}>
                <Save className="dl-icon-1 pc-y" />
              </ActionButton>
            )}
          </div>

          {tab === "general" ? (
            <div className="st-grid st-general scrollbar f-1 min-height-0">
              <CardThemeSection />
            </div>
          ) : tab === "devices" ? (
            <div className="scrollbar f-1 min-height-0 dv-scroll">
              <DevicesSection />
            </div>
          ) : (
          <div className="st-grid scrollbar f-1 min-height-0">
            {SETTINGS.map((setting) => (
              <section className="st-card flex-column g-05" key={setting.key}>
                <Slider
                  onChange={(value) => handleSettingChange(setting.key, value)}
                  min={setting.min}
                  max={setting.max}
                  unit={setting.unit}
                  value={settings[setting.key]}
                  label={setting.label}
                  description={setting.description}
                />
              </section>
            ))}
          </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default SettingsDashboard;
