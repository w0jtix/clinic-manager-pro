import { useState, useCallback, useEffect, useMemo } from "react";
import { parseVolumeFromName } from "../../utils/productNameUtils";
import Modal from "../Modal";
import ProductForm from "../Products/ProductForm";
import AllProductService from "../../services/AllProductService";
import BrandService from "../../services/BrandService";
import { Action } from "../../models/action";
import { Product, NewProduct } from "../../models/product";
import { NewBrand } from "../../models/brand";
import { AlertType } from "../../models/alert";
import { validateBrandForm, validateProductForm } from "../../utils/validators";
import {
  extractProductErrorMessage,
  extractBrandErrorMessage,
} from "../../utils/errorHandler";
import { useAlert } from "../Alert/AlertProvider";
import { VatRate } from "../../models/vatrate";

export interface ProductModalProps {
  onClose: () => void;
  onReset: (createdProduct?: Product) => void;
  productId?: number | string | null;
  initialName?: string;
  initialVatRate?: VatRate | null;
  hideStock?: boolean;
  hidePurchaseInfo?: boolean;
  purchaseInfoOnly?: boolean;
  className?: string;
}

export function ProductModal({
  onClose,
  onReset,
  productId,
  initialName = "",
  initialVatRate = null,
  hideStock = false,
  hidePurchaseInfo = false,
  purchaseInfoOnly = false,
  className = "",
}: ProductModalProps) {
  const [fetchedProduct, setFetchedProduct] = useState<Product | null>(null);
  const [productDTO, setProductDTO] = useState<NewProduct>({
    name: initialName,
    category: null,
    brand: null,
    supply: 0,
    sellingPrice: null,
    vatRate: initialVatRate ?? VatRate.VAT_23,
    volume: null,
    unit: null,
    description: "",
    fallbackNetPurchasePrice: null,
    fallbackVatRate: initialVatRate ?? VatRate.VAT_23,
    isDeleted: false,
  });
  const productDefaults = useMemo(
    () => ({ vatRate: initialVatRate, ...parseVolumeFromName(initialName) }),
    [initialVatRate, initialName]
  );
  const [brandToCreate, setBrandToCreate] = useState<NewBrand | null>(null);
  const { showAlert } = useAlert();

  const action = productId ? Action.EDIT : Action.CREATE;

  const fetchProductById = async (productId: number | string) => {
    AllProductService.getProductById(productId)
      .then((data) => {
        setFetchedProduct(data);
        setProductDTO(data);
      })
      .catch((error) => {
        console.error("Error fetching product by id: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  const handleBrandToCreate = useCallback(
    async (brandToCreate: NewBrand) => {
      const error = validateBrandForm(brandToCreate, undefined, Action.CREATE);
      if (error) {
        showAlert(error, AlertType.ERROR);
        return null;
      }
      try {
        const newBrand = await BrandService.createBrand(brandToCreate);
        return newBrand;
      } catch (error) {
        console.error("Error creating new Brand.", error);
        const errorMessage = extractBrandErrorMessage(error, action);
        showAlert(errorMessage, AlertType.ERROR);
        return null;
      }
    },
    [showAlert, action]
  );

  const handleProductAction = useCallback(async () => {
    if (!productDTO) return;
    try {
      const error = validateProductForm(
        { ...productDTO, brand: brandToCreate ?? productDTO.brand },
        fetchedProduct,
        action
      );
      if (error) {
        showAlert(error, AlertType.ERROR);
        return;
      }
      if (brandToCreate) {
        const brandError = validateBrandForm(brandToCreate, undefined, Action.CREATE);
        if (brandError) {
          showAlert(brandError, AlertType.ERROR);
          return;
        }
      }

      let productToSave: NewProduct = productDTO;
      if (brandToCreate) {
        const newBrand = await handleBrandToCreate(brandToCreate);
        if (!newBrand) {
          return;
        }
        productToSave = { ...productDTO, brand: newBrand };
        setProductDTO(productToSave);
        setBrandToCreate(null);
      }

      if (action === Action.CREATE) {
        const createdProduct = await AllProductService.createProduct(productToSave);
        showAlert(
          `Produkt ${productDTO.name} został utworzony!`,
          AlertType.SUCCESS
        );
        onReset(createdProduct);
      } else if (action === Action.EDIT && productId && fetchedProduct) {
        await AllProductService.updateProduct(productId, productToSave);
        showAlert(
          `Produkt ${productDTO.name} został zaktualizowany!`,
          AlertType.SUCCESS
        );
        onReset();
      }
      onClose();
    } catch (error) {
      console.error(
        `Error ${action === Action.CREATE ? "creating" : "updating"} product:`,
        error
      );
      const errorMessage = extractProductErrorMessage(error, action);
      showAlert(errorMessage, AlertType.ERROR);
    }
  }, [
    productDTO,
    brandToCreate,
    action,
    fetchedProduct,
    productId,
    onReset,
    onClose,
    showAlert,
    handleBrandToCreate,
  ]);

  useEffect(() => {
    if (productId) {
      fetchProductById(productId);
    }
  }, [productId]);

  return (
    <Modal
      dirtyValue={[productDTO, brandToCreate]}
      title={
        purchaseInfoOnly
          ? `${productDTO.name} - uzupełnij dane`
          : action === Action.CREATE
          ? "Dodaj Nowy Produkt"
          : "Edytuj Produkt"
      }
      onClose={onClose}
      size={purchaseInfoOnly ? "sm" : "md"}
      onConfirm={handleProductAction}
      className={className}
    >
      <ProductForm
        brandToCreate={brandToCreate}
        setBrandToCreate={setBrandToCreate}
        action={action}
        productDTO={productDTO}
        setProductDTO={setProductDTO}
        hideStock={hideStock}
        hidePurchaseInfo={hidePurchaseInfo}
        purchaseInfoOnly={purchaseInfoOnly}
        productDefaults={action === Action.CREATE ? productDefaults : undefined}
      />
    </Modal>
  );
}

export default ProductModal;
