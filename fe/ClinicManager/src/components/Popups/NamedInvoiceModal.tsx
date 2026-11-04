import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Trash2, CirclePlus, FileText, Eye, Check } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import CostInput from "../CostInput";
import DigitInput from "../DigitInput";
// import DateInput from "../DateInput";
import WheelDateField from "../WheelDateField";
import SelectVATButton from "../SelectVATButton";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { Action } from "../../models/action";
import {
  NamedInvoice,
  NamedInvoiceItem,
  NewNamedInvoice,
} from "../../models/named_invoice";
import NamedInvoiceService from "../../services/NamedInvoiceService";
import VisitPreviewModal from "./VisitPreviewModal";
import {
  openNamedInvoicePdf,
  invoiceErrorMessage,
} from "../../utils/namedInvoiceUtils";
import NamedInvoiceSendModal from "./NamedInvoiceSendModal";
import StatSettingsService from "../../services/StatSettingsService";
import { StatSettings } from "../../models/business_settings";
import { VatRate, VAT_NUMERIC_VALUES } from "../../models/vatrate";
import { isMedicalVat } from "../ProductVatTag";
import { PaymentMethod } from "../../models/payment";
import { translatePaymentMethod } from "../../utils/paymentUtils";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface NamedInvoiceModalProps {
  visitId?: number;
  invoiceId?: number;
  onClose: () => void;
  onSaved?: (invoice: NamedInvoice) => void;
  className?: string;
}


const round2 = (value: number): number => Math.round(value * 100) / 100;

// Na fakturze imiennej pozycje maja zbiorcze nazwy, a nie nazwy konkretnych
// uslug i produktow z wizyty. Dotyczy tylko draftu - przy edycji istniejacej
// faktury nazwy sa juz zapisane i moga byc recznie zmienione.
const invoiceItemLabel = (item: NamedInvoiceItem): string => {
  switch (item.sourceType) {
    case "SERVICE":
    case "VOUCHER":
      return "Usługa podologiczna";
    case "PRODUCT":
      return isMedicalVat(item.vatRate)
        ? "Sprzedaż produktów medycznych"
        : "Sprzedaż produktów";
    default:
      return item.name;
  }
};

const withInvoiceLabels = (items: NamedInvoiceItem[]): NamedInvoiceItem[] =>
  items.map((item) => ({ ...item, name: invoiceItemLabel(item) }));

const paymentItems = Object.values(PaymentMethod).map((method) => ({
  id: method,
  name: translatePaymentMethod(method),
}));

// Wizyta moze byc oplacona kilkoma metodami naraz (np. czesc karta, czesc
// gotowka) - faktura ma to odzwierciedlac, stad "Platnosc dzielona".
const paymentLabel = (methods: PaymentMethod[]): string =>
  methods.length > 1 ? "Płatność dzielona:" : "Płatność:";

const paymentValue = (methods: PaymentMethod[]): string =>
  methods.length === 0
    ? "Przypisz"
    : methods.map(translatePaymentMethod).join(", ");

const recalcItem = (
  item: NamedInvoiceItem,
  changed: Partial<NamedInvoiceItem>
): NamedInvoiceItem => {
  const next = { ...item, ...changed };
  const qty = next.quantity ?? 1;
  const vatMultiplier = 1 + (VAT_NUMERIC_VALUES[next.vatRate] ?? 0) / 100;

  // Edycja sumy brutto - to ona jest wartoscia wiodaca, netto liczymy wstecz.
  // Porownanie z item.grossValue odsiewa echo: CostInput zglasza onChange takze
  // wtedy, gdy sam dostal wyliczona wartosc z gory (po edycji netto).
  if (
    changed.grossValue !== undefined &&
    changed.grossValue !== item.grossValue
  ) {
    next.unitGrossPrice = qty ? round2(next.grossValue / qty) : 0;
    next.unitNetPrice = round2(next.unitGrossPrice / vatMultiplier);
    next.netValue = round2(next.unitNetPrice * qty);
    return next;
  }

  if (changed.unitGrossPrice !== undefined) {
    next.unitNetPrice = round2(next.unitGrossPrice / vatMultiplier);
  } else {
    next.unitGrossPrice = round2(next.unitNetPrice * vatMultiplier);
  }
  next.netValue = round2(next.unitNetPrice * qty);
  next.grossValue = round2(next.unitGrossPrice * qty);
  return next;
};

export function NamedInvoiceModal({
  visitId,
  invoiceId,
  onClose,
  onSaved,
  className = "",
}: NamedInvoiceModalProps) {
  const { isCompact } = useBreakpoint();
  const { showAlert } = useAlert();
  const action = invoiceId != null ? Action.EDIT : Action.CREATE;

  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [settings, setSettings] = useState<StatSettings | null>(null);
  const [dto, setDto] = useState<NewNamedInvoice | null>(null);
  const [previewVisitId, setPreviewVisitId] = useState<number | null>(null);
  const [paymentMenuOpen, setPaymentMenuOpen] = useState<boolean>(false);
  const [sendStep, setSendStep] = useState<NamedInvoice | null>(null);
  const paymentRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    StatSettingsService.getSettings()
      .then(setSettings)
      .catch(() => setSettings(null));
  }, []);

  useEffect(() => {
    setLoading(true);
    const loader =
      invoiceId != null
        ? NamedInvoiceService.getById(invoiceId).then((inv) => ({
            clientId: inv.clientId,
            sourceVisitId: inv.sourceVisitId,
            invoiceNumber: inv.invoiceNumber,
            sequenceInMonth: inv.sequenceInMonth,
            year: inv.year,
            month: inv.month,
            issueDate: inv.issueDate,
            saleDate: inv.saleDate,
            paymentMethods: inv.paymentMethods ?? [],
            notes: inv.notes,
            buyerName: inv.buyerName,
            buyerAddress: inv.buyerAddress,
            buyerPostalCode: inv.buyerPostalCode,
            buyerCity: inv.buyerCity,
            items: inv.items,
          }))
        : NamedInvoiceService.getDraft(visitId!).then((draft) => ({
            clientId: draft.clientId,
            sourceVisitId: draft.sourceVisitId,
            invoiceNumber: draft.suggestedInvoiceNumber,
            sequenceInMonth: draft.suggestedSequenceInMonth,
            year: draft.year,
            month: draft.month,
            issueDate: draft.issueDate,
            saleDate: draft.saleDate,
            paymentMethods: draft.paymentMethods ?? [],
            notes: draft.notes,
            buyerName: draft.buyerName ?? "",
            buyerAddress: draft.buyerAddress,
            buyerPostalCode: draft.buyerPostalCode,
            buyerCity: draft.buyerCity,
            items: withInvoiceLabels(draft.items),
          }));

    loader
      .then(setDto)
      .catch((error) => {
        setDto(null);
        showAlert(
          invoiceErrorMessage(error, "Błąd przygotowania faktury."),
          AlertType.ERROR
        );
      })
      .finally(() => setLoading(false));
  }, [visitId, invoiceId]);

  // Modal stops click propagation on .modal, and React's stopPropagation also
  // stops the native event - a document "click" listener would never receive
  // clicks from inside the modal. Mousedown is not blocked.
  useEffect(() => {
    if (!paymentMenuOpen) return;
    const handleOutside = (event: MouseEvent) => {
      if (paymentRef.current?.contains(event.target as Node)) return;
      setPaymentMenuOpen(false);
    };
    document.addEventListener("pointerdown", handleOutside);
    return () => document.removeEventListener("pointerdown", handleOutside);
  }, [paymentMenuOpen]);

  const totals = useMemo(() => {
    const items = dto?.items ?? [];
    const net = round2(items.reduce((sum, i) => sum + (i.netValue ?? 0), 0));
    const gross = round2(items.reduce((sum, i) => sum + (i.grossValue ?? 0), 0));
    return { net, gross, vat: round2(gross - net) };
  }, [dto?.items]);

  const togglePaymentMethod = useCallback((method: PaymentMethod) => {
    setDto((prev) => {
      if (!prev) return prev;
      const selected = prev.paymentMethods.includes(method);
      return {
        ...prev,
        paymentMethods: selected
          ? prev.paymentMethods.filter((m) => m !== method)
          : [...prev.paymentMethods, method],
      };
    });
  }, []);

  const patch = useCallback((changed: Partial<NewNamedInvoice>) => {
    setDto((prev) => (prev ? { ...prev, ...changed } : prev));
  }, []);

  const patchItem = useCallback(
    (index: number, changed: Partial<NamedInvoiceItem>) => {
      setDto((prev) => {
        if (!prev) return prev;
        const items = [...prev.items];
        items[index] = recalcItem(items[index], changed);
        return { ...prev, items };
      });
    },
    []
  );

  const removeItem = useCallback((index: number) => {
    setDto((prev) =>
      prev ? { ...prev, items: prev.items.filter((_, i) => i !== index) } : prev
    );
  }, []);

  const addItem = useCallback(() => {
    setDto((prev) =>
      prev
        ? {
            ...prev,
            items: [
              ...prev.items,
              {
                name: "",
                quantity: 1,
                unitNetPrice: 0,
                netValue: 0,
                unitGrossPrice: 0,
                grossValue: 0,
                vatRate: VatRate.VAT_8,
                sourceType: "MANUAL",
              },
            ],
          }
        : prev
    );
  }, []);

  const handleInvoiceNumberChange = useCallback((value: string) => {
    const parsed = parseInt(value, 10);
    setDto((prev) =>
      prev
        ? {
            ...prev,
            invoiceNumber: value,
            sequenceInMonth: Number.isNaN(parsed)
              ? prev.sequenceInMonth
              : parsed,
          }
        : prev
    );
  }, []);

  const handlePreviewPdf = useCallback(async () => {
    if (invoiceId == null) return;
    const ok = await openNamedInvoicePdf(invoiceId, dto?.invoiceNumber);
    if (!ok) showAlert("Błąd generowania PDF.", AlertType.ERROR);
  }, [invoiceId, dto?.invoiceNumber, showAlert]);

  const handleSave = useCallback(async () => {
    if (!dto) return;
    if (!dto.buyerName || dto.buyerName.trim().length < 3) {
      showAlert("Uzupełnij nazwę nabywcy!", AlertType.ERROR);
      return;
    }
    if (dto.items.length === 0) {
      showAlert("Faktura musi mieć co najmniej jedną pozycję!", AlertType.ERROR);
      return;
    }
    if (dto.items.some((i) => !i.name || i.name.trim() === "")) {
      showAlert("Każda pozycja musi mieć nazwę!", AlertType.ERROR);
      return;
    }

    setSaving(true);
    try {
      const saved =
        action === Action.EDIT
          ? await NamedInvoiceService.update(invoiceId!, dto)
          : await NamedInvoiceService.create(dto);
      showAlert(
        action === Action.EDIT
          ? `Faktura ${saved.invoiceNumber} zaktualizowana!`
          : `Faktura ${saved.invoiceNumber} utworzona!`,
        AlertType.SUCCESS
      );
      onSaved?.(saved);
      setSendStep(saved);
    } catch (error) {
      showAlert(
        invoiceErrorMessage(error, "Błąd zapisu faktury."),
        AlertType.ERROR
      );
    } finally {
      setSaving(false);
    }
  }, [dto, action, invoiceId, onSaved, onClose, showAlert]);

  if (sendStep != null) {
    return (
      <NamedInvoiceSendModal
        invoiceId={sendStep.id}
        invoice={sendStep}
        origin={action === Action.EDIT ? "EDIT" : "CREATE"}
        onSent={(sent) => onSaved?.(sent)}
        onClose={() => {
          setSendStep(null);
          onClose();
        }}
        className={className}
      />
    );
  }

  return (
    <Modal
      dirtyValue={dto}
      title={
        action === Action.EDIT ? "Edytuj fakturę imienną" : "Nowa faktura imienna"
      }
      onClose={onClose}
      size="2xl"
      height="tall"
      headerExtra={
        dto?.sourceVisitId != null ? (
          <ActionButton
            text={isCompact ? "Wizyta" : "Podgląd wizyty"}
            iconTitle="Otwórz wizytę, z której powstała faktura"
            onClick={() => setPreviewVisitId(dto.sourceVisitId)}
          >
            <Eye className="dl-icon-1" />
          </ActionButton>
        ) : undefined
      }
      onConfirm={handleSave}
      footer={
        <>
          {action === Action.EDIT && (
            <ActionButton
              text="Podgląd PDF"
              iconTitle="Otwórz PDF faktury"
              onClick={handlePreviewPdf}
              className="ni-pdf-btn"
            >
              <FileText className="dl-icon-1 pc-y" />
            </ActionButton>
          )}
          <ActionButton
            text={saving ? "Zapisywanie..." : "Zapisz"}
            className="modal-button"
            disabled={saving || loading || !dto}
            onClick={handleSave}
          />
        </>
      }
      className={`scroller ni-modal ${className}`}
      bodyClassName="ksm-body"
    >
      {loading && <span className="qv-span text-align-center">Ładowanie...</span>}

      {!loading && !dto && (
        <span className="qv-span text-align-center opacity-half italic">
          Nie udało się przygotować faktury
        </span>
      )}

      {!loading && dto && (
        <div className="ksef-invoice-document flex-column">
          <div className="ksef-invoice-meta flex space-between">
            <div className="flex-column g-05">
              <span className="ni-field flex align-items-center g-05">
                <span className="ksef-label">Numer faktury:</span>
                <TextInput
                  value={dto.invoiceNumber}
                  onSelect={(v) => handleInvoiceNumberChange(v as string)}
                  className="in-md"
                  noCapitalize={true}
                />
              </span>
              <span className="ni-field ni-payment flex align-items-center g-05">
                <span className="ksef-label">
                  {paymentLabel(dto.paymentMethods)}
                </span>
                <div className="relative flex align-items-center" ref={paymentRef}>
                  <span
                    className={`exp-cat-badge pointer ${
                      dto.paymentMethods.length > 0 ? "colored" : ""
                    }`}
                    style={
                      dto.paymentMethods.length > 0
                        ? ({ "--cat-rgb": "252, 181, 12" } as React.CSSProperties)
                        : undefined
                    }
                    title="Zmień formy płatności"
                    onClick={(e) => {
                      e.stopPropagation();
                      setPaymentMenuOpen((open) => !open);
                    }}
                  >
                    {paymentValue(dto.paymentMethods)}
                  </span>
                  {paymentMenuOpen && (
                    <div
                      className="exp-cat-menu absolute"
                      onClick={(e) => e.stopPropagation()}
                    >
                      {paymentItems.map((item) => {
                        const selected = dto.paymentMethods.includes(item.id);
                        return (
                          <div
                            key={item.id}
                            className="exp-cat-menu-item flex align-items-center space-between g-05 pointer"
                            onClick={(e) => {
                              e.stopPropagation();
                              togglePaymentMethod(item.id);
                            }}
                          >
                            <span>{item.name}</span>
                            {selected && <Check className="dl-icon pc-y" />}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </span>
            </div>
            <div className="flex-column align-items-end g-05">
              <span className="ni-field flex align-items-center g-05">
                <span className="ksef-label">Data wystawienia:</span>
                {/* <DateInput
                  selectedDate={dto.issueDate}
                  onChange={(date) =>
                    patch({ issueDate: (date as string) ?? dto.issueDate })
                  }
                /> */}
                <WheelDateField
                  title="Data wystawienia"
                  value={dto.issueDate}
                  onChange={(date) => patch({ issueDate: date ?? dto.issueDate })}
                />
              </span>
              <span className="ni-field flex align-items-center g-05">
                <span className="ksef-label">Data sprzedaży:</span>
                {/* <DateInput
                  selectedDate={dto.saleDate}
                  onChange={(date) => patch({ saleDate: date as string | null })}
                /> */}
                <WheelDateField
                  title="Data sprzedaży"
                  value={dto.saleDate}
                  clearable={true}
                  onChange={(date) => patch({ saleDate: date })}
                />
              </span>
            </div>
          </div>

          <div className="ksef-invoice-divider width-max" />

          <div className="ksef-invoice-parties flex g-15">
            <div className="ksef-invoice-party flex-column f-1">
              <span className="ksef-party-label">Sprzedawca</span>
              {settings?.companyName ? (
                <>
                  <span className="ksef-party-name">{settings.companyName}</span>
                  {settings.companyNip && (
                    <span className="ksef-party-detail">
                      NIP: {settings.companyNip}
                    </span>
                  )}
                  {settings.companyAddress && (
                    <span className="ksef-party-detail">
                      {settings.companyAddress}
                    </span>
                  )}
                  {(settings.companyPostalCode || settings.companyCity) && (
                    <span className="ksef-party-detail">
                      {[settings.companyPostalCode, settings.companyCity]
                        .filter(Boolean)
                        .join(" ")}
                    </span>
                  )}
                </>
              ) : (
                <span className="ksef-party-detail ksef-party-empty italic">
                  Uzupełnij dane firmy w Ustawieniach Firmowych
                </span>
              )}
            </div>

            <div className="ksef-invoice-party ni-party-buyer flex-column f-1">
              <span className="ksef-party-label">Nabywca</span>
              <label className="ni-field-col flex g-2 align-items-center">
                <span className="ksef-party-label ni-label ni-name">Imię i nazwisko:</span>
                <TextInput
                  value={dto.buyerName}
                  onSelect={(v) => patch({ buyerName: v as string })}
                  placeholder="Imię i nazwisko"
                  className="in-full"
                />
              </label>
              <label className="ni-field-col flex g-2 align-items-center">
                <span className="ksef-party-label ni-label ni-address">Adres:</span>
                <TextInput
                  value={dto.buyerAddress ?? ""}
                  onSelect={(v) => patch({ buyerAddress: v as string })}
                  placeholder="Ulica i numer"
                  className="in-full"
                />
              </label>
              <div className="ni-buyer-pair flex g-2">
                <label className="ni-field-col ni-postal flex g-1 f-1 align-items-center">
                  <span className="ksef-party-label ni-label nowrap">Kod pocztowy:</span>
                  <TextInput
                    value={dto.buyerPostalCode ?? ""}
                    onSelect={(v) => patch({ buyerPostalCode: v as string })}
                    placeholder="Kod pocztowy"
                    className="in-full"
                    noCapitalize={true}
                  />
                </label>
                <label className="ni-field-col ni-city flex g-1 f-1 align-items-center">
                  <span className="ksef-party-label ni-label nowrap">Miasto:</span>
                  <TextInput
                    value={dto.buyerCity ?? ""}
                    onSelect={(v) => patch({ buyerCity: v as string })}
                    placeholder="Miasto"
                    className="in-full"
                  />
                </label>
              </div>
            </div>
          </div>

          <div className="ksef-invoice-divider width-max" />

          <div className="ksef-invoice-items-section flex-column">
            <div className="ksef-items-header">
              <span className="ksef-col-lp">Lp.</span>
              <span className="ksef-col-name">Pozycja</span>
              <span className="ksef-col-qty">Ilość</span>
              <span className="ksef-col-price">Cena netto</span>
              <span className="ksef-col-vat">VAT</span>
              <span className="ksef-col-net">Σ brutto</span>
              <span></span>
            </div>

            {dto.items.map((item, i) => {
              const nameInput = (
                <TextInput
                  value={item.name}
                  onSelect={(v) => patchItem(i, { name: v as string })}
                  placeholder="Nazwa pozycji"
                  className="in-full"
                />
              );
              const qtyInput = (
                <DigitInput
                  value={item.quantity}
                  onChange={(v) => patchItem(i, { quantity: v ?? 1 })}
                  min={1}
                  max={999}
                  className="in-xs"
                />
              );
              const priceInput = (
                <CostInput
                  selectedCost={item.unitNetPrice}
                  onChange={(v: number) =>
                    patchItem(i, { unitNetPrice: v ?? 0 })
                  }
                  className="in-sm"
                />
              );
              const vatSelect = (
                <SelectVATButton
                  selectedVat={item.vatRate}
                  onSelect={(vatRate) => patchItem(i, { vatRate })}
                />
              );
              const grossInput = (
                <CostInput
                  selectedCost={item.grossValue}
                  onChange={(v: number) => patchItem(i, { grossValue: v ?? 0 })}
                  className="in-sm"
                />
              );
              const removeButton = (
                <ActionButton
                  iconTitle="Usuń pozycję"
                  disableText={true}
                  disableBorder={true}
                  onClick={() => removeItem(i)}
                >
                  <Trash2 className="dl-icon c-red" />
                </ActionButton>
              );

              if (isCompact) {
                return (
                  <div
                    key={i}
                    className={`ni-row-mobile flex align-items-center g-05 ${
                      i % 2 === 1 ? "ksef-items-row-alt" : ""
                    }`}
                  >
                    <span className="ni-m-index pc-y">Pozycja {i + 1}.</span>
                    <div className="ni-m-body flex-column f-1 mw-0 g-05">
                      <div className="ni-m-line flex align-items-center g-05">
                        <label className="ni-m-field ni-m-name flex align-items-center mw-0 g-05">
                          <span className="ksef-party-label ni-label">
                            Nazwa:
                          </span>
                          {nameInput}
                        </label>
                        <label className="ni-m-field ni-m-qty flex align-items-center g-05">
                          <span className="ksef-party-label ni-label">
                            Ilość:
                          </span>
                          {qtyInput}
                        </label>
                      </div>
                      <div className="ni-m-line ni-m-line-nums flex align-items-center g-05">
                        <label className="ni-m-field ni-m-price flex align-items-center mw-0 g-05">
                          <span className="ksef-party-label ni-label">
                            Netto:
                          </span>
                          {priceInput}
                        </label>
                        <label className="ni-m-field ni-m-vat flex align-items-center mw-0 g-05">
                          <span className="ksef-party-label ni-label">Vat:</span>
                          {vatSelect}
                        </label>
                        <label className="ni-m-field ni-m-gross flex align-items-center mw-0 g-05">
                          <span className="ksef-party-label ni-label">
                            Σ brutto:
                          </span>
                          {grossInput}
                        </label>
                      </div>
                    </div>
                    {removeButton}
                  </div>
                );
              }

              return (
                <div
                  key={i}
                  className={`ksef-items-row ni-row ${
                    i % 2 === 1 ? "ksef-items-row-alt" : ""
                  }`}
                >
                  <span className="ksef-col-lp">{i + 1}</span>
                  {nameInput}
                  {qtyInput}
                  {priceInput}
                  {vatSelect}
                  {grossInput}
                  {removeButton}
                </div>
              );
            })}

            <div className="ni-add-row flex justify-start mt-1">
              <ActionButton text="Dodaj pozycję" onClick={addItem}>
                <CirclePlus className="dl-icon-1 c-green" />
              </ActionButton>
            </div>
          </div>

          <div className="ksef-invoice-divider width-max" />

          <section className="ksef-summary flex space-between g-2">
            <div className="ni-summary-left flex-column f-1 min-width-0 g-1">
              <label className="ni-notes flex g-1 align-items-start">
                <span className="ksef-party-label ni-label">Uwagi:</span>
                <TextInput
                  value={dto.notes ?? ""}
                  onSelect={(v) => patch({ notes: (v as string) || null })}
                  placeholder="opcjonalnie"
                  className="in-full"
                  multiline={true}
                  rows={isCompact ? 1 : 2}
                />
              </label>
            </div>
            <div className="ksef-invoice-summary flex-column align-items-end">
              <div className="ksef-summary-row flex">
                <span className="ksef-summary-label">Wartość netto:</span>
                <span className="ksef-summary-value">
                  {totals.net.toFixed(2)} PLN
                </span>
              </div>
              <div className="ksef-summary-row flex">
                <span className="ksef-summary-label">VAT:</span>
                <span className="ksef-summary-value">
                  {totals.vat.toFixed(2)} PLN
                </span>
              </div>
              <div className="ksef-summary-row ksef-summary-gross flex">
                <span className="ksef-summary-label">Do zapłaty brutto:</span>
                <span className="ksef-summary-value">
                  {totals.gross.toFixed(2)} PLN
                </span>
              </div>
            </div>
          </section>
        </div>
      )}

      {previewVisitId != null && (
        <VisitPreviewModal
          visitId={previewVisitId}
          onClose={() => setPreviewVisitId(null)}
        />
      )}

    </Modal>
  );
}

export default NamedInvoiceModal;
