import { useEffect, useRef } from 'react';
import AuthService from '../services/AuthService';
import { flushPendingSaves, hasPendingSaves } from '../utils/pendingSaves';

const INACTIVITY_TIMEOUT_MS = 8 * 60 * 1000; // 8 mins
const EXTENDED_INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000; // 15 mins

let extendedTimeoutHolders = 0;

const currentTimeout = () =>
    extendedTimeoutHolders > 0 ? EXTENDED_INACTIVITY_TIMEOUT_MS : INACTIVITY_TIMEOUT_MS;

export function useExtendedInactivityTimeout(enabled = true) {
    useEffect(() => {
        if (!enabled) return;
        extendedTimeoutHolders += 1;
        return () => {
            extendedTimeoutHolders -= 1;
        };
    }, [enabled]);
}

export function useInactivityTimer() {
    const lastActivityRef = useRef<number>(Date.now());
    const loggingOutRef = useRef<boolean>(false);

    useEffect(() => {
        const handleActivity = () => {
            lastActivityRef.current = Date.now();
        };

        const events = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'pointerdown', 'pointermove'];
        events.forEach(e => window.addEventListener(e, handleActivity));

        const intervalId = setInterval(() => {
            if (loggingOutRef.current) return;
            if (Date.now() - lastActivityRef.current > currentTimeout()) {
                loggingOutRef.current = true;
                if (hasPendingSaves()) {
                    flushPendingSaves().finally(() => AuthService.logout({ rememberLocation: true }));
                } else {
                    AuthService.logout({ rememberLocation: true });
                }
            }
        }, 30_000);

        return () => {
            events.forEach(e => window.removeEventListener(e, handleActivity));
            clearInterval(intervalId);
        };
    }, []);
}
