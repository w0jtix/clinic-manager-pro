import "./App.css";
import {
  createBrowserRouter,
  createRoutesFromElements,
  Navigate,
  Route,
  RouterProvider,
} from "react-router-dom";
import Warehouse from "./pages/Warehouse";
import Login from "./pages/Login";
import RegisterDevice from "./pages/RegisterDevice";
import { AlertProvider } from "./components/Alert/AlertProvider";
import { Main } from "./layouts/Main";
import Profile from "./pages/Profile";
import { UserProvider } from "./components/User/UserProvider";
import Services from "./pages/Services";
import PriceList from "./pages/PriceList";
import Clients from "./pages/Clients";
import Settings from "./pages/Settings";
import Visits from "./pages/Visits";
import Business from "./pages/Business";
import AccessDenied from "./pages/AccessDenied";
import { ProtectedRoute } from "./components/ProtectedRoute";
import CashRegistry from "./pages/CashRegistry";
import Product from "./pages/Product";
import KsefSeller from "./pages/KsefSeller";
import Client from "./pages/Client";
import ClientCards from "./pages/ClientCards";
import ClientCard from "./pages/ClientCard";

const router = createBrowserRouter(
  createRoutesFromElements(
    <>
      <Route element={<Main />}>
        <Route path="/" element={<Warehouse />} />
        <Route path="/warehouse/product/:id" element={<ProtectedRoute permissions={['ROLE_COMPANY']}><Product /></ProtectedRoute>}/>
        <Route path="/ksef/seller/:id" element={<ProtectedRoute permissions={['ROLE_COMPANY']}><KsefSeller /></ProtectedRoute>}/>
        <Route path="/profile" element={<Profile />}/>
        <Route path="/pricelist" element={<PriceList />}/>
        <Route path="/visits" element={<Visits />}/>
        <Route path="/services" element={<Services />}/>
        <Route path="/clients" element={<Clients />}/>
        <Route path="/client/:id" element={<Client />}/>
        <Route path="/client-cards" element={<ClientCards />}/>
        <Route path="/client-cards/:clientId" element={<ClientCard />}/>
        <Route path="/cash-ledger" element={<CashRegistry />} />
        <Route path="/my-company" element={<ProtectedRoute permissions={['ROLE_COMPANY']}><Business /></ProtectedRoute>}/>
        <Route path="/settings" element={<Settings />}/>
        <Route path="/invalid_url" element={<AccessDenied />}/>
        <Route path="*" element={<Navigate to="/invalid_url" replace />}/>
      </Route>
      <Route path="/login" element={<Login />} />
      <Route path="/login/register-device" element={<RegisterDevice />} />
    </>
  )
);

function App() {
  return (
    <AlertProvider>
      <UserProvider>
        <RouterProvider router={router} />
      </UserProvider>
    </AlertProvider>
  );
}

export default App;
