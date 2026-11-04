import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import ClientsDashboard from "../components/Clients/ClientsDashboard";
import ReviewsDashboard from "../components/Clients/ReviewsDashboard";
import VouchersDashboard from "../components/Clients/VouchersDashboard";
import DebtsDashboard from "../components/Clients/DebtsDashboard";
import Navbar from "../components/Navbar";
import { SubModuleType } from "../constants/modules";

const CLIENT_MODULES: SubModuleType[] = [
  "ClientList",
  "Reviews",
  "Vouchers",
  "Debts",
];

const Clients = () => {
  const location = useLocation();
  const [moduleVisible, setModuleVisible] =
    useState<SubModuleType>("ClientList");

  useEffect(() => {
    const stateModule = (location.state as { module?: SubModuleType })?.module;
    if (stateModule && CLIENT_MODULES.includes(stateModule)) {
      setModuleVisible(stateModule);
    }
  }, [location.state]);

  return (
    <div className="container">
      <div className="display">
        <Navbar />
        {moduleVisible === "ClientList" && <ClientsDashboard />}
        {moduleVisible === "Reviews" && <ReviewsDashboard />}
        {moduleVisible === "Vouchers" && <VouchersDashboard />}
        {moduleVisible === "Debts" && <DebtsDashboard />}
      </div>
    </div>
  );
};

export default Clients;
