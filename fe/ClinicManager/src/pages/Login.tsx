import { useNavigate } from "react-router-dom";
import NavbarLogoContainer from "../components/NavbarLogoContainer";
import { useEffect, useRef, useState } from "react";
import ActionButton from "../components/ActionButton";
import PinInput from "../components/PinInput";
import { AlertType } from "../models/alert";
import { JwtUser } from "../models/login";
import { MFA_CODE_LENGTH, isMfaChallenge } from "../models/mfa";
import { ACCESS_DENIED, ACCESS_DENIED_FLAG_KEY, ACCESS_DENIED_MESSAGE } from "../models/access-control";
import { validateLoginForm } from "../utils/validators";
import { MFA_SESSION_EXPIRED, apiErrorCode, apiErrorMessage } from "../utils/apiError";
import { backupCodesLeftMessage } from "../utils/mfa";
import AuthService from "../services/AuthService";
import { useAlert } from "../components/Alert/AlertProvider";
import { useUser } from "../components/User/UserProvider";

type LoginStep = "credentials" | "code" | "backup";

const LOW_BACKUP_CODES = 2;
const MFA_PENDING_KEY = "auth.mfaPending";
const MFA_PENDING_TTL_MS = 5 * 60 * 1000;

interface PendingMfa {
  token: string;
  step: Exclude<LoginStep, "credentials">;
  rememberDevice: boolean;
  expiresAt: number;
}

const readPendingMfa = (): PendingMfa | null => {
  try {
    const raw = sessionStorage.getItem(MFA_PENDING_KEY);
    if (!raw) return null;
    const pending = JSON.parse(raw) as PendingMfa;
    if (!pending.token || pending.expiresAt < Date.now()) {
      sessionStorage.removeItem(MFA_PENDING_KEY);
      return null;
    }
    return pending;
  } catch {
    return null;
  }
};

const writePendingMfa = (pending: PendingMfa | null) => {
  try {
    if (pending) {
      sessionStorage.setItem(MFA_PENDING_KEY, JSON.stringify(pending));
    } else {
      sessionStorage.removeItem(MFA_PENDING_KEY);
    }
  } catch {
    return;
  }
};

const Login = () => {
  const { showAlert } = useAlert();
  const { setUser } = useUser();
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [initialPending] = useState<PendingMfa | null>(readPendingMfa);
  const [step, setStep] = useState<LoginStep>(initialPending?.step ?? "credentials");
  const [mfaToken, setMfaToken] = useState<string | null>(initialPending?.token ?? null);
  const [mfaExpiresAt, setMfaExpiresAt] = useState<number>(initialPending?.expiresAt ?? 0);
  const [code, setCode] = useState<string>("");
  const [backupCode, setBackupCode] = useState<string>("");
  const [rememberDevice, setRememberDevice] = useState<boolean>(initialPending?.rememberDevice ?? false);
  const busyRef = useRef<boolean>(false);
  const navigate = useNavigate();

  useEffect(() => {
    if (step === "credentials" || !mfaToken) {
      writePendingMfa(null);
    } else {
      writePendingMfa({ token: mfaToken, step, rememberDevice, expiresAt: mfaExpiresAt });
    }
  }, [step, mfaToken, rememberDevice, mfaExpiresAt]);

  useEffect(() => {
    let denied = false;
    try {
      denied = sessionStorage.getItem(ACCESS_DENIED_FLAG_KEY) !== null;
      sessionStorage.removeItem(ACCESS_DENIED_FLAG_KEY);
    } catch {}
    if (denied) showAlert(ACCESS_DENIED_MESSAGE, AlertType.ERROR);
  }, [showAlert]);

  const completeLogin = (user: JwtUser) => {
    writePendingMfa(null);
    setUser(user);
    const left = user.remainingBackupCodes;
    if (left != null && left <= LOW_BACKUP_CODES) {
      showAlert(backupCodesLeftMessage(left), AlertType.INFO);
    } else {
      showAlert(`Witaj ${user.username}!`, AlertType.SUCCESS);
    }
    navigate(AuthService.consumeReturnTo() ?? "/", { replace: true });
  };

  const backToCredentials = () => {
    setStep("credentials");
    setMfaToken(null);
    setCode("");
    setBackupCode("");
    setRememberDevice(false);
  };

  const handleLogin = async () => {
    const error = validateLoginForm(username, password);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    if (busyRef.current) return;
    busyRef.current = true;
    try {
      const result = await AuthService.login(username, password);
      if (isMfaChallenge(result)) {
        setMfaToken(result.mfaToken);
        setMfaExpiresAt(Date.now() + MFA_PENDING_TTL_MS);
        setCode("");
        setStep("code");
      } else if (result) {
        completeLogin(result);
      }
    } catch (error: any) {
      if (apiErrorCode(error) === ACCESS_DENIED) {
        showAlert(ACCESS_DENIED_MESSAGE, AlertType.ERROR);
      } else if (error?.response?.status === 429) {
        showAlert("Zbyt wiele nieudanych prób logowania. Spróbuj ponownie za chwilę.", AlertType.ERROR);
      } else {
        showAlert("Wystąpił błąd podczas logowania", AlertType.ERROR);
      }
    } finally {
      setPassword("");
      busyRef.current = false;
    }
  };

  const handleMfaError = (error: unknown, fallback: string) => {
    const message = apiErrorMessage(error) ?? fallback;
    showAlert(message, AlertType.ERROR);
    if (apiErrorCode(error) === MFA_SESSION_EXPIRED) {
      backToCredentials();
    }
  };

  const handleVerifyCode = async () => {
    if (!mfaToken || busyRef.current) return;
    if (code.length !== MFA_CODE_LENGTH) {
      showAlert("Wpisz 6-cyfrowy kod z aplikacji.", AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    try {
      const user = await AuthService.verifyMfa(mfaToken, code, rememberDevice);
      if (user) completeLogin(user);
    } catch (error) {
      setCode("");
      handleMfaError(error, "Nie udało się zweryfikować kodu.");
    } finally {
      busyRef.current = false;
    }
  };

  const handleVerifyBackup = async () => {
    if (!mfaToken || busyRef.current) return;
    if (!backupCode.trim()) {
      showAlert("Wpisz kod zapasowy.", AlertType.ERROR);
      return;
    }
    busyRef.current = true;
    try {
      const user = await AuthService.verifyBackupCode(mfaToken, backupCode.trim(), rememberDevice);
      if (user) completeLogin(user);
    } catch (error) {
      setBackupCode("");
      handleMfaError(error, "Nie udało się zweryfikować kodu zapasowego.");
    } finally {
      busyRef.current = false;
    }
  };

  useEffect(() => {
    const root = document.documentElement;
    root.classList.add("login-page");
    let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    const createdMeta = !meta;
    const previousColor = meta?.content ?? null;
    if (!meta) {
      meta = document.createElement("meta");
      meta.name = "theme-color";
      document.head.appendChild(meta);
    }
    const glowTop = getComputedStyle(root).getPropertyValue("--login-glow-top").trim();
    if (glowTop) meta.content = glowTop;
    return () => {
      root.classList.remove("login-page");
      if (createdMeta) {
        meta?.remove();
      } else if (meta && previousColor !== null) {
        meta.content = previousColor;
      }
    };
  }, []);

  const handleEnter = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      handleLogin();
    }
  };

  const rememberCheckbox = (
    <label className="lg-remember flex align-items-center g-05" htmlFor="login-remember">
      <input
        id="login-remember"
        type="checkbox"
        className="app-checkbox"
        checked={rememberDevice}
        onChange={(e) => setRememberDevice(e.target.checked)}
      />
      Zapamiętaj to urządzenie na 180 dni
    </label>
  );

  const credentialsStep = (
    <>
      <div className="lg-fields flex-column">
        <div className="lg-field flex-column">
          <label className="lg-label" htmlFor="login-username">Login</label>
          <input
            id="login-username"
            type="text"
            className="lg-input"
            value={username}
            onChange={(value: React.ChangeEvent<HTMLInputElement>) => setUsername(value.target.value)}
            onKeyDown={handleEnter}
            required={true}
            autoComplete="off"
            autoCapitalize="none"
          />
        </div>
        <div className="lg-field flex-column">
          <label className="lg-label" htmlFor="login-password">Hasło</label>
          <input
            id="login-password"
            type="password"
            className="lg-input"
            value={password}
            onChange={(value: React.ChangeEvent<HTMLInputElement>) => setPassword(value.target.value)}
            onKeyDown={handleEnter}
            required={true}
            autoComplete="off"
          />
        </div>
      </div>
      <ActionButton
        text="Zaloguj"
        disableImg={true}
        onClick={handleLogin}
        className="lg-submit"
      />
    </>
  );

  const codeStep = (
    <>
      <div className="lg-mfa flex-column align-items-center g-1">
        <h2 className="lg-title m-0">Weryfikacja dwuetapowa</h2>
        <p className="lg-sub m-0">Wpisz 6-cyfrowy kod z aplikacji uwierzytelniającej.</p>
        <PinInput
          value={code}
          onChange={setCode}
          length={MFA_CODE_LENGTH}
          masked={false}
          revealable={false}
          autoFocus={true}
          onSubmit={handleVerifyCode}
          className="pin-lg"
        />
      </div>
      {rememberCheckbox}
      <ActionButton text="Zweryfikuj" disableImg={true} onClick={handleVerifyCode} className="lg-submit" />
      <div className="lg-links flex-column align-items-center g-025">
        <ActionButton
          text="Nie masz dostępu do aplikacji? Użyj kodu zapasowego"
          disableImg={true}
          onClick={() => setStep("backup")}
          className="lg-link"
        />
        <ActionButton text="Wróć do logowania" disableImg={true} onClick={backToCredentials} className="lg-link lg-link-dim" />
      </div>
    </>
  );

  const backupStep = (
    <>
      <div className="lg-mfa flex-column align-items-center g-1">
        <h2 className="lg-title m-0">Kod zapasowy</h2>
        <p className="lg-sub m-0">Wpisz jeden z zapisanych kodów. Każdy kod działa tylko raz.</p>
      </div>
      <div className="lg-field flex-column">
        <label className="lg-label" htmlFor="login-backup">Kod zapasowy</label>
        <input
          id="login-backup"
          type="text"
          className="lg-input lg-mono"
          value={backupCode}
          placeholder="xxxx-xxxx"
          onChange={(e) => setBackupCode(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") handleVerifyBackup();
          }}
          autoFocus={true}
          autoComplete="off"
          autoCapitalize="none"
          autoCorrect="off"
          spellCheck={false}
        />
      </div>
      {rememberCheckbox}
      <ActionButton text="Zweryfikuj" disableImg={true} onClick={handleVerifyBackup} className="lg-submit" />
      <div className="lg-links flex-column align-items-center g-025">
        <ActionButton text="Użyj kodu z aplikacji" disableImg={true} onClick={() => setStep("code")} className="lg-link" />
        <ActionButton text="Wróć do logowania" disableImg={true} onClick={backToCredentials} className="lg-link lg-link-dim" />
      </div>
    </>
  );

  return (
    <div className="container">
      <div className="display">
        <div className="lg-card flex-column m-auto">
          <NavbarLogoContainer className="centered lg-logo" />
          {step === "credentials" && credentialsStep}
          {step === "code" && codeStep}
          {step === "backup" && backupStep}
        </div>
      </div>
    </div>
  );
};

export default Login;
