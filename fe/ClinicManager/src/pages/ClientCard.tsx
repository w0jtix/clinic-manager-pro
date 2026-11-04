import Navbar from "../components/Navbar";
import ClientCardView from "../components/ClientCards/ClientCardView";

const ClientCard = () => {
  return (
    <div className="container">
      <div className="display">
        <Navbar />
        <ClientCardView />
      </div>
    </div>
  );
};

export default ClientCard;
