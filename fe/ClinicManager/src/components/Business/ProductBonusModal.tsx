import Modal from "../Modal";
import ProductBonusContent from "./ProductBonusContent";
import { BonusProduct } from "../../models/employee";
import { formatAmount } from "../../utils/formatUtils";

export interface ProductBonusModalProps {
  product: BonusProduct;
  onClose: () => void;
  onProductUpdated?: () => void;
}

export function ProductBonusModal({
  product,
  onClose,
  onProductUpdated,
}: ProductBonusModalProps) {
  const missingPrice =
    product.noPurchaseHistory && !product.fallbackPurchasePriceUsed;

  return (
    <Modal
      title={
        <span className="pv-pbm-title flex align-items-center g-05">
          <span className="pv-day-title pc-y">{product.productName}</span>
          <span className="pv-pbm-meta flex align-items-center g-05">
            <span className="pv-gr-brand">{product.brandName}</span>
            <span className="pv-gr-qty tnum">×{product.quantitySold}</span>
          </span>
          {!missingPrice && (
            <span
              className="pv-day-sum"
              style={
                product.totalBonus < 0
                  ? { color: "var(--color-finance-red)" }
                  : undefined
              }
            >
              {product.totalBonus >= 0 ? "+ " : "− "}
              {formatAmount(Math.abs(product.totalBonus))} zł
            </span>
          )}
        </span>
      }
      onClose={onClose}
      size="md"
      hideFooter
      bodyClassName="pv-day-body"
    >
      <ProductBonusContent
        product={product}
        onProductUpdated={onProductUpdated}
      />
    </Modal>
  );
}

export default ProductBonusModal;
