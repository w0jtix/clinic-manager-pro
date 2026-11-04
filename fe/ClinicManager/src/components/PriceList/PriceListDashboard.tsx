import { useState, useCallback, useEffect } from "react";
import NavigationBar from "../NavigationBar";
import SearchBar from "../SearchBar";
import DropdownSelect from "../DropdownSelect";
import DataList, { Column } from "../DataList";
import ServicePriceList from "./ServicePriceList";
import { Product, ProductFilterDTO, Unit } from "../../models/product";
import AllProductService from "../../services/AllProductService";
import { BaseService, ServiceFilterDTO } from "../../models/service";
import BaseServiceService from "../../services/BaseServiceService";
import { BaseServiceCategory } from "../../models/categories";
import BaseServiceCategoryService from "../../services/BaseServiceCategoryService";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";

const SALE_CATEGORY_ID = 1;

type PriceListTab = "services" | "products";

export function PriceListDashboard() {
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();
  const [activeTab, setActiveTab] = useState<PriceListTab>("services");

  const [services, setServices] = useState<BaseService[]>([]);
  const [servicesLoading, setServicesLoading] = useState<boolean>(true);
  const [serviceFilter, setServiceFilter] = useState<ServiceFilterDTO>({
    categoryIds: null,
    keyword: "",
  });
  const [categories, setCategories] = useState<BaseServiceCategory[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<
    BaseServiceCategory[]
  >([]);

  const [products, setProducts] = useState<Product[]>([]);
  const [productFilter, setProductFilter] = useState<ProductFilterDTO>({
    categoryIds: [SALE_CATEGORY_ID],
    keyword: "",
  });
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [productsLoading, setProductsLoading] = useState<boolean>(true);

  const fetchServices = async (): Promise<void> => {
    setServicesLoading(true);
    BaseServiceService.getServices(serviceFilter)
      .then((data) => setServices(data))
      .catch((error) => {
        setServices([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Services: ", error);
      })
      .finally(() => setServicesLoading(false));
  };

  const fetchCategories = async (): Promise<void> => {
    BaseServiceCategoryService.getCategories()
      .then((data) => setCategories(data))
      .catch((error) => {
        setCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching categories:", error);
      });
  };

  const fetchProducts = async (
    pageNum: number = 0,
    append: boolean = false
  ): Promise<void> => {
    setProductsLoading(true);
    AllProductService.getProducts(productFilter, pageNum, 30)
      .then((data) => {
        const content = data?.content || [];
        setProducts((prev) => (append ? [...prev, ...content] : content));
        setHasMore(!data.last);
        setPage(pageNum);
      })
      .catch((error) => {
        if (!append) setProducts([]);
        setHasMore(false);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching products:", error);
      })
      .finally(() => setProductsLoading(false));
  };

  const handleServiceKeywordChange = useCallback((newKeyword: string) => {
    setServiceFilter((prev) => ({ ...prev, keyword: newKeyword }));
  }, []);

  const handleProductKeywordChange = useCallback((newKeyword: string) => {
    setProductFilter((prev) => ({ ...prev, keyword: newKeyword }));
  }, []);

  const handleFilterByCategory = useCallback(
    (cats: BaseServiceCategory | BaseServiceCategory[] | null) => {
      const arr = !cats ? [] : Array.isArray(cats) ? cats : [cats];
      setSelectedCategories(arr);
      setServiceFilter((prev) => ({
        ...prev,
        categoryIds: arr.length > 0 ? arr.map((c) => c.id) : null,
      }));
    },
    []
  );

  useEffect(() => {
    fetchServices();
  }, [serviceFilter]);

  useEffect(() => {
    fetchCategories();
  }, []);

  useEffect(() => {
    fetchProducts(0, false);
    setPage(0);
    setHasMore(true);
  }, [productFilter]);

  const handleProductScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !productsLoading) {
        fetchProducts(page + 1, true);
      }
    },
    [hasMore, productsLoading, page, productFilter]
  );

  const productColumns: Column<Product>[] = [
    {
      key: "name",
      header: "Nazwa",
      grow: true,
      render: (item) => {
        const dot = (
          <span
            className="dl-dot"
            style={{
              backgroundColor: item.category?.color
                ? `rgb(${item.category.color})`
                : "rgba(255,255,255,0.2)",
              boxShadow: item.category?.color
                ? `0 0 6px rgba(${item.category.color}, 0.5)`
                : undefined,
            }}
          />
        );
        const volume = item.volume ? (
          <span className="dl-dim italic">
            {item.volume}
            {item.unit === Unit.ML ? "ml" : item.unit === Unit.G ? "g" : ""}
          </span>
        ) : null;

        if (isMobile) {
          return (
            <>
              {dot}
              <span className="pl-name-stack flex-column mw-0">
                <span className="dl-name">{item.name}</span>
                {volume && <span className="pl-name-meta">{volume}</span>}
              </span>
            </>
          );
        }

        return (
          <span className="dl-name flex align-items-center g-05">
            {dot}
            {item.name}
            {volume && <span className="align-self-end">{volume}</span>}
          </span>
        );
      },
    },
    {
      key: "supply",
      header: "Magazyn",
      width: isMobile ? "3.5rem" : "7rem",
      align: "center",
      render: (item) => <span className="dl-dim">{item.supply} szt.</span>,
    },
    {
      key: "price",
      header: "Cena",
      width: isMobile ? "2.75rem" : "6rem",
      align: "end",
      render: (item) =>
        item.sellingPrice ? (
          <span className="sv-price">{item.sellingPrice} zł</span>
        ) : (
          <span className="dl-dim">-</span>
        ),
    },
  ];

  const servicesColumn = (
    <div className="pl-col g-05 flex-column min-height-0 f-1">
      {!isMobile && <span className="pl-col-title">Usługi</span>}
      <div className="pl-filters flex g-05 width-max">
        <SearchBar
          onKeywordChange={handleServiceKeywordChange}
          resetTriggered={false}
          placeholder="Szukaj usługi..."
          className="pricelist f-1"
        />
        <DropdownSelect
          items={categories}
          value={selectedCategories}
          onChange={handleFilterByCategory}
          placeholder="Kategorie"
          searchable={false}
          allowNew={false}
          allowColors={true}
          className="categories"
          multiple={true}
        />
      </div>
      <ServicePriceList
        items={services}
        isLoading={servicesLoading}
        className="pl-list"
      />
    </div>
  );

  const productsColumn = (
    <div className="pl-col g-05 flex-column min-height-0 f-1">
      {!isMobile && <span className="pl-col-title">Produkty</span>}
      <div className="pl-filters flex g-05 width-max">
        <SearchBar
          onKeywordChange={handleProductKeywordChange}
          resetTriggered={false}
          placeholder="Szukaj produktu..."
          className="pricelist f-1"
        />
      </div>
      <DataList<Product>
        columns={productColumns}
        items={products}
        getRowKey={(item) => item.id}
        onScroll={handleProductScroll}
        isLoading={productsLoading}
        hideHeader={isMobile}
        emptyText="Brak produktów"
        className="pl-list"
      />
    </div>
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />
        {isMobile && (
          <div className="cs-seg pl-seg flex align-items-center align-self-center mt-2">
            <button
              type="button"
              className={activeTab === "services" ? "on" : ""}
              onClick={() => setActiveTab("services")}
            >
              Usługi
            </button>
            <button
              type="button"
              className={activeTab === "products" ? "on" : ""}
              onClick={() => setActiveTab("products")}
            >
              Produkty
            </button>
          </div>
        )}
        <div className="pl-lists align-self-center f-1 flex g-2 width-93 min-height-0 mt-2 mb-1">
          {(!isMobile || activeTab === "services") && servicesColumn}
          {(!isMobile || activeTab === "products") && productsColumn}
        </div>
      </div>
    </div>
  );
}

export default PriceListDashboard;
