import { Product, NewProduct } from "../models/product";
import { Action } from "../models/action";
import { Brand, NewBrand } from "../models/brand";
import { BaseServiceCategory, NewBaseServiceCategory, NewProductCategory, ProductCategory } from "../models/categories";
import { JwtUser, User, Role, RoleType } from "../models/login";
import { Employee, NewEmployee } from "../models/employee";
import { BaseService, NewBaseService } from "../models/service";
import { Client, NewClient, NewClientNote } from "../models/client";
import { ClientDebt, NewClientDebt } from "../models/debt";
import { NewVoucher, Voucher } from "../models/voucher";
import { NewReview, Review } from "../models/review";
import { AppSettings, NewAppSettings } from "../models/app_settings";
import { Discount, NewDiscount, NewVisit, Visit, VisitDiscountType } from "../models/visit";
import { PaymentMethod } from "../models/payment";
import { UsageRecordItem } from "../models/usage-record";
import { CompanyExpense, ExpenseCategory, NewCompanyExpense, NewCompanyExpenseItem } from "../models/expense";
import { NewStatSettings } from "../models/business_settings";
import { RegisterRequest } from "../models/register";
import { InventoryReportItem, NewInventoryReportItem } from "../models/inventory_report";
import { CashLedger } from "../models/cash_ledger";
import { KsefBookCorrectionItem, KsefCredentials, KsefInvoiceItem, KsefVerifyProductsItem } from "../models/ksef_invoice";

  export function validateLoginForm(
    username: string,
    password: string
  ): string | null {
    if(!username && ! password) {
      return "Uzupełnij nazwę użytkownika i hasło!";
    }
    if(!username) {
      return "Brak nazwy użytkownika!";
    }
    if(!password) {
      return "Brak hasła!";
    }
    return null;
  };

  export function validateOpenCashLedgerForm(
    cashLedgerForm: CashLedger
  ): string | null {
    const today = new Date().toISOString().slice(0, 10);

    if(!cashLedgerForm.date || cashLedgerForm.date.slice(0, 10) !== today) {
      return "Niepoprawna data!";
    }

    if(cashLedgerForm.openingAmount == null) {
      return "Niepoprawny Stan początkowy!"
    }

    return null;
  }

  export function validateCashLedgerForm(
    cashLedgerForm: CashLedger,
    selectedCashLedger: CashLedger| null,
    action: Action,
  ): string | null {

    if(cashLedgerForm.closingAmount == null) {
      return "Uzupełnij Saldo Końcowe!";
    }

    if(action === Action.EDIT && selectedCashLedger) {
      const noChangesDetected = 
      cashLedgerForm.createdBy === selectedCashLedger.createdBy &&
      cashLedgerForm.date === selectedCashLedger.date &&
      cashLedgerForm.openingAmount === selectedCashLedger.openingAmount &&
      cashLedgerForm.deposit === selectedCashLedger.deposit &&
      cashLedgerForm.cashOutAmount === selectedCashLedger.cashOutAmount &&
      cashLedgerForm.note === selectedCashLedger.note &&
      cashLedgerForm.closingAmount === selectedCashLedger.closingAmount &&
      cashLedgerForm.closedBy === selectedCashLedger.closedBy &&
      cashLedgerForm.isClosed === selectedCashLedger.isClosed;

        if(noChangesDetected) {
          return "Brak zmian!";
        }
    }

    return null;
  }

  export function isValidNip(nip: string): boolean {
    if (!/^\d{10}$/.test(nip)) return false;
    const weights = [6, 5, 7, 2, 3, 4, 5, 6, 7];
    const sum = weights.reduce((acc, weight, index) => acc + weight * Number(nip[index]), 0);
    const control = sum % 11;
    return control !== 10 && control === Number(nip[9]);
  }

  export function normalizeTaxId(value: string): string {
    const compact = value.replace(/[\s.-]/g, "").toUpperCase();
    return /^PL\d{10}$/.test(compact) ? compact.slice(2) : compact;
  }

  export function isForeignTaxId(taxId: string): boolean {
    return /^[A-Z]{2}[A-Z0-9]{2,12}$/.test(taxId) && !taxId.startsWith("PL");
  }

  export function isValidTaxId(taxId: string): boolean {
    return isValidNip(taxId) || isForeignTaxId(taxId);
  }

  export function validateExpenseHeader(
    expenseForm: CompanyExpense | NewCompanyExpense,
  ): string | null {
    if (!expenseForm.expenseDate) {
      return "Podaj datę kosztu!";
    }
    if (!expenseForm.category) {
      return "Wybierz kategorię kosztu!";
    }
    if (!expenseForm.source || expenseForm.source.trim().length <= 2) {
      return "Nazwa źródła za krótka! (2+)";
    }
    if (expenseForm.seller) {
      if (!isValidTaxId(expenseForm.seller.nip)) {
        return "Niepoprawny NIP lub numer VAT UE!";
      }
      if (!expenseForm.seller.id && !expenseForm.seller.address?.trim()) {
        return "Podaj adres dostawcy!";
      }
    }
    return null;
  }

  export function validateExpenseForm(
    expenseForm: CompanyExpense | NewCompanyExpense,
  ): string | null {
    if (Object.entries(expenseForm).some(([key, value]) => {
      return key !== "employeeId" && key !== "seller" && (value === null || value === undefined);
    })) {
      return "Brak pełnych informacji!";
    }

    if (expenseForm.seller) {
      if (!isValidTaxId(expenseForm.seller.nip)) {
        return "Niepoprawny NIP lub numer VAT UE!";
      }
      if (!expenseForm.seller.id && !expenseForm.seller.address?.trim()) {
        return "Podaj adres dostawcy!";
      }
    }

    if (expenseForm.category === ExpenseCategory.SALARY && !expenseForm.employeeId) {
      return "Wybierz pracownika dla wypłaty!";
    }

    if (expenseForm.source && expenseForm.source.trim().length <= 2) {
      return "Nazwa źródła za krótka! (2+)";
    }

    if(!expenseForm.expenseItems || expenseForm.expenseItems.length === 0) {
      return "Pusty koszt... Dodaj pozycje!";
    }

    if (expenseForm.expenseItems.some(
      (ei) => ei.quantity <= 0
    )) {
      return "Ilość w pozycji nie może wynosić 0!";
    }

    if (expenseForm.category === ExpenseCategory.MIXED) {
      const items = expenseForm.expenseItems as NewCompanyExpenseItem[];
      if (items.some((ei) => ei.category == null)) {
        return "Przypisz kategorię każdej pozycji!";
      }
      if (items.some((ei) => ei.category === ExpenseCategory.PRODUCTS && ei.productId == null)) {
        return "Wybierz produkt dla każdej pozycji z kategorią Produkty!";
      }
      if (items.some((ei) => ei.category !== ExpenseCategory.PRODUCTS && !ei.name?.trim())) {
        return "Uzupełnij nazwę każdej pozycji!";
      }
      if (new Set(items.map((ei) => ei.category)).size < 2) {
        return "Wszystkie pozycje mają tę samą kategorię - wybierz ją jako kategorię kosztu!";
      }
    }

    if (expenseForm.category === ExpenseCategory.PRODUCTS) {
      const items = expenseForm.expenseItems as NewCompanyExpenseItem[];
      if (items.every((ei) => ei.notProduct)) {
        return "Żadna pozycja nie jest produktem. Zmień kategorię kosztu!";
      }
      if (items.some((ei) => !ei.notProduct && (ei.productId === null || ei.productId === undefined))) {
        return "Wybierz produkt dla każdej pozycji albo oznacz ją jako nie produkt!";
      }
      if (items.some((ei) => ei.notProduct && !ei.name?.trim())) {
        return "Uzupełnij nazwę pozycji, która nie jest produktem!";
      }
    }

    return null;
  }

  export  function validateProductForm(
    productForm: Product | NewProduct,
    selectedProduct: Product | null | undefined,
    action: Action,
  ): string | null {
    if (!productForm.name || !productForm.category || !productForm.brand) {
      return "Brak pełnych informacji o produkcie!";
    }

    if (productForm.name && productForm.name.trim().length <= 2) {
      return "Nazwa produktu za krótka! (2+)";
    }
    if (productForm.category?.system && (productForm.sellingPrice == null || productForm.sellingPrice === 0)) {
      return "Uzupełnij cenę sprzedaży Produktu!"
    }
    if (productForm.category?.system && (productForm.unit !== null && (productForm.volume == null || productForm.volume === 0))) {
      return "Uzupełnij objętość Produktu!"
    }
    if (productForm.category?.system && (productForm.volume !== null && productForm.unit == null)) {
      return "Uzupełnij jednostkę Produktu!"
    }
    if (productForm.category?.system && (productForm.fallbackNetPurchasePrice === 0)) {
      return "Cena zakupu Netto nie może być 0!"
    }
    if (productForm.lowStockThreshold != null && productForm.lowStockThreshold < 0) {
      return "Próg niskiego stanu nie może być ujemny!"
    }
    if (action === Action.EDIT && selectedProduct) {
      const noChangesDetected =
      productForm.name === selectedProduct?.name &&
      productForm.category === selectedProduct?.category &&
      productForm.brand === selectedProduct?.brand &&
      (productForm.description ?? "") === (selectedProduct?.description ?? "") &&
      productForm.supply === selectedProduct?.supply &&
      (productForm.lowStockThreshold ?? null) ===
        (selectedProduct?.lowStockThreshold ?? null) &&
      productForm.sellingPrice === selectedProduct?.sellingPrice &&
      productForm.volume === selectedProduct?.volume &&
      productForm.unit === selectedProduct?.unit &&
      productForm.vatRate === selectedProduct?.vatRate &&
      productForm.fallbackNetPurchasePrice === selectedProduct.fallbackNetPurchasePrice &&
      productForm.fallbackVatRate === selectedProduct.fallbackVatRate;

      if (noChangesDetected) {
      return "Brak zmian!";
      }
    }
    return null;
  };

  export function validateKsefSettings(
    ksefSettings: KsefCredentials
  ): string | null {
    if (
      Object.values(ksefSettings).some(
        (value) => value === null || value === undefined
      )
    ) {
      return "Brak pełnych informacji!";
    }

    if(ksefSettings.nip != null && ksefSettings.nip.trim().length !== 10) {
      return "NIP musi składać się z 10 cyfr!";
    }

    return null;
  }

  export function validateKsefVerifyProducts(
    verifyItems: KsefVerifyProductsItem[],
    invoiceItems: KsefInvoiceItem[],
  ): string | null {
    if (verifyItems.length === 0) {
      return "Faktura nie zawiera żadnych pozycji do zaksięgowania!";
    }
    for (const verifyItem of verifyItems) {
      if (verifyItem.productId === null) {
        return "Brak przypisanego produktu!";
      }
      if (!verifyItem.quantity || verifyItem.quantity === 0) {
        return "Wprowadzona ilość nie może wynosić 0!";
      }
      const invoiceItem = invoiceItems.find((i) => i.id === verifyItem.itemId);
      if (invoiceItem && invoiceItem.quantity !== null && verifyItem.quantity < invoiceItem.quantity) {
        return "Ilość produktu nie może być mniejsza niż ilość na fakturze!";
      }
    }
    return null;
  }

  export function validateKsefBookCorrection(
    items: KsefBookCorrectionItem[],
  ): string | null {
    if (items.length === 0) {
      return "Korekta nie zawiera żadnych pozycji!";
    }
    if (items.every((item) => item.productId === null)) {
      return "Przypisz produkt do przynajmniej jednej pozycji!";
    }
    for (const item of items) {
      if (item.productId === null) {
        continue;
      }
      if (item.quantity < 0 || !Number.isInteger(item.quantity)) {
        return "Ilość do zdjęcia musi być liczbą całkowitą (0 lub więcej)!";
      }
    }
    return null;
  }

  export function validateBrandForm(
    brandForm: Brand | NewBrand,
    selectedBrand: Brand | null | undefined,
    action: Action,
  ): string | null {
    if (
      Object.values(brandForm).some(
        (value) => value === null || value === undefined
      )
    ) {
      return "Brak pełnych informacji!";
    }

    if (brandForm.name && brandForm.name.trim().length <= 2) {
        return "Nazwa marki za krótka! (2+)";
      }

    if (action === Action.EDIT && selectedBrand) {
    const noChangesDetected = brandForm.name === selectedBrand.name;

    if (noChangesDetected) {
      return "Brak zmian!";
    }
  }

    return null;
  };

  export function validateReviewForm(
    reviewForm: NewReview | Review,
    action: Action,
    selectedReview: Review | null | undefined,
  ): string | null {
    if(Object.entries(reviewForm).some(([key, value]) => {
      if(action === Action.CREATE && key === "isUsed") return false;
      return value === null || value === undefined
    })) {
      return "Brak pełnych informacji!";
    }
    if(action === Action.EDIT && selectedReview) {
      const noChangesDetected =
      reviewForm.client === selectedReview.client &&
      reviewForm.issueDate === selectedReview.issueDate &&
      reviewForm.source === selectedReview.source;
      if (noChangesDetected) {
      return "Brak zmian!";
    }
    }
    return null;
  }

  export function validateClientNoteForm(
    notes: NewClientNote[],
  ): string | null {
    for(const noteForm of notes) {
      if(noteForm.content?.trim().length < 1) {
        return "Pusta notatka!"
      }
    }
    
    return null;
  }

  export function validateDiscountForm(
    discountForm: NewDiscount | Discount,
    action: Action,
    selectedDiscount: Discount | undefined | null
  ): string | null {
    if(Object.entries(discountForm).some(([key, value]) => {
      if(key === "clients") return false;
      return value === null || value === undefined || value === ""
    })
  ) {
    return "Brak pełnych informacji!";
  }
  if(discountForm.name?.trim().length < 2) {
    return "Nazwa za krótka (2+)!"
  }
  if(discountForm.name?.trim().length > 6) {
    return "Nazwa za długa max 6 znaków!"
  }

  if(discountForm.percentageValue === 0) {
    return "Rabat nie może być 0!"
  }

  if(action === Action.EDIT && selectedDiscount) {
    const formClientIds = (discountForm.clients || []).map((c) => c.id).sort();
    const selectedClientIds = (selectedDiscount.clients || []).map((c) => c.id).sort();

    const sameClients =
      formClientIds.length === selectedClientIds.length &&
      formClientIds.every((id, index) => id === selectedClientIds[index]);

    const noChangesDetected =
    discountForm.name === selectedDiscount.name &&
    discountForm.percentageValue === selectedDiscount.percentageValue &&
    sameClients;
    
  if (noChangesDetected) {
        return "Brak zmian!";
      }
    }
    return null;
  }

  export function validateStatGoalsSettings(settingsForm: NewStatSettings): string | null {
    const goals = [settingsForm.servicesRevenueGoal, settingsForm.productsRevenueGoal];
    if (goals.some((value) => value === null || value === undefined)) {
      return "Brak pełnych informacji!";
    }
    if (goals.some((value) => value === 0)) {
      return "Wartość nie może być 0!";
    }
    return null;
  }

  export function validatePayoutSettings(settingsForm: NewStatSettings): string | null {
    if (settingsForm.bonusThreshold === null || settingsForm.bonusThreshold === undefined) {
      return "Brak pełnych informacji!";
    }
    if (settingsForm.bonusThreshold === 0) {
      return "Wartość nie może być 0!";
    }
    const months = settingsForm.saleBonusPayoutMonths ?? [];
    if (months.length !== 4) {
      return "Wybierz dokładnie 4 miesiące!";
    }
    const firstMonthMod = months[0] % 3;
    if (!months.every((m) => m % 3 === firstMonthMod)) {
      return "Premia kwartalna wybierz co 3 miesiąc!";
    }
    return null;
  }

  export function validateSettingsForm(
  settingsForm: NewAppSettings | AppSettings,
  existingSettings: AppSettings
  ): string | null {
    if(Object.values(settingsForm).some(
      (value) => value === null || value === undefined
    )) {
      return "Brak pełnych informacji!";
    }
    if(Object.values(settingsForm).some(
      (value) => value === 0
    )) {
      return "Wartość nie może być 0!";
    }
    if(!existingSettings) {
      return "Błąd walidacji formularza."
    }
    if(existingSettings) {
      const noChangesDetected =
      settingsForm.voucherExpiryTime === existingSettings.voucherExpiryTime &&
      settingsForm.visitAbsenceRate === existingSettings.visitAbsenceRate &&
      settingsForm.visitVipRate === existingSettings.visitVipRate &&
      settingsForm.boostNetRate === existingSettings.boostNetRate &&
      settingsForm.googleReviewDiscount === existingSettings.googleReviewDiscount;
      if (noChangesDetected) {
        return "Brak zmian!";
      }
    }
    return null;
  }

  export function validateUserForm(
    signupRequest: RegisterRequest
  ): string | null {
    if(Object.values(signupRequest).some(
      (value) => value === null || value === undefined
    )) {
      return "Brak pełnych informacji!";
    }
    if(signupRequest.username.trim().length <= 3) {
      return "Nazwa Użytkownika za krótka! (3+)"
    }
    if(signupRequest.username.trim().length >= 20) {
      return "Nazwa Użytkownika za długa! (max 20 znaków)"
    }
    if(signupRequest.password.trim().length < 10) {
      return "Hasło Użytkownika za krótkie! (10+)"
    }
    if(signupRequest.password.trim().length > 40) {
      return "Hasło Użytkownika za długie! (max 40 znaków)"
    }
    return null;
  }

  export function validateVisitForm(
    visitForm: NewVisit | Visit,
    products?: Product[],
  ): string | null {
    if(visitForm.employee == null || visitForm.client == null || visitForm.date === null ){
      return "Brak pełnych informacji!";
    }
    if(new Date(visitForm.date) > new Date()) {
      return "Data wizyty nie może być późniejsza niż dzisiaj! Dodaj wizyty, które już się odbyły.";
    }
    if(visitForm.items.length === 0 && (visitForm.sale == null || visitForm.sale.items.length === 0) && visitForm.debtRedemptions.length === 0) {
      return "Nie można utworzyć pustej Wizyty!";
    }
    if(visitForm.isVip && visitForm.serviceDiscounts) {
      for(const discount of visitForm.serviceDiscounts) {
        if(discount.type === VisitDiscountType.HAPPY_HOURS ||
          discount.type === VisitDiscountType.CLIENT_DISCOUNT ||
          discount.type === VisitDiscountType.CUSTOM) {
            return "W przypadku Wizyty VIP można zastosować tylko rabat OPINIA GOOGLE!"
          }
        
      }
    }
    if(visitForm.items.length === 0 && visitForm.serviceDiscounts?.some((sd) => sd.type === VisitDiscountType.GOOGLE_REVIEW)){
      return "Rabat OPINIA GOOGLE łączy się tylko z usługami!"
    }
    if(visitForm.sale != null) {
      const productItems = visitForm.sale.items.filter(item => 'product' in item && item.product != null);
      const productCounts = productItems.reduce((acc, item) => {
        const id = item.product!.id;

        acc[id] = (acc[id] || 0) + 1;

        return acc;
      }, {} as Record<number, number>);
      for(const [productId, count] of Object.entries(productCounts)) {
        const availableSupply = products?.find(p => p.id === Number(productId))?.supply;
        if( availableSupply && availableSupply < count) {
          const productName = products?.find(p => p.id === Number(productId))?.name;
          return `Niewystarczająca ilość produktu ${productName} w magazynie! Dostępne: ${availableSupply}`
        }
      }
      
    }
    if(visitForm.absence && visitForm.serviceDiscounts) {
      for(const discount of visitForm.serviceDiscounts) {
        if(discount.type === VisitDiscountType.HAPPY_HOURS ||
          discount.type === VisitDiscountType.GOOGLE_REVIEW ||
          discount.type === VisitDiscountType.CUSTOM) {
            return "W przypadku nieobecności można zastosować tylko stały rabat (klienta)!"
          }
        
      }
    }
    const usedVoucherIds = new Set<number>();

    for(const payment of visitForm.payments) {
      if(!payment.method) {
        return "Brak informacji o metodzie płatności!";
      }
      if(payment.method === PaymentMethod.VOUCHER && !payment.voucher) {
        return "Nie przypisano Vouchera jako metody płatności!";
      }
      if(payment.method === PaymentMethod.VOUCHER && payment.voucher) {
        if(usedVoucherIds.has(payment.voucher.id)) {
          return "Nie można użyć tego samego Vouchera dwukrotnie!";
        }
        usedVoucherIds.add(payment.voucher.id);
      }
    }
    const saleValue = (visitForm.sale?.items ?? []).reduce(
      (sum, item) => sum + (item.price ?? 0), 0);
    const redeemedDebtsValue = (visitForm.debtRedemptions ?? []).reduce(
      (sum, redemption) => sum + (redemption.debtSource?.value ?? 0), 0);
    const paymentsSum = visitForm.payments.reduce(
      (sum, payment) => sum + (payment.amount ?? 0), 0);
    if(paymentsSum < saleValue + redeemedDebtsValue - 0.001) {
      return "Suma płatności nie może być niższa niż wartość produktów i spłacanych zadłużeń!";
    }
    return null;
  }

  export function validateVoucherForm(
    voucherForm: NewVoucher | Voucher,
    action: Action,
    selectedVoucher: Voucher | null | undefined,
  ): string | null {
    if(Object.entries(voucherForm).some(([key, value]) => {
      if(action === Action.CREATE && key === "expiryDate") return false;
      return value === null || value === undefined || value === ""
    })
  ) {
    return "Brak pełnych informacji!";
  }
    if(voucherForm.value === 0) {
      return "Wartość vouchera nie może być = 0!"
    }
    if(action === Action.EDIT && selectedVoucher) {
      const noChangesDetected =
      voucherForm.value === selectedVoucher.value &&
      voucherForm.client === selectedVoucher.client &&
      voucherForm.issueDate === selectedVoucher.issueDate &&
      voucherForm.expiryDate === selectedVoucher.expiryDate;
      if (noChangesDetected) {
        return "Brak zmian!";
      }
    }
    return null;
  }

  export function validateNoSourceClientDebtForm(
    debtForm: NewClientDebt | ClientDebt,
    action: Action,
    selectedDebt: ClientDebt | null | undefined,
  ): string | null {
    if(Object.entries(debtForm).some(
      ([key, value]) =>
        key !== "sourceVisit" && key !== "reason" && (value === null || value === undefined)
    )) {
      return "Brak pełnych informacji!";
    }
    if(debtForm.value === 0) {
      return "Wartość długu nie może być = 0!"
    }
    if(action === Action.EDIT && selectedDebt) {
      const noChangesDetected =
      debtForm.value === selectedDebt.value &&
      debtForm.client === selectedDebt.client &&
      debtForm.type === selectedDebt.type &&
      (debtForm.reason ?? null) === (selectedDebt.reason ?? null);
      if (noChangesDetected) {
      return "Brak zmian!";
    }
    }
    return null;
  }

  export function validateClientForm(
    clientForm: NewClient,
    action: Action,
    selectedClient: Client | null | undefined,
  ): string | null {
    if(Object.entries(clientForm).some(([key, value]) => {
      if (["phoneNumber", "discount", "email", "birthDate", "address", "postalCode", "city"].includes(key)) return false;
      return value === null || value === undefined;
    })) {
      return "Brak pełnych informacji!";
    }
    if(clientForm.firstName.trim().length <= 2) {
      return "Imię klienta za krótkie! (2+)"
    }
    if(clientForm.lastName.trim().length <= 2) {
      return "Nawisko klienta za krótkie! (2+)"
    }

    if(action ===Action.EDIT && selectedClient) {
      const noChangesDetected =
      clientForm.firstName === selectedClient.firstName &&
      clientForm.lastName === selectedClient.lastName &&
      clientForm.boostClient === selectedClient.boostClient &&
      clientForm.signedRegulations === selectedClient.signedRegulations &&
      clientForm.redFlag === selectedClient.redFlag &&
      clientForm.phoneNumber === selectedClient.phoneNumber &&
      (clientForm.birthDate ?? null) === (selectedClient.birthDate ?? null) &&
      clientForm.email === selectedClient.email;

      if (noChangesDetected) {
      return "Brak zmian!";
    }
    }
    return null;
  }

  export function validateClientCardData(data: {
    firstName: string;
    lastName: string;
    birthDate: string | null;
  }): string | null {
    if (data.firstName.trim().length <= 2) {
      return "Imię klienta za krótkie! (2+)";
    }
    if (data.lastName.trim().length <= 2) {
      return "Nazwisko klienta za krótkie! (2+)";
    }
    if (!data.birthDate) {
      return null;
    }
    const birthDate = new Date(data.birthDate);
    if (birthDate.getTime() > Date.now()) {
      return "Data urodzenia nie może być z przyszłości!";
    }
    if (birthDate.getFullYear() < 1900) {
      return "Data urodzenia nie może być wcześniejsza niż 1900!";
    }
    return null;
  }

  export function validateServiceForm(
    serviceForm: BaseService | NewBaseService,
    action: Action,
    selectedService: BaseService | null | undefined,
  ): string | null {
    if(Object.values(serviceForm).some(
      (value) => value === null || value === undefined
    )) {
      return "Brak pełnych informacji!";
    }
    if (serviceForm.variants && serviceForm.variants.length > 0) {
    for (const variant of serviceForm.variants) {
      if (!variant.name || variant.name.trim().length <= 2) {
        return "Nazwa wariantu za krótka! (2+)";
      }
    }
  }
    if (serviceForm.name && serviceForm.name.trim().length <= 2) {
        return "Nazwa usługi za krótka! (2+)";
      }

    if (action === Action.EDIT && selectedService) {

      const variantsEqual =
      (serviceForm.variants?.length ?? 0) ===
        (selectedService.variants?.length ?? 0) &&
      serviceForm.variants.every((v, i) => {
        const sv = selectedService.variants[i];
        return (
          v.name === sv.name &&
          v.price === sv.price &&
          v.duration === sv.duration
        );
      });

    const noChangesDetected =
      serviceForm.name === selectedService.name &&
      serviceForm.category?.id === selectedService.category?.id &&
      serviceForm.duration === selectedService.duration &&
      serviceForm.price === selectedService.price &&
      variantsEqual;

    if (noChangesDetected) {
      return "Brak zmian!";
    }
  }

    return null;
  }

  export function validateCategoryForm(
    categoryForm: ProductCategory | NewProductCategory | BaseServiceCategory | NewBaseServiceCategory,
    selectedCategory: ProductCategory | BaseServiceCategory | null | undefined,
    action: Action,
    fetchedCategories: ProductCategory[] | BaseServiceCategory[],
  ): string | null {
    if (
      Object.values(categoryForm).some(
        (value) => value === null || value === undefined
      )
    ) {
      return "Brak pełnych informacji!";
    }

    if (categoryForm.name && categoryForm.name.trim().length <= 2) {
      return "Nazwa kategorii za krótka! (2+)";
    }

    const nameExists = fetchedCategories.some(
      (cat) => 
        cat.name.toLowerCase().trim() === categoryForm.name?.toLowerCase().trim() &&
        (!selectedCategory || cat.id !== selectedCategory.id)
    );

    if(action === Action.CREATE && nameExists) {
      return "Kategoria o takiej nazwie już istnieje!";
    }

    if (action === Action.EDIT && selectedCategory) {
      const noChangesDetected = 
          categoryForm.name === selectedCategory.name &&
          categoryForm.color === selectedCategory.color;
      
          if (noChangesDetected) {
            return "Brak zmian!";
          }
    }
    return null;
  }

  //helper fnc
  function normalizeRoles(user: JwtUser | User | null | undefined): RoleType[] {
  if (!user) return [];
  
  if ('token' in user) {
    return user.roles;
  }
  
  return user.roles.map((role: Role) => role.name);
}
  //helper fnc
  function areRolesEqual(roles1: RoleType[], roles2: RoleType[]): boolean {
    if (roles1.length !== roles2.length) return false;
    
    const sorted1 = [...roles1].sort();
    const sorted2 = [...roles2].sort();
    
    return sorted1.every((role, index) => role === sorted2[index]);
  }

  export function validateUpdateUser(updatedUser: User, user: JwtUser | User | null | undefined) {

    if(!updatedUser.id || !updatedUser.username || !updatedUser.avatar) {
      return "Brak pełnych danych użytkownika!"
    }
    if(updatedUser.roles.length === 0) {
      return "Użytkownik musi posiadać conajmniej 1 role!"
    }
    const updatedUserRoles = updatedUser.roles.map(r => r.name);
    const currentUserRoles = normalizeRoles(user);

    const isTryingToAddAdmin =
    updatedUserRoles.includes(RoleType.ROLE_ADMIN) &&
    !currentUserRoles.includes(RoleType.ROLE_ADMIN);

  if (isTryingToAddAdmin) {
    return "Nie masz uprawnień do nadania roli ADMIN!";
  }

    const noChangesDetected = 
      updatedUser.avatar === user?.avatar &&
      (updatedUser.employee?.id ?? null) === (user?.employee?.id ?? null) &&
      areRolesEqual(updatedUserRoles, currentUserRoles);
      
    if (noChangesDetected) {
      return "Brak zmian!";
    }

    return null;
  }

  export function validateChangePasswordForm(
    oldPassword: string,
    newPassword: string,
    confirmPassword: string
  ): string | null {
    if(!oldPassword || !newPassword || !confirmPassword) {
      return "Brak pełnych informacji!";
    }
    if(newPassword.length < 10) {
      return "Nowe hasło za krótkie! (10+)";
    }
    if(newPassword.length > 40) {
      return "Nowe hasło za długie! (max 40)";
    }
    if(newPassword !== confirmPassword) {
      return "Nowe hasła nie są identyczne!";
    }

    if(oldPassword === newPassword) {
      return "Nowe hasło musi różnić się od starego!";
    }
    return null;
  }

  export function validateForceChangePasswordForm(
    newPassword: string,
    confirmPassword: string
  ): string | null {
    if(!newPassword || !confirmPassword) {
      return "Brak pełnych informacji!";
    }
    if(newPassword.length < 10) {
      return "Nowe hasło za krótkie! (10+)";
    }
    if(newPassword.length > 40) {
      return "Nowe hasło za długie! (max 40)";
    }
    if(newPassword !== confirmPassword) {
      return "Nowe hasła nie są identyczne!";
    }
    return null;
  }

  export function validateEmployeeForm(
    employeeForm: Employee | NewEmployee,
    selectedEmployee: Employee | null | undefined,
    action: Action,
  ): string | null {
    if(!employeeForm.name || !employeeForm.lastName) {
      return "Brak pełnych informacji!";
    }
    if(employeeForm.name && employeeForm.name.trim().length <= 2) {
      return "Imię za krótkie! (2+)"
    }
    if(employeeForm.lastName && employeeForm.lastName.trim().length <= 2) {
      return "Nazwisko za krótkie! (2+)"
    }
    if(action === Action.EDIT && selectedEmployee) {
      const noChangesDetected = 
      employeeForm.name === selectedEmployee.name && 
      employeeForm.lastName === selectedEmployee.lastName &&
      employeeForm.employmentType === selectedEmployee.employmentType &&
      employeeForm.bonusPercent === selectedEmployee.bonusPercent &&
      employeeForm.saleBonusPercent === selectedEmployee.saleBonusPercent;
    if (noChangesDetected) {
      return "Brak zmian!";
    }   
  }
  return null;
  }

  export function validateInventoryReportForm(
    inventoryReportItems: InventoryReportItem[] | NewInventoryReportItem[],
    action: Action,
    selectedItems: InventoryReportItem[] | null,
  ): string | null {
    if (inventoryReportItems.length === 0) {
      return "Dodaj przynajmniej jeden produkt";
    }
    const hasUnchanged = inventoryReportItems.some(
      (item) => item.supplyBefore === item.supplyAfter
    );
    if (hasUnchanged) {
      return "Jeden z produktów ma te same wartosci przed i po!";
    }
    if (action === Action.EDIT && selectedItems) {
      const hasChanges =
        inventoryReportItems.length !== selectedItems.length ||
        inventoryReportItems.some((item, index) => {
          const selected = selectedItems[index];
          if (!selected) return true;
          return (
            item.product.id !== selected.product.id ||
            item.supplyBefore !== selected.supplyBefore ||
            item.supplyAfter !== selected.supplyAfter
          );
        });
      if (!hasChanges) {
        return "Brak zmian!";
      }
    }
    return null;
  }

  export function validateUsageRecordsForm(
    usageRecordItems: UsageRecordItem[],
    sharedFields: { employee: Employee | null; usageDate: string },
    hasSupplyError: boolean = false
  ): string | null {
    if (!sharedFields.employee) {
      return "Wybierz pracownika";
    }

    if (usageRecordItems.length === 0) {
      return "Dodaj przynajmniej jeden produkt";
    }

    if (hasSupplyError) {
      return "Ilość jednego lub więcej produktów przekracza dostępny stan magazynowy";
    }

    const invalidItems = usageRecordItems.filter((item) => item.quantity <= 0);
    if (invalidItems.length > 0) {
      return "Wszystkie produkty muszą mieć ilość większą niż 0";
    }

    const exceededItems = usageRecordItems.filter(
      (item) => item.quantity > item.product.supply
    );
    if (exceededItems.length > 0) {
      const productNames = exceededItems
        .map((item) => item.product.name)
        .join(", ");
      return `Ilość przekracza dostępny stan dla: ${productNames}`;
    }

    return null;
  }