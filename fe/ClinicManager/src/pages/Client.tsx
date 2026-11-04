import Navbar from "../components/Navbar";
import ClientDashboard from "../components/Clients/ClientDashboard";

const Client = () => {
  return (
    <div className="container">
      <div className="display">
        <Navbar />
        <ClientDashboard />
      </div>
    </div>
  );
};

export default Client;
