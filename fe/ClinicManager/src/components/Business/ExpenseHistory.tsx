import { useAlert } from "../Alert/AlertProvider";
import { useState, useCallback, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Blocks, CirclePlus, RotateCw } from "lucide-react";
import { CompanyExpense, ExpenseFilterDTO, ExpenseCategory, ExpenseCategoryCode } from "../../models/expense";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import ExpenseService from "../../services/ExpenseService";
import { AlertType } from "../../models/alert";
import ActionButton from "../ActionButton";
import DropdownSelect from "../DropdownSelect";
import ExpenseRemoveModal from "../Popups/ExpenseRemoveModal";
import ExpenseModal from "../Popups/ExpenseModal";
import { KsefRememberCategoryModal } from "../Popups/KsefRememberCategoryModal";
import KsefInvoiceModal from "../Popups/KsefInvoiceModal";
import CategoryManagerModal from "../Popups/CategoryManagerModal";
import KsefInvoiceService from "../../services/KsefInvoiceService";
import ExpenseDataList from "./ExpenseDataList";
import FilterDropdown from "../FilterDropdown";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";
import PeriodPill from "../PeriodPill";


export function ExpenseHistory() {
  const { items: expenseCategoryItems } = useExpenseCategories();
  const { showAlert } = useAlert();
  const location = useLocation();
  const navigate = useNavigate();
  const [expenses, setExpenses] = useState<CompanyExpense[]>([]);
  const currentDate = new Date();
  const [filter, setFilter] = useRestorableState<ExpenseFilterDTO>(
    "expenses.filter",
    {
      categories: null,
      year: currentDate.getFullYear(),
      month: currentDate.getMonth() + 1,
    },
    { force: !!(location.state as { period?: unknown } | null)?.period }
  );
  const [isAddNewExpensePopupOpen, setIsAddNewExpensePopupOpen] =
      useState<boolean>(false);
    const [removeExpenseId, setRemoveExpenseId] =
      useState<number | null>(null);
  const [pendingCategory, setPendingCategory] = useState<{
    id: number;
    source: string;
    category: ExpenseCategoryCode;
  } | null>(null);
  const [previewInvoiceId, setPreviewInvoiceId] = useRestorableState<number | null>(
    "expenses.previewInvoiceId",
    null,
    { skipOnReload: true }
  );
  const [previewExpenseId, setPreviewExpenseId] = useState<number | null>(null);
  const [editExpenseId, setEditExpenseId] = useState<number | null>(null);
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const currentYear = new Date().getFullYear();
  const currentMonth = new Date().getMonth() + 1;
  const { isMobile, isCompact, isNarrow } = useBreakpoint();
  const [filtersOpen, setFiltersOpen] = useState<boolean>(false);
  const [isCategoryManagerOpen, setIsCategoryManagerOpen] = useState<boolean>(false);

  const handleResetFiltersAndData = useCallback(() => {
    const now = new Date();
    setFilter({
      categories: null,
      year: now.getFullYear(),
      month: now.getMonth() + 1,
    });
    setPage(0);
    setHasMore(true);
  }, []);

  const handleOpenPreview = useCallback((id: number) => {
    const expense = expenses.find((e) => e.id === id);
    if (expense?.sourceType === "KSEF") {
      setPreviewInvoiceId(id);
    } else {
      setPreviewExpenseId(id);
    }
  }, [expenses]);

  const fetchExpenses = useCallback(
    async (pageNum: number = 0, append: boolean = false) => {
      setLoading(true);
      ExpenseService.getExpenses(filter, pageNum, 30)
        .then((data) => {
          const content = data?.content || [];
          if (append) {
            setExpenses((prev) => [...prev, ...content]);
          } else {
            setExpenses(content);
          }

          setHasMore(!data.last);
          setPage(pageNum);
        })
        .catch((error) => {
          if (!append) setExpenses([]);
          setHasMore(false);
          showAlert("Błąd", AlertType.ERROR);
          console.error("Error fetching Expenses:", error);
        })
        .finally(() => {
          setLoading(false);
        });
    },
    [filter]
  );

  useEffect(() => {
      fetchExpenses(0, false);
      setPage(0);
      setHasMore(true);
    }, [filter, fetchExpenses]);

  useEffect(() => {
    const state = location.state as {
      previewInvoiceId?: number;
      period?: { year?: number | null; month?: number | null };
    } | null;
    if (!state?.period && state?.previewInvoiceId == null) return;

    if (state.period) {
      setFilter((prev) => ({
        ...prev,
        year: state.period?.year ?? null,
        month: state.period?.month ?? null,
      }));
    }
    if (state.previewInvoiceId != null) {
      setPreviewInvoiceId(state.previewInvoiceId);
    }
    navigate(location.pathname, { replace: true, state: { module: "Expenses" } });
  }, [location, navigate]);


  const handleMonthChange = useCallback(
    (month: number | null) => {
      setFilter((prevFilter) => ({
        ...prevFilter,
        month: month ?? null,
      }));
    },
    []
  );

  const handleYearChange = useCallback(
    (year: number | null) => {
      setFilter((prevFilter) => ({
        ...prevFilter,
        year: year ?? null,
      }));
    },
    []
  );

  const toggleCategoryFilter = useCallback((category: ExpenseCategoryCode) => {
    setFilter((prevFilter) => {
      const current = prevFilter.categories ?? [];
      const next = current.includes(category)
        ? current.filter((c) => c !== category)
        : [...current, category];
      return { ...prevFilter, categories: next.length > 0 ? next : null };
    });
  }, []);

  const handleExpenseRemoved = useCallback(() => {
    setRemoveExpenseId(null);
    fetchExpenses();
    handleResetFiltersAndData();
  }, [fetchExpenses, handleResetFiltersAndData]);

  const handleExpenseSaved = useCallback(() => {
    fetchExpenses(0, false);
  }, [fetchExpenses]);

  const handleSelectProducts = useCallback(
    (expense: CompanyExpense) => {
      if (expense.awaitingProductVerification) {
        setPreviewInvoiceId(expense.id);
        return;
      }
      KsefInvoiceService.toggleProductInvoice(expense.id)
        .then(() => {
          setPreviewInvoiceId(expense.id);
          fetchExpenses(0, false);
        })
        .catch(() => showAlert("Błąd zmiany statusu faktury.", AlertType.ERROR));
    },
    [showAlert, fetchExpenses]
  );

  const applySetCategory = useCallback(
    (id: number, category: ExpenseCategoryCode, rememberForSeller: boolean) => {
      KsefInvoiceService.setCategory(id, category, rememberForSeller)
        .then(() => {
          showAlert("Pomyślnie zmieniono kategorię faktury!", AlertType.SUCCESS);
          fetchExpenses(0, false);
        })
        .catch(() => showAlert("Błąd zmiany kategorii faktury.", AlertType.ERROR));
    },
    [showAlert, fetchExpenses]
  );

  const handleSelectCategory = useCallback(
    (expense: CompanyExpense, category: ExpenseCategoryCode) => {
      if (category === ExpenseCategory.PRODUCTS) {
        handleSelectProducts(expense);
        return;
      }
      if (category === expense.sellerDefaultExpenseCategory) {
        applySetCategory(expense.id, category, false);
        return;
      }
      setPendingCategory({ id: expense.id, source: expense.source, category });
    },
    [handleSelectProducts, applySetCategory]
  );

  const doSetCategory = useCallback(
    (rememberForSeller: boolean) => {
      if (!pendingCategory) return;
      applySetCategory(pendingCategory.id, pendingCategory.category, rememberForSeller);
      setPendingCategory(null);
    },
    [pendingCategory, applySetCategory]
  );

    const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
        const target = e.currentTarget;
        const scrolledToBottom = 
          target.scrollHeight - target.scrollTop <= target.clientHeight + 100; // 100px b4 end of the list
    
        if (scrolledToBottom && hasMore && !loading) {
          fetchExpenses(page + 1, true);
        }
      }, [hasMore, loading, page, filter]);

  const categorySelect = (
    <DropdownSelect
      items={expenseCategoryItems}
      value={expenseCategoryItems.filter((category) =>
        filter.categories?.includes(category.id)
      )}
      onChange={(selected) => {
        const ids = ((selected as typeof expenseCategoryItems | null) ?? []).map(
          (category) => category.id
        );
        setFilter((prev) => ({
          ...prev,
          categories: ids.length > 0 ? ids : null,
        }));
      }}
      placeholder="Kategoria"
      multiple={true}
      searchable={false}
      allowNew={false}
      className="exp-category-dropdown"
    />
  );

  const periodSelect = (
    <PeriodPill
      month={filter.month}
      year={filter.year}
      onMonthChange={(month) => {
        handleMonthChange(month);
        setFiltersOpen(false);
      }}
      onYearChange={handleYearChange}
    />
  );

  return (
  <>
  <section className="exp-toolbar width-90 flex align-self-center align-items-center g-1 mt-1 mb-1">
    <div className="flex align-items-center g-1">
      {isCompact ? (
        <FilterDropdown
          label="Filtry"
          className="exp-filters"
          open={filtersOpen}
          onOpenChange={setFiltersOpen}
          count={
            Number(filter.categories != null) +
            Number(filter.month !== currentMonth || filter.year !== currentYear)
          }
        >
          {periodSelect}
          {categorySelect}
        </FilterDropdown>
      ) : (
        <>
          {periodSelect}
          {isMobile && categorySelect}
        </>
      )}
      <ActionButton
        iconTitle="Resetuj filtry"
        onClick={handleResetFiltersAndData}
        disableText={true}
        className="reset"
      >
        <RotateCw className="dl-icon icon-control" />
      </ActionButton>
    </div>
    <div className="exp-actions flex align-items-center g-1">
        <ActionButton
          text={isCompact ? "Koszt" : isMobile ? "Faktura kosztowa" : "Nowa Faktura Kosztowa"}
          iconTitle="Nowa Faktura Kosztowa"
          disableText={isNarrow}
          onClick={() => setIsAddNewExpensePopupOpen(true)}
        >
          <CirclePlus className="dl-icon-1 c-green" />
        </ActionButton>
        <ActionButton
          text="Kategorie"
          iconTitle="Kategorie"
          disableText={isNarrow}
          onClick={() => setIsCategoryManagerOpen(true)}
        >
          <Blocks className="dl-icon-1 c-yellow" aria-label="Kategoria" />
        </ActionButton>
    </div>
  </section>
  {!isMobile && (
    <div className="width-90 flex align-self-center align-items-center space-between g-1 mb-1">
      <div className="cat-seg-group flex flex-wrap align-items-center width-fit-content g-025">
        <button
          type="button"
          className={`cat-seg align-items-center pointer ${
            !filter.categories || filter.categories.length === 0 ? "active" : ""
          }`}
          onClick={() => setFilter((prev) => ({ ...prev, categories: null }))}
        >
          Wszystko
        </button>
        {expenseCategoryItems.map((category) => (
          <button
            type="button"
            key={category.id}
            className={`cat-seg align-items-center pointer ${
              filter.categories?.includes(category.id) ? "active" : ""
            }`}
            onClick={() => toggleCategoryFilter(category.id)}
          >
            <span
              className="dl-dot"
              style={{
                backgroundColor: `rgb(${category.color})`,
                boxShadow: `0 0 6px rgba(${category.color}, 0.5)`,
              }}
            />
            {category.name}
          </button>
        ))}
      </div>
    </div>
  )}
  <div className="width-90 flex-column f-1 align-items-center min-height-0 mb-2">
  <ExpenseDataList
      expenses={expenses}
      onScroll={handleScroll}
      isLoading={loading}
      setRemoveExpenseId={setRemoveExpenseId}
      onSelectCategory={handleSelectCategory}
      onOpenPreview={handleOpenPreview}
      onEdit={setEditExpenseId}
      originPeriod={{ year: filter.year, month: filter.month }}
      className="exp-list"
  />
  </div>
  {isAddNewExpensePopupOpen && (
        <ExpenseModal
          onClose={() => setIsAddNewExpensePopupOpen(false)}
          onReset={handleResetFiltersAndData}
        />
      )}
      {previewExpenseId != null && (
        <ExpenseModal
          onClose={() => setPreviewExpenseId(null)}
          onReset={handleExpenseSaved}
          expenseId={previewExpenseId}
          originPeriod={{ year: filter.year, month: filter.month }}
        />
      )}
      {editExpenseId != null && (
        <ExpenseModal
          onClose={() => setEditExpenseId(null)}
          onReset={handleExpenseSaved}
          expenseId={editExpenseId}
          originPeriod={{ year: filter.year, month: filter.month }}
          startInEdit={true}
        />
      )}
      {removeExpenseId != null && (
        <ExpenseRemoveModal
          expenseId={removeExpenseId}
          onClose={() => setRemoveExpenseId(null)}
          onRemoved={handleExpenseRemoved}
        />
      )}
      {pendingCategory && (
        <KsefRememberCategoryModal
          sellerName={pendingCategory.source}
          categoryName={
            expenseCategoryItems.find((c) => c.id === pendingCategory.category)?.name ?? ""
          }
          categoryColor={
            expenseCategoryItems.find((c) => c.id === pendingCategory.category)?.color
          }
          onConfirm={() => doSetCategory(true)}
          onCancel={() => doSetCategory(false)}
          onClose={() => setPendingCategory(null)}
        />
      )}
      {isCategoryManagerOpen && (
        <CategoryManagerModal
          kind="expense"
          onClose={() => setIsCategoryManagerOpen(false)}
          onChanged={() => fetchExpenses(0, false)}
        />
      )}
      {previewInvoiceId !== null && (
        <KsefInvoiceModal
          invoiceId={previewInvoiceId}
          onClose={() => setPreviewInvoiceId(null)}
          showCategory={true}
          onCategoryChanged={() => fetchExpenses(0, false)}
          onVerified={() => fetchExpenses(0, false)}
          originModule="Expenses"
          originPeriod={{ year: filter.year, month: filter.month }}
        />
      )}
  </>
  );
}

export default ExpenseHistory;
