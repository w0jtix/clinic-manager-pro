import { Employee } from "../../models/employee";
import { UsageRecordItem, UsageReason } from "../../models/usage-record";
import { useCallback, useEffect, useMemo, useState } from "react";
import EmployeeService from "../../services/EmployeeService";
import DropdownSelect from "../DropdownSelect";
import { X, Webhook, CalendarX2 } from "lucide-react";
import { Product } from "../../models/product";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
// import DateInput from "../DateInput";
import WheelDateField from "../WheelDateField";
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
import Modal from "../Modal";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface UsageRecordFormProps {
  usageRecordItems: UsageRecordItem[];
  setUsageRecordItems: React.Dispatch<React.SetStateAction<UsageRecordItem[]>>;
  sharedFields: { employee: Employee | null; usageDate: string };
  setSharedFields: React.Dispatch<
    React.SetStateAction<{ employee: Employee | null; usageDate: string }>
  >;
  hasSupplyError: boolean;
  setHasSupplyError: React.Dispatch<React.SetStateAction<boolean>>;
  className?: string;
}

export function UsageRecordForm({
  usageRecordItems,
  setUsageRecordItems,
  sharedFields,
  setSharedFields,
  setHasSupplyError,
  className = "",
}: UsageRecordFormProps) {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<
    ProductCategory[]
  >([]);
  const [productFilter, setProductFilter] = useState<ProductFilterDTO>({
    categoryIds: null,
    keyword: "",
  });
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const [isPickerOpen, setIsPickerOpen] = useState<boolean>(false);
  const [pendingProducts, setPendingProducts] = useState<Product[]>([]);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const usageReasonItems = [
    { id: UsageReason.REGULAR_USAGE, name: "ZUŻYCIE" },
    { id: UsageReason.OUT_OF_DATE, name: "PO DACIE" },
  ];

  const fetchEmployees = async () => {
    EmployeeService.getAllEmployees()
      .then((data) => {
        setEmployees(data);
      })
      .catch((error) => {
        console.error("Error fetching Employees: ", error);
        showAlert("Błąd", AlertType.ERROR);
        setEmployees([]);
      });
  };
  const fetchProducts = async (pageNum: number = 0, append: boolean = false): Promise<void> => {
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

  const handleEmployeeChange = useCallback(
    (employee: Employee | Employee[] | null) => {
      const selectedEmployee = Array.isArray(employee) ? employee[0] : employee;
      setSharedFields((prev) => ({
        ...prev,
        employee: selectedEmployee,
      }));
    },
    [setSharedFields]
  );
  const handleUsageDateChange = useCallback(
    (newDate: string | null) => {
      setSharedFields((prev) => ({
        ...prev,
        usageDate: newDate || new Date().toISOString().split("T")[0],
      }));
    },
    [setSharedFields]
  );

  const handleProductKeywordChange = useCallback((newKeyword: string) => {
    setProductFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);
  const handleSelectProducts = useCallback(
    (product: Product) => {
      const exists = usageRecordItems.some(
        (item) => item.product.id === product.id
      );

      if (exists) {
        showAlert("Produkt już jest na liście", AlertType.ERROR);
        return;
      }

      const newItem: UsageRecordItem = {
        product: product,
        quantity: 1,
        usageReason: UsageReason.REGULAR_USAGE,
      };

      setUsageRecordItems((prev) => [...prev, newItem]);
    },
    [usageRecordItems, showAlert, setUsageRecordItems]
  );
  const handleCategoryChange = useCallback(
    (categories: ProductCategory[] | null) => {
      categories
        ? setSelectedCategories(categories)
        : setSelectedCategories([]);
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
      setUsageRecordItems((prev) =>
        prev.filter((item) => item.product.id !== productId)
      );
      setHasSupplyError(false);
    },
    [setUsageRecordItems, setHasSupplyError]
  );

  const handleQuantityChange = useCallback(
    async (productId: number, quantity: number | null) => {
      setUsageRecordItems((prev) =>
        prev.map((item) =>
          item.product.id === productId
            ? { ...item, quantity: quantity ?? 0 }
            : item
        )
      );

      if (quantity && quantity > 0) {
        try {
          const currentProduct = await AllProductService.getProductById(productId);
          if (quantity > currentProduct.supply) {
            setHasSupplyError(true);
            showAlert(
              `Przekroczono dostępny stan magazynowy dla ${currentProduct.name}. Dostępne: ${currentProduct.supply}`,
              AlertType.ERROR
            );
          }
        } catch (error) {
          console.error("Error checking product supply:", error);
        }
      }
    },
    [setUsageRecordItems, showAlert, setHasSupplyError]
  );

  const handleUsageReasonChange = useCallback(
    (productId: number, reason: UsageReason) => {
      setUsageRecordItems((prev) =>
        prev.map((item) =>
          item.product.id === productId
            ? { ...item, usageReason: reason }
            : item
        )
      );
    },
    [setUsageRecordItems]
  );

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100; // 100px b4 end of the list

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
    fetchEmployees();
    fetchCategories();
  }, []);

  const selectedProductIds = useMemo(
    () => new Set(usageRecordItems.map((item) => item.product.id)),
    [usageRecordItems]
  );

  const isPicked = useCallback(
    (product: Product): boolean =>
      selectedProductIds.has(product.id) ||
      pendingProducts.some((p) => p.id === product.id),
    [selectedProductIds, pendingProducts]
  );

  const handleTogglePending = useCallback(
    (product: Product) => {
      if (selectedProductIds.has(product.id)) return;
      setPendingProducts((prev) =>
        prev.some((p) => p.id === product.id)
          ? prev.filter((p) => p.id !== product.id)
          : [...prev, product]
      );
    },
    [selectedProductIds]
  );

  const handleOpenPicker = useCallback(() => {
    setPendingProducts([]);
    setIsPickerOpen(true);
  }, []);

  const handleConfirmPicker = useCallback(() => {
    if (pendingProducts.length > 0) {
      const newItems: UsageRecordItem[] = pendingProducts.map((product) => ({
        product: product,
        quantity: 1,
        usageReason: UsageReason.REGULAR_USAGE,
      }));
      setUsageRecordItems((prev) => [...prev, ...newItems]);
    }
    setPendingProducts([]);
    setIsPickerOpen(false);
  }, [pendingProducts, setUsageRecordItems]);

  const pickerColumns = useMemo<Column<Product>[]>(
    () => [
      {
        key: "name",
        header: "Produkt",
        grow: true,
        render: (product) => (
          <span
            className={`flex align-items-center g-05 dl-name ${
              isPicked(product) ? "pc-y" : ""
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
        cellClassName: "dl-cell",
        render: (product) => (
          <span className={isPicked(product) ? "pc-y" : ""}>
            {product.supply}
          </span>
        ),
      },
    ],
    [isPicked]
  );

  const employeeField = (
    <DropdownSelect<Employee>
      items={employees}
      onChange={handleEmployeeChange}
      value={sharedFields.employee}
      placeholder="Pracownik"
      searchable={false}
      multiple={false}
      allowNew={false}
      className="visit-list"
    />
  );

  const dateField = (
    <>
      {/* <DateInput
        onChange={handleUsageDateChange}
        selectedDate={sharedFields.usageDate}
      /> */}
      <WheelDateField
        title="Data zużycia"
        value={sharedFields.usageDate}
        onChange={handleUsageDateChange}
      />
    </>
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
      className={`form-container flex-column f-1 min-height-0 align-self-center ${
        isMobile ? "width-max" : "width-90"
      }`}
    >
      <div className="width-max f-1 flex space-between min-height-0">
        <div
          className={`flex-column align-items-center ${
            isMobile ? "width-max min-height-0" : "width-60"
          }`}
        >
          {isMobile ? (
            <div className="ur-shared flex width-max align-items-center">
              <div className="flex align-items-center g-05">
                {employeeField}
                {dateField}
              </div>
              <ActionButton
                disableImg={true}
                text={"Wybierz z listy"}
                iconTitle={"Wybierz z listy"}
                className="ur-pick-btn"
                onClick={handleOpenPicker}
              />
            </div>
          ) : (
            <div className="flex space-between width-max align-items-center">
              <div className="popup-common-section-row flex align-items-center space-between g-10px name">
                {employeeField}
              </div>
              <div className="popup-common-section-row flex align-items-center space-between g-10px name">
                <a className="product-form-input-title">Data:</a>
                {dateField}
              </div>
            </div>
          )}
          <div className="ur-list scrollbar flex-column f-1 width-max mt-05">
            {usageRecordItems.length === 0 && (
              <span className="dl-empty flex f-1 align-items-center justify-center">
                Wybierz zużyte produkty z listy
              </span>
            )}
            {usageRecordItems.map((item) => (
              <div
                key={item.product.id}
                className="ur-item flex align-items-center width-max g-075"
              >
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
                {isMobile ? (
                  <span className="ur-name-stack flex-column f-1 mw-0">
                    <span className="ur-name">{item.product.name}</span>
                    <span className="ur-brand">{item.product.brand.name}</span>
                  </span>
                ) : (
                  <>
                    <span className="ur-name">{item.product.name}</span>
                    <span className="ur-brand f-1">
                      {item.product.brand.name}
                    </span>
                  </>
                )}
                <DigitInput
                  value={item.quantity}
                  onChange={(value) =>
                    handleQuantityChange(item.product.id, value)
                  }
                  min={1}
                  placeholder="1"
                  className={"in-xs"}
                />
                {isMobile ? (
                  <div className="cs-seg ur-reason-seg flex align-items-center">
                    <button
                      type="button"
                      title="ZUŻYCIE"
                      className={`usage ${
                        item.usageReason === UsageReason.REGULAR_USAGE
                          ? "on"
                          : ""
                      }`}
                      onClick={() =>
                        handleUsageReasonChange(
                          item.product.id,
                          UsageReason.REGULAR_USAGE
                        )
                      }
                    >
                      <Webhook className="dl-icon-1" />
                    </button>
                    <button
                      type="button"
                      title="PO DACIE"
                      className={`date ${
                        item.usageReason === UsageReason.OUT_OF_DATE
                          ? "on"
                          : ""
                      }`}
                      onClick={() =>
                        handleUsageReasonChange(
                          item.product.id,
                          UsageReason.OUT_OF_DATE
                        )
                      }
                    >
                      <CalendarX2 className="dl-icon-1" />
                    </button>
                  </div>
                ) : (
                  <DropdownSelect
                    items={usageReasonItems}
                    value={
                      usageReasonItems.find((r) => r.id === item.usageReason) ||
                      usageReasonItems[0]
                    }
                    onChange={(selected) => {
                      const reason = Array.isArray(selected)
                        ? selected[0]?.id
                        : selected?.id;
                      if (reason)
                        handleUsageReasonChange(item.product.id, reason);
                    }}
                    searchable={false}
                    allowNew={false}
                    multiple={false}
                    className={`ur-reason ${
                      item.usageReason === UsageReason.OUT_OF_DATE
                        ? "date"
                        : "usage"
                    }`}
                  />
                )}
              </div>
            ))}
          </div>
        </div>
        {!isMobile && (
          <div className="ur-picker  flex-column min-height-0 g-075">
            <h2 className="ur-picker-title m-0">Wybierz z listy:</h2>
            {pickerContent}
          </div>
        )}
      </div>
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
export default UsageRecordForm;
