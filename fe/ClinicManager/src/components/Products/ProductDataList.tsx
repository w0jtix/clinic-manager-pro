import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Trash2, SquarePen, LayersPlus } from "lucide-react";
import RowActionMenu from "../RowActionMenu";
import DataList, { Column } from "../DataList";
import { MedicalMark } from "../ProductVatTag";
import {
  StockBadge,
  getStockStatus,
  STOCK_STATUS_CLASS,
  STOCK_STATUS_HINTS,
} from "../StockStatusTag";
import AllProductService from "../../services/AllProductService";
import { Product, ProductFilterDTO, Unit } from "../../models/product";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface ProductDataListProps {
  filter: ProductFilterDTO;
  setIsAddNewProductsPopupOpen?: (isOpen: boolean) => void;
  setEditProductId: (productId: string | number | null) => void;
  setRemoveProductId: (productId: string | number | null) => void;
  setUsageProduct: (product: Product | null) => void;
  isCompany?: boolean;
  className?: string;
}

const CategoryDot = ({ color }: { color?: string }) =>
  color ? (
    <span
      className="dl-dot"
      style={{ backgroundColor: `rgb(${color})`, boxShadow: `0 0 6px rgba(${color}, 0.5)` }}
    />
  ) : (
    <span className="dl-dot" style={{ backgroundColor: "rgba(255,255,255,0.2)" }} />
  );

export function ProductDataList({
  filter,
  setEditProductId,
  setRemoveProductId,
  setUsageProduct,
  isCompany = false,
  className = "",
}: ProductDataListProps) {
  const [items, setItems] = useState<Product[]>([]);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const buildFilterDTO = (filter: ProductFilterDTO) => {
    const filterDTO: ProductFilterDTO = {};
    if (filter.categoryIds && filter.categoryIds.length > 0) filterDTO.categoryIds = filter.categoryIds;
    if (filter.brandIds && filter.brandIds.length > 0) filterDTO.brandIds = filter.brandIds;
    if (filter.keyword && filter.keyword.trim() !== "") filterDTO.keyword = filter.keyword;
    if (filter.includeZero !== null) filterDTO.includeZero = filter.includeZero;
    if (filter.isDeleted !== null) filterDTO.isDeleted = filter.isDeleted;
    if (filter.stockStatus) filterDTO.stockStatus = filter.stockStatus;
    return filterDTO;
  };

  const fetchItems = useCallback(
    async (pageNum: number = 0, append: boolean = false): Promise<void> => {
      setLoading(true);
      AllProductService.getProducts(buildFilterDTO(filter), pageNum, 30)
        .then((data) => {
          const content = data?.content || [];
          setItems((prev) => (append ? [...prev, ...content] : content));
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
    },
    [filter, showAlert]
  );

  useEffect(() => {
    fetchItems(0, false);
    setPage(0);
    setHasMore(true);
  }, [filter]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom = target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !loading) {
        fetchItems(page + 1, true);
      }
    },
    [hasMore, loading, page, fetchItems]
  );

  const indexColumn: Column<Product> = {
    key: "#",
    width: isMobile ? "1.25rem" : "2.5rem",
    align: isMobile ? "start" : "center",
    render: (_item, index) => <span className="dl-dim">{index + 1}</span>,
  };

  const nameColumn: Column<Product> = {
    key: "Nazwa",
    grow: true,
    align: "start",
    render: (item) => {
      const nameNode =
        isCompany && !item.isDeleted ? (
          <Link
            to={`/warehouse/product/${item.id}`}
            className="pd-product-link"
            title="Otwórz stronę produktu"
            onClick={(e) => e.stopPropagation()}
          >
            {item.name}
          </Link>
        ) : (
          item.name
        );

      const volumeNode = (
        <span className="dl-dim italic">
          {item.volume ?? ""}
          {item.unit === Unit.ML ? "ml" : item.unit === Unit.G ? "g" : ""}
        </span>
      );

      const pricePerUnitNode =
        item.sellingPrice && item.volume ? (
          <span className="dl-dim italic">
            {((item.sellingPrice * 100) / item.volume).toFixed(2)} zł/
            {item.unit === Unit.ML ? "100ml" : item.unit === Unit.G ? "100g" : ""}
          </span>
        ) : null;

      if (isMobile) {
        return (
          <>
            <CategoryDot color={item.category?.color} />
            <span className="pd-name-stack flex-column">
              <span className="dl-name flex align-items-center g-05">
                <MedicalMark vatRate={item.vatRate} />
                {nameNode}
              </span>
              <span className="pd-name-meta flex align-items-center g-05">
                <span className="dl-dim">{item.brand.name}</span>
                {volumeNode}
                {pricePerUnitNode}
              </span>
            </span>
          </>
        );
      }

      return (
        <>
          <CategoryDot color={item.category?.color} />
          <span className="dl-name flex align-items-center g-05">
            <MedicalMark vatRate={item.vatRate} />
            {nameNode}
            <span className="flex g-05 align-self-end">
              {volumeNode}
              {pricePerUnitNode}
            </span>
          </span>
        </>
      );
    },
  };

  const brandColumn: Column<Product> = {
    key: "Marka",
    width: "9rem",
    align: "center",
    render: (item) => <span className="dl-dim">{item.brand.name}</span>,
  };

  const priceColumn: Column<Product> = {
    key: "Cena",
    width: isMobile ? "2.75rem" : "6rem",
    align: "center",
    render: (item) =>
      !item.category.system || !item.sellingPrice ? null : (
        <span>{item.sellingPrice} zł</span>
      ),
  };

  const supplyColumn: Column<Product> = {
    key: "Stan Magazynowy",
    width: isMobile ? "2rem" : "9rem",
    align: "center",
    render: (item) => {
      const status = getStockStatus(item.supply, item.lowStockThreshold);

      if (isMobile) {
        return (
          <span className="pd-supply-cell flex align-items-center justify-center">
            <span
              className={`pd-supply tnum flex justify-center ${STOCK_STATUS_CLASS[status]}`}
              title={STOCK_STATUS_HINTS[status]}
            >
              {item.supply}
            </span>
          </span>
        );
      }

      return (
        <span className="pd-supply-cell flex align-items-center justify-center g-025">
          <StockBadge
            supply={item.supply}
            lowStockThreshold={item.lowStockThreshold}
          />
          <span className="pd-supply tnum flex justify-center">{item.supply}</span>
        </span>
      );
    },
  };

  const optionsColumn: Column<Product> = {
    key: "Opcje",
    width: isMobile ? "1.9rem" : "6rem",
    align: "center",
    render: (item) => (
      <div className="flex align-items-center justify-center">
        <RowActionMenu
          items={[
            {
              label: "Utwórz zużycie",
              icon: <LayersPlus className="dl-icon pc-y" />,
              onClick: () => setUsageProduct(item),
              disabled: !!item.isDeleted,
            },
            {
              label: "Edytuj",
              icon: <SquarePen className="dl-icon c-blue" />,
              onClick: () => setEditProductId(item.id),
              disabled: !!item.isDeleted,
            },
            {
              label: "Usuń",
              icon: <Trash2 className="dl-icon c-red" />,
              onClick: () => setRemoveProductId(item.id),
              disabled: !!item.isDeleted,
              danger: true,
            },
          ]}
        />
      </div>
    ),
  };

  const columns: Column<Product>[] = isMobile
    ? [indexColumn, nameColumn, priceColumn, supplyColumn, optionsColumn]
    : [indexColumn, nameColumn, priceColumn, brandColumn, supplyColumn, optionsColumn];

  return (
    <div className={`pd-list min-height-0 width-93 f-1 flex-column align-self-center mb-2 ${className}`}>
      <DataList<Product>
        columns={columns}
        items={items}
        getRowKey={(item) => item.id}
        isRowDisabled={(item) => !!item.isDeleted}
        onScroll={handleScroll}
        isLoading={loading}
        hideHeader={isMobile}
        emptyText="Brak produktów"
      />
    </div>
  );
}

export default ProductDataList;
