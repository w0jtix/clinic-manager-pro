import { useCallback } from "react";
import { KsefCredentials} from "../../models/ksef_invoice";
import TextInput from "../TextInput";

export interface KsefSettingsFormProps {
  credentialsDTO: KsefCredentials;
  setCredentialsDTO: React.Dispatch<React.SetStateAction<KsefCredentials>>;
  className?: string;
}

export function KsefSettingsForm({
  credentialsDTO,
  setCredentialsDTO,
  className = "",
}: KsefSettingsFormProps) {
  const handleNipChange = useCallback((value: string) => {
    setCredentialsDTO((prev) => ({
      ...prev,
      nip: value === "" ? null : value,
    }));
  }, []);

  const handleTokenChange = useCallback((value: string) => {
    setCredentialsDTO((prev) => ({
      ...prev,
      token: value,
    }));
  }, []);

  return (
    <div className={`custom-form-container flex-column width-max g-05 ${className}`}>
      <section className="form-row flex width-max align-items-center space-between">
        <span className="input-label">NIP:</span>
        <TextInput
          value={credentialsDTO.nip ?? ""}
          numbersOnly={true}
          numberSpacing={false}
          maxLength={12}
          onSelect={(input) => {
            if (typeof input === "string") handleNipChange(input);
          }}
          className="wide"
        />
      </section>
      <section className="form-row flex width-max align-items-center space-between">
        <span className="input-label">Token KSeF:</span>
        <TextInput
          value={credentialsDTO.token}
          password={true}
          onSelect={(input) => {
            if (typeof input === "string") handleTokenChange(input);
          }}
          className="wide"
        />
      </section>
    </div>
  );
}

export default KsefSettingsForm;
