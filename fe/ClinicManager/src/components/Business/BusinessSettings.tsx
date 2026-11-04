import { useState, useEffect, useCallback } from "react";
import { useLocation } from "react-router-dom";
import { Save, ScrollText, Upload, Trash2 } from "lucide-react";
import TextInput from "../TextInput";
import UnsavedChangesGuard from "../UnsavedChangesGuard";
import { useAlert } from "../Alert/AlertProvider";
import { Slider } from "../Slider";
import ActionButton from "../ActionButton";
import { AlertType } from "../../models/alert";
import { validatePayoutSettings, validateStatGoalsSettings } from "../../utils/validators";
import { NewStatSettings, StatSettings } from "../../models/business_settings";
import StatSettingsService from "../../services/StatSettingsService";
import { MONTHS } from "../../utils/dateUtils";
import LogsModal from "../Popups/LogsModal";
import useLogoUpload, { LogoApi } from "../../hooks/useLogoUpload";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import QuestionnaireFieldsEditor, {
  QuestionnaireEnglishToggle,
  useQuestionnaireFieldsEditor,
} from "../ClientCards/QuestionnaireFieldsEditor";
import DrawingTemplatesSection from "./DrawingTemplatesSection";
import DrawingMarkersSection from "./DrawingMarkersSection";

const COMPANY_LOGO_API: LogoApi = {
  get: () => StatSettingsService.getLogo(),
  upload: (file) => StatSettingsService.uploadLogo(file),
  remove: () => StatSettingsService.deleteLogo(),
};

const FOOTER_LOGO_API: LogoApi = {
  get: () => StatSettingsService.getFooterLogo(),
  upload: (file) => StatSettingsService.uploadFooterLogo(file),
  remove: () => StatSettingsService.deleteFooterLogo(),
};

const EMPTY_SETTINGS: NewStatSettings = {
  bonusThreshold: 0,
  servicesRevenueGoal: 0,
  productsRevenueGoal: 0,
  saleBonusPayoutMonths: [],
  companyName: null,
  companyAddress: null,
  companyPostalCode: null,
  companyCity: null,
  companyNip: null,
  invoiceEmailBcc: null,
  companyPhone: null,
  companyWebsite: null,
  facebookUrl: null,
  instagramUrl: null,
  booksyUrl: null,
  footerCompanyName: null,
  footerAddress: null,
  footerPostalCode: null,
  footerCity: null,
};

type CompanyKey =
  | "companyName"
  | "companyAddress"
  | "companyPostalCode"
  | "companyCity"
  | "companyNip"
  | "invoiceEmailBcc"
  | "companyPhone"
  | "companyWebsite"
  | "facebookUrl"
  | "instagramUrl"
  | "booksyUrl"
  | "footerCompanyName"
  | "footerAddress"
  | "footerPostalCode"
  | "footerCity";

const COMPANY_FIELDS: { key: CompanyKey; label: string; placeholder: string }[] = [
  { key: "companyName", label: "Nazwa firmy", placeholder: "Nazwa firmy" },
  { key: "companyNip", label: "NIP", placeholder: "0000000000" },
  { key: "companyAddress", label: "Adres", placeholder: "Ulica i numer" },
  { key: "companyPostalCode", label: "Kod pocztowy", placeholder: "00-000" },
  { key: "companyCity", label: "Miasto", placeholder: "Miasto" },
  {
    key: "invoiceEmailBcc",
    label: "Kopia wysyłanych faktur",
    placeholder: "opcjonalnie",
  },
];

// Stopka jest niezalezna od danych z faktury - gabinet ma nazwe handlowa i
// adres salonu, ktore nie musza pokrywac sie z danymi podmiotu.
const FOOTER_FIELDS: { key: CompanyKey; label: string; placeholder: string }[] = [
  {
    key: "footerCompanyName",
    label: "Nazwa",
    placeholder: "Nazwa firmy",
  },
  { key: "footerAddress", label: "Adres", placeholder: "Ulica i numer" },
  { key: "footerPostalCode", label: "Kod pocztowy", placeholder: "00-000" },
  { key: "footerCity", label: "Miasto", placeholder: "Miasto" },
  { key: "companyPhone", label: "Telefon", placeholder: "+48 000 000 000" },
  { key: "companyWebsite", label: "Strona www", placeholder: "clinic.example.com" },
  {
    key: "facebookUrl",
    label: "Facebook",
    placeholder: "facebook.com/<TwojaStrona>",
  },
  {
    key: "instagramUrl",
    label: "Instagram",
    placeholder: "instagram.com/<TwojaStrona>",
  },
  {
    key: "booksyUrl",
    label: "Booksy",
    placeholder: "Link do profilu Booksy",
  },
];

// Pola z nazwami wlasnymi - reszta to NIP, adresy URL i email, gdzie
// automatyczna wielka litera psuje wartosc.
const CAPITALIZED_FIELDS = new Set<CompanyKey>([
  "companyName",
  "companyCity",
  "footerCompanyName",
  "footerCity",
]);

const PAYOUT_MONTH_ROWS: number[][] = [
  [1, 4, 7, 10],
  [2, 5, 8, 11],
  [3, 6, 9, 12],
];

type SliderKey = "bonusThreshold" | "servicesRevenueGoal" | "productsRevenueGoal";

interface SettingDefinition {
  key: SliderKey;
  label: string;
  description: string;
  min: number;
  max: number;
  step: number;
  unit: string;
}

const SETTINGS: SettingDefinition[] = [
  {
    key: "bonusThreshold",
    label: "Próg, od którego naliczana jest premia",
    description:
      "Przychód wygenerowany przez zrealizowane usługi w danym miesiącu, który nie wlicza się do premii. Innymi słowy, jeśli pracownik wygeneruje przychód poniżej tej wartości, nie otrzyma premii.  Zmiana wartości obowiązuje od 1. dnia miesiąca, w którym ją wprowadzasz (premie, statysyki).",
    min: 0,
    max: 10000,
    step: 100,
    unit: " zł",
  },
  {
    key: "servicesRevenueGoal",
    label: "Cel usług",
    description:
      "Miesięczny cel przychodu z usług dla pracownika pełnoetatowego, który powinien osiągnąć. Na podstawie ustawionego wymiaru czasu pracy pracownika program automatycznie przeliczy cel przychodu z usług.",
    min: 0,
    max: 50000,
    step: 100,
    unit: " zł",
  },
  {
    key: "productsRevenueGoal",
    label: "Cel sprzedaży produktów",
    description:
      "Miesięczny cel przychodu ze sprzedaży produktów dla pracownika pełnoetatowego, który powinien osiągnąć. Na podstawie ustawionego wymiaru czasu pracy pracownika program automatycznie przeliczy cel przychodu ze sprzedaży produktów.",
    min: 0,
    max: 10000,
    step: 100,
    unit: " zł",
  },
];

type SettingsTab = "stats" | "payouts" | "company" | "card";

type StatSettingsTab = Exclude<SettingsTab, "card">;

const TABS: { key: SettingsTab; label: string }[] = [
  { key: "stats", label: "Statystyki" },
  { key: "payouts", label: "Wypłaty" },
  { key: "company", label: "Dane" },
  { key: "card", label: "Karta Klienta" },
];

const TAB_KEYS: Record<StatSettingsTab, (keyof NewStatSettings)[]> = {
  stats: ["servicesRevenueGoal", "productsRevenueGoal"],
  payouts: ["bonusThreshold", "saleBonusPayoutMonths"],
  company: [...COMPANY_FIELDS, ...FOOTER_FIELDS].map((field) => field.key),
};

const STATS_SLIDERS = SETTINGS.filter((s) => TAB_KEYS.stats.includes(s.key));
const PAYOUT_SLIDERS = SETTINGS.filter((s) => TAB_KEYS.payouts.includes(s.key));

const pickKeys = (source: NewStatSettings, keys: (keyof NewStatSettings)[]): Partial<NewStatSettings> =>
  Object.fromEntries(keys.map((key) => [key, source[key]])) as Partial<NewStatSettings>;

const normalizeValue = (value: unknown): unknown =>
  Array.isArray(value) ? [...value].sort((a, b) => a - b) : value ?? null;

const isSameKeys = (a: NewStatSettings, b: NewStatSettings, keys: (keyof NewStatSettings)[]): boolean =>
  keys.every((key) => JSON.stringify(normalizeValue(a[key])) === JSON.stringify(normalizeValue(b[key])));

export function BusinessSettings() {
  const [initialSettings, setInitialSettings] = useState<StatSettings>({
    id: 0,
    hasCompanyLogo: false,
    hasEmailFooterLogo: false,
    ...EMPTY_SETTINGS,
  });
  const [settings, setSettings] = useState<NewStatSettings>(EMPTY_SETTINGS);
  const [logsPopupOpen, setLogsPopupOpen] = useState<boolean>(false);
  const [logoBusy, setLogoBusy] = useState<boolean>(false);
  const location = useLocation();
  const [tab, setTab] = useState<SettingsTab>(() =>
    (location.state as { settingsTab?: string } | null)?.settingsTab === "card" ? "card" : "stats"
  );
  const { showAlert } = useAlert();
  const { isCompact, isNarrow } = useBreakpoint();

  const showLogoError = useCallback(
    (message: string) => showAlert(message, AlertType.ERROR),
    [showAlert]
  );

  const companyLogo = useLogoUpload(COMPANY_LOGO_API, showLogoError);
  const footerLogo = useLogoUpload(FOOTER_LOGO_API, showLogoError);
  const questionnaire = useQuestionnaireFieldsEditor();

  const fetchStatSettings = async () => {
    StatSettingsService.getSettings()
      .then((data) => {
        setSettings(data);
        setInitialSettings(data);
        companyLogo.load(data.hasCompanyLogo);
        footerLogo.load(data.hasEmailFooterLogo);
      })
      .catch((error) => {
        setSettings(EMPTY_SETTINGS);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Stat Settings:", error);
      });
  };

  const handleSettingChange = useCallback((key: SliderKey, value: number) => {
    setSettings((prev) => ({
      ...prev,
      [key]: value,
    }));
  }, []);

  const handleCompanyFieldChange = useCallback(
    (key: CompanyKey, value: string) => {
      setSettings((prev) => ({
        ...prev,
        [key]: value.trim() === "" ? null : value,
      }));
    },
    []
  );

  const handleSaleBonusPayoutMonthChange = useCallback((months: number[]) => {
    setSettings((prev) => ({
      ...prev,
      saleBonusPayoutMonths: months,
    }));
  }, []);

  const isTabDirty = useCallback(
    (target: SettingsTab): boolean => {
      if (target === "card") return questionnaire.dirty;
      const changed = !isSameKeys(settings, initialSettings, TAB_KEYS[target]);
      if (target === "company") return changed || companyLogo.changed || footerLogo.changed;
      return changed;
    },
    [settings, initialSettings, questionnaire.dirty, companyLogo.changed, footerLogo.changed]
  );

  const anyDirty = TABS.some((t) => isTabDirty(t.key));

  const showSaveError = useCallback(
    (err: any) => {
      const data = err?.response?.data;
      showAlert(
        typeof data === "string" && data.trim() !== "" ? data : "Błąd aktualizacji ustawień!",
        AlertType.ERROR
      );
    },
    [showAlert]
  );

  const saveCardTab = useCallback(async () => {
    const error = questionnaire.dirty ? questionnaire.validate() : null;
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    setLogoBusy(true);
    try {
      if (questionnaire.dirty) {
        await questionnaire.save();
      }
      showAlert("Ustawienia karty klienta zapisane!", AlertType.SUCCESS);
    } catch (err) {
      showSaveError(err);
    } finally {
      setLogoBusy(false);
    }
  }, [questionnaire, showAlert, showSaveError]);

  const saveStatSettingsTab = useCallback(
    async (target: StatSettingsTab) => {
      const error =
        target === "stats"
          ? validateStatGoalsSettings(settings)
          : target === "payouts"
            ? validatePayoutSettings(settings)
            : null;
      if (error) {
        showAlert(error, AlertType.ERROR);
        return;
      }
      const keys = TAB_KEYS[target];
      setLogoBusy(true);
      try {
        if (target === "company") {
          await companyLogo.commit();
          await footerLogo.commit();
        }
        const payload =
          initialSettings.id === 0 ? settings : { ...initialSettings, ...pickKeys(settings, keys) };
        await StatSettingsService.updateSettings(payload as StatSettings | NewStatSettings);
        if (target === "company") {
          companyLogo.reset();
          footerLogo.reset();
        }
        const saved = await StatSettingsService.getSettings();
        setInitialSettings(saved);
        setSettings((prev) => ({ ...prev, ...pickKeys(saved, keys) }));
        if (target === "company") {
          companyLogo.load(saved.hasCompanyLogo);
          footerLogo.load(saved.hasEmailFooterLogo);
        }
        showAlert("Ustawienia pomyślnie zaktualizowane!", AlertType.SUCCESS);
      } catch (err) {
        showSaveError(err);
      } finally {
        setLogoBusy(false);
      }
    },
    [settings, initialSettings, companyLogo, footerLogo, showAlert, showSaveError]
  );

  const handleSave = useCallback(() => {
    if (!isTabDirty(tab)) {
      showAlert("Brak zmian!", AlertType.ERROR);
      return;
    }
    if (tab === "card") {
      saveCardTab();
    } else {
      saveStatSettingsTab(tab);
    }
  }, [tab, isTabDirty, showAlert, saveCardTab, saveStatSettingsTab]);

  useEffect(() => {
    fetchStatSettings();
  }, []);

  const renderSlider = (setting: SettingDefinition) => (
    <section className="st-card flex-column g-05" key={setting.key}>
      <Slider
        onChange={(value) => handleSettingChange(setting.key, value)}
        min={setting.min}
        max={setting.max}
        step={setting.step}
        unit={setting.unit}
        value={settings[setting.key]}
        label={setting.label}
        description={setting.description}
      />
    </section>
  );

  const renderField = (field: {
    key: CompanyKey;
    label: string;
    placeholder: string;
  }) => (
    <label className="st-company-field flex-column g-025" key={field.key}>
      <span className="st-company-label">{field.label}</span>
      <TextInput
        value={settings[field.key] ?? ""}
        onSelect={(value) => handleCompanyFieldChange(field.key, value as string)}
        placeholder={field.placeholder}
        className="in-full"
        noCapitalize={!CAPITALIZED_FIELDS.has(field.key)}
      />
    </label>
  );

  const renderLogoRow = (
    label: string,
    hint: string,
    logo: ReturnType<typeof useLogoUpload>
  ) => (
    <div className="st-logo-row flex align-items-center g-1">
      <span className="st-company-label">{label}</span>
      {logo.url ? (
        <img src={logo.url} alt={label} className="st-logo-preview" />
      ) : (
        <span className="st-logo-empty italic">Brak logo</span>
      )}
      {logo.changed && (
        <span className="st-logo-pending italic">
          niezapisane - kliknij Zapisz
        </span>
      )}
      <input
        ref={logo.inputRef}
        type="file"
        accept="image/png"
        hidden
        onChange={logo.select}
      />
      <ActionButton
        text={logo.pendingFile ? "Zmień plik" : "Wybierz logo"}
        iconTitle={hint}
        onClick={() => logo.inputRef.current?.click()}
        disabled={logoBusy}
      >
        <Upload className="dl-icon-1 pc-y" />
      </ActionButton>
      {logo.url && (
        <ActionButton
          iconTitle="Usuń logo"
          onClick={logo.clear}
          disabled={logoBusy}
          disableText={true}
          disableBorder={true}
        >
          <Trash2 className="dl-icon c-red" />
        </ActionButton>
      )}
    </div>
  );

  return (
    <div className="st-content width-93 flex-column f-1 min-height-0 g-1 mt-1 mb-2">
      <UnsavedChangesGuard when={anyDirty} />
      <div className="flex align-items-center space-between g-1">
        <h2 className="st-title m-0">Ustawienia Firmowe</h2>
        <div className="flex align-items-center g-1">
          <ActionButton
            text={"Zapisz"}
            onClick={handleSave}
            disabled={logoBusy}
            className={anyDirty ? "ag-btn ag-yellow" : ""}
          >
            <Save className="dl-icon-1 pc-y" />
          </ActionButton>
          <ActionButton
            text={isCompact ? "Logi" : "Wyświetl logi"}
            iconTitle="Wyświetl logi"
            onClick={() => setLogsPopupOpen(true)}
          >
            <ScrollText className="dl-icon-1" />
          </ActionButton>
        </div>
      </div>

      <div className="cat-seg-group st-tabs flex align-items-center width-fit-content g-025">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={`cat-seg align-items-center pointer ${tab === t.key ? "active" : ""}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
            {isTabDirty(t.key) && <span className="st-tab-dirty" title="Niezapisane zmiany" />}
          </button>
        ))}
      </div>

      <div className="st-grid scrollbar f-1 min-height-0">
        {tab === "stats" && STATS_SLIDERS.map(renderSlider)}

        {tab === "payouts" && (
          <>
            {PAYOUT_SLIDERS.map(renderSlider)}
            <section className="st-card flex-column g-1">
              <span className="st-card-label">Miesiące wypłaty premii kwartalnej</span>
              <div className="flex-column g-05">
                {PAYOUT_MONTH_ROWS.map((row) => {
                  const isRowSelected = row.every((m) => settings.saleBonusPayoutMonths.includes(m));
                  return (
                    <div key={row[0]} className="cat-seg-group st-month-row flex align-items-center g-025">
                      {row.map((month) => (
                        <button
                          key={month}
                          type="button"
                          className={`cat-seg align-items-center pointer ${isRowSelected ? "active" : ""}`}
                          onClick={() => handleSaleBonusPayoutMonthChange(row)}
                          title={MONTHS[month - 1].name}
                        >
                          {isNarrow ? MONTHS[month - 1].name.slice(0, 3) : MONTHS[month - 1].name}
                        </button>
                      ))}
                    </div>
                  );
                })}
              </div>
              <span className="st-card-desc">
                Wybierz kwartał, w którym wypłacana jest premia od sprzedaży produktów. Zaznaczenie
                dowolnego miesiąca wybiera cały cykl.
              </span>
            </section>
          </>
        )}

        {tab === "company" && (
          <>
            <section className="st-card st-company flex-column g-1">
              <span className="st-card-label">Dane firmy</span>
              <div className="st-company-grid">{COMPANY_FIELDS.map(renderField)}</div>
              {renderLogoRow("Logo faktury", "Wybierz logo drukowane na fakturze (PNG, do 1 MB)", companyLogo)}
              <span className="st-card-desc">
                Dane wykorzystywane jako Sprzedawca na fakturach imiennych. Logo (PNG, maks. 1 MB) trafia w
                prawy górny róg dokumentu.
              </span>
            </section>
            <section className="st-card st-footer flex-column g-1">
              <span className="st-card-label">Dane mailingowe</span>
              <div className="st-company-grid">{FOOTER_FIELDS.map(renderField)}</div>
              {renderLogoRow("Logo stopki", "Wybierz logo stopki wiadomości email (PNG, do 1 MB)", footerLogo)}
              <span className="st-card-desc">
                Stopka maili wysyłanych do klientów, niezależna od danych na fakturze. Puste pole ukrywa dany
                element stopki - brak nazwy ukrywa ją w całości.
              </span>
            </section>
          </>
        )}

        {tab === "card" && <DrawingTemplatesSection />}

        {tab === "card" && <DrawingMarkersSection />}

        {tab === "card" && (
          <section className="st-card st-questionnaire flex-column g-1">
            <div className="flex align-items-center space-between g-1 flex-wrap">
              <span className="st-card-label">Ankieta karty klienta</span>
              <QuestionnaireEnglishToggle editor={questionnaire} />
            </div>
            <QuestionnaireFieldsEditor editor={questionnaire} />
            <span className="st-card-desc">
              Pola, które mają już odpowiedzi, można tylko zarchiwizować
              (odpowiedzi zostają w kartach) i nie można zmienić ich typu. Wersja angielska dodaje w karcie
              przełącznik PL / EN - brak tłumaczenia oznacza polską nazwę.
            </span>
          </section>
        )}
      </div>

      {logsPopupOpen && <LogsModal onClose={() => setLogsPopupOpen(false)} />}
    </div>
  );
}

export default BusinessSettings;
