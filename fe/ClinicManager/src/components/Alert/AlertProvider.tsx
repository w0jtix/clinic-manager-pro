import { AlertType, Alert } from "../../models/alert";
import CustomAlert from "./CustomAlert";
import React, { createContext, useState, useContext, useCallback, useMemo } from "react";
import { createPortal } from "react-dom";

interface AlertContextType {
    showAlert: (message: string, variant: AlertType) => void;
}

const AlertContext = createContext<AlertContextType | undefined>(undefined);

export const AlertProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [alert, setAlert] = useState<Alert | null> (null);

    // Stable identity so consumers that keep showAlert in effect/callback deps
    // don't re-run just because an alert was shown.
    const showAlert = useCallback((message: string, variant: AlertType) => {
        setAlert({ message, variant });
        setTimeout(() => {
            setAlert(null);
        }, 3000);
    }, []);

    const contextValue = useMemo(() => ({ showAlert }), [showAlert]);

    return (
        <AlertContext.Provider value = {contextValue}>
            {children}
            {alert && createPortal(<CustomAlert {...alert} />, document.body)}
        </AlertContext.Provider>
    )
}

export const useAlert = () => {
    const ctx = useContext(AlertContext);
    if (!ctx) {
        throw new Error("useAlert must be used within an AlertProvider");
    }
    return ctx;
}