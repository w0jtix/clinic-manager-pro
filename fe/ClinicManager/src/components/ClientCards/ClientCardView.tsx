import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { isUnsavedChangesPromptSuppressed } from "../../utils/unsavedChangesPrompt";
import { useBlocker, useLocation, useNavigate, useParams } from "react-router-dom";
import ReactDOM from "react-dom";
import { CalendarPlus, ChevronLeft, History, RotateCcw, Save, TriangleAlert, UserRound, UserX, X } from "lucide-react";
import RowActionMenu from "../RowActionMenu";
import CardHistoryModal from "./CardHistoryModal";
import NavigationBar from "../NavigationBar";
import Modal from "../Modal";
import TextInput from "../TextInput";
import ActionButton from "../ActionButton";
import DuplicateClientNotice from "./DuplicateClientNotice";
import BirthDateField from "./BirthDateField";
import RegulationsSegment from "./RegulationsSegment";
import DrawingTab, { DrawingOverview, OpenSheet, writeDrawingSelection } from "./DrawingTab";
import RemarksList, { normalizeRemarks } from "./RemarksList";
import TherapyTab from "./TherapyTab";
import QuestionnaireForm, { formatUpdatedAt, QuestionnaireLanguageToggle } from "./QuestionnaireForm";
import { Questionnaire, QuestionnaireItem } from "../../models/questionnaire";
import {
  getQuestionnaireConflict,
  isSameAnswers,
  toAnswers,
  QuestionnaireLanguage,
  validateQuestionnaireAnswers,
} from "../../utils/questionnaireUtils";
import { ClientCardPrefill, parseInitialText } from "./NewClientCardModal";
import ClientCardService from "../../services/ClientCardService";
import ClientService from "../../services/ClientService";
import { ClientCard, ClientCardData } from "../../models/client-card";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useRoles } from "../../hooks/useRoles";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useExtendedInactivityTimeout } from "../../hooks/useInactivityTimer";
import { useSystemBarsColor } from "../../hooks/useSystemBarsColor";
import { useCardTheme } from "../../hooks/useCardTheme";
import { useLightPortals } from "../../hooks/useLightPortals";
import { useRestorableState } from "../../hooks/useRestorableState";
import VisitModal from "../Popups/VisitModal";
import { registerPendingSave } from "../../utils/pendingSaves";
import { validateClientCardData } from "../../utils/validators";
import { formatDate } from "../../utils/dateUtils";
import {
  buildDuplicateClientMessage,
  getDuplicateClientError,
} from "../../utils/clientDuplicateUtils";

type CardTab = "data" | "questionnaire" | "drawing" | "therapy";

const TABS: { key: CardTab; label: string }[] = [
  { key: "data", label: "Dane klienta" },
  { key: "questionnaire", label: "Ankieta" },
  { key: "drawing", label: "Rysunek" },
  { key: "therapy", label: "Wpisy" },
];

const QUESTIONNAIRE_CONFLICT_MESSAGE =
  "Ankieta została zmieniona na innym urządzeniu. Wczytaj aktualną wersję, aby kontynuować.";

const getPushedTab = (state: unknown): CardTab | null =>
  (state as { cardTab?: CardTab } | null)?.cardTab ?? null;

const getTabDepth = (state: unknown): number => (state as { cardTabDepth?: number } | null)?.cardTabDepth ?? 0;

const getInitialTab = (state: unknown): CardTab => {
  const tab = getPushedTab(state) ?? (state as { tab?: CardTab } | null)?.tab;
  return tab === "questionnaire" || tab === "drawing" || tab === "therapy" ? tab : "data";
};

const toFormData = (card: ClientCard): ClientCardData => ({
  firstName: card.firstName,
  lastName: card.lastName,
  birthDate: card.birthDate,
  phoneNumber: card.phoneNumber,
  redFlag: card.redFlag,
  signedRegulations: card.signedRegulations,
  remarks: card.remarks,
});

const isSameData = (a: ClientCardData, b: ClientCardData): boolean =>
  a.firstName === b.firstName &&
  a.lastName === b.lastName &&
  (a.birthDate ?? null) === (b.birthDate ?? null) &&
  (a.phoneNumber || null) === (b.phoneNumber || null) &&
  a.redFlag === b.redFlag &&
  a.signedRegulations === b.signedRegulations &&
  normalizeRemarks(a.remarks) === normalizeRemarks(b.remarks);

const toPayload = (data: ClientCardData): ClientCardData => ({
  ...data,
  firstName: data.firstName.trim(),
  lastName: data.lastName.trim(),
  phoneNumber: data.phoneNumber?.trim() || null,
  remarks: normalizeRemarks(data.remarks),
});

const CLIENT_REMARKS_MAX_LENGTH = 2000;

const EMPTY_FORM: ClientCardData = {
  firstName: "",
  lastName: "",
  birthDate: null,
  phoneNumber: null,
  redFlag: false,
  signedRegulations: false,
  remarks: null,
};

export function ClientCardView() {
  const { clientId: clientIdParam } = useParams();
  const isNew = clientIdParam === "new";
  const clientId = Number(clientIdParam);
  const navigate = useNavigate();
  const location = useLocation();
  const { isMobile, isNarrow } = useBreakpoint();
  const { showAlert } = useAlert();
  const { isMaster } = useRoles();
  const [historyOpen, setHistoryOpen] = useState<boolean>(false);
  const [visitModalOpen, setVisitModalOpen] = useState<boolean>(false);

  const [card, setCard] = useState<ClientCard | null>(null);
  const [notFound, setNotFound] = useState<boolean>(false);
  const [form, setForm] = useState<ClientCardData | null>(null);
  const [baseForm, setBaseForm] = useState<ClientCardData | null>(null);
  const [tab, setTab] = useRestorableState<CardTab>("tab", () => getInitialTab(location.state));
  const tabClientIdRef = useRef<string | undefined>(clientIdParam);
  const [questionnaire, setQuestionnaire] = useState<Questionnaire | null>(null);
  const [answers, setAnswers] = useState<QuestionnaireItem[] | null>(null);
  const [baseAnswers, setBaseAnswers] = useState<QuestionnaireItem[] | null>(null);
  const [questionnaireLoadError, setQuestionnaireLoadError] = useState<boolean>(false);
  const [questionnaireConflict, setQuestionnaireConflict] = useState<boolean>(false);
  const [language, setLanguage] = useState<QuestionnaireLanguage>("pl");
  const [unavailable, setUnavailable] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [restoring, setRestoring] = useState<boolean>(false);
  const [lastSaved, setLastSaved] = useState<{ sections: CardTab[]; at: Date } | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const hiddenSaveSentRef = useRef<boolean>(false);
  const hiddenQuestionnaireSentRef = useRef<boolean>(false);
  const allowNavigationRef = useRef<boolean>(false);

  useExtendedInactivityTimeout();
  const { theme: cardTheme } = useCardTheme();
  const themeClass = cardTheme === "LIGHT" ? "cc-light" : "";

  useLightPortals(visitModalOpen && themeClass !== "");
  useSystemBarsColor(isMobile, cardTheme === "LIGHT" ? "--card-paper" : "--items-background");

  useEffect(() => {
    if (!isMobile) return;
    const root = document.documentElement;
    root.classList.add("cc-sheet-open");
    window.scrollTo(0, 0);
    return () => root.classList.remove("cc-sheet-open");
  }, [isMobile]);

  const isDeleted = !!card?.isDeleted;
  const isReadOnly = isDeleted || unavailable;
  const isDataDirty = !!form && !!baseForm && !isReadOnly && !isSameData(form, baseForm);
  const isQuestionnaireDirty =
    !!answers && !!baseAnswers && !isReadOnly && !isSameAnswers(answers, baseAnswers);
  const [isDrawingDirty, setDrawingDirty] = useState<boolean>(false);
  const drawingSaveRef = useRef<(() => Promise<boolean>) | null>(null);
  const isDirty = isDataDirty || isQuestionnaireDirty || isDrawingDirty;

  const handleDrawingDirtyChange = useCallback(
    (dirty: boolean, saveDrawing: (() => Promise<boolean>) | null) => {
      drawingSaveRef.current = saveDrawing;
      setDrawingDirty(dirty);
    },
    []
  );

  const applyQuestionnaire = useCallback((data: Questionnaire) => {
    setQuestionnaire(data);
    setAnswers(data.items);
    setBaseAnswers(data.items);
    setQuestionnaireConflict(false);
    setQuestionnaireLoadError(false);
  }, []);

  const loadQuestionnaire = useCallback(
    () =>
      ClientCardService.getQuestionnaire(clientId)
        .then(applyQuestionnaire)
        .catch((error) => {
          console.error("Error fetching Questionnaire:", error);
          setQuestionnaireLoadError(true);
        }),
    [clientId, applyQuestionnaire]
  );

  const applyCard = useCallback((data: ClientCard, prefill?: ClientCardPrefill | null) => {
    const loaded = toFormData(data);
    setCard(data);
    setBaseForm(loaded);
    setForm(
      prefill && !data.isDeleted
        ? {
            ...loaded,
            birthDate: loaded.birthDate ?? prefill.birthDate,
            phoneNumber: loaded.phoneNumber || prefill.phoneNumber,
          }
        : loaded
    );
  }, []);

  const loadCard = useCallback(
    (prefill?: ClientCardPrefill | null) =>
      ClientCardService.getCard(clientId)
        .then((data) => {
          applyCard(data, prefill);
          setNotFound(false);
        })
        .catch((error) => {
          console.error("Error fetching Client Card:", error);
          setNotFound(true);
        }),
    [clientId, applyCard]
  );

  useEffect(() => {
    allowNavigationRef.current = false;
    if (tabClientIdRef.current !== clientIdParam) {
      tabClientIdRef.current = clientIdParam;
      setTab(getInitialTab(location.state));
    }
    setQuestionnaire(null);
    setAnswers(null);
    setBaseAnswers(null);
    setQuestionnaireLoadError(false);
    setQuestionnaireConflict(false);
    setUnavailable(false);
    setLanguage("pl");
    setLastSaved(null);
    setSaveError(null);
    if (isNew) {
      const initialText = (location.state as { initialText?: string } | null)?.initialText ?? "";
      setCard(null);
      setNotFound(false);
      setBaseForm(EMPTY_FORM);
      setForm({ ...EMPTY_FORM, ...parseInitialText(initialText) });
      return;
    }
    const prefill = (location.state as { prefill?: ClientCardPrefill } | null)?.prefill ?? null;
    loadCard(prefill);
  }, [clientIdParam]);

  useEffect(() => {
    if (tab !== "questionnaire" || isNew || !card || questionnaire || questionnaireLoadError) return;
    loadQuestionnaire();
  }, [tab, isNew, card, questionnaire, questionnaireLoadError, loadQuestionnaire]);

  const openCard = useCallback(
    (targetId: number, prefill?: ClientCardPrefill, nextTab?: CardTab) => {
      allowNavigationRef.current = true;
      const state = prefill || nextTab ? { prefill, tab: nextTab } : null;
      navigate(`/client-cards/${targetId}`, { replace: true, state });
    },
    [navigate]
  );

  const openEntryDate = (location.state as { newEntryDate?: string } | null)?.newEntryDate ?? null;
  const backLabel = (location.state as { backLabel?: string } | null)?.backLabel ?? "Karty Klienta";
  const highlightVisitDate =
    (location.state as { highlightVisitDate?: string } | null)?.highlightVisitDate ?? null;

  const handleOpenEntryHandled = useCallback(() => {
    const nextState: Record<string, unknown> = { ...((location.state as object | null) ?? {}) };
    delete nextState.newEntryDate;
    allowNavigationRef.current = true;
    navigate(`${location.pathname}${location.search}`, { replace: true, state: nextState });
  }, [navigate, location.pathname, location.search, location.state]);

  const failSave = useCallback(
    (message: string) => {
      setSaveError(message);
      showAlert(message, AlertType.ERROR);
      return false;
    },
    [showAlert]
  );

  const failDeletedClient = useCallback(() => {
    setUnavailable(true);
    if (isMaster) {
      setCard((prev) => (prev ? { ...prev, isDeleted: true } : prev));
      return failSave("Klient został usunięty. Karta jest tylko do odczytu.");
    }
    return failSave("Ta karta nie jest już dostępna.");
  }, [isMaster, failSave]);

  const saveData = useCallback(
    async (openCreatedCard: boolean, tabAfterCreate?: CardTab): Promise<boolean> => {
      if (!form) return true;
      const error = validateClientCardData(form);
      if (error) return failSave(error);
      try {
        const payload = toPayload(form);
        if (isNew) {
          const created = await ClientCardService.createCard({
            firstName: payload.firstName,
            lastName: payload.lastName,
            birthDate: payload.birthDate,
            phoneNumber: payload.phoneNumber,
            signedRegulations: payload.signedRegulations,
          });
          showAlert(`Karta klienta ${created.firstName} ${created.lastName} utworzona!`, AlertType.SUCCESS);
          setBaseForm(form);
          if (openCreatedCard) openCard(created.clientId, undefined, tabAfterCreate);
          return true;
        }
        const updated = await ClientCardService.updateClientData(clientId, payload);
        applyCard(updated);
        return true;
      } catch (err) {
        console.error("Error saving Client Card:", err);
        const duplicate = getDuplicateClientError(err);
        if (duplicate) return failSave(buildDuplicateClientMessage(duplicate, isMaster));
        if ((err as any)?.response?.status === 409) return failDeletedClient();
        return failSave("Nie zapisano zmian. Spróbuj ponownie.");
      }
    },
    [form, isNew, clientId, applyCard, showAlert, isMaster, openCard, failSave, failDeletedClient]
  );

  const saveQuestionnaire = useCallback(async (): Promise<boolean> => {
    if (!answers || !questionnaire) return true;
    const error = validateQuestionnaireAnswers(answers);
    if (error) return failSave(error);
    const sent = answers;
    try {
      const saved = await ClientCardService.updateQuestionnaire(clientId, {
        expectedVersion: questionnaire.version,
        answers: toAnswers(sent),
      });
      setQuestionnaire(saved);
      setBaseAnswers(saved.items);
      setAnswers((prev) => (prev && isSameAnswers(prev, sent) ? saved.items : prev));
      return true;
    } catch (err) {
      console.error("Error saving Questionnaire:", err);
      if (getQuestionnaireConflict(err)) {
        setQuestionnaireConflict(true);
        return failSave(QUESTIONNAIRE_CONFLICT_MESSAGE);
      }
      if ((err as any)?.response?.status === 409) return failDeletedClient();
      return failSave("Nie zapisano ankiety. Spróbuj ponownie.");
    }
  }, [answers, questionnaire, clientId, failSave, failDeletedClient]);

  const save = useCallback(
    async (openCreatedCard = true, tabAfterCreate?: CardTab): Promise<boolean> => {
      if (isReadOnly || (!isNew && !card)) return true;
      if (isNew && !isDataDirty && !tabAfterCreate) return true;
      if (!isNew && !isDirty) return true;
      setSaving(true);
      try {
        const sections: CardTab[] = [];
        if (isNew || isDataDirty) {
          if (!(await saveData(openCreatedCard, tabAfterCreate))) return false;
          if (isNew) return true;
          sections.push("data");
        }
        if (isQuestionnaireDirty) {
          if (!(await saveQuestionnaire())) return false;
          sections.push("questionnaire");
        }
        if (isDrawingDirty && drawingSaveRef.current) {
          if (!(await drawingSaveRef.current())) return false;
          sections.push("drawing");
        }
        setLastSaved({ sections, at: new Date() });
        setSaveError(null);
        return true;
      } finally {
        setSaving(false);
      }
    },
    [isReadOnly, isNew, card, isDirty, isDataDirty, isQuestionnaireDirty, isDrawingDirty, saveData, saveQuestionnaire]
  );

  const saveRef = useRef(save);
  const dirtyRef = useRef(isDirty);
  saveRef.current = save;
  dirtyRef.current = isDirty;

  useEffect(
    () =>
      registerPendingSave(async () => {
        if (dirtyRef.current) await saveRef.current();
      }),
    []
  );

  useEffect(() => {
    if (!isDirty) return;
    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      if (isUnsavedChangesPromptSuppressed()) return;
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [isDirty]);

  useEffect(() => {
    const handleVisibility = () => {
      if (isNew) return;
      if (document.visibilityState === "hidden") {
        if (isDataDirty && form && !validateClientCardData(form)) {
          ClientCardService.updateClientDataOnPageHide(clientId, toPayload(form));
          hiddenSaveSentRef.current = true;
        }
        if (isQuestionnaireDirty && answers && questionnaire && !validateQuestionnaireAnswers(answers)) {
          ClientCardService.updateQuestionnaireOnPageHide(clientId, {
            expectedVersion: questionnaire.version,
            answers: toAnswers(answers),
          });
          hiddenQuestionnaireSentRef.current = true;
        }
        return;
      }
      if (hiddenSaveSentRef.current) {
        hiddenSaveSentRef.current = false;
        ClientCardService.getCard(clientId)
          .then((data) => {
            const server = toFormData(data);
            setCard(data);
            setBaseForm(server);
            if (form && isSameData(toPayload(form), server)) {
              setForm(server);
              setLastSaved({ sections: ["data"], at: new Date() });
            }
          })
          .catch(() => undefined);
      }
      if (hiddenQuestionnaireSentRef.current) {
        hiddenQuestionnaireSentRef.current = false;
        ClientCardService.getQuestionnaire(clientId)
          .then((data) => {
            if (answers && isSameAnswers(answers, data.items)) {
              applyQuestionnaire(data);
              setLastSaved({ sections: ["questionnaire"], at: new Date() });
            }
          })
          .catch(() => undefined);
      }
    };
    document.addEventListener("visibilitychange", handleVisibility);
    return () => document.removeEventListener("visibilitychange", handleVisibility);
  }, [
    clientId,
    form,
    isNew,
    isDataDirty,
    isQuestionnaireDirty,
    answers,
    questionnaire,
    applyQuestionnaire,
  ]);

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      !allowNavigationRef.current &&
      isDirty &&
      (currentLocation.pathname !== nextLocation.pathname ||
        getTabDepth(currentLocation.state) !== getTabDepth(nextLocation.state))
  );
  useLightPortals(blocker.state === "blocked" && themeClass !== "");

  const tabDepth = getTabDepth(location.state);

  const handleClose = useCallback(() => {
    const historyIndex = (window.history.state as { idx?: number } | null)?.idx ?? 0;
    const steps = tabDepth + 1;
    if (historyIndex >= steps) {
      navigate(-steps);
    } else {
      navigate("/client-cards", { replace: true });
    }
  }, [navigate, tabDepth]);

  const locationRef = useRef<{ key: string; clientId?: string }>({ key: location.key, clientId: clientIdParam });
  useEffect(() => {
    const previous = locationRef.current;
    if (previous.key === location.key) return;
    locationRef.current = { key: location.key, clientId: clientIdParam };
    allowNavigationRef.current = false;
    if (previous.clientId === clientIdParam) setTab(getInitialTab(location.state));
  }, [location.key, location.state, clientIdParam, setTab]);

  const [openDrawingEditor, setOpenDrawingEditor] = useState<OpenSheet | null>(null);

  const handleTabChange = useCallback(
    async (next: CardTab): Promise<boolean> => {
      if (next === tab) return false;
      if (isNew) {
        if (next !== "data") await save(true, next);
        return false;
      }
      if (isDirty && !(await save())) return false;
      allowNavigationRef.current = true;
      navigate(`${location.pathname}${location.search}`, {
        state: { ...((location.state as object | null) ?? {}), cardTab: next, cardTabDepth: tabDepth + 1 },
      });
      setTab(next);
      return true;
    },
    [tab, isNew, isDirty, save, tabDepth, navigate, location.pathname, location.search, location.state]
  );

  const updateAnswer = useCallback(
    (fieldId: number, patch: Pick<QuestionnaireItem, "checked" | "text">) => {
      setAnswers((prev) => prev?.map((item) => (item.fieldId === fieldId ? { ...item, ...patch } : item)) ?? prev);
      setSaveError(null);
    },
    []
  );

  const reloadQuestionnaire = useCallback(async () => {
    await loadQuestionnaire();
    setSaveError(null);
  }, [loadQuestionnaire]);

  const handleRestore = useCallback(async () => {
    if (!card || restoring) return;
    setRestoring(true);
    try {
      await ClientService.restoreClient(card.clientId);
      showAlert(`Klient ${card.firstName} ${card.lastName} przywrócony!`, AlertType.SUCCESS);
      await loadCard();
      setUnavailable(false);
      setSaveError(null);
    } catch (err) {
      const duplicate = getDuplicateClientError(err);
      showAlert(
        duplicate ? buildDuplicateClientMessage(duplicate, true) : "Błąd przywracania klienta.",
        AlertType.ERROR
      );
    } finally {
      setRestoring(false);
    }
  }, [card, restoring, showAlert, loadCard]);

  const update = useCallback(<K extends keyof ClientCardData>(key: K, value: ClientCardData[K]) => {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
    setSaveError(null);
  }, []);

  const statusText = useMemo(() => {
    if (isReadOnly) return "Tylko do odczytu";
    if (saving) return "Zapisywanie…";
    if (saveError) return "Nie zapisano";
    if (isNew) return isDirty ? "Nowa karta - niezapisana" : "Nowa karta";
    if (isDirty) return "Niezapisane zmiany";
    if (lastSaved) {
      const label = TABS.filter((t) => lastSaved.sections.includes(t.key))
        .map((t) => t.label)
        .join(", ");
      const time = lastSaved.at.toLocaleTimeString("pl-PL", { hour: "2-digit", minute: "2-digit" });
      return `Zapisano zmiany - ${label} (${time})`;
    }
    return "Brak zmian";
  }, [isReadOnly, saving, saveError, isDirty, lastSaved, isNew]);

  const title = isNew ? "Nowa karta pacjenta" : card ? `${card.firstName} ${card.lastName}` : "Karta klienta";

  const canSave = (!!card || isNew) && !isReadOnly;

  const identityMeta = card
    ? [
        card.birthDate ? `ur. ${formatDate(card.birthDate)}` : null,
        card.phoneNumber ? `tel. ${card.phoneNumber}` : null,
      ]
        .filter(Boolean)
        .join(" · ")
    : "";

  const englishEnabled = questionnaire?.englishEnabled ?? false;
  const questionnaireLanguage: QuestionnaireLanguage = englishEnabled ? language : "pl";
  const questionnaireUpdatedAt = tab === "questionnaire" ? questionnaire?.updatedAt ?? null : null;

  const languageToggle = tab === "questionnaire" && englishEnabled && (
    <QuestionnaireLanguageToggle language={language} onChange={setLanguage} />
  );

  const saveStatus = (
    <div className="cc-save-info flex-column">
      <span className={`cc-save-status ${isDirty ? "dirty" : ""} ${saveError ? "error" : ""}`}>
        {isDirty && !saveError && <TriangleAlert className="dl-icon-1 cc-save-status-icon" />}
        {statusText}
      </span>
      {questionnaireUpdatedAt && (
        <span className="cc-save-sub">Ostatnia aktualizacja: {formatUpdatedAt(questionnaireUpdatedAt)}</span>
      )}
    </div>
  );

  const saveButton = (
    <ActionButton
      text="Zapisz"
      onClick={() => save()}
      disabled={saving || !isDirty}
      className={`cl-filter cc-save-btn ${isDirty && !saving ? "active" : ""}`}
    >
      <Save className="dl-icon-1 c-green" />
    </ActionButton>
  );

  const visitButton = !isNew && !!card && !isDeleted && (
    <ActionButton
      iconTitle="Nowa wizyta"
      disableText={true}
      className="cl-filter cc-visit-btn"
      onClick={() => setVisitModalOpen(true)}
    >
      <CalendarPlus className="dl-icon-1 c-green" />
    </ActionButton>
  );

  const visitModal = visitModalOpen && (
    <VisitModal onClose={() => setVisitModalOpen(false)} preselectedClientId={clientId} className={themeClass} />
  );

  const headerSaveControls = !isMobile && canSave && (
    <div className="cc-head-save flex align-items-center g-1">
      {saveStatus}
      {saveButton}
      {visitButton}
    </div>
  );

  const header = (card || isNew) && (
    <div className="cc-head flex-column g-05">
      {card ? (
        <div className="cc-head-row flex align-items-center space-between g-1">
          <div className="flex-column g-025 mw-0">
            {!isMobile && themeClass && <span className="cc-head-eyebrow">Karta klienta</span>}
            {!isMobile && <span className="cc-head-name">{title}</span>}
            <span className="cc-head-meta">
              {identityMeta}
              <span className="cc-head-visit">
                {identityMeta && <span className="cc-head-sep"> · </span>}
                {card.lastAbsenceUnpaidDate ? (
                  <span className="cc-head-absence">
                    <span className="c-red nowrap">{`ostatnia wizyta ${formatDate(card.lastAbsenceUnpaidDate)}`}</span>
                    <span className="cc-head-absence-tag">
                      <UserX className="dl-icon c-red" />
                      <span className="cl-chip absence">Nieobecność</span>
                    </span>
                  </span>
                ) : card.lastVisitDate ? (
                  `ostatnia wizyta ${formatDate(card.lastVisitDate)}`
                ) : (
                  "brak wizyt"
                )}
              </span>
            </span>
          </div>
          <div className="cc-head-actions flex-column g-05">
            <div className="flex align-items-center g-075">
              {languageToggle}
              <ActionButton
                text="Strona klienta"
                iconTitle="Otwórz stronę klienta"
                disableText={isNarrow}
                onClick={() => navigate(`/client/${card.clientId}`)}
              >
                <UserRound className="dl-icon-1" />
              </ActionButton>
              {isMaster && (
                <RowActionMenu
                  alwaysMenu={true}
                  items={[
                    {
                      label: "Historia",
                      icon: <History className="dl-icon pc-y" />,
                      onClick: () => setHistoryOpen(true),
                    },
                  ]}
                />
              )}
            </div>
            {headerSaveControls}
          </div>
        </div>
      ) : (
        !isMobile && (
          <div className="cc-head-row flex align-items-center space-between g-1">
            <span className="cc-head-name">{title}</span>
            {headerSaveControls}
          </div>
        )
      )}
      {isDeleted && (
        <div className="cl-deleted-banner flex align-items-center space-between g-075">
          <span className="flex align-items-center g-05">
            <TriangleAlert className="dl-icon-1 c-red" />
            <span className="flex-column">
              <span className="cl-deleted-title">Klient usunięty</span>
              <span className="cl-deleted-sub">Karta jest tylko do odczytu. Przywróć klienta, aby ją edytować.</span>
            </span>
          </span>
          {isMaster && (
            <ActionButton text="Przywróć klienta" onClick={handleRestore} disabled={restoring} className="cl-restore">
              <RotateCcw className="dl-icon-1 c-green" />
            </ActionButton>
          )}
        </div>
      )}
      {isMobile && canSave && (
        <div className="cc-savebar flex align-items-center space-between g-1">
          {saveStatus}
          <div className="flex align-items-center g-05">
            {saveButton}
            {visitButton}
          </div>
        </div>
      )}
      {form && !isReadOnly && (
        <div className="cc-dup">
          <DuplicateClientNotice
            firstName={form.firstName}
            lastName={form.lastName}
            phoneNumber={form.phoneNumber}
            excludeId={isNew ? null : clientId}
            onUseClient={
              isNew
                ? (id) => openCard(id, { birthDate: form.birthDate, phoneNumber: form.phoneNumber })
                : undefined
            }
            dismissible
          />
        </div>
      )}
      <div className="cat-seg-group cc-seg flex align-items-center width-fit-content g-025">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={`cat-seg align-items-center pointer ${tab === t.key ? "active" : ""}`}
            onClick={() => handleTabChange(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>
    </div>
  );

  const dataForm = form && (card || isNew) && (
    <div className="cc-form cc-data-form flex-column g-075">
      {!card?.signedRegulations && (
        <div className="cc-toggles flex align-items-center g-075">
          <RegulationsSegment
            value={form.signedRegulations}
            onChange={(value) => update("signedRegulations", value)}
            disabled={isReadOnly}
          />
        </div>
      )}
      <div className="pf-row flex align-items-center g-1">
        <span className="pf-label">Imię:</span>
        <TextInput
          dropdown={false}
          disabled={isReadOnly}
          className="in-full"
          value={form.firstName}
          onSelect={(value) => {
            if (typeof value === "string") update("firstName", value);
          }}
        />
      </div>
      <div className="pf-row flex align-items-center g-1">
        <span className="pf-label">Nazwisko:</span>
        <TextInput
          dropdown={false}
          disabled={isReadOnly}
          className="in-full"
          value={form.lastName}
          onSelect={(value) => {
            if (typeof value === "string") update("lastName", value);
          }}
        />
      </div>
      <div className="pf-row flex align-items-center g-1">
        <span className="pf-label">Data urodzenia:</span>
        <BirthDateField
          value={form.birthDate}
          onChange={(date) => update("birthDate", date)}
          disabled={isReadOnly}
          lightPortal={themeClass !== ""}
        />
      </div>
      <div className="pf-row flex align-items-center g-1">
        <span className="pf-label">Nr kontaktowy:</span>
        <TextInput
          dropdown={false}
          disabled={isReadOnly}
          className="in-full"
          numbersOnly={true}
          value={form.phoneNumber ?? ""}
          onSelect={(value) => {
            if (typeof value === "string") update("phoneNumber", value === "" ? null : value);
          }}
        />
      </div>
      {!isNew && (
        <div className="cc-remarks flex-column g-05">
          <RemarksList
            label="Warto zapamiętać:"
            value={form.remarks}
            maxLength={CLIENT_REMARKS_MAX_LENGTH}
            disabled={isReadOnly}
            onChange={(value) => update("remarks", value)}
          />
        </div>
      )}
      {!isNew && (
        <DrawingOverview
          clientId={clientId}
          onOpen={async (templateId, sheet) => {
            writeDrawingSelection(clientId, templateId);
            setOpenDrawingEditor(sheet);
            if (!(await handleTabChange("drawing"))) setOpenDrawingEditor(null);
          }}
        />
      )}
    </div>
  );

  const questionnaireContent = questionnaireLoadError ? (
    <div className="cc-empty flex-column align-items-center g-05">
      <span>Nie udało się wczytać ankiety.</span>
      <ActionButton text="Spróbuj ponownie" onClick={() => setQuestionnaireLoadError(false)}>
        <RotateCcw className="dl-icon-1" />
      </ActionButton>
    </div>
  ) : answers ? (
    <QuestionnaireForm
      items={answers}
      language={questionnaireLanguage}
      disabled={isReadOnly || questionnaireConflict}
      onChange={updateAnswer}
    />
  ) : (
    <span className="cq-empty italic">Wczytywanie ankiety…</span>
  );

  const saveErrorNotice = saveError && (
    <div className="cc-save-error-row flex align-items-center g-075 flex-wrap">
      <span className="cc-save-error c-red">{saveError}</span>
      {questionnaireConflict && (
        <ActionButton text="Wczytaj aktualną wersję" onClick={reloadQuestionnaire}>
          <RotateCcw className="dl-icon-1" />
        </ActionButton>
      )}
    </div>
  );

  const notFoundContent = (
    <div className="cc-empty flex-column align-items-center g-05">
      <span>Nie znaleziono karty klienta.</span>
      <ActionButton text="Wróć do Kart Klienta" onClick={() => navigate("/client-cards")} />
    </div>
  );

  const content = (
    <div className="cc-body scrollbar flex-column f-1 min-height-0 g-075">
      {header}
      {tab === "data" && dataForm}
      {tab === "questionnaire" && !isNew && questionnaireContent}
      {tab === "therapy" && !isNew && card && (
        <TherapyTab
          clientId={clientId}
          readOnly={isReadOnly}
          openEntryDate={openEntryDate}
          highlightVisitDate={highlightVisitDate}
          onOpenEntryHandled={handleOpenEntryHandled}
        />
      )}
      {tab === "drawing" && !isNew && card && (
        <DrawingTab
          clientId={clientId}
          readOnly={isReadOnly}
          onDirtyChange={handleDrawingDirtyChange}
          openEditorOnLoad={openDrawingEditor}
          onEditorOpened={() => setOpenDrawingEditor(null)}
        />
      )}
      {saveErrorNotice}
    </div>
  );

  const body = notFound ? notFoundContent : content;

  const historyModal = historyOpen && card && (
    <CardHistoryModal clientId={card.clientId} onClose={() => setHistoryOpen(false)} />
  );

  const leaveModal = blocker.state === "blocked" && (
    <Modal
      title="Masz niezapisane zmiany"
      onClose={() => blocker.reset?.()}
      size="sm"
      footer={
        <>
          <ActionButton text="Wróć" onClick={() => blocker.reset?.()} className="cc-back-btn" />
          <ActionButton
            text="Odrzuć"
            onClick={() => blocker.proceed?.()}
            className="cl-filter cc-discard-btn active"
          />
          <ActionButton
            text="Zapisz"
            className="cl-filter cc-save-btn active"
            onClick={async () => {
              if (await save(false)) {
                blocker.proceed?.();
              } else {
                blocker.reset?.();
              }
            }}
          >
            <Save className="dl-icon-1 c-green" />
          </ActionButton>
        </>
      }
    >
      <span>Zapisać zmiany w karcie klienta przed wyjściem?</span>
    </Modal>
  );

  if (isMobile) {
    const portalRoot = document.getElementById("portal-root");
    const sheet = (
      <div className={`cc-sheet cc-view flex-column ${themeClass}`}>
        <header className="cc-sheet-header flex-column g-075">
          <div className="cc-sheet-titlebar flex align-items-center space-between g-1">
            <h2 className="modal-title m-0">{title}</h2>
            <button
              type="button"
              className="modal-close transparent border-none flex align-items-center justify-center pointer"
              onClick={handleClose}
              aria-label="Zamknij"
            >
              <X className="dl-icon-1" />
            </button>
          </div>
        </header>
        <div className="cc-sheet-body flex-column">{notFound ? notFoundContent : content}</div>
      </div>
    );
    return (
      <>
        {portalRoot ? ReactDOM.createPortal(sheet, portalRoot) : sheet}
        {leaveModal}
        {historyModal}
        {visitModal}
      </>
    );
  }

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />
        <div className="cc-page width-93 flex-column f-1 min-height-0 g-075 mt-1 mb-2">
          <ActionButton
            text={backLabel}
            iconTitle="Wróć"
            disableBorder={true}
            onClick={handleClose}
            className="cc-back"
          >
            <ChevronLeft className="dl-icon-1" />
          </ActionButton>
          <div className={`cc-view flex-column f-1 min-height-0 g-075 ${themeClass}`}>{body}</div>
        </div>
      </div>
      {leaveModal}
      {historyModal}
      {visitModal}
    </div>
  );
}

export default ClientCardView;
