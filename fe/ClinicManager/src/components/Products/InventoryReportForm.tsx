import { useCallback, useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import { Product } from "../../models/product";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import SearchBar from "../SearchBar";
import DataList, { Column } from "../DataList";
import { ProductFilterDTO } from "../../models/product";
import AllProductService from "../../services/AllProductService";
import { ProductCategory } from "../../models/categories";
import CategoryService from "../../services/CategoryService";
import CategorySegments from "../CategorySegments";
import ActionButton from "../ActionButton";
import DigitInput from "../DigitInput";
import { MedicalMark } from "../ProductVatTag";
import {
  NewInventoryReportItem,
  InventoryReportItem,
} from "../../models/inventory_report";
import { getSupplyChange } from "../../utils/inventoryReportUtils";
import Modal from "../Modal";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface InventoryReportFormProps {
  inventoryReportItems: InventoryReportItem[] | NewInventoryReportItem[];
  setInventoryReportItems: React.Dispatch<
    React.SetStateAction<InventoryReportItem[] | NewInventoryReportItem[]>
  >;
  className?: string;
}

export function InventoryReportForm({
  inventoryReportItems,
  setInventoryReportItems,
  className = "",
}: InventoryReportFormProps) {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<ProductCategory[]>(
    []
  );
  const [productFilter, setProductFilter] = useState<ProductFilterDTO>({
    categoryIds: null,
    keyword: "",
    includeZero: true,
    isDeleted: false,
  });
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();
  const [isPickerOpen, setIsPickerOpen] = useState<boolean>(false);
  const [pendingProducts, setPendingProducts] = useState<Product[]>([]);

  const fetchProducts = async (
    pageNum: number = 0,
    append: boolean = false
  ): Promise<void> => {
    AllProductService.getProducts(productFilter, pageNum, 30)
      .then((data) => {
        const content = data?.content || [];
        if (append) {
          setProducts((prev) => [...prev, ...content]);
        } else {
          setProducts(content);
        }

        setHasMore(!data.last);
        setPage(pageNum);
        setLoading(false);
      })
      .catch((error) => {
        if (!append) setProducts([]);
        setLoading(false);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching products:", error);
      });
  };

  const fetchCategories = async (): Promise<void> => {
    CategoryService.getCategories()
      .then((data) => {
        setCategories(data);
      })
      .catch((error) => {
        setCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching categories:", error);
      });
  };

  const handleProductKeywordChange = useCallback((newKeyword: string) => {
    setProductFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);

  const handleSelectProducts = useCallback(
    (product: Product) => {
      const exists = inventoryReportItems.some(
        (item) => item.product.id === product.id
      );

      if (exists) {
        showAlert("Produkt już jest na liście", AlertType.ERROR);
        return;
      }

      const newItem: NewInventoryReportItem = {
        product: product,
        supplyBefore: product.supply,
        supplyAfter: product.supply,
      };

      setInventoryReportItems((prev) => [...prev, newItem]);
    },
    [inventoryReportItems, showAlert, setInventoryReportItems]
  );

  const addedProductIds = useMemo(
    () => new Set(inventoryReportItems.map((item) => item.product.id)),
    [inventoryReportItems]
  );

  const isPicked = useCallback(
    (product: Product): boolean =>
      addedProductIds.has(product.id) ||
      pendingProducts.some((p) => p.id === product.id),
    [addedProductIds, pendingProducts]
  );

  const handleTogglePending = useCallback(
    (product: Product) => {
      if (addedProductIds.has(product.id)) return;
      setPendingProducts((prev) =>
        prev.some((p) => p.id === product.id)
          ? prev.filter((p) => p.id !== product.id)
          : [...prev, product]
      );
    },
    [addedProductIds]
  );

  const handleOpenPicker = useCallback(() => {
    setPendingProducts([]);
    setIsPickerOpen(true);
  }, []);

  const handleConfirmPicker = useCallback(() => {
    if (pendingProducts.length > 0) {
      const newItems: NewInventoryReportItem[] = pendingProducts.map(
        (product) => ({
          product: product,
          supplyBefore: product.supply,
          supplyAfter: product.supply,
        })
      );
      setInventoryReportItems((prev) => [...prev, ...newItems] as typeof prev);
    }
    setPendingProducts([]);
    setIsPickerOpen(false);
  }, [pendingProducts, setInventoryReportItems]);

  const handleCategoryChange = useCallback(
    (categories: ProductCategory[] | null) => {
      categories ? setSelectedCategories(categories) : setSelectedCategories([]);
    },
    []
  );

  useEffect(() => {
    setProductFilter((prev) => ({
      ...prev,
      categoryIds:
        selectedCategories.length > 0
          ? selectedCategories.map((cat) => cat.id)
          : null,
    }));
  }, [selectedCategories]);

  const handleRemoveProduct = useCallback(
    (productId: number) => {
      setInventoryReportItems((prev) =>
        prev.filter((item) => item.product.id !== productId)
      );
    },
    [setInventoryReportItems]
  );

  const handleSupplyAfterChange = useCallback(
    (productId: number, supplyAfter: number | null) => {
      setInventoryReportItems((prev) =>
        prev.map((item) =>
          item.product.id === productId
            ? { ...item, supplyAfter: supplyAfter ?? 0 }
            : item
        )
      );
    },
    [setInventoryReportItems]
  );

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;

      if (scrolledToBottom && hasMore && !loading) {
        fetchProducts(page + 1, true);
      }
    },
    [hasMore, loading, page, productFilter]
  );

  useEffect(() => {
    fetchProducts(0, false);
    setPage(0);
    setHasMore(true);
  }, [productFilter]);

  useEffect(() => {
    fetchCategories();
  }, []);

  const pickerColumns = useMemo<Column<Product>[]>(
    () => [
      {
        key: "name",
        header: "Produkt",
        grow: true,
        render: (product) => (
          <span
            className={`flex align-items-center g-05 dl-name ${
              isMobile && isPicked(product) ? "pc-y" : ""
            }`}
          >
            <span
              className="dl-dot"
              style={{
                backgroundColor: product.category?.color
                  ? `rgb(${product.category.color})`
                  : undefined,
                boxShadow: product.category?.color
                  ? `0 0 6px rgba(${product.category.color}, 0.5)`
                  : undefined,
              }}
            />
            <MedicalMark vatRate={product.vatRate} />
            {product.name}
          </span>
        ),
      },
      {
        key: "supply",
        header: "Stan",
        width: "3.5rem",
        align: "center",
        cellClassName: "dl-dim",
        render: (product) => (
          <span className={isMobile && isPicked(product) ? "pc-y" : ""}>
            {product.supply}
          </span>
        ),
      },
    ],
    [isMobile, isPicked]
  );

  const pickerContent = (
    <>
      <SearchBar
        onKeywordChange={handleProductKeywordChange}
        resetTriggered={false}
        placeholder="Szukaj produktu..."
        className="ur-picker-search"
      />
      <CategorySegments
        categories={categories}
        selectedCategories={selectedCategories}
        onSelect={(selected) =>
          handleCategoryChange(selected as ProductCategory[] | null)
        }
      />
      <DataList<Product>
        columns={pickerColumns}
        items={products}
        getRowKey={(product) => product.id}
        onRowClick={(product) =>
          isMobile ? handleTogglePending(product) : handleSelectProducts(product)
        }
        onScroll={handleScroll}
        isLoading={loading}
        hideHeader={isMobile}
        emptyText="Brak produktów"
      />
    </>
  );

  return (
    <div
      className={`width-max f-1 min-height-0 ${
        isMobile ? "flex-column g-075" : "flex g-2"
      } ${className}`}
    >
      {isMobile && (
        <div className="ir-form-top flex align-items-center justify-end width-max">
          <ActionButton
            disableImg={true}
            text={"Wybierz z listy"}
            iconTitle={"Wybierz z listy"}
            onClick={handleOpenPicker}
          />
        </div>
      )}
      <div className="ur-list scrollbar flex-column f-1 min-height-0">
        {inventoryReportItems.length === 0 && (
          <span className="dl-empty flex f-1 align-items-center justify-center">
            Wybierz produkty z listy
          </span>
        )}
        {inventoryReportItems.map((item) => {
          const change = getSupplyChange(item);
          return (
            <div
              key={item.product.id}
              className={`ur-item ir-form-item width-max ${
                isMobile ? "flex-column g-05" : "flex align-items-center g-075"
              }`}
            >
              {isMobile ? (
                <>
                  <div className="flex align-items-center width-max g-05 mw-0">
                    <span
                      className="dl-dot"
                      style={{
                        backgroundColor: item.product.category?.color
                          ? `rgb(${item.product.category.color})`
                          : undefined,
                        boxShadow: item.product.category?.color
                          ? `0 0 6px rgba(${item.product.category.color}, 0.5)`
                          : undefined,
                      }}
                    />
                    <MedicalMark vatRate={item.product.vatRate} />
                    <span className="ur-name">{item.product.name}</span>
                    <span className="ur-brand f-1">
                      {item.product.brand.name}
                    </span>
                    <ActionButton
                      iconTitle={"Usuń Produkt"}
                      onClick={() => handleRemoveProduct(item.product.id)}
                      disableText={true}
                      disableBorder={true}
                      className="ur-remove"
                    >
                      <X className="dl-icon-1" />
                    </ActionButton>
                  </div>
                  <div className="ir-form-meta flex align-items-center">
                    <div className="flex align-items-center g-05">
                      <span className="ir-label">Było:</span>
                      <DigitInput
                        value={item.supplyBefore}
                        onChange={() => undefined}
                        min={0}
                        max={999}
                        disabled={true}
                        className="in-xs"
                      />
                    </div>
                    <div className="flex align-items-center g-05">
                      <span className="ir-label">Jest:</span>
                      <DigitInput
                        value={item.supplyAfter ?? item.supplyBefore}
                        onChange={(value) =>
                          handleSupplyAfterChange(item.product.id, value)
                        }
                        min={0}
                        max={999}
                        className="in-xs"
                      />
                    </div>
                    <span className={`ir-change ${change.className}`}>
                      {change.label}
                    </span>
                  </div>
                </>
              ) : (
                <>
                  <ActionButton
                    iconTitle={"Usuń Produkt"}
                    onClick={() => handleRemoveProduct(item.product.id)}
                    disableText={true}
                    disableBorder={true}
                    className="ur-remove"
                  >
                    <X className="dl-icon-1" />
                  </ActionButton>
                  <span
                    className="dl-dot"
                    style={{
                      backgroundColor: item.product.category?.color
                        ? `rgb(${item.product.category.color})`
                        : undefined,
                      boxShadow: item.product.category?.color
                        ? `0 0 6px rgba(${item.product.category.color}, 0.5)`
                        : undefined,
                    }}
                  />
                  <MedicalMark vatRate={item.product.vatRate} />
                  <span className="ur-name">{item.product.name}</span>
                  <span className="ur-brand f-1">
                    {item.product.brand.name}
                  </span>
                  <span className="ir-label">Było:</span>
                  <DigitInput
                    value={item.supplyBefore}
                    onChange={() => undefined}
                    min={0}
                    max={999}
                    disabled={true}
                    className="in-xs"
                  />
                  <span className="ir-label">Jest:</span>
                  <DigitInput
                    value={item.supplyAfter ?? item.supplyBefore}
                    onChange={(value) =>
                      handleSupplyAfterChange(item.product.id, value)
                    }
                    min={0}
                    max={999}
                    className="in-xs"
                  />
                  <span className={`ir-change ${change.className}`}>
                    {change.label}
                  </span>
                </>
              )}
            </div>
          );
        })}
      </div>

      {!isMobile && (
        <div className="ur-picker width-30 flex-column min-height-0 g-075">
          <h2 className="ur-picker-title m-0">Wybierz z listy:</h2>
          {pickerContent}
        </div>
      )}
      {isMobile && isPickerOpen && (
        <Modal
          title="Wybierz z listy"
          onClose={() => {
            setPendingProducts([]);
            setIsPickerOpen(false);
          }}
          size="md"
          height="tall"
          bodyClassName="nested"
          confirmText="Zatwierdź"
          onConfirm={handleConfirmPicker}
          className="ur-picker-modal"
        >
          <div className="ur-picker flex-column f-1 min-height-0 g-075">
            {pickerContent}
          </div>
        </Modal>
      )}
    </div>
  );
}

export default InventoryReportForm;
