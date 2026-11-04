import { useState, useCallback, useEffect } from "react";
import Modal from "../Modal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { validateKsefSettings } from "../../utils/validators";
import KsefCredentialsService from "../../services/KsefCredentialsService";
import { KsefCredentials } from "../../models/ksef_invoice";
import { KsefSettingsForm } from "../Business/KsefSettingsForm";

export interface KsefSettingsModalProps {
  onClose: () => void;
  className?: string;
}

export function KsefSettingsModal({
  onClose,
  className = "",
}: KsefSettingsModalProps) {
  const [fetchedKsefSettings, setFetchedKsefSettings] =
    useState<KsefCredentials | null>(null);
  const [ksefSettings, setKsefSettings] = useState<KsefCredentials>({
    nip: null,
    token: "",
  });
  const { showAlert } = useAlert();

  const fetchKsefSettings = async () => {
    KsefCredentialsService.getCredentials()
      .then((data) => {
        setFetchedKsefSettings(data);
        setKsefSettings(data);
      })
      .catch((error) => {
        if (error.response?.status === 404) return;
        console.error("Error fetching KseF settings: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  const handleSaveKsefSettings = useCallback(async () => {
    const error = validateKsefSettings(ksefSettings);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      await KsefCredentialsService.saveCredentials(ksefSettings);
      showAlert("Ustawienia KseF zapisane!", AlertType.SUCCESS);
      onClose();
    } catch (error) {
      showAlert("Błąd zapisu ustawień KseF!", AlertType.ERROR);
    }
  }, [ksefSettings, showAlert, onClose]);

  useEffect(() => {
    if (fetchedKsefSettings === null) {
      fetchKsefSettings();
    }
  }, []);

  return (
    <Modal
      dirtyValue={ksefSettings}
      title="Ustawienia KSeF"
      onClose={onClose}
      size="md"
      className={className}
      onConfirm={handleSaveKsefSettings}
      confirmText="Zapisz"
    >
      <KsefSettingsForm
        credentialsDTO={ksefSettings}
        setCredentialsDTO={setKsefSettings}
        className="ksef-settings"
      />
    </Modal>
  );
}

export default KsefSettingsModal;
