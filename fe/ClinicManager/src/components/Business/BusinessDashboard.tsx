import NavigationBar from "../NavigationBar";
import { SubModuleType } from "../../constants/modules";
import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";
import ExpenseHistory from "./ExpenseHistory";
import EmployeesStats from "./EmployeesStats";
import EmployeeBonusPage from "./EmployeeBonusPage";
import BusinessSettings from "./BusinessSettings";
import { CompanyStatistics } from "./CompanyStatistics";
import KsefInvoicesPage from "./KsefInvoicesPage";

export function BusinessDashboard() {
    const location = useLocation();
    const [moduleVisible, setModuleVisible] = useState<SubModuleType>("Expenses");

    useEffect(() => {
      const stateModule = (location.state as { module?: string })?.module;
      if (stateModule === "Expenses" || stateModule === "Employees" || stateModule === "EmployeeBonus" || stateModule === "Statistics" || stateModule === "CompanySettings" || stateModule === "KSeF") {
        setModuleVisible(stateModule);
      }
    }, [location.state]);


    return (
      <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
        <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />
        {moduleVisible === "Expenses" && <ExpenseHistory/>}
        {moduleVisible === "KSeF" && <KsefInvoicesPage />}
        {moduleVisible === "Employees" && <EmployeesStats/>}
        {moduleVisible === "EmployeeBonus" && <EmployeeBonusPage/>}
        {moduleVisible === "Statistics" && <CompanyStatistics />}
        {moduleVisible === "CompanySettings" && <BusinessSettings />}
        
        </div>
        </div>
    );

}

export default BusinessDashboard;