import { useState, useCallback, useEffect, useMemo } from "react";
import { RotateCw } from "lucide-react";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import DataList, { Column } from "../DataList";
import CategorySegments from "../CategorySegments";
import DropdownSelect from "../DropdownSelect";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { ProductFilterDTO, Product } from "../../models/product";
import { ProductCategory } from "../../models/categories";
import CategoryService from "../../services/CategoryService";
import AllProductService from "../../services/AllProductService";
import BrandService from "../../services/BrandService";
import { Brand } from "../../models/brand";
import { MedicalMark } from "../ProductVatTag";
import FilterDropdown from "../FilterDropdown";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { openPdf } from "../../utils/pdfUtils";

export interface ProductReportModalProps {
  onClose: () => void;
  className?: string;
}

export function ProductReportModal({
  onClose,
  className = "",
}: ProductReportModalProps) {
  const [filter, setFilter] = useState<ProductFilterDTO>({
    categoryIds: null,
    brandIds: null,
    keyword: "",
    includeZero: true,
    isDeleted: false,
  });
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<ProductCategory[]>(
    []
  );
  const [brands, setBrands] = useState<Brand[]>([]);
  const [selectedBrands, setSelectedBrands] = useState<Brand[]>([]);
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const { isMobile } = useBreakpoint();
  const [items, setItems] = useState<Product[]>([]);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);

  const { showAlert } = useAlert();

  const fetchItems = async (
    pageNum: number = 0,
    append: boolean = false
  ): Promise<void> => {
    setLoading(true);
    AllProductService.getProducts(filter, pageNum, 30)
      .then((data) => {
        const content = data?.content || [];

        if (append) {
          setItems((prev) => [...prev, ...content]);
        } else {
          setItems(content);
        }

        setHasMore(!data.last);
        setPage(pageNum);
        setLoading(false);
      })
      .catch((error) => {
        if (!append) setItems([]);
        setLoading(false);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching products:", error);
      });
  };

  const fetchCategories = useCallback(async () => {
    CategoryService.getCategories()
      .then((data) => setCategories(data))
      .catch((error) => {
        setCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching categories:", error);
      });
  }, []);

  const fetchBrands = async (): Promise<void> => {
    BrandService.getBrands()
      .then((data) => {
        const sortedBrands = data.sort((a, b) => a.name.localeCompare(b.name));
        setBrands(sortedBrands);
      })
      .catch(() => setBrands([]));
  };

  const handleResetFiltersAndData = useCallback(() => {
    setFilter({
      categoryIds: null,
      brandIds: null,
      keyword: "",
      includeZero: true,
      isDeleted: false,
    });
    setResetTriggered((prev) => !prev);
    setSelectedBrands([]);
    setSelectedCategories([]);
  }, []);

  const handleCategoryChange = useCallback(
    (categories: ProductCategory[] | null) => {
      if (categories && categories.length > 0) {
        setSelectedCategories(categories);
        setFilter((prev) => ({
          ...prev,
          categoryIds: categories.map((c) => c.id),
        }));
      } else {
        setSelectedCategories([]);
        setFilter((prev) => ({
          ...prev,
          categoryIds: null,
        }));
      }
    },
    []
  );

  const handleBrandChange = useCallback((selected: Brand | Brand[] | null) => {
    const brandsArray = !selected
      ? []
      : Array.isArray(selected)
      ? selected
      : [selected];
    if (brandsArray.length > 0) {
      setSelectedBrands(brandsArray);
      setFilter((prev) => ({
        ...prev,
        brandIds: brandsArray.map((b) => b.id),
      }));
    } else {
      setSelectedBrands([]);
      setFilter((prev) => ({
        ...prev,
        brandIds: null,
      }));
    }
  }, []);

  const handleIncludeZeroChange = useCallback((includeZero: boolean) => {
    setFilter((prev) => ({
      ...prev,
      includeZero,
    }));
  }, []);

  const handlePreviewPDF = useCallback(async () => {
    const dateStr = new Date().toLocaleDateString("pl-PL");
    const ok = await openPdf(
      () => AllProductService.generateInventoryReport(filter),
      `Stan magazynowy-${dateStr}.pdf`
    );
    if (!ok) showAlert("Błąd generowania PDF", AlertType.ERROR);
  }, [filter, showAlert]);

  useEffect(() => {
    fetchCategories();
    fetchBrands();
  }, []);

  useEffect(() => {
    fetchItems(0, false);
    setPage(0);
    setHasMore(true);
  }, [filter]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;

      if (scrolledToBottom && hasMore && !loading) {
        fetchItems(page + 1, true);
      }
    },
    [hasMore, loading, page, filter]
  );

  const columns = useMemo<Column<Product>[]>(
    () => [
      {
        key: "name",
        header: "Produkt",
        grow: true,
        render: (product) => {
          const dot = (
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
          );

          if (isMobile) {
            return (
              <>
                {dot}
                <span className="pr-name-stack flex-column mw-0">
                  <span className="dl-name flex align-items-center g-05">
                    <MedicalMark vatRate={product.vatRate} />
                    {product.name}
                  </span>
                  <span className="pr-name-meta dl-dim">
                    {product.brand.name}
                  </span>
                </span>
              </>
            );
          }

          return (
            <span className="flex align-items-center g-05 dl-name">
              {dot}
              <MedicalMark vatRate={product.vatRate} />
              {product.name}
            </span>
          );
        },
      },
      ...(isMobile
        ? []
        : [
            {
              key: "brand",
              header: "Marka",
              width: "9rem",
              align: "center" as const,
              render: (product: Product) => (
                <span className="dl-dim">{product.brand.name}</span>
              ),
            },
          ]),
      {
        key: "supply",
        header: "Stan",
        width: isMobile ? "2rem" : "5rem",
        align: "center",
        render: (product) => <span className="tnum">{product.supply}</span>,
      },
    ],
    [isMobile]
  );

  return (
    <Modal
      title="Utwórz Plik PDF"
      onClose={onClose}
      size="xl"
      height="tall"
      bodyClassName="nested"
      onConfirm={handlePreviewPDF}
      confirmText="Utwórz PDF"
      className={className}
    >
      <section className="modal-filters flex width-max align-items-center space-between g-1">
        {isMobile ? (
          <div className="flex align-items-center g-05">
            <FilterDropdown
              label="Filtry"
              className="pr-filters"
              count={
                [
                  selectedCategories.length > 0,
                  selectedBrands.length > 0,
                  !filter.includeZero,
                ].filter(Boolean).length
              }
            >
            <CategorySegments
              categories={categories}
              selectedCategories={selectedCategories}
              onSelect={(selected) =>
                handleCategoryChange(selected as ProductCategory[] | null)
              }
              resetTriggered={resetTriggered}
            />
            <div className="fd-row flex align-items-center g-05 space-between">
              <div className="cat-seg-group flex align-items-center width-fit-content g-025">
                <span className="cat-seg-label">Zerowy stan:</span>
                <button
                  type="button"
                  className={`cat-seg cl-yes align-items-center pointer ${
                    filter.includeZero ? "active" : ""
                  }`}
                  onClick={() => handleIncludeZeroChange(true)}
                >
                  TAK
                </button>
                <button
                  type="button"
                  className={`cat-seg cl-no align-items-center pointer ${
                    !filter.includeZero ? "active" : ""
                  }`}
                  onClick={() => handleIncludeZeroChange(false)}
                >
                  NIE
                </button>
              </div>
              <DropdownSelect<Brand>
                items={brands}
                value={selectedBrands}
                onChange={handleBrandChange}
                placeholder="Wybierz markę"
                multiple={true}
                allowNew={false}
                className="brand-dropdown"
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
        ) : (
          <>
          <CategorySegments
            categories={categories}
            selectedCategories={selectedCategories}
            onSelect={(selected) =>
              handleCategoryChange(selected as ProductCategory[] | null)
            }
            resetTriggered={resetTriggered}
          />
          <div className="cat-seg-group flex align-items-center width-fit-content g-025">
            <span className="cat-seg-label">Zerowy stan:</span>
            <button
              type="button"
              className={`cat-seg cl-yes align-items-center pointer ${
                filter.includeZero ? "active" : ""
              }`}
              onClick={() => handleIncludeZeroChange(true)}
            >
              TAK
            </button>
            <button
              type="button"
              className={`cat-seg cl-no align-items-center pointer ${
                !filter.includeZero ? "active" : ""
              }`}
              onClick={() => handleIncludeZeroChange(false)}
            >
              NIE
            </button>
          </div>
          <DropdownSelect<Brand>
            items={brands}
            value={selectedBrands}
            onChange={handleBrandChange}
            placeholder="Wybierz markę"
            multiple={true}
            allowNew={false}
            className="brand-dropdown"
          />
          <ActionButton
            disableText={true}
            iconTitle={"Resetuj filtry"}
            onClick={handleResetFiltersAndData}
            className={"reset"}
          >
            <RotateCw className="dl-icon icon-control" />
          </ActionButton>
          </>
        )}
      </section>

      <DataList<Product>
        columns={columns}
        items={items}
        getRowKey={(product) => product.id}
        onScroll={handleScroll}
        isLoading={loading}
        hideHeader={isMobile}
        emptyText="Brak produktów"
        className="pr-list"
      />
    </Modal>
  );
}

export default ProductReportModal;
