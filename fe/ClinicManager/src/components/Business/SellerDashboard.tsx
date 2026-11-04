import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, ChevronLeft, ChevronRight, Building2 } from "lucide-react";
import NavigationBar from "../NavigationBar";
import ActionButton from "../ActionButton";
import SellerIdentityCard from "./SellerIdentityCard";
import SellerSpendChart from "./SellerSpendChart";
import SellerInvoiceDataList from "./SellerInvoiceDataList";
import SellerItemMappingList from "./SellerItemMappingList";
import KsefInvoiceModal from "../Popups/KsefInvoiceModal";
import KsefRememberCategoryModal from "../Popups/KsefRememberCategoryModal";
import RemoveModal from "../Popups/RemoveModal";
import ExpenseModal from "../Popups/ExpenseModal";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import KsefInvoiceService from "../../services/KsefInvoiceService";
import KsefSellerService from "../../services/KsefSellerService";
import {
  CompanyExpense, ExpenseCategory, ExpenseCategoryCode, getItemCategories
} from "../../models/expense";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import { KsefInvoice, KsefSeller } from "../../models/ksef_invoice";
import {
  AWAITING_FILTER,
  KsefSellerItemMapping,
  KsefSellerSummary,
  KsefSpendChart,
  SellerCategoryFilterId,
  SellerPageOrigin,
  UNCATEGORIZED_FILTER,
  getEffectiveDefaultCategory,
} from "../../models/ksef_seller";

interface FilterOption {
  id: SellerCategoryFilterId;
  name: string;
  color: string;
}

export function SellerDashboard() {
  const { items: expenseCategoryItems } = useExpenseCategories();
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { showAlert } = useAlert();
  const sellerId = Number(id);
  const origin = (location.state as SellerPageOrigin | null) ?? null;
  const isValidId = Number.isInteger(sellerId) && sellerId > 0;

  const [notFound, setNotFound] = useState<boolean>(false);
  const [seller, setSeller] = useState<KsefSeller | null>(null);
  const [summary, setSummary] = useState<KsefSellerSummary | null>(null);
  const [mappings, setMappings] = useState<KsefSellerItemMapping[]>([]);
  const [invoices, setInvoices] = useState<KsefInvoice[]>([]);
  const [chart, setChart] = useState<KsefSpendChart | null>(null);
  const [year, setYear] = useState<number>(new Date().getFullYear());
  const [activeFilters, setActiveFilters] = useState<SellerCategoryFilterId[]>([]);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [invoicesLoading, setInvoicesLoading] = useState<boolean>(true);
  const [chartLoading, setChartLoading] = useState<boolean>(true);
  const [mappingsLoading, setMappingsLoading] = useState<boolean>(true);
  const [previewInvoiceId, setPreviewInvoiceId] = useState<number | null>(null);
  const [mappingToRemove, setMappingToRemove] = useState<KsefSellerItemMapping | null>(
    null
  );
  const [expenses, setExpenses] = useState<CompanyExpense[]>([]);
  const [previewExpenseId, setPreviewExpenseId] = useState<number | null>(null);
  const [pendingCategory, setPendingCategory] = useState<{
    invoiceId: number;
    category: ExpenseCategoryCode;
  } | null>(null);

  const fetchSeller = useCallback(() => {
    KsefSellerService.getById(sellerId)
      .then((data) => {
        setSeller(data);
        setNotFound(false);
      })
      .catch((error) => {
        setSeller(null);
        setNotFound(true);
        console.error("Error fetching KSeF seller: ", error);
      });
  }, [sellerId]);

  const fetchSummary = useCallback(() => {
    KsefSellerService.getSummary(sellerId)
      .then((data) => setSummary(data))
      .catch((error) => {
        setSummary(null);
        console.error("Error fetching KSeF seller summary: ", error);
      });
  }, [sellerId]);

  const fetchMappings = useCallback(() => {
    setMappingsLoading(true);
    KsefSellerService.getItemMappings(sellerId)
      .then((data) => setMappings(Array.isArray(data) ? data : []))
      .catch((error) => {
        setMappings([]);
        console.error("Error fetching KSeF seller item mappings: ", error);
      })
      .finally(() => setMappingsLoading(false));
  }, [sellerId]);

  const fetchChart = useCallback(
    (selectedYear: number) => {
      setChartLoading(true);
      KsefSellerService.getSpendChart(sellerId, selectedYear)
        .then((data) => setChart(data))
        .catch((error) => {
          setChart(null);
          console.error("Error fetching KSeF seller spend chart: ", error);
        })
        .finally(() => setChartLoading(false));
    },
    [sellerId]
  );

  const fetchInvoices = useCallback(
    (pageNum: number = 0, append: boolean = false) => {
      setInvoicesLoading(true);
      const categories = activeFilters.filter(
        (filterId) =>
          filterId !== UNCATEGORIZED_FILTER && filterId !== AWAITING_FILTER
      ) as ExpenseCategoryCode[];

      KsefInvoiceService.getInvoices(
        {
          sellerId,
          categories: categories.length > 0 ? categories : null,
          uncategorized: activeFilters.includes(UNCATEGORIZED_FILTER) ? true : null,
          awaitingVerification: activeFilters.includes(AWAITING_FILTER) ? true : null,
        },
        pageNum,
        30
      )
        .then((data) => {
          const isEmptyResponse = Array.isArray(data) || !data?.content;
          const content = isEmptyResponse ? [] : data.content;
          setInvoices((prev) => (append ? [...prev, ...content] : content));
          setHasMore(isEmptyResponse ? false : !data.last);
          setPage(pageNum);
        })
        .catch((error) => {
          if (!append) setInvoices([]);
          setHasMore(false);
          console.error("Error fetching KSeF seller invoices: ", error);
        })
        .finally(() => setInvoicesLoading(false));
    },
    [sellerId, activeFilters]
  );

  const fetchExpenses = useCallback(() => {
    KsefSellerService.getExpenses(sellerId, 0, 500)
      .then((data) => setExpenses(data?.content ?? []))
      .catch((error) => {
        setExpenses([]);
        console.error("Error fetching seller expenses: ", error);
      });
  }, [sellerId]);

  useEffect(() => {
    if (!isValidId) {
      setNotFound(true);
      return;
    }
    fetchSeller();
    fetchSummary();
    fetchMappings();
  }, [isValidId, fetchSeller, fetchSummary, fetchMappings]);

  useEffect(() => {
    if (!isValidId) return;
    fetchInvoices(0, false);
  }, [isValidId, fetchInvoices]);

  useEffect(() => {
    if (!isValidId) return;
    fetchChart(year);
  }, [isValidId, year, fetchChart]);

  useEffect(() => {
    if (!isValidId) return;
    fetchExpenses();
  }, [isValidId, fetchExpenses]);

  const filterOptions = useMemo<FilterOption[]>(() => {
    if (!summary) return [];
    const categories = new Set<ExpenseCategoryCode>(
      summary.categoryFacets.map((facet) => facet.category)
    );
    expenses.forEach((expense) => {
      if (expense.category === ExpenseCategory.MIXED) {
        getItemCategories(expense).forEach((category) => categories.add(category));
      } else if (expense.category) {
        categories.add(expense.category);
      }
    });
    const options: FilterOption[] = expenseCategoryItems
      .filter((item) => categories.has(item.id))
      .map((item) => ({ id: item.id, name: item.name, color: item.color }));
    if (summary.uncategorizedCount > 0) {
      options.push({
        id: UNCATEGORIZED_FILTER,
        name: "Nieprzypisane",
        color: "255,255,255",
      });
    }
    if (summary.awaitingVerificationCount > 0) {
      options.push({ id: AWAITING_FILTER, name: "Weryfikacja", color: "252,181,12" });
    }
    return options;
  }, [summary, expenses, expenseCategoryItems]);

  const toggleFilter = useCallback((filterId: SellerCategoryFilterId) => {
    setActiveFilters((prev) =>
      prev.includes(filterId)
        ? prev.filter((entry) => entry !== filterId)
        : [...prev, filterId]
    );
  }, []);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !invoicesLoading) {
        fetchInvoices(page + 1, true);
      }
    },
    [hasMore, invoicesLoading, page, fetchInvoices]
  );

  const visibleExpenses = useMemo(() => {
    const categories = activeFilters.filter(
      (filterId) => filterId !== UNCATEGORIZED_FILTER && filterId !== AWAITING_FILTER
    );
    const oldestLoaded = hasMore ? invoices[invoices.length - 1]?.issueDate : undefined;
    return expenses.filter(
      (expense) =>
        (activeFilters.length === 0 ||
          (expense.category === ExpenseCategory.MIXED
            ? getItemCategories(expense).some((category) => categories.includes(category))
            : expense.category != null && categories.includes(expense.category))) &&
        (!oldestLoaded || expense.expenseDate >= oldestLoaded)
    );
  }, [expenses, activeFilters, hasMore, invoices]);

  const handleSelectProducts = useCallback(
    (invoice: KsefInvoice) => {
      if (invoice.awaitingProductVerification) {
        setPreviewInvoiceId(invoice.id);
        return;
      }
      KsefInvoiceService.toggleProductInvoice(invoice.id)
        .then((updated) => {
          setInvoices((prev) =>
            prev.map((entry) => (entry.id === updated.id ? updated : entry))
          );
          setPreviewInvoiceId(invoice.id);
          fetchSummary();
        })
        .catch(() => showAlert("Błąd zmiany statusu faktury.", AlertType.ERROR));
    },
    [fetchSummary, showAlert]
  );

  const applySetCategory = useCallback(
    (invoiceId: number, category: ExpenseCategoryCode, rememberForSeller: boolean) => {
      KsefInvoiceService.setCategory(invoiceId, category, rememberForSeller)
        .then((updated) => {
          setInvoices((prev) =>
            prev.map((entry) => (entry.id === updated.id ? updated : entry))
          );
          fetchSummary();
          if (rememberForSeller) fetchSeller();
          showAlert("Przypisano kategorię.", AlertType.SUCCESS);
        })
        .catch(() => showAlert("Błąd przypisania kategorii.", AlertType.ERROR));
    },
    [fetchSummary, fetchSeller, showAlert]
  );

  const handleSelectCategory = useCallback(
    (invoice: KsefInvoice, category: ExpenseCategoryCode) => {
      if (category === ExpenseCategory.PRODUCTS) {
        handleSelectProducts(invoice);
        return;
      }
      if (category === getEffectiveDefaultCategory(seller)) {
        applySetCategory(invoice.id, category, false);
        return;
      }
      setPendingCategory({ invoiceId: invoice.id, category });
    },
    [handleSelectProducts, applySetCategory, seller]
  );

  const doSetCategory = useCallback(
    (rememberForSeller: boolean) => {
      if (!pendingCategory) return;
      applySetCategory(
        pendingCategory.invoiceId,
        pendingCategory.category,
        rememberForSeller
      );
      setPendingCategory(null);
    },
    [pendingCategory, applySetCategory]
  );

  const handleTogglePaid = useCallback(
    (invoiceId: number) => {
      KsefInvoiceService.togglePaid(invoiceId)
        .then((updated) => {
          setInvoices((prev) =>
            prev.map((entry) => (entry.id === updated.id ? updated : entry))
          );
          fetchSummary();
          showAlert("Pomyślnie zmieniono status faktury.", AlertType.SUCCESS);
        })
        .catch(() => showAlert("Błąd zmiany statusu faktury.", AlertType.ERROR));
    },
    [fetchSummary, showAlert]
  );

  const handleSetDefaultCategory = useCallback(
    (category: ExpenseCategoryCode | null) => {
      KsefSellerService.setDefaultCategory(sellerId, category)
        .then(() => {
          fetchSeller();
          showAlert(
            category
              ? "Zmieniono domyślną kategorię dostawcy."
              : "Wyłączono domyślną kategorię dostawcy.",
            AlertType.SUCCESS
          );
        })
        .catch(() => showAlert("Błąd zmiany domyślnej kategorii.", AlertType.ERROR));
    },
    [sellerId, fetchSeller, showAlert]
  );

  const handleRemoveMapping = useCallback(() => {
    if (!mappingToRemove) return;
    KsefSellerService.deleteItemMapping(sellerId, mappingToRemove.id)
      .then(() => {
        setMappings((prev) => prev.filter((entry) => entry.id !== mappingToRemove.id));
        showAlert("Usunięto przypisanie.", AlertType.SUCCESS);
      })
      .catch(() => showAlert("Błąd usuwania przypisania.", AlertType.ERROR))
      .finally(() => setMappingToRemove(null));
  }, [mappingToRemove, sellerId, showAlert]);

  const handleInvoiceVerified = useCallback(
    (updated: KsefInvoice) => {
      setInvoices((prev) =>
        prev.map((entry) => (entry.id === updated.id ? updated : entry))
      );
      fetchSummary();
      fetchMappings();
    },
    [fetchSummary, fetchMappings]
  );

  const handleGoBack = useCallback(() => {
    if (window.history.length > 1) {
      navigate(-1);
      return;
    }
    navigate("/my-company");
  }, [navigate]);

  const showMappings =
    getEffectiveDefaultCategory(seller) === ExpenseCategory.PRODUCTS ||
    mappings.length > 0;

  const canGoBack = chart ? year > chart.firstYear : false;
  const canGoForward = chart ? year < chart.lastYear : false;

  if (notFound) {
    return (
      <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
        <div className="dashboard-panel width-max height-max flex-column align-items-center">
          <NavigationBar showSearchbar={false} />
          <section className="pd-missing flex-column align-items-center justify-center g-1 f-1">
            <Building2 className="pd-missing-icon pc-y" />
            <span className="pd-missing-title">Nie znaleziono dostawcy</span>
            <span className="dl-dim">
              Dostawca z ID #{id} nie istnieje lub został usunięty.
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
          {seller && (
            <SellerIdentityCard
              seller={seller}
              summary={summary}
              origin={origin}
              onSetDefaultCategory={handleSetDefaultCategory}
            />
          )}

          {showMappings && (
            <section className="pd-card flex-column">
              <header className="pd-panel-head flex align-items-center space-between g-1">
                <span className="pd-panel-title">Zapamiętane przypisania pozycji</span>
                <span className="sd-sub">
                  {mappings.length === 1 ? "1 reguła" : `${mappings.length} reguł`}
                </span>
              </header>
              <SellerItemMappingList
                mappings={mappings}
                isLoading={mappingsLoading}
                onRemove={setMappingToRemove}
              />
            </section>
          )}

          <section className="pd-card sd-invoices flex-column">
            <header className="pd-panel-head flex align-items-center space-between g-1">
              <span className="pd-panel-title">Faktury od dostawcy</span>
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

            <SellerSpendChart data={chart} year={year} loading={chartLoading} />

            {filterOptions.length > 0 && (
              <div className="pd-filter flex align-items-center justify-center">
                <div
                  className="cat-seg-group flex flex-wrap align-items-center width-fit-content g-025"
                  style={
                    {
                      "--seg-half":
                        filterOptions.length + 1 <= 2
                          ? filterOptions.length + 1
                          : Math.ceil((filterOptions.length + 1) / 2),
                    } as React.CSSProperties
                  }
                >
                  <button
                    type="button"
                    className={`cat-seg align-items-center pointer ${
                      activeFilters.length === 0 ? "active" : ""
                    }`}
                    onClick={() => setActiveFilters([])}
                  >
                    Wszystkie
                  </button>
                  {filterOptions.map((option) => (
                    <button
                      type="button"
                      key={option.id}
                      className={`cat-seg align-items-center pointer ${
                        activeFilters.includes(option.id) ? "active" : ""
                      }`}
                      onClick={() => toggleFilter(option.id)}
                    >
                      <span
                        className="dl-dot"
                        style={{ backgroundColor: `rgb(${option.color})` }}
                      />
                      {option.name}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <SellerInvoiceDataList
              invoices={invoices}
              onScroll={handleScroll}
              isLoading={invoicesLoading}
              onSelectCategory={handleSelectCategory}
              onTogglePaid={handleTogglePaid}
              onOpenPreview={setPreviewInvoiceId}
              manualExpenses={visibleExpenses}
              onOpenExpensePreview={setPreviewExpenseId}
            />
          </section>
        </div>

        {previewExpenseId !== null && (
          <ExpenseModal
            expenseId={previewExpenseId}
            sellerLink={false}
            onClose={() => setPreviewExpenseId(null)}
            onReset={fetchExpenses}
          />
        )}

        {previewInvoiceId !== null && (
          <KsefInvoiceModal
            invoiceId={previewInvoiceId}
            onClose={() => setPreviewInvoiceId(null)}
            onVerified={handleInvoiceVerified}
            onCategoryChanged={fetchSummary}
            onPaidChanged={(invoiceId, isPaid) =>
              setInvoices((prev) =>
                prev.map((entry) =>
                  entry.id === invoiceId ? { ...entry, isPaid } : entry
                )
              )
            }
          />
        )}

        {pendingCategory && (
          <KsefRememberCategoryModal
            sellerName={seller?.name ?? seller?.nip ?? ""}
            categoryName={
              expenseCategoryItems.find((item) => item.id === pendingCategory.category)
                ?.name ?? ""
            }
            categoryColor={
              expenseCategoryItems.find((item) => item.id === pendingCategory.category)
                ?.color
            }
            onConfirm={() => doSetCategory(true)}
            onCancel={() => doSetCategory(false)}
            onClose={() => setPendingCategory(null)}
          />
        )}

        {mappingToRemove && (
          <RemoveModal
            onClose={() => setMappingToRemove(null)}
            handleRemove={handleRemoveMapping}
            warningText={
              <>
                {"Usunąć przypisanie\n"}
                <span className="rm-warning-text pc-y">{mappingToRemove.ksefItemName}</span>
                {"?\n\nZaksięgowane faktury pozostaną bez zmian - znika tylko podpowiedź przy kolejnych fakturach."}
              </>
            }
          />
        )}
      </div>
    </div>
  );
}

export default SellerDashboard;
