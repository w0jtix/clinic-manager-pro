import Navbar from "../components/Navbar";
import ProductDashboard from "../components/Products/ProductDashboard";

const Product = () => {
  return (
    <div className="container">
      <div className="display">
        <Navbar />
        <ProductDashboard />
      </div>
    </div>
  );
};

export default Product;
