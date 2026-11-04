import { useLocation, Navigate, Outlet } from "react-router-dom";
import AuthService from "../services/AuthService";
import { FunctionComponent, useEffect } from "react";
import { useInactivityTimer } from "../hooks/useInactivityTimer";



function RequireAuth({ children }: { children: JSX.Element }) {
    useInactivityTimer();
    const location = useLocation();
    const user = AuthService.getCurrentUser();
    const hasToken = !!user?.token;
    useEffect(() => {
        if (hasToken) AuthService.syncCurrentUser();
    }, [hasToken]);
    if(!user || !user.token) {
        return <Navigate to="/login" state={{ fromt: location }} replace/>;
    }
    return children;
}

export const Main: FunctionComponent = () => {
    return (
        <RequireAuth>
            <Outlet />
        </RequireAuth>
    )
}