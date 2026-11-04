import React, { useCallback, useEffect, useRef, useState } from "react";
import ReactDOM from "react-dom";
import { X } from "lucide-react";
import { isUnsavedChangesPromptSuppressed } from "../utils/unsavedChangesPrompt";
import ActionButton from "./ActionButton";

// Stack of currently open modals so Escape only closes the topmost one.
const modalStack: symbol[] = [];

let scrollLockCount = 0;
let lockedScrollY = 0;

let dirtyModalCount = 0;

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (isUnsavedChangesPromptSuppressed()) return;
  event.preventDefault();
  event.returnValue = "";
};

const registerDirtyModal = () => {
  if (dirtyModalCount++ === 0) window.addEventListener("beforeunload", handleBeforeUnload);
};

const unregisterDirtyModal = () => {
  if (--dirtyModalCount === 0) window.removeEventListener("beforeunload", handleBeforeUnload);
};

const lockScroll = () => {
  if (scrollLockCount++ > 0) return;
  lockedScrollY = window.scrollY;
  document.body.style.top = `-${lockedScrollY}px`;
  document.documentElement.classList.add("scroll-locked");
};

const unlockScroll = () => {
  if (--scrollLockCount > 0) return;
  document.documentElement.classList.remove("scroll-locked");
  document.body.style.top = "";
  window.scrollTo(0, lockedScrollY);
};

export type ModalSize = "xs" | "sm" | "md" | "lg" | "xl" | "2xl" | "full";
export type ModalHeight = "auto" | "tall" | "full";

export interface ModalProps {
  title?: React.ReactNode;
  onClose: () => void;
  size?: ModalSize;
  height?: ModalHeight;

  onConfirm?: () => void;
  confirmText?: string;
  confirmDisabled?: boolean;

  footer?: React.ReactNode;
  hideFooter?: boolean;
  headerExtra?: React.ReactNode;

  closeOnOverlay?: boolean;
  closeOnEsc?: boolean;
  dirty?: boolean;
  dirtyValue?: unknown;

  className?: string;
  bodyClassName?: string;
  overlayClassName?: string;
  children: React.ReactNode;
}

export function Modal({
  title,
  onClose,
  size = "sm",
  height = "auto",
  onConfirm,
  confirmText = "Zapisz",
  confirmDisabled = false,
  footer,
  hideFooter = false,
  headerExtra,
  closeOnOverlay = true,
  closeOnEsc = true,
  dirty = false,
  dirtyValue,
  className = "",
  bodyClassName = "",
  overlayClassName = "",
  children,
}: ModalProps) {
  const portalRoot = document.getElementById("portal-root");

  const idRef = useRef<symbol>();
  if (!idRef.current) idRef.current = Symbol("modal");
  const [confirmClose, setConfirmClose] = useState<boolean>(false);
  const baselineRef = useRef<string | null>(null);
  const serializedValue = dirtyValue === undefined ? null : JSON.stringify(dirtyValue);
  const isDirty =
    dirty || (baselineRef.current !== null && serializedValue !== baselineRef.current);

  const armBaseline = () => {
    if (baselineRef.current === null && serializedValue !== null) {
      baselineRef.current = serializedValue;
    }
  };

  const requestClose = useCallback(() => {
    if (isDirty) {
      setConfirmClose(true);
    } else {
      onClose();
    }
  }, [isDirty, onClose]);

  useEffect(() => {
    if (!isDirty) return;
    registerDirtyModal();
    return unregisterDirtyModal;
  }, [isDirty]);

  useEffect(() => {
    const id = idRef.current!;
    modalStack.push(id);
    return () => {
      const i = modalStack.lastIndexOf(id);
      if (i >= 0) modalStack.splice(i, 1);
    };
  }, []);

  useEffect(() => {
    if (!closeOnEsc) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        event.key === "Escape" &&
        modalStack[modalStack.length - 1] === idRef.current
      ) {
        requestClose();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [closeOnEsc, requestClose]);

  useEffect(() => {
    lockScroll();
    return unlockScroll;
  }, []);

  // Guards against closing when a drag (e.g. selecting text inside an input)
  // starts in the modal and ends on the overlay.
  const overlayMouseDownRef = useRef<boolean>(false);

  const handleOverlayMouseDown = useCallback((event: React.MouseEvent) => {
    overlayMouseDownRef.current = event.target === event.currentTarget;
  }, []);

  const handleOverlayClick = useCallback(
    (event: React.MouseEvent) => {
      const startedOnOverlay = overlayMouseDownRef.current;
      overlayMouseDownRef.current = false;
      if (!closeOnOverlay) return;
      if (!startedOnOverlay || event.target !== event.currentTarget) return;
      if (modalStack[modalStack.length - 1] !== idRef.current) return;
      requestClose();
    },
    [closeOnOverlay, requestClose]
  );

  if (!portalRoot) {
    console.error("Portal root element not found");
    return null;
  }

  const showFooter = !hideFooter && (footer !== undefined || onConfirm !== undefined);

  return (
    <>
    {ReactDOM.createPortal(
    <div
      className={`modal-overlay flex align-items-center justify-center ${overlayClassName}`}
      onMouseDown={handleOverlayMouseDown}
      onClick={handleOverlayClick}
    >
      <div
        className={`modal modal-${size} modal-h-${height} flex-column ${className}`}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="modal-header flex align-items-center space-between g-1">
          <h2 className="modal-title m-0">{title}</h2>
          <div className="modal-header-actions flex align-items-center g-05">
            {headerExtra}
            <button
              className="modal-close transparent border-none flex align-items-center justify-center pointer"
              onClick={requestClose}
              aria-label="Zamknij"
            >
              <X className="dl-icon-1" />
            </button>
          </div>
        </header>

        <div
          className={`modal-body scrollbar ${bodyClassName}`}
          onPointerDownCapture={armBaseline}
          onKeyDownCapture={armBaseline}
        >
          {children}
        </div>

        {showFooter && (
          <footer className="modal-footer flex align-items-center justify-end g-075">
            {footer !== undefined ? (
              footer
            ) : (
              <ActionButton
                text={confirmText}
                className="modal-button"
                disabled={confirmDisabled}
                onClick={() => onConfirm?.()}
              />
            )}
          </footer>
        )}
      </div>
    </div>,
    portalRoot
    )}
    {confirmClose && (
      <Modal
        title="Masz niezapisane zmiany"
        onClose={() => setConfirmClose(false)}
        size="sm"
        footer={
          <>
            <ActionButton
              text="Wróć do edycji"
              className="cc-back-btn"
              onClick={() => setConfirmClose(false)}
            />
            <ActionButton
              text="Zamknij bez zapisu"
              className="cl-filter cc-discard-btn active"
              onClick={() => {
                setConfirmClose(false);
                onClose();
              }}
            />
          </>
        }
      >
        <p className="remove-modal-warning text-align-center m-0">Zamknąć bez zapisywania?</p>
      </Modal>
    )}
    </>
  );
}

export default Modal;
