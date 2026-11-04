import React, { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ChevronLeft, ChevronRight, ArrowLeft, PackageX } from "lucide-react";
import NavigationBar from "../NavigationBar";
import ActionButton from "../ActionButton";
import DropdownSelect from "../DropdownSelect";
import ProductIdentityCard from "./ProductIdentityCard";
import SupplyChart from "./SupplyChart";
import SupplyHistoryList from "./SupplyHistoryList";
import ProductModal from "../Popups/ProductModal";
import UsageRecordModal from "../Popups/UsageRecordModal";
import UsageRecordPreviewModal from "../Popups/UsageRecordPreviewModal";
import KsefInvoiceModal from "../Popups/KsefInvoiceModal";
import ExpenseModal from "../Popups/ExpenseModal";
import VisitPreviewModal from "../Popups/VisitPreviewModal";
import InventoryReportPreviewModal from "../Popups/InventoryReportPreviewModal";
import AllProductService from "../../services/AllProductService";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { Product } from "../../models/product";
import {
  ProductSupplySummary,
  SupplyChart as SupplyChartData,
  SupplyHistoryEntry,
  SupplySourceGroup,
  SupplySourceGroupId,
  getSupplySourceGroup,
  supplySourceGroups,
} from "../../models/supply-history";

type PreviewTarget =
  | { kind: "KSEF"; id: number }
  | { kind: "EXPENSE"; id: number }
  | { kind: "INVENTORY"; id: number }
  | { kind: "SALE"; id: number }
  | { kind: "USAGE"; id: number };

export function ProductDashboard() {
  const { id } = useParams();
  const navigate = useNavigate();
  const productId = Number(id);
  const isValidId = Number.isInteger(productId) && productId > 0;

  const [notFound, setNotFound] = useState<boolean>(false);
  const [product, setProduct] = useState<Product | null>(null);
  const [summary, setSummary] = useState<ProductSupplySummary | null>(null);
  const [entries, setEntries] = useState<SupplyHistoryEntry[]>([]);
  const [chart, setChart] = useState<SupplyChartData | null>(null);
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [activeGroups, setActiveGroups] = useState<SupplySourceGroupId[]>([]);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [historyLoading, setHistoryLoading] = useState<boolean>(true);
  const [chartLoading, setChartLoading] = useState<boolean>(true);
  const [isEditOpen, setIsEditOpen] = useState<boolean>(false);
  const [isUsageOpen, setIsUsageOpen] = useState<boolean>(false);
  const [preview, setPreview] = useState<PreviewTarget | null>(null);
  const { isNarrow } = useBreakpoint();

  const fetchProduct = useCallback(() => {
    AllProductService.getProductById(productId)
      .then((data) => {
        setProduct(data);
        setNotFound(false);
      })
      .catch((error) => {
        setProduct(null);
        setNotFound(true);
        console.error("Error fetching Product: ", error);
      });
  }, [productId]);

  const fetchSummary = useCallback(() => {
    AllProductService.getSupplySummary(productId)
      .then((data) => setSummary(data))
      .catch((error) => {
        setSummary(null);
        console.error("Error fetching supply summary: ", error);
      });
  }, [productId]);

  const fetchChart = useCallback(
    (selectedYear: number) => {
      setChartLoading(true);
      AllProductService.getSupplyChart(productId, selectedYear)
        .then((data) => setChart(data))
        .catch((error) => {
          setChart(null);
          console.error("Error fetching supply chart: ", error);
        })
        .finally(() => setChartLoading(false));
    },
    [productId]
  );

  const fetchHistory = useCallback(
    (pageNum: number = 0, append: boolean = false) => {
      setHistoryLoading(true);
      const sources =
        activeGroups.length > 0
          ? supplySourceGroups
              .filter((group) => activeGroups.includes(group.id))
              .flatMap((group) => group.sources)
          : null;
      AllProductService.getSupplyHistory(productId, pageNum, 30, sources)
        .then((data) => {
          const isEmptyResponse = Array.isArray(data) || !data?.content;
          const content = isEmptyResponse ? [] : data.content;
          setEntries((prev) => (append ? [...prev, ...content] : content));
          setHasMore(isEmptyResponse ? false : !data.last);
          setPage(pageNum);
        })
        .catch((error) => {
          if (!append) setEntries([]);
          setHasMore(false);
          console.error("Error fetching supply history: ", error);
        })
        .finally(() => setHistoryLoading(false));
    },
    [productId, activeGroups]
  );

  const toggleGroup = useCallback((groupId: SupplySourceGroupId) => {
    setActiveGroups((prev) =>
      prev.includes(groupId)
        ? prev.filter((id) => id !== groupId)
        : [...prev, groupId]
    );
  }, []);

  useEffect(() => {
    if (!isValidId) {
      setNotFound(true);
      return;
    }
    fetchProduct();
    fetchSummary();
  }, [isValidId, fetchProduct, fetchSummary]);

  useEffect(() => {
    if (!isValidId) return;
    fetchHistory(0, false);
  }, [isValidId, fetchHistory]);

  useEffect(() => {
    if (!isValidId) return;
    fetchChart(year);
  }, [isValidId, year, fetchChart]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !historyLoading) {
        fetchHistory(page + 1, true);
      }
    },
    [hasMore, historyLoading, page, fetchHistory]
  );

  const handleSaved = useCallback(() => {
    fetchProduct();
    fetchSummary();
    fetchHistory(0, false);
    fetchChart(year);
  }, [fetchProduct, fetchSummary, fetchHistory, fetchChart, year]);

  const handlePreview = useCallback(
    (entry: SupplyHistoryEntry) => {
      const group = getSupplySourceGroup(entry.source);
      if (entry.sourceId == null || !entry.sourceExists) return;
      if (group.id === "FORM") return;
      setPreview({ kind: group.id, id: entry.sourceId } as PreviewTarget);
    },
    []
  );

  const canGoBack = chart ? year > chart.firstYear : false;
  const canGoForward = chart ? year < chart.lastYear : false;

  const handleGoBack = useCallback(() => {
    if (window.history.length > 1) {
      navigate(-1);
      return;
    }
    navigate("/");
  }, [navigate]);

  if (notFound) {
    return (
      <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
        <div className="dashboard-panel width-max height-max flex-column align-items-center">
          <NavigationBar showSearchbar={false} />
          <section className="pd-missing flex-column align-items-center justify-center g-1 f-1">
            <PackageX className="pd-missing-icon pc-y" />
            <span className="pd-missing-title">Nie znaleziono produktu</span>
            <span className="dl-dim">
              Produkt z ID #{id} nie istnieje lub został usunięty.
            </span>
            <ActionButton text="Powrót" iconTitle="Wróć" onClick={handleGoBack}>
              <ArrowLeft className="dl-icon-1 pc-y" />
            </ActionButton>
          </section>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <div className="pd-app width-93 flex-column g-1 mt-1 mb-2">
          {product && (
            <ProductIdentityCard
              product={product}
              summary={summary}
              onEdit={() => setIsEditOpen(true)}
              onUsage={() => setIsUsageOpen(true)}
            />
          )}

          <section className="pd-card flex-column f-1 min-height-0">
            <header className="pd-panel-head flex align-items-center space-between g-1">
              <span className="pd-panel-title">Historia stanu magazynowego</span>
              <div className="pd-year-nav flex align-items-center">
                <ActionButton
                  iconTitle="Poprzedni rok"
                  disableText={true}
                  disableBorder={true}
                  disabled={!canGoBack}
                  onClick={() => setYear((prev) => prev - 1)}
                >
                  <ChevronLeft className="dl-icon-1" />
                </ActionButton>
                <span className="pd-year tnum">{year}</span>
                <ActionButton
                  iconTitle="Następny rok"
                  disableText={true}
                  disableBorder={true}
                  disabled={!canGoForward}
                  onClick={() => setYear((prev) => prev + 1)}
                >
                  <ChevronRight className="dl-icon-1" />
                </ActionButton>
              </div>
            </header>

            <SupplyChart data={chart} year={year} loading={chartLoading} />

            <div className="pd-filter flex align-items-center justify-center">
              {isNarrow ? (
                <DropdownSelect<SupplySourceGroup>
                  items={supplySourceGroups}
                  value={supplySourceGroups.filter((group) =>
                    activeGroups.includes(group.id)
                  )}
                  onChange={(selected) =>
                    setActiveGroups(
                      ((selected as SupplySourceGroup[] | null) ?? []).map(
                        (group) => group.id
                      )
                    )
                  }
                  placeholder="Wszystko"
                  multiple={true}
                  searchable={false}
                  allowNew={false}
                  className="pd-source-dropdown"
                />
              ) : (
                <div className="cat-seg-group flex flex-wrap align-items-center width-fit-content g-025">
                  <button
                    type="button"
                    className={`cat-seg align-items-center pointer ${
                      activeGroups.length === 0 ? "active" : ""
                    }`}
                    onClick={() => setActiveGroups([])}
                  >
                    Wszystko
                  </button>
                  {supplySourceGroups.map((group) => (
                    <button
                      type="button"
                      key={group.id}
                      className={`cat-seg align-items-center pointer ${
                        activeGroups.includes(group.id) ? "active" : ""
                      }`}
                      onClick={() => toggleGroup(group.id)}
                    >
                      <span
                        className="dl-dot"
                        style={{ backgroundColor: `rgb(${group.color})` }}
                      />
                      {group.name}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="pd-history f-1 min-height-0">
              <SupplyHistoryList
                entries={entries}
                onPreview={handlePreview}
                onScroll={handleScroll}
                isLoading={historyLoading}
              />
            </div>
          </section>
        </div>

        {isEditOpen && (
          <ProductModal
            productId={productId}
            onClose={() => setIsEditOpen(false)}
            onReset={handleSaved}
          />
        )}
        {isUsageOpen && (
          <UsageRecordModal
            initialProduct={product}
            onCreated={handleSaved}
            onClose={() => setIsUsageOpen(false)}
          />
        )}
        {preview?.kind === "KSEF" && (
          <KsefInvoiceModal
            invoiceId={preview.id}
            highlightProductId={productId}
            originModule="Product"
            originProductId={productId}
            onClose={() => setPreview(null)}
          />
        )}
        {preview?.kind === "EXPENSE" && (
          <ExpenseModal
            expenseId={preview.id}
            highlightProductId={productId}
            originModule="Product"
            originProductId={productId}
            onClose={() => setPreview(null)}
            onReset={handleSaved}
          />
        )}
        {preview?.kind === "INVENTORY" && (
          <InventoryReportPreviewModal
            inventoryReportId={preview.id}
            onClose={() => setPreview(null)}
          />
        )}
        {preview?.kind === "SALE" && (
          <VisitPreviewModal
            visitId={preview.id}
            highlightProductId={productId}
            onClose={() => setPreview(null)}
          />
        )}
        {preview?.kind === "USAGE" && (
          <UsageRecordPreviewModal
            usageRecordId={preview.id}
            onClose={() => setPreview(null)}
          />
        )}
      </div>
    </div>
  );
}

export default ProductDashboard;
