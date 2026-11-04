import React from "react";
import { useState, useEffect, useCallback, useRef } from "react";
import { Link } from "react-router-dom";
import { CirclePlus, LoaderCircle, Repeat, RotateCw } from "lucide-react";
import TextInput from "../TextInput";
import { useAlert } from "../Alert/AlertProvider";
import { getCategoryTemplate } from "../../utils/expenseUtils";
import {
  CompanyExpense, ExpenseCategory, ExpenseCategoryCode, NewCompanyExpense, ITEM_FORBIDDEN_CATEGORIES, NewCompanyExpenseItem, EDIT_LOCKED_CATEGORIES,
  CURRENCY_SYMBOLS, EXPENSE_CURRENCIES, ExpenseCurrency
} from "../../models/expense";
import useExchangeRate from "../../hooks/useExchangeRate";
import useExpenseCategories from "../../hooks/useExpenseCategories";
import { formatDate, toIsoDate } from "../../utils/dateUtils";
import DropdownSelect from "../DropdownSelect";
// import DateInput from "../DateInput";
import WheelDateField from "../WheelDateField";
import ActionButton from "../ActionButton";
import { VatRate } from "../../models/vatrate";
import ExpenseItemList from "./ExpenseItemList";
import ExpenseService from "../../services/ExpenseService";
import AllProductService from "../../services/AllProductService";
import EmployeeService from "../../services/EmployeeService";
import { AlertType } from "../../models/alert";
import { Product } from "../../models/product";
import { Employee } from "../../models/employee";
import { SellerOriginModule, SellerOriginPeriod } from "../../models/ksef_seller";
import KsefSellerService from "../../services/KsefSellerService";
import { isValidTaxId, normalizeTaxId } from "../../utils/validators";

const FIXED_SOURCE_CATEGORIES: ExpenseCategoryCode[] = [ExpenseCategory.ZUS, ExpenseCategory.TAXES];

const SELLERLESS_CATEGORIES: ExpenseCategoryCode[] = [
  ExpenseCategory.SALARY,
  ExpenseCategory.ZUS,
  ExpenseCategory.TAXES,
];

type SellerLookupStatus = "idle" | "loading" | "found" | "new" | "invalid";

const toMixedItem = (
  item: NewCompanyExpenseItem,
  previousCategory: ExpenseCategoryCode | null
): NewCompanyExpenseItem => {
  const { notProduct, salaryComponent, ...rest } = item;
  if (item.productId != null) return { ...rest, category: ExpenseCategory.PRODUCTS };
  if (previousCategory === ExpenseCategory.PRODUCTS) {
    return notProduct
      ? { ...rest, category: null }
      : { ...rest, category: ExpenseCategory.PRODUCTS };
  }
  return {
    ...rest,
    category:
      previousCategory && !ITEM_FORBIDDEN_CATEGORIES.includes(previousCategory)
        ? previousCategory
        : null,
  };
};

const TEMPLATE_CATEGORIES: ExpenseCategoryCode[] = [
  ExpenseCategory.RENT,
  ExpenseCategory.ZUS,
  ExpenseCategory.TAXES,
  ExpenseCategory.SALARY,
];

const isTemplateCategory = (category: ExpenseCategoryCode | null | undefined) =>
  category != null && TEMPLATE_CATEGORIES.includes(category);

const defaultExpenseItem = (): NewCompanyExpenseItem => ({
  name: "",
  quantity: 1,
  vatRate: VatRate.VAT_23,
  price: 0,
  category: null,
});

const fromMixedItem = (
  item: NewCompanyExpenseItem,
  nextCategory: ExpenseCategoryCode
): NewCompanyExpenseItem => {
  if (nextCategory === ExpenseCategory.PRODUCTS) {
    return item.category === ExpenseCategory.PRODUCTS
      ? { ...item, category: null, notProduct: false }
      : { ...item, category: null, productId: null, notProduct: true };
  }
  return { ...item, category: null, productId: null };
};

const convertItem = (
  item: NewCompanyExpenseItem,
  previousCategory: ExpenseCategoryCode | null,
  nextCategory: ExpenseCategoryCode
): NewCompanyExpenseItem => {
  if (nextCategory === ExpenseCategory.MIXED) return toMixedItem(item, previousCategory);
  if (previousCategory === ExpenseCategory.MIXED) return fromMixedItem(item, nextCategory);
  const { notProduct, ...rest } = item;
  if (nextCategory === ExpenseCategory.PRODUCTS) {
    return item.productId != null
      ? { ...rest, notProduct: false }
      : { ...rest, productId: null, notProduct: false, name: "" };
  }
  return item.productId != null
    ? { ...rest, productId: null, category: null, name: "" }
    : { ...rest, productId: null, category: null };
};

const FIXED_ITEM_COUNTS: Partial<Record<ExpenseCategoryCode, number>> = {
  [ExpenseCategory.ZUS]: 1,
  [ExpenseCategory.TAXES]: 3,
  [ExpenseCategory.RENT]: 1,
};

export interface ExpenseFormProps {
  preview?: boolean;
  highlightProductId?: number | null;
  className?: string;
  expenseDTO: NewCompanyExpense;
  setExpenseDTO: React.Dispatch<React.SetStateAction<NewCompanyExpense>>;
  originPeriod?: SellerOriginPeriod;
  originModule?: SellerOriginModule;
  originProductId?: number;
  sellerLink?: boolean;
  headerEditable?: boolean;
  originalCategory?: ExpenseCategoryCode | null;
  onRateReadyChange?: (ready: boolean) => void;
}

const formatRate = (rate: number): string => rate.toFixed(4).replace(".", ",");

const toPln = (value: number | null | undefined, rate: number): number =>
  Math.round((value ?? 0) * rate * 100) / 100;

export function ExpenseForm({
  preview = false,
  headerEditable = false,
  originalCategory = null,
  highlightProductId = null,
  className = "",
  expenseDTO,
  setExpenseDTO,
  originPeriod,
  originModule = "Expenses",
  originProductId,
  sellerLink = true,
  onRateReadyChange,
}: ExpenseFormProps) {
  const { allItems: allExpenseCategoryItems, editableItems: editableCategoryItems, nameOf: getExpenseCategoryDisplay } = useExpenseCategories();
  const { showAlert } = useAlert();
  const [expensePreview, setExpensePreview] = useState<CompanyExpense | null>(null);
  const [allProducts, setAllProducts] = useState<Product[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [nip, setNip] = useState<string>(expenseDTO.seller?.nip ?? "");
  const [sellerStatus, setSellerStatus] = useState<SellerLookupStatus>(
    expenseDTO.seller?.id ? "found" : "idle"
  );
  const lookupRef = useRef<number>(0);
  const headerPreview = preview && !headerEditable;
  const categoryLocked =
    headerEditable && originalCategory != null && EDIT_LOCKED_CATEGORIES.includes(originalCategory);

  const isSalary = expenseDTO.category === ExpenseCategory.SALARY;
  const isMixed = expenseDTO.category === ExpenseCategory.MIXED;
  const hasFixedSource =
    expenseDTO.category != null &&
    FIXED_SOURCE_CATEGORIES.includes(expenseDTO.category);
  const fixedItemCount = isSalary
    ? expenseDTO.expenseItems.filter((item) => item.salaryComponent != null).length
    : expenseDTO.category
    ? FIXED_ITEM_COUNTS[expenseDTO.category] ?? 0
    : 0;
  const allowsSeller =
    expenseDTO.category == null || !SELLERLESS_CATEGORIES.includes(expenseDTO.category);
  const sellerLocked = sellerStatus === "found" || sellerStatus === "loading";
  const selectedEmployee =
    employees.find((e) => e.id === expenseDTO.employeeId) ?? null;
  const currency: ExpenseCurrency = expenseDTO.currency ?? "PLN";
  const isForeign = allowsSeller && currency !== "PLN";
  const currencySymbol = CURRENCY_SYMBOLS[isForeign ? currency : "PLN"];
  const futureRateDate = isForeign && !headerPreview && expenseDTO.expenseDate > toIsoDate(new Date());
  const liveRate = useExchangeRate(
    currency,
    expenseDTO.expenseDate,
    isForeign && !headerPreview && !futureRateDate
  );
  const rateMid = headerPreview ? expenseDTO.exchangeRate ?? null : liveRate.rate?.mid ?? null;
  const rateDate = headerPreview ? expenseDTO.exchangeRateDate ?? null : liveRate.rate?.rateDate ?? null;
  const plnTotals =
    rateMid != null && (headerPreview || liveRate.status === "ready")
      ? {
          value: toPln(expensePreview?.totalValue, rateMid),
          vat: toPln(expensePreview?.totalVat, rateMid),
        }
      : null;

  const fetchExpensePreview = async (expenseDTO: NewCompanyExpense) => {
      ExpenseService.getExpensePreview({ ...expenseDTO, currency: "PLN" })
        .then((data) => {
          setExpensePreview(data);
        })
        .catch((error) => {
          console.error("Error fetching order preview: ", error);
          showAlert("Błąd", AlertType.ERROR);
        })
  }

  const handleCategoryChange = useCallback(
    async (
      selected:
        | { id: ExpenseCategoryCode; name: string }
        | { id: ExpenseCategoryCode; name: string }[]
        | null,
      onlyIfEmpty: boolean = false
    ) => {
      const category = Array.isArray(selected)
        ? selected[0].id
        : selected
        ? selected.id
        : null;

      const baseReset = {
        source: "",
        expenseItems: [],
        employeeId: null,
      };

      const keptSeller = (prev: NewCompanyExpense) =>
        prev.seller && (!category || !SELLERLESS_CATEGORIES.includes(category))
          ? { source: prev.source, seller: prev.seller }
          : { seller: null };

      if (category && !isTemplateCategory(category)) {
        setExpenseDTO((prev) => {
          if (onlyIfEmpty && prev.category) return prev;
          if (isTemplateCategory(prev.category)) {
            return {
              ...prev,
              category,
              employeeId: null,
              source: "",
              seller: null,
              expenseItems: prev.expenseItems.map(() => defaultExpenseItem()),
            };
          }
          return {
            ...prev,
            category,
            employeeId: null,
            expenseItems: prev.expenseItems.map((item) =>
              convertItem(item, prev.category, category)
            ),
          };
        });
      } else if (category) {
        const template = await getCategoryTemplate(category);
        setExpenseDTO((prev) =>
          onlyIfEmpty && prev.category
            ? prev
            : {
                ...prev,
                ...baseReset,
                category: category,
                ...template,
                ...keptSeller(prev),
              }
        );
      } else {
        setExpenseDTO((prev) => ({
          ...prev,
          ...baseReset,
          category: null,
          ...keptSeller(prev),
        }));
      }
    },
    []
  );
  const handleSource = useCallback((source: string) => {
    setExpenseDTO((prev) => ({
      ...prev,
      source: source,
    }));
  }, []);
  const handleInvoiceNumber = useCallback((invoiceNumber: string) => {
    setExpenseDTO((prev) => ({ ...prev, invoiceNumber }));
  }, []);
  const handleCurrencyCycle = useCallback(() => {
    setExpenseDTO((prev) => {
      const current = prev.currency ?? "PLN";
      const next = EXPENSE_CURRENCIES[(EXPENSE_CURRENCIES.indexOf(current) + 1) % EXPENSE_CURRENCIES.length];
      return { ...prev, currency: next };
    });
  }, []);
  const handleEditCategoryChange = useCallback(
    (
      selected:
        | { id: ExpenseCategoryCode; name: string }
        | { id: ExpenseCategoryCode; name: string }[]
        | null
    ) => {
      const category = Array.isArray(selected) ? selected[0]?.id : selected?.id;
      if (!category) return;
      setExpenseDTO((prev) => ({ ...prev, category }));
    },
    []
  );
  const handleSellerAddress = useCallback((address: string) => {
    setExpenseDTO((prev) =>
      prev.seller ? { ...prev, seller: { ...prev.seller, address } } : prev
    );
  }, []);
  const handleNipChange = useCallback(
    (value: string) => {
      const taxId = normalizeTaxId(value);
      setNip(taxId);
      const requestId = ++lookupRef.current;
      const detachSeller = () =>
        setExpenseDTO((prev) => ({
          ...prev,
          source: prev.seller?.id ? "" : prev.source,
          seller: taxId.length > 0
            ? { nip: taxId, name: "", address: prev.seller?.id ? "" : prev.seller?.address ?? "" }
            : null,
        }));

      const minLength = taxId.startsWith("PL") ? 12 : /^[A-Z]/.test(taxId) ? 8 : 10;
      if (taxId.length < minLength || !isValidTaxId(taxId)) {
        setSellerStatus(taxId.length < minLength ? "idle" : "invalid");
        detachSeller();
        return;
      }

      setSellerStatus("loading");
      KsefSellerService.getByNip(taxId)
        .then((seller) => {
          if (requestId !== lookupRef.current) return;
          if (seller) {
            setSellerStatus("found");
            setExpenseDTO((prev) => ({
              ...prev,
              source: seller.name ?? prev.source,
              seller: {
                id: seller.id,
                nip: taxId,
                name: seller.name ?? "",
                address: seller.address ?? "",
              },
            }));
            const defaultCategory = seller.defaultExpenseCategory;
            if (defaultCategory && !SELLERLESS_CATEGORIES.includes(defaultCategory)) {
              handleCategoryChange({ id: defaultCategory, name: "" }, true);
            }
          } else {
            setSellerStatus("new");
            setExpenseDTO((prev) => ({
              ...prev,
              source: prev.seller?.id ? "" : prev.source,
              seller: { nip: taxId, name: "", address: "" },
            }));
          }
        })
        .catch((error) => {
          if (requestId !== lookupRef.current) return;
          console.error("Error fetching seller by NIP: ", error);
          setSellerStatus("idle");
          showAlert("Błąd wyszukiwania dostawcy!", AlertType.ERROR);
        });
    },
    [handleCategoryChange]
  );
  const handleExpenseDate = useCallback((expenseDate: string | null) => {
    if (expenseDate) {
      setExpenseDTO((prev) => ({
        ...prev,
        expenseDate: expenseDate,
      }));
    }
  }, []);
  const handleEmployeeChange = useCallback(
    (selected: Employee | Employee[] | null) => {
      const employee = Array.isArray(selected) ? selected[0] : selected;
      setExpenseDTO((prev) => ({
        ...prev,
        employeeId: employee?.id ?? null,
      }));
    },
    []
  );
  const handleAddNewExpenseItem = useCallback(() => {
    setExpenseDTO((prev) => ({
      ...prev,
      expenseItems: [
        ...prev.expenseItems,
        {
          name: "",
          quantity: 1,
          vatRate: VatRate.VAT_23,
          price: 0,
        },
      ],
    }));
  }, []);

  const setExpenseItems: React.Dispatch<React.SetStateAction<NewCompanyExpenseItem[]>> = useCallback(
    (action) => {
      setExpenseDTO((prev) => ({
        ...prev,
        expenseItems: typeof action === "function" ? action(prev.expenseItems) : action,
      }));
    },
    []
  );

  useEffect(() => {
    if (!headerEditable) return;
    lookupRef.current++;
    setNip(expenseDTO.seller?.nip ?? "");
    setSellerStatus(expenseDTO.seller?.id ? "found" : "idle");
  }, [headerEditable]);

  useEffect(() => {
    if (!expenseDTO.seller && nip) {
      lookupRef.current++;
      setNip("");
      setSellerStatus("idle");
    }
  }, [expenseDTO.seller]);

  useEffect(() => {
    if (!allowsSeller) {
      lookupRef.current++;
      setNip("");
      setSellerStatus("idle");
      if (!headerPreview) {
        setExpenseDTO((prev) =>
          prev.currency && prev.currency !== "PLN" ? { ...prev, currency: "PLN" } : prev
        );
      }
    }
  }, [allowsSeller]);

  useEffect(() => {
    onRateReadyChange?.(!isForeign || headerPreview || liveRate.status === "ready");
  }, [isForeign, headerPreview, liveRate.status]);

  useEffect(() => {
    if (liveRate.status === "error" && liveRate.error) {
      showAlert(liveRate.error, AlertType.ERROR);
    }
  }, [liveRate.status, liveRate.error]);

  useEffect(() => {
    if (!isSalary) {
      setEmployees([]);
      return;
    }
    EmployeeService.getAllEmployees()
      .then((data) => setEmployees(data))
      .catch(() => showAlert("Błąd pobierania listy pracowników!", AlertType.ERROR));
  }, [isSalary]);

  useEffect(() => {
    if (expenseDTO.category === ExpenseCategory.PRODUCTS || expenseDTO.category === ExpenseCategory.MIXED) {
      AllProductService.getProducts({ includeZero: true, isDeleted: false }, 0, 500)
        .then((result) => setAllProducts(result.content ?? []))
        .catch(() => showAlert("Błąd pobierania listy produktów!", AlertType.ERROR));
    } else {
      setAllProducts([]);
    }
  }, [expenseDTO.category]);

  useEffect(() => {
      fetchExpensePreview(expenseDTO);
    }, [expenseDTO])

  const sellerDetails = expenseDTO.seller && (
    <>
      <span className="ksef-party-name">
        {expenseDTO.seller.name || expenseDTO.source || expenseDTO.seller.nip}
      </span>
      <span className="ksef-party-detail">NIP: {expenseDTO.seller.nip}</span>
      {expenseDTO.seller.address && (
        <span className="ksef-party-detail">{expenseDTO.seller.address}</span>
      )}
    </>
  );

  const sellerFrame =
    headerPreview && expenseDTO.seller?.id ? (
      <div className="ksef-invoice-party flex-column">
        <span className="ksef-party-label">Sprzedawca</span>
        {sellerLink ? (
          <Link
            to={`/ksef/seller/${expenseDTO.seller.id}`}
            state={{ module: originModule, period: originPeriod, productId: originProductId }}
            className="ksef-party-link flex-column"
            title="Otwórz stronę dostawcy"
          >
            {sellerDetails}
          </Link>
        ) : (
          <span className="flex-column">{sellerDetails}</span>
        )}
      </div>
    ) : null;

  const headRows = (
    <>
      <div className="exf-row flex align-items-center space-between">
        <span className="exf-label">Kategoria</span>
        {headerPreview || categoryLocked ? (
          expenseDTO.category ? (
            <span
              className="exp-cat-badge colored"
              style={
                {
                  "--cat-rgb": allExpenseCategoryItems.find(
                    (c) => c.id === expenseDTO.category
                  )?.color,
                } as React.CSSProperties
              }
            >
              {getExpenseCategoryDisplay(expenseDTO.category)}
            </span>
          ) : (
            <span className="exf-value">-</span>
          )
        ) : (
          <DropdownSelect
            items={headerEditable ? editableCategoryItems : allExpenseCategoryItems}
            value={
              expenseDTO.category
                ? allExpenseCategoryItems.filter(
                    (item) => expenseDTO.category === item.id
                  )
                : null
            }
            onChange={headerEditable ? handleEditCategoryChange : handleCategoryChange}
            searchable={false}
            allowNew={false}
            allowColors={true}
            multiple={false}
            placeholder="Wybierz"
            className="expense-category"
          />
        )}
      </div>
      {isSalary && (
        <div className="exf-row flex align-items-center space-between">
          <span className="exf-label">Pracownik</span>
          {headerPreview ? (
            <span className="exf-value">
              {selectedEmployee
                ? `${selectedEmployee.name} ${selectedEmployee.lastName}`
                : "-"}
            </span>
          ) : (
            <DropdownSelect<Employee>
              items={employees}
              value={selectedEmployee}
              onChange={handleEmployeeChange}
              getItemLabel={(employee) =>
                `${employee.name} ${employee.lastName}`
              }
              searchable={false}
              allowNew={false}
              multiple={false}
              placeholder="Wybierz"
              className="expense-category"
            />
          )}
        </div>
      )}

      <div className="exf-row flex align-items-center space-between">
        <span className="exf-label">Data</span>
        {headerPreview ? (
          <span className="exf-value">{formatDate(expenseDTO.expenseDate)}</span>
        ) : (
          <>
            {/* <DateInput
              onChange={handleExpenseDate}
              selectedDate={expenseDTO.expenseDate}
            /> */}
            <WheelDateField
              title="Data kosztu"
              value={expenseDTO.expenseDate}
              onChange={handleExpenseDate}
            />
          </>
        )}
      </div>

      {allowsSeller && (
        <div className="exf-row flex align-items-center space-between g-05">
          <span className="exf-currency-label flex align-items-center">
            <span className="exf-label">Waluta</span>
            {isForeign && !headerPreview && liveRate.status === "loading" && (
              <span className="exf-rate flex align-items-center g-025">
                <LoaderCircle className="exf-rate-spin" />
                Kurs NBP...
              </span>
            )}
            {futureRateDate && (
              <span className="exf-rate error">Brak kursu NBP dla przyszłej daty</span>
            )}
            {isForeign && !headerPreview && liveRate.status === "error" && (
              <span className="exf-rate error flex align-items-center g-05">
                Brak kursu NBP
                <ActionButton
                  text="Ponów"
                  className="exf-rate-retry"
                  onClick={liveRate.retry}
                >
                  <RotateCw className="dl-icon" />
                </ActionButton>
              </span>
            )}
            {isForeign && rateMid != null && (headerPreview || liveRate.status === "ready") && (
              <span className="exf-rate">
                Kurs NBP <b>{formatRate(rateMid)}</b>
                {rateDate && ` z ${formatDate(rateDate)}`}
              </span>
            )}
          </span>
          {headerPreview ? (
            <span className="exf-value">{currency}</span>
          ) : (
            <ActionButton
              iconTitle="Zmień walutę"
              className="cycle-filter-btn exf-currency-btn"
              onClick={handleCurrencyCycle}
            >
              <span className="cycle-filter-value nowrap">{currency}</span>
              <Repeat className="exf-currency-icon" />
            </ActionButton>
          )}
        </div>
      )}
    </>
  );

  return (
    <div className={`exf flex-column width-max f-1 min-height-0 g-075 ${className}`}>
      {sellerFrame ? (
        <div className="exf-split flex g-2">
          {sellerFrame}
          <div className="exf-split-side flex-column  g-075 f-1 mw-0">{headRows}</div>
        </div>
      ) : (
        headRows
      )}

      {allowsSeller && !headerPreview && (
        <div className="exf-row flex align-items-center space-between">
          <span className="exf-label flex align-items-center g-05">
            NIP
            {sellerStatus === "invalid" && (
              <span className="exf-hint invalid">niepoprawny</span>
            )}
            {sellerStatus === "found" && (
              <span className="exf-hint">dostawca z bazy</span>
            )}
            {sellerStatus === "new" && (
              <span className="exf-hint new">nowy dostawca</span>
            )}
          </span>
          <TextInput
            dropdown={false}
            value={nip}
            onSelect={(input) => {
              if (typeof input === "string" && normalizeTaxId(input) !== nip) {
                handleNipChange(input);
              }
            }}
            noCapitalize
            maxLength={20}
            placeholder="opcjonalnie"
            className="in-lg"
          />
        </div>
      )}

      {!sellerFrame && (
        <div className="exf-row flex align-items-center space-between">
          <span className="exf-label">Dostawca</span>
          {headerPreview ? (
            <span className="exf-value">{expenseDTO.source || "-"}</span>
          ) : (
            <TextInput
              dropdown={false}
              value={expenseDTO.source}
              onSelect={(input) => {
                if (typeof input === "string") {
                  handleSource(input);
                }
              }}
              disabled={hasFixedSource || sellerLocked}
              className="in-lg"
            />
          )}
        </div>
      )}

      {!headerPreview && expenseDTO.seller && sellerStatus !== "idle" && (
        <div className="exf-row flex align-items-center space-between">
          <span className="exf-label">Adres</span>
          <TextInput
            dropdown={false}
            value={expenseDTO.seller.address}
            onSelect={(input) => {
              if (typeof input === "string") {
                handleSellerAddress(input);
              }
            }}
            disabled={sellerLocked}
            placeholder="ulica, kod, miasto"
            className="in-lg"
          />
        </div>
      )}

      {allowsSeller && (
        <div className="exf-row flex align-items-center space-between">
          <span className="exf-label">Nr faktury</span>
          {headerPreview ? (
            <span className="exf-value">{expenseDTO.invoiceNumber || "-"}</span>
          ) : (
            <TextInput
              dropdown={false}
              value={expenseDTO.invoiceNumber ?? ""}
              onSelect={(input) => {
                if (typeof input === "string") {
                  handleInvoiceNumber(input);
                }
              }}
              placeholder="opcjonalnie"
              maxLength={64}
              noCapitalize
              className="in-lg"
            />
          )}
        </div>
      )}

      <div className="exf-items-head flex align-items-center space-between">
        <span className="exf-section-title">Pozycje</span>
      </div>

      <div className="exf-items f-1 min-height-0">
        <ExpenseItemList
          expenseItems={expenseDTO.expenseItems}
          setExpenseItems={setExpenseItems}
          preview={preview}
          fixedItemCount={fixedItemCount}
          highlightProductId={highlightProductId}
          allProducts={allProducts.length > 0 ? allProducts : undefined}
          productsMode={expenseDTO.category === ExpenseCategory.PRODUCTS}
          mixedMode={isMixed}
          onProductCreated={(p) =>
            setAllProducts((prev) => [p, ...prev.filter((x) => x.id !== p.id)])
          }
          emptyAction={
            !preview && (
              <ActionButton text="Nowa pozycja" onClick={handleAddNewExpenseItem}>
                <CirclePlus className="dl-icon-1 c-green" />
              </ActionButton>
            )
          }
        />
        {!preview && expenseDTO.expenseItems.length > 0 && (
          <div className="exf-add-row flex justify-start">
            <ActionButton text="Nowa pozycja" onClick={handleAddNewExpenseItem}>
              <CirclePlus className="dl-icon-1 c-green" />
            </ActionButton>
          </div>
        )}
      </div>

      <div className="exf-totals flex-column align-items-end g-025">
        <div className="flex align-items-center justify-end g-1">
          <span className="exf-total-label">Netto</span>
          <span className="clx-form-value">
            {expensePreview?.totalNet ?? 0} {currencySymbol}
          </span>
          <span className="exf-total-label">VAT</span>
          <span className="clx-form-value">
            {expensePreview?.totalVat ?? 0} {currencySymbol}
          </span>
          <span className="exf-total-label">Brutto</span>
          <span className="clx-form-value pc-y">
            {expensePreview?.totalValue ?? 0} {currencySymbol}
          </span>
        </div>
        {isForeign && (
          <div className="exf-totals-pln flex align-items-center justify-end g-075">
            <span className="exf-total-label">w PLN</span>
            <span>Netto {plnTotals ? `${(plnTotals.value - plnTotals.vat).toFixed(2)} zł` : "-"}</span>
            <span>VAT {plnTotals ? `${plnTotals.vat.toFixed(2)} zł` : "-"}</span>
            <b>
              Brutto <span className="pc-y">{plnTotals ? `${plnTotals.value.toFixed(2)} zł` : "-"}</span>
            </b>
          </div>
        )}
      </div>
    </div>
  );
}

export default ExpenseForm;
