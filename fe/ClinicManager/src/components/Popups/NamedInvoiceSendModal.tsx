import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { MailCheck, LoaderCircle } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import NamedInvoiceService from "../../services/NamedInvoiceService";
import { NamedInvoice } from "../../models/named_invoice";
import {
  getInvoiceSendStatus,
  invoiceErrorMessage,
} from "../../utils/namedInvoiceUtils";
import { formatTimestamp } from "../../utils/dateUtils";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";

export type SendModalOrigin = "CREATE" | "EDIT" | "LIST";

export interface NamedInvoiceSendModalProps {
  invoiceId: number;
  invoice?: NamedInvoice;
  origin?: SendModalOrigin;
  onClose: () => void;
  onSent?: (invoice: NamedInvoice) => void;
  className?: string;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export function NamedInvoiceSendModal({
  invoiceId,
  invoice: preloaded,
  origin = "LIST",
  onClose,
  onSent,
  className = "",
}: NamedInvoiceSendModalProps) {
  const { showAlert } = useAlert();

  const [invoice, setInvoice] = useState<NamedInvoice | null>(preloaded ?? null);
  const [loading, setLoading] = useState<boolean>(preloaded == null);
  const [email, setEmail] = useState<string>(preloaded?.clientEmail ?? "");
  const [updateClientEmail, setUpdateClientEmail] = useState<boolean>(true);
  const [sending, setSending] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [frameReady, setFrameReady] = useState<boolean>(false);

  const pdfUrlRef = useRef<string | null>(null);

  useEffect(() => {
    if (preloaded) return;
    setLoading(true);
    NamedInvoiceService.getById(invoiceId)
      .then((inv) => {
        setInvoice(inv);
        setEmail(inv.clientEmail ?? "");
      })
      .catch((err) => {
        setInvoice(null);
        showAlert(
          invoiceErrorMessage(err, "Błąd pobrania faktury."),
          AlertType.ERROR
        );
      })
      .finally(() => setLoading(false));
  }, [invoiceId, preloaded]);

  // Wtyczka PDF przegladarki jest kosztowna w kompozycji. Montujemy ramke
  // dopiero po wejsciu modala, zeby nie nakladac jej na animacje overlaya.
  useEffect(() => {
    const timer = window.setTimeout(() => setFrameReady(true), 200);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    let cancelled = false;
    NamedInvoiceService.getPdf(invoiceId)
      .then((blob) => {
        if (cancelled) return;
        const url = window.URL.createObjectURL(blob);
        pdfUrlRef.current = url;
        setPdfUrl(url);
      })
      .catch(() => setPdfUrl(null));

    return () => {
      cancelled = true;
      if (pdfUrlRef.current) {
        window.URL.revokeObjectURL(pdfUrlRef.current);
        pdfUrlRef.current = null;
      }
    };
  }, [invoiceId]);

  const clientEmail = invoice?.clientEmail ?? "";
  const trimmedEmail = email.trim();

  const showSyncRow = useMemo(
    () =>
      clientEmail !== "" &&
      trimmedEmail !== "" &&
      trimmedEmail.toLowerCase() !== clientEmail.toLowerCase(),
    [clientEmail, trimmedEmail]
  );

  const alreadySent = invoice != null && invoice.emailSentAt != null;
  const isStale = invoice != null && getInvoiceSendStatus(invoice) === "STALE";

  const handleSend = useCallback(async () => {
    if (!invoice) return;
    if (!EMAIL_PATTERN.test(trimmedEmail)) {
      setError("Niepoprawny adres email.");
      return;
    }

    setSending(true);
    setError(null);
    try {
      const sent = await NamedInvoiceService.sendEmail(invoice.id, {
        email: trimmedEmail,
        updateClientEmail: showSyncRow ? updateClientEmail : true,
      });
      showAlert(
        `Faktura ${sent.invoiceNumber} wysłana na ${trimmedEmail}!`,
        AlertType.SUCCESS
      );
      onSent?.(sent);
      onClose();
    } catch (err) {
      setError(
        invoiceErrorMessage(
          err,
          "Nie udało się wysłać faktury. Spróbuj ponownie."
        )
      );
    } finally {
      setSending(false);
    }
  }, [
    invoice,
    trimmedEmail,
    showSyncRow,
    updateClientEmail,
    onSent,
    onClose,
    showAlert,
  ]);

  const title = (() => {
    if (!invoice) return "Wysyłka faktury";
    if (origin === "CREATE") return `Faktura ${invoice.invoiceNumber} zapisana`;
    if (origin === "EDIT") return `Faktura ${invoice.invoiceNumber} zaktualizowana`;
    return `Wyślij fakturę ${invoice.invoiceNumber}`;
  })();

  const sendLabel = sending
    ? "Wysyłanie..."
    : alreadySent
    ? "Wyślij ponownie"
    : "Wyślij fakturę";

  const hint = (() => {
    if (trimmedEmail === "") return "Uzupełnij adres email.";
    if (origin === "EDIT" || isStale) {
      return "Edytowana faktura zostanie wysłana na podany adres email. Kopia trafi na Twoją skrzynkę.";
    }
    return "Faktura zostanie wysłana na podany adres email. Kopia trafi na Twoją skrzynkę.";
  })();

  const hintClass = error
    ? "nis-hint bad"
    : trimmedEmail === ""
    ? "nis-hint warn"
    : "nis-hint";

  return (
    <Modal
      dirtyValue={[email, updateClientEmail]}
      title={title}
      onClose={sending ? () => {} : onClose}
      size="xl"
      height="tall"
      closeOnOverlay={!sending}
      closeOnEsc={!sending}
      footer={
        <>
          <ActionButton
            text="Pomiń wysyłkę"
            iconTitle="Zamknij bez wysyłania"
            onClick={onClose}
            disabled={sending}
            className="nis-skip-btn"
          />
          <ActionButton
            text={sendLabel}
            className="modal-button"
            disabled={sending || loading || !invoice || trimmedEmail === ""}
            onClick={handleSend}
          >
            {sending && <LoaderCircle className="dl-icon-1 spin-icon pc-y" />}
          </ActionButton>
        </>
      }
      className={`nis-modal ${className}`}
      bodyClassName="nis-body"
    >
      {loading && (
        <span className="qv-span text-align-center">Ładowanie...</span>
      )}

      {!loading && !invoice && (
        <span className="qv-span text-align-center opacity-half italic">
          Nie udało się pobrać faktury
        </span>
      )}

      {!loading && invoice && (
        <>
          <div className="nis-preview">
            {pdfUrl && frameReady ? (
              <iframe
                src={pdfUrl}
                title={`Podgląd faktury ${invoice.invoiceNumber}`}
                className="nis-preview-frame"
              />
            ) : (
              <span className="qv-span text-align-center opacity-half italic">
                Generowanie podglądu...
              </span>
            )}
          </div>

          {alreadySent && (
            <div className="nis-sent-note flex align-items-center g-05">
              <MailCheck
                className={`dl-icon-1 ${isStale ? "pc-y" : "c-green"}`}
              />
              <span>
                {isStale
                  ? `Wysłano ${formatTimestamp(invoice.emailSentAt!)}${
                      invoice.sentTo ? ` na ${invoice.sentTo}` : ""
                    } - klient ma starszą wersję dokumentu.`
                  : `Wysłano ${formatTimestamp(invoice.emailSentAt!)}${
                      invoice.sentTo ? ` na ${invoice.sentTo}` : ""
                    }.`}
              </span>
            </div>
          )}

          <label className="nis-field flex g-2 align-items-center">
            <span className="ksef-party-label ni-label nowarap">
              Email klienta:
            </span>
            <TextInput
              value={email}
              onSelect={(v) => {
                setEmail(v as string);
                setError(null);
              }}
              placeholder="adres@domena.pl"
              className="in-full"
              noCapitalize={true}
              disabled={sending}
            />
          </label>

          {showSyncRow && (
            <div className="nis-sync flex align-items-start g-05">
              <input
                type="checkbox"
                id="nis-sync-checkbox"
                className="app-checkbox"
                checked={updateClientEmail}
                disabled={sending}
                onChange={(e) => setUpdateClientEmail(e.target.checked)}
              />
              <label htmlFor="nis-sync-checkbox" className="nis-sync-label">
                Zaktualizuj email klienta - poprzednio zapisany adres:{" "}
                <span className="nis-sync-was">{clientEmail}</span>
              </label>
            </div>
          )}

          <p className={hintClass}>{error ?? hint}</p>
        </>
      )}
    </Modal>
  );
}

export default NamedInvoiceSendModal;
