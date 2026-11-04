import Navbar from "../components/Navbar";
import ClientCardsDashboard from "../components/ClientCards/ClientCardsDashboard";

const ClientCards = () => {
  return (
    <div className="container">
      <div className="display">
        <Navbar />
        <ClientCardsDashboard />
      </div>
    </div>
  );
};

export default ClientCards;
