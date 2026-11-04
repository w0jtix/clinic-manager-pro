import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import NavbarLogoContainer from "../components/NavbarLogoContainer";
import ActionButton from "../components/ActionButton";
import PinInput from "../components/PinInput";
import { useAlert } from "../components/Alert/AlertProvider";
import { AlertType } from "../models/alert";
import { DEVICE_CODE_LENGTH } from "../models/device";
import DeviceService from "../services/DeviceService";
import { apiErrorMessage } from "../utils/apiError";

const RegisterDevice = () => {
  const { showAlert } = useAlert();
  const navigate = useNavigate();
  const [code, setCode] = useState<string>("");
  const busyRef = useRef<boolean>(false);

  useEffect(() => {
    const root = document.documentElement;
    root.classList.add("login-page");
    return () => root.classList.remove("login-page");
  }, []);

  const handleRegister = async () => {
    if (busyRef.current) return;
    if (code.length !== DEVICE_CODE_LENGTH) {
      showAlert("Wpisz 6-znakowy kod.", AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    try {
      const response = await DeviceService.registerDevice(code);
      showAlert(response?.message ?? "Urządzenie zarejestrowane. Możesz się zalogować.", AlertType.SUCCESS);
      navigate("/login", { replace: true });
    } catch (error: any) {
      setCode("");
      const status = error?.response?.status;
      showAlert(
        apiErrorMessage(error) ?? "Nie udało się zarejestrować urządzenia.",
        status === 409 ? AlertType.INFO : AlertType.ERROR
      );
    } finally {
      busyRef.current = false;
    }
  };

  return (
    <div className="container">
      <div className="display">
        <div className="lg-card flex-column m-auto">
          <NavbarLogoContainer className="centered lg-logo" />
          <div className="lg-mfa flex-column align-items-center g-1">
            <h2 className="lg-title m-0">Rejestracja urządzenia</h2>
            <p className="lg-sub m-0">Wpisz kod otrzymany od administratora.</p>
            <PinInput
              value={code}
              onChange={setCode}
              length={DEVICE_CODE_LENGTH}
              masked={false}
              revealable={false}
              alphanumeric={true}
              groupSize={3}
              autoFocus={true}
              onSubmit={handleRegister}
              className="pin-lg"
            />
          </div>
          <ActionButton text="Zarejestruj urządzenie" disableImg={true} onClick={handleRegister} className="lg-submit" />
          <div className="lg-links flex-column align-items-center">
            <ActionButton
              text="Wróć do logowania"
              disableImg={true}
              onClick={() => navigate("/login")}
              className="lg-link lg-link-dim"
            />
          </div>
        </div>
      </div>
    </div>
  );
};

export default RegisterDevice;
