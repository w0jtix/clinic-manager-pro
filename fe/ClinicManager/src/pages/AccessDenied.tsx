import Navbar from "../components/Navbar";
import ActionButton from "../components/ActionButton";
import { useNavigate } from "react-router-dom";
import { House } from "lucide-react";

const AccessDenied = () => {
  const navigate = useNavigate();

  return (
    <div className="container">
      <div className="display">
        <Navbar />
        <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
        <div className="dashboard-panel width-max f-1 height-max flex justify-center align-items-center ">
          <div className="flex-column align-items-center g-1">
            <h1 className="access-denied-title">Strona nie istnieje</h1>
            <ActionButton
              text="Strona główna"
              iconTitle="Strona główna"
              onClick={() => navigate("/")}
            >
              <House className="dl-icon-1 pc-y" />
            </ActionButton>
          </div>
        </div>
        </div>
      </div>
    </div>
  );
};

export default AccessDenied;
