import { useEffect } from "react";
import { useBlocker } from "react-router-dom";
import Modal from "./Modal";
import ActionButton from "./ActionButton";
import { isUnsavedChangesPromptSuppressed } from "../utils/unsavedChangesPrompt";

export interface UnsavedChangesGuardProps {
  when: boolean;
}

export function UnsavedChangesGuard({ when }: UnsavedChangesGuardProps) {
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => when && currentLocation.key !== nextLocation.key
  );

  useEffect(() => {
    if (!when) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (isUnsavedChangesPromptSuppressed()) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [when]);

  if (blocker.state !== "blocked") return null;

  return (
    <Modal
      title="Masz niezapisane zmiany"
      onClose={() => blocker.reset()}
      size="sm"
      footer={
        <>
          <ActionButton
            text="Wróć do edycji"
            className="cc-back-btn"
            onClick={() => blocker.reset()}
          />
          <ActionButton
            text="Wyjdź bez zapisu"
            className="cl-filter cc-discard-btn active"
            onClick={() => blocker.proceed()}
          />
        </>
      }
    >
      <p className="remove-modal-warning text-align-center m-0">Wyjść bez zapisywania?</p>
    </Modal>
  );
}

export default UnsavedChangesGuard;
