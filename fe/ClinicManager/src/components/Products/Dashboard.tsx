import { useEffect } from "react";
import { LayersPlus, PackagePlus, Blocks, RotateCw } from "lucide-react";
import NavigationBar from "../NavigationBar";
import ProductDataList from "./ProductDataList";
import { useState, useCallback } from "react";
import ActionButton from "../ActionButton";
import ProductModal from "../Popups/ProductModal";
import RemoveModal from "../Popups/RemoveModal";
import CategoryManagerModal from "../Popups/CategoryManagerModal";
import SearchBar from "../SearchBar";
import CategorySegments from "../CategorySegments";
import DropdownSelect from "../DropdownSelect";
import FilterDropdown from "../FilterDropdown";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { STOCK_STATUS_LABELS, StockStatusIcon } from "../StockStatusTag";
import CycleFilter from "../CycleFilter";
import { useAlert } from "../Alert/AlertProvider";
import { Product, ProductFilterDTO, StockStatus } from "../../models/product";
import { Brand } from "../../models/brand";
import { AlertType } from "../../models/alert";
import { ProductCategory } from "../../models/categories";
import CategoryService from "../../services/CategoryService";
import BrandService from "../../services/BrandService";
import AllProductService from "../../services/AllProductService";
import UsageRecordsModal from "../Popups/UsageRecordsModal";
import UsageRecordModal from "../Popups/UsageRecordModal";
import { useRoles } from "../../hooks/useRoles";
import { useRestorableState } from "../../hooks/useRestorableState";

export function Dashboard() {
  const [isAddNewProductsPopupOpen, setIsAddNewProductsPopupOpen] =
    useState<boolean>(false);
  const [editProductId, setEditProductId] =
    useState<string | number | null>(null);
  const [removeProductId, setRemoveProductId] =
    useState<string | number | null>(null);
  const [isCategoryPopupOpen, setIsCategoryPopupOpen] =
    useState<boolean>(false);
  const [isUsageRecordsPopupOpen, setIsUsageRecordsPopupOpen] = useState<boolean>(false);
  const [usageProduct, setUsageProduct] = useState<Product | null>(null);
  const [filter, setFilter] = useRestorableState<ProductFilterDTO>("warehouse.filter", {
    categoryIds: null,
    brandIds: null,
    keyword: "",
    includeZero: true,
    isDeleted: false,
  });
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [selectedCategories, setSelectedCategories] = useRestorableState<ProductCategory[]>(
    "warehouse.categories",
    []
  );
  const [brands, setBrands] = useState<Brand[]>([]);
  const [selectedBrands, setSelectedBrands] = useRestorableState<Brand[]>(
    "warehouse.brands",
    []
  );
  const { isCompany } = useRoles();
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchCategories = useCallback(async () => {
    CategoryService.getCategories()
      .then((data) => {
        setCategories(data);
      })
      .catch((error) => {
        setCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching categories:", error);
      });
  }, []);

  const fetchBrands = useCallback(async () => {
    BrandService.getBrands()
      .then((data) =>
        setBrands([...data].sort((a, b) => a.name.localeCompare(b.name)))
      )
      .catch(() => setBrands([]));
  }, []);

  const handleResetFiltersAndData = useCallback(() => {
    setFilter({
      categoryIds: null,
      brandIds: null,
      keyword: "",
      includeZero: true,
      isDeleted: false,
      stockStatus: null,
    });
    setSelectedCategories([]);
    setSelectedBrands([]);
    fetchCategories();
    setResetTriggered((prev) => !prev);
  }, []);

  const handleKeywordChange = useCallback((newKeyword: string) => {
    setFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);

  const handleCategoryChange = useCallback(
    (selected: ProductCategory[] | null) => {
      const list = selected ?? [];
      setSelectedCategories(list);
      setFilter((prev) => ({
        ...prev,
        categoryIds: list.length > 0 ? list.map((c) => c.id) : null,
      }));
    },
    []
  );

  const handleBrandChange = useCallback((selected: Brand | Brand[] | null) => {
    const list = !selected ? [] : Array.isArray(selected) ? selected : [selected];
    setSelectedBrands(list);
    setFilter((prev) => ({
      ...prev,
      brandIds: list.length > 0 ? list.map((b) => b.id) : null,
    }));
  }, []);

  const toggleStockStatus = useCallback(() => {
    setFilter((prev) => ({
      ...prev,
      stockStatus:
        prev.stockStatus == null
          ? StockStatus.OK
          : prev.stockStatus === StockStatus.OK
          ? StockStatus.LOW_STOCK
          : prev.stockStatus === StockStatus.LOW_STOCK
          ? StockStatus.OUT_OF_STOCK
          : null,
    }));
  }, []);

  const handleProductRemove = useCallback(async () => {
    if (removeProductId === null) return;
    AllProductService.deleteProduct(removeProductId)
      .then(() => {
        showAlert(`Produkt usunięty!`, AlertType.SUCCESS);
        handleResetFiltersAndData();
        setRemoveProductId(null);
      })
      .catch((error) => {
        console.error("Error removing Product", error);
        showAlert("Błąd usuwania produktu.", AlertType.ERROR);
      });
  }, [showAlert, removeProductId]);

  useEffect(() => {
    fetchCategories();
    fetchBrands();
  }, []);

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
    <div className="dashboard-panel width-max height-max flex-column align-items-center">
      <NavigationBar showSearchbar={false} />
      <section className="action-buttons-section width-93 flex space-between align-items-center g-1">
        <div className="pd-filters flex flex-wrap align-items-center g-1">
          <FilterDropdown
            label="Filtry"
            count={
              [
                !!filter.keyword?.trim(),
                (selectedCategories?.length ?? 0) > 0,
                (selectedBrands?.length ?? 0) > 0,
                !!filter.stockStatus,
              ].filter(Boolean).length
            }
          >
            <SearchBar
              onKeywordChange={handleKeywordChange}
              resetTriggered={resetTriggered}
              initialValue={filter.keyword ?? ""}
            />
            <CategorySegments
              categories={categories}
              selectedCategories={selectedCategories}
              onSelect={(selected) =>
                handleCategoryChange(selected as ProductCategory[] | null)
              }
              resetTriggered={resetTriggered}
            />
            <div className="fd-row flex align-items-center g-05 space-between">
              <DropdownSelect<Brand>
                items={brands}
                value={selectedBrands}
                onChange={handleBrandChange}
                placeholder="Wybierz markę"
                multiple={true}
                allowNew={false}
                className="brand-dropdown"
              />
              <CycleFilter
                label="Stan:"
                value={
                  filter.stockStatus
                    ? STOCK_STATUS_LABELS[filter.stockStatus]
                    : "wszystkie"
                }
                tone={
                  filter.stockStatus === StockStatus.OK
                    ? "teal"
                    : filter.stockStatus === StockStatus.LOW_STOCK
                    ? "yellow"
                    : filter.stockStatus === StockStatus.OUT_OF_STOCK
                    ? "red"
                    : null
                }
                icon={
                  filter.stockStatus ? (
                    <StockStatusIcon status={filter.stockStatus} />
                  ) : undefined
                }
                iconTitle="Stan magazynowy"
                onClick={toggleStockStatus}
              />
            </div>
          </FilterDropdown>
          <ActionButton
            disableText={true}
            iconTitle={"Resetuj filtry"}
            onClick={handleResetFiltersAndData}
            className={"reset"}
          >
            <RotateCw className="dl-icon icon-control" />
          </ActionButton>
        </div>
        <section className="products-action-buttons flex align-self-center justify-end g-1 mt-1 mb-1">
          <ActionButton
            text={isMobile ? "Zużycie" : "Zużycie produktów"}
            iconTitle={"Zużycie produktów"}
            onClick={() => setIsUsageRecordsPopupOpen(true)}
          >
            <LayersPlus className="dl-icon-1 pc-y" aria-label="Zużycie" />
          </ActionButton>
          <ActionButton
            text={isMobile ? "Produkt" : "Nowy produkt"}
            iconTitle={"Nowy produkt"}
            onClick={() => setIsAddNewProductsPopupOpen(true)}
          >
            <PackagePlus className="dl-icon-1 c-green" aria-label="Produkt" />
          </ActionButton>
          {isCompany && (
            <ActionButton
              text="Kategorie"
              iconTitle={"Kategorie"}
              onClick={() => setIsCategoryPopupOpen(true)}
            >
              <Blocks className="dl-icon-1 c-yellow" aria-label="Kategoria" />
            </ActionButton>
          )}
        </section>
      </section>
      <ProductDataList
        filter={filter}
        setIsAddNewProductsPopupOpen={setIsAddNewProductsPopupOpen}
        setEditProductId={setEditProductId}
        setRemoveProductId={setRemoveProductId}
        setUsageProduct={setUsageProduct}
        isCompany={isCompany}
      />
      {usageProduct && (
        <UsageRecordModal
          initialProduct={usageProduct}
          onCreated={() => setFilter((prev) => ({ ...prev }))}
          onClose={() => setUsageProduct(null)}
        />
      )}
      {isAddNewProductsPopupOpen && (
        <ProductModal
          onClose={() => setIsAddNewProductsPopupOpen(false)}
          onReset={handleResetFiltersAndData}
        />
      )}
      {editProductId != null && (
        <ProductModal
          onClose={() => setEditProductId(null)}
          onReset={handleResetFiltersAndData}
          productId={editProductId}
        />
      )}
      {removeProductId != null && (
        <RemoveModal
          onClose={() => setRemoveProductId(null)}
          handleRemove={handleProductRemove}
          warningText={"Usunięcie spowoduje utratę informacji o produkcie."}
        />
      )}
      {isCategoryPopupOpen && (
        <CategoryManagerModal
          kind="product"
          onClose={() => setIsCategoryPopupOpen(false)}
          onChanged={handleResetFiltersAndData}
        />
      )}
      {isUsageRecordsPopupOpen && (
        <UsageRecordsModal
          onClose={() => {
            setIsUsageRecordsPopupOpen(false);
            handleResetFiltersAndData();
          }}
        />
      )}
    </div>
    </div>
  );
}

export default Dashboard;
