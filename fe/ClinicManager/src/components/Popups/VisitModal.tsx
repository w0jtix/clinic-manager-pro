import React, { useEffect, useState, useCallback, useMemo, useRef } from "react";
import {
  Receipt,
  UserX,
  Clock,
  Rocket,
  Crown,
  CirclePlus,
  Ticket,
  TriangleAlert,
  Trash2,
  Info,
} from "lucide-react";
import Modal from "../Modal";
import DropdownSelect, { DropdownItem } from "../DropdownSelect";
// import DateInput from "../DateInput";
import WheelDateField from "../WheelDateField";
import DigitInput from "../DigitInput";
import CostInput from "../CostInput";
import TextInput from "../TextInput";
import SearchBar from "../SearchBar";
import ActionButton from "../ActionButton";
import DataList, { Column } from "../DataList";
import CartServiceDataList from "../Visit/CartServiceDataList";
import CartProductDataList from "../Visit/CartProductDataList";
import ServicePickerModal, { ServicePick } from "./ServicePickerModal";
import ProductPickerModal from "./ProductPickerModal";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRoles } from "../../hooks/useRoles";
import CartDebtDataList from "../Visit/CartDebtDataList";
import ClientModal from "./ClientModal";
import ServiceVariantsModal from "./ServiceVariantsModal";
import AvailableVouchersModal from "./AvailableVouchersModal";
import ProductVatTag, { MedicalMark } from "../ProductVatTag";
import { Employee } from "../../models/employee";
import { Client } from "../../models/client";
import { BaseService, ServiceVariant, ServiceFilterDTO } from "../../models/service";
import { BaseServiceCategory } from "../../models/categories";
import { Product, ProductFilterDTO } from "../../models/product";
import { ClientDebt } from "../../models/debt";
import { DiscountSettings } from "../../models/app_settings";
import { PaymentMethod } from "../../models/payment";
import { Voucher } from "../../models/voucher";
import { NewSale, NewSaleItem } from "../../models/sale";
import {
  Visit,
  NewVisit,
  NewVisitItem,
  VisitDiscountType,
  discountLabelFor,
} from "../../models/visit";
import { useUser } from "../User/UserProvider";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import EmployeeService from "../../services/EmployeeService";
import ClientService from "../../services/ClientService";
import ClientDebtService from "../../services/ClientDebtService";
import AppSettingsService from "../../services/AppSettingsService";
import BaseServiceService from "../../services/BaseServiceService";
import BaseServiceCategoryService from "../../services/BaseServiceCategoryService";
import AllProductService from "../../services/AllProductService";
import VisitService from "../../services/VisitService";
import { validateVisitForm } from "../../utils/validators";
import { translatePaymentMethod } from "../../utils/paymentUtils";

export interface VisitModalProps {
  onClose: () => void;
  onReset?: () => void;
  preselectedClientId?: number | null;
  className?: string;
}

export function VisitModal({
  onClose,
  onReset,
  preselectedClientId = null,
  className = "",
}: VisitModalProps) {
  const { user } = useUser();
  const { showAlert } = useAlert();
  const { isMobile, isCompact, isNarrow } = useBreakpoint();
  const { isAdmin, isCompany, isMaster } = useRoles();
  const canManageReceipt = isAdmin || isCompany || isMaster;

  const [catalogTab, setCatalogTab] = useState<"services" | "products">(
    "services"
  );
  const [services, setServices] = useState<BaseService[]>([]);
  const [serviceFilter, setServiceFilter] = useState<ServiceFilterDTO>({
    categoryIds: null,
    keyword: "",
  });
  const [selectedService, setSelectedService] = useState<BaseService | null>(
    null
  );
  const [serviceCategories, setServiceCategories] = useState<
    BaseServiceCategory[]
  >([]);
  const [selectedServiceCategories, setSelectedServiceCategories] = useState<
    BaseServiceCategory[]
  >([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [productFilter, setProductFilter] = useState<ProductFilterDTO>({
    categoryIds: [1],
    keyword: "",
  });
  const [page, setPage] = useState<number>(0);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);

  const [discountSettings, setDiscountSettings] =
    useState<DiscountSettings | null>(null);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [chosenServices, setChosenServices] = useState<NewVisitItem[]>([]);
  const [chosenProducts, setChosenProducts] = useState<NewSaleItem[]>([]);
  const [selectedDebtIds, setSelectedDebtIds] = useState<number[]>([]);
  const [isDelayed, setIsDelayed] = useState<boolean>(false);
  const [clientDebts, setClientDebts] = useState<ClientDebt[]>([]);
  const [customDiscountValue, setCustomDiscountValue] = useState<number | null>(
    null
  );
  const [voucherSelectionPopup, setVoucherSelectionPopup] =
    useState<boolean>(false);
  const [voucherPaymentIndex, setVoucherPaymentIndex] = useState<number | null>(
    null
  );
  const [visitPreview, setVisitPreview] = useState<Visit | null>(null);
  const [pendingService, setPendingService] = useState<BaseService | null>(
    null
  );
  const [isServicePickerOpen, setIsServicePickerOpen] =
    useState<boolean>(false);
  const [isDebtConfirmOpen, setIsDebtConfirmOpen] = useState<boolean>(false);
  const [dismissedAutoDiscounts, setDismissedAutoDiscounts] = useState<VisitDiscountType[]>([]);
  const wasAllFrozenRef = useRef<boolean>(false);
  const [isProductPickerOpen, setIsProductPickerOpen] =
    useState<boolean>(false);
  const [variantsAddOnsPopupOpen, setVariantsAddOnsPopupOpen] =
    useState<boolean>(false);
  const [allItemsHaveFinalPrice, setAllItemsHaveFinalPrice] =
    useState<boolean>(false);

  const [visitDTO, setVisitDTO] = useState<NewVisit>({
    employee: user?.employee ?? null,
    client: null,
    serviceDiscounts: [],
    receipt: true,
    isBoost: false,
    isVip: false,
    delayTime: null,
    absence: false,
    items: [],
    sale: null,
    debtRedemptions: [],
    date: new Date().toISOString().split("T")[0],
    payments: [{ method: null, amount: 0 }],
    notes: null,
  });

  const clientWithDebt = clientDebts.length > 0;

  const fetchEmployees = async () => {
    EmployeeService.getAllEmployees()
      .then((data) => setEmployees(data))
      .catch((error) => {
        setEmployees([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching employees:", error);
      });
  };
  const fetchClients = async (): Promise<void> => {
    ClientService.getClients()
      .then((data) => {
        const sortedClients = [...data].sort((a, b) =>
          a.firstName.localeCompare(b.firstName, "pl", { sensitivity: "base" })
        );
        setClients(sortedClients);
      })
      .catch((error) => {
        setClients([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Clients: ", error);
      });
  };
  const fetchDiscountSettings = async () => {
    AppSettingsService.getDiscountSettings()
      .then((data) => setDiscountSettings(data))
      .catch((error) => {
        setDiscountSettings(null);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching DiscountSettings:", error);
      });
  };
  const fetchServiceCategories = async (): Promise<void> => {
    BaseServiceCategoryService.getCategories()
      .then((data) => setServiceCategories(data))
      .catch((error) => {
        setServiceCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching service categories:", error);
      });
  };
  const handleFilterServicesByCategory = useCallback(
    (categories: BaseServiceCategory[] | null) => {
      const selected = categories ?? [];
      setSelectedServiceCategories(selected);
      setServiceFilter((prev) => ({
        ...prev,
        categoryIds: selected.length > 0 ? selected.map((c) => c.id) : null,
      }));
    },
    []
  );
  const fetchServices = async (): Promise<void> => {
    BaseServiceService.getServices(serviceFilter)
      .then((data) => {
        const sorted = data.sort(
          (a, b) => (a.category?.id || 0) - (b.category?.id || 0)
        );
        setServices(sorted);
      })
      .catch((error) => {
        setServices([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Services: ", error);
      });
  };
  const fetchProducts = async (
    pageNum: number = 0,
    append: boolean = false
  ): Promise<void> => {
    AllProductService.getProducts(productFilter)
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

  const getVisitPreview = async () => {
    VisitService.getVisitPreview(visitDTO as NewVisit)
      .then((data) => setVisitPreview(data))
      .catch((error) => {
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Visit preview:", error);
      });
  };

  const handleServiceKeywordChange = useCallback((newKeyword: string) => {
    setServiceFilter((prev) => ({ ...prev, keyword: newKeyword }));
  }, []);
  const handleProductKeywordChange = useCallback((newKeyword: string) => {
    setProductFilter((prev) => ({ ...prev, keyword: newKeyword }));
  }, []);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const target = e.currentTarget;
      const scrolledToBottom =
        target.scrollHeight - target.scrollTop <= target.clientHeight + 100;
      if (scrolledToBottom && hasMore && !loading) {
        fetchProducts(page + 1, true);
      }
    },
    [hasMore, loading, page, productFilter]
  );

  useEffect(() => {
    fetchEmployees();
    fetchClients();
    fetchDiscountSettings();
    fetchServiceCategories();
    fetchServices();
    fetchProducts(0, false);
  }, []);

  useEffect(() => {
    fetchServices();
  }, [serviceFilter]);

  useEffect(() => {
    fetchProducts(0, false);
    setPage(0);
    setHasMore(true);
  }, [productFilter]);

  useEffect(() => {
    getVisitPreview();
  }, [visitDTO]);

  useEffect(() => {
    setDismissedAutoDiscounts([]);
    if (visitDTO.client) {
      ClientDebtService.getUnpaidDebtsByClientId(visitDTO.client.id)
        .then((data) => setClientDebts(data))
        .catch(() => setClientDebts([]));

      if (
        !visitDTO.isVip &&
        visitDTO.client.discount != null &&
        !allItemsHaveFinalPrice
      ) {
        toggleDiscount(VisitDiscountType.CLIENT_DISCOUNT);
      }
      if (
        !visitDTO.absence &&
        visitDTO.client.hasActiveGoogleReview &&
        !allItemsHaveFinalPrice
      ) {
        toggleDiscount(VisitDiscountType.GOOGLE_REVIEW);
      }
    } else {
      setClientDebts([]);
    }
  }, [visitDTO.client]);

  useEffect(() => {
    if (visitDTO.isVip) {
      setVisitDTO((prev) => ({
        ...prev,
        serviceDiscounts: prev.serviceDiscounts?.filter(
          (d) => d.type === VisitDiscountType.GOOGLE_REVIEW
        ),
      }));
      setChosenServices((prev) =>
        prev.map((item) => ({ ...item, finalPrice: null }))
      );
    }
  }, [visitDTO.isVip]);

  useEffect(() => {
    if (visitDTO.absence) {
      setVisitDTO((prev) => ({
        ...prev,
        serviceDiscounts: prev.serviceDiscounts?.filter(
          (d) => d.type === VisitDiscountType.CLIENT_DISCOUNT
        ),
        payments: [],
      }));
      setChosenServices((prev) =>
        prev.map((item) => ({ ...item, finalPrice: null }))
      );
    }
  }, [visitDTO.absence]);

  useEffect(() => {
    if (selectedProduct && !visitDTO.absence) {
      const availableSupply = products?.find(
        (p) => p.id === selectedProduct.id
      )?.supply;
      const currentlyChosenQty = chosenProducts.filter(
        (sp) => sp.product?.id === selectedProduct.id
      ).length;
      if (currentlyChosenQty >= availableSupply!) {
        showAlert("Niewystarczająca ilość w magazynie!", AlertType.INFO);
      }

      const newSaleItem: NewSaleItem = {
        product: selectedProduct,
        price: selectedProduct.sellingPrice ?? 0,
      };
      setChosenProducts((prev) => [...prev, newSaleItem]);
      setSelectedProduct(null);
    }
  }, [selectedProduct]);

  useEffect(() => {
    if (!selectedService) return;

    if (selectedService.variants.length > 0) {
      setPendingService(selectedService);
      setVariantsAddOnsPopupOpen(true);
      setSelectedService(null);
      return;
    }

    const newVisitItem: NewVisitItem = {
      service: selectedService,
      boostItem: visitDTO.isBoost,
    };
    setChosenServices((prev) => [...prev, newVisitItem]);
    setSelectedService(null);
  }, [selectedService]);

  useEffect(() => {
    setVisitDTO((prev) => ({ ...prev, items: chosenServices }));
    if (chosenServices.length > 0) {
      const allHaveFinalPrice = chosenServices.every(
        (item) => item.finalPrice !== undefined && item.finalPrice !== null
      );
      setAllItemsHaveFinalPrice(allHaveFinalPrice);
    } else {
      setAllItemsHaveFinalPrice(false);
    }
  }, [chosenServices]);

  useEffect(() => {
    if (allItemsHaveFinalPrice) {
      wasAllFrozenRef.current = true;
      setVisitDTO((prev) => ({ ...prev, serviceDiscounts: [] }));
      return;
    }
    if (!wasAllFrozenRef.current) return;
    wasAllFrozenRef.current = false;
    setVisitDTO((prev) => {
      const current = prev.serviceDiscounts ?? [];
      const restored = [...current];
      const restore = (type: VisitDiscountType, allowed: boolean) => {
        if (
          allowed &&
          !dismissedAutoDiscounts.includes(type) &&
          !restored.some((d) => d.type === type)
        ) {
          restored.push({ type });
        }
      };
      restore(
        VisitDiscountType.CLIENT_DISCOUNT,
        !prev.isVip && prev.client?.discount != null
      );
      restore(
        VisitDiscountType.GOOGLE_REVIEW,
        !prev.absence && prev.client?.hasActiveGoogleReview === true
      );
      return { ...prev, serviceDiscounts: restored };
    });
  }, [allItemsHaveFinalPrice]);

  const handleDiscountClick = (type: VisitDiscountType) => {
    const isActive = (visitDTO.serviceDiscounts ?? []).some((d) => d.type === type);
    setDismissedAutoDiscounts((prev) =>
      isActive
        ? prev.includes(type) ? prev : [...prev, type]
        : prev.filter((t) => t !== type)
    );
    toggleDiscount(type);
  };

  useEffect(() => {
    const newSale: NewSale = { items: chosenProducts };
    setVisitDTO((prev) => ({ ...prev, sale: newSale }));
    if (chosenProducts.length === 0) {
      setVisitDTO((prev) => ({ ...prev, sale: null }));
    }
  }, [chosenProducts]);

  const handleVisitAction = useCallback(async () => {
    setIsDebtConfirmOpen(false);
    try {
      await VisitService.createVisit(visitDTO as NewVisit);
      showAlert(`Wizyta utworzona pomyślnie!`, AlertType.SUCCESS);
      onReset?.();
      onClose();
    } catch (error) {
      showAlert(`Błąd podczas tworzenia wizyty!`, AlertType.ERROR);
      console.error("Error creating visit:", error);
    }
  }, [visitDTO, showAlert, onReset, onClose]);

  const handleVisitDate = useCallback((visitDate: string | null) => {
    if (visitDate) setVisitDTO((prev) => ({ ...prev, date: visitDate }));
  }, []);

  const handleBoostVisit = useCallback(() => {
    setVisitDTO((prev) => {
      const newIsBoost = !prev.isBoost;
      setChosenServices((prevServices) =>
        prevServices.map((item) => ({ ...item, boostItem: newIsBoost }))
      );
      return { ...prev, isBoost: newIsBoost };
    });
  }, []);
  const handleVipVisit = useCallback(() => {
    setVisitDTO((prev) => ({ ...prev, isVip: !prev.isVip }));
  }, []);
  const handleAbsence = useCallback(() => {
    setVisitDTO((prev) => ({
      ...prev,
      absence: !prev.absence,
      payments:
        prev.absence && prev.payments.length === 0
          ? [{ method: null, amount: 0 }]
          : prev.payments,
    }));
    setChosenProducts([]);
  }, []);
  const handleReceipt = useCallback(() => {
    setVisitDTO((prev) => ({ ...prev, receipt: !prev.receipt }));
  }, []);
  const handleDelay = () => {
    setVisitDTO((prev) => ({
      ...prev,
      delayTime: prev.delayTime ? null : prev.delayTime,
    }));
    setIsDelayed((prev) => {
      const newValue = !prev;
      if (!newValue) setVisitDTO((v) => ({ ...v, delayTime: null }));
      return newValue;
    });
  };
  const handleDelayTime = useCallback((time: number | null) => {
    setVisitDTO((prev) => ({ ...prev, delayTime: time }));
  }, []);

  const handleEmployeeSelect = useCallback(
    (employee: Employee | Employee[] | null) => {
      const selectedEmployee = Array.isArray(employee) ? employee[0] : employee;
      setVisitDTO((prev) => ({ ...prev, employee: selectedEmployee ?? null }));
    },
    []
  );
  const handleClientChange = useCallback(
    (client: Client | Client[] | null) => {
      const selectedClient = Array.isArray(client) ? client[0] : client;
      setVisitDTO((prev) => ({
        ...prev,
        client: selectedClient ?? null,
        serviceDiscounts: [],
        debtRedemptions: [],
      }));
      fetchClients();
    },
    []
  );

  useEffect(() => {
    if (!preselectedClientId || clients.length === 0 || visitDTO.client) return;
    const match = clients.find((c) => c.id === preselectedClientId);
    if (match) {
      setVisitDTO((prev) => ({ ...prev, client: match }));
    }
  }, [preselectedClientId, clients, visitDTO.client]);

  const handleDebtRedemption = useCallback((clientDebt: ClientDebt) => {
    setVisitDTO((prev) => {
      const list = prev.debtRedemptions ?? [];
      const exists = list.some((dr) => dr.debtSource.id === clientDebt.id);
      const updated = exists
        ? list.filter((dr) => dr.debtSource.id !== clientDebt.id)
        : [...list, { debtSource: clientDebt }];
      return { ...prev, debtRedemptions: updated };
    });
    setSelectedDebtIds((prev) =>
      prev.includes(clientDebt.id)
        ? prev.filter((id) => id !== clientDebt.id)
        : [...prev, clientDebt.id]
    );
  }, []);

  const getAvailableDiscountTypes = () => {
    const types = [VisitDiscountType.HAPPY_HOURS, VisitDiscountType.CUSTOM];
    if (visitDTO.client?.discount != null) {
      types.push(VisitDiscountType.CLIENT_DISCOUNT);
    }
    if (visitDTO.client?.hasActiveGoogleReview === true) {
      types.push(VisitDiscountType.GOOGLE_REVIEW);
    }
    const customIndex = types.indexOf(VisitDiscountType.CUSTOM);
    if (customIndex > -1) {
      types.splice(customIndex, 1);
      types.push(VisitDiscountType.CUSTOM);
    }
    return types;
  };
  const toggleDiscount = useCallback(
    (type: VisitDiscountType) => {
      setVisitDTO((prev) => {
        const current = prev.serviceDiscounts ?? [];
        const exists = current.some((d) => d.type === type);
        if (exists) {
          if (type === VisitDiscountType.CUSTOM) setCustomDiscountValue(null);
          return {
            ...prev,
            serviceDiscounts: current.filter((d) => d.type !== type),
          };
        }
        if (allItemsHaveFinalPrice) {
          showAlert(
            "Rabat bez efektu, wszystkie ceny usług są zamrożone!",
            AlertType.INFO
          );
          return prev;
        }
        return { ...prev, serviceDiscounts: [...current, { type }] };
      });
    },
    [allItemsHaveFinalPrice]
  );
  const handleCustomDiscountValueChange = (value: number | null) => {
    const numericValue = value ?? 0;
    setCustomDiscountValue(numericValue);
    setVisitDTO((prev) => {
      const discounts = prev.serviceDiscounts ?? [];
      const customIndex = discounts.findIndex(
        (d) => d.type === VisitDiscountType.CUSTOM
      );
      let updatedDiscounts = discounts;
      if (customIndex >= 0) {
        updatedDiscounts = discounts.map((d, i) =>
          i === customIndex ? { ...d, percentageValue: numericValue } : d
        );
      }
      return { ...prev, serviceDiscounts: updatedDiscounts };
    });
  };

  const paymentItems: DropdownItem[] = Object.values(PaymentMethod).map(
    (method) => ({ id: method, name: translatePaymentMethod(method) })
  );
  const handlePaymentMethodChange = (
    index: number,
    val: DropdownItem | DropdownItem[] | null
  ) => {
    const value = Array.isArray(val) ? val[0] : val;
    setVisitDTO((prev) => {
      const payments = [...prev.payments];
      const newMethod = (value?.id as PaymentMethod) ?? null;
      payments[index] = { ...payments[index], method: newMethod, voucher: null };
      return { ...prev, payments };
    });
  };
  const handlePaymentAmount = (index: number, value: number | null) => {
    setVisitDTO((prev) => {
      const payments = [...prev.payments];
      payments[index].amount = (value as number) ?? 0;
      return { ...prev, payments };
    });
  };
  const handleAssignVoucher = (index: number, voucher: Voucher | null) => {
    setVisitDTO((prev) => {
      const payments = [...prev.payments];
      payments[index] = {
        ...payments[index],
        voucher: voucher,
        amount: voucher ? voucher.value : 0,
      };
      return { ...prev, payments };
    });
  };
  const getRemainingAmount = (index: number): number => {
    const total = visitPreview?.totalValue ?? 0;
    const otherSum = visitDTO.payments.reduce(
      (sum, p, i) => (i !== index ? sum + (p.amount ?? 0) : sum),
      0
    );
    return Math.max(0, Math.round((total - otherSum) * 100) / 100);
  };
  const addPayment = useCallback(() => {
    setVisitDTO((prev) => ({
      ...prev,
      payments: [...prev.payments, { method: null, amount: 0 }],
    }));
  }, []);
  const handlePaymentRemove = useCallback((index: number) => {
    setVisitDTO((prev) => ({
      ...prev,
      payments: prev.payments.filter((_, i) => i !== index),
    }));
  }, []);

  const handleNotes = useCallback((note: string) => {
    setVisitDTO((prev) => ({ ...prev, notes: note }));
  }, []);

  const handleServicePicks = useCallback(
    (picks: ServicePick[]) => {
      if (picks.length === 0) return;
      const newItems: NewVisitItem[] = picks.map((pick) => ({
        service: pick.service,
        serviceVariant: pick.variant ?? undefined,
        boostItem: visitDTO.isBoost,
      }));
      setChosenServices((prev) => [...prev, ...newItems]);
    },
    [visitDTO.isBoost]
  );

  const handleProductPicks = useCallback(
    (pickedProducts: Product[]) => {
      if (pickedProducts.length === 0) return;
      const exceedsSupply = pickedProducts.some((product) => {
        const availableSupply =
          products?.find((p) => p.id === product.id)?.supply ?? 0;
        const currentlyChosenQty = chosenProducts.filter(
          (sp) => sp.product?.id === product.id
        ).length;
        return currentlyChosenQty + 1 > availableSupply;
      });
      if (exceedsSupply) {
        showAlert("Niewystarczająca ilość w magazynie!", AlertType.INFO);
      }
      const newItems: NewSaleItem[] = pickedProducts.map((product) => ({
        product: product,
        price: product.sellingPrice ?? 0,
      }));
      setChosenProducts((prev) => [...prev, ...newItems]);
    },
    [products, chosenProducts, showAlert]
  );

  const handleVariantSelect = (variant: ServiceVariant | null) => {
    if (!pendingService) return;
    const newItem: NewVisitItem = {
      service: pendingService,
      serviceVariant: variant,
      boostItem: visitDTO.isBoost,
    };
    setChosenServices((prev) => [...prev, newItem]);
    setPendingService(null);
  };
  const handleRemoveServiceByIndex = useCallback((index: number) => {
    setChosenServices((prev) => prev.filter((_, i) => i !== index));
  }, []);
  const onManageBoostByIndex = useCallback((index: number) => {
    setChosenServices((prev) =>
      prev.map((item, i) =>
        i !== index ? item : { ...item, boostItem: item.boostItem ? false : true }
      )
    );
  }, []);
  const freezePrice = useCallback((index: number) => {
    setChosenServices((prev) =>
      prev.map((item, i) => {
        if (i !== index) return item;
        const isFrozen =
          item.finalPrice !== undefined && item.finalPrice !== null;
        const isVariant = item.serviceVariant != null;
        return {
          ...item,
          finalPrice: isFrozen
            ? undefined
            : isVariant
            ? item.serviceVariant?.price
            : item.service?.price ?? 0,
        };
      })
    );
  }, []);
  const handleRemoveProductByIndex = useCallback((index: number) => {
    setChosenProducts((prev) => prev.filter((_, i) => i !== index));
  }, []);
  const handleSaleItemPriceChange = useCallback(
    (index: number, value: number) => {
      setChosenProducts((prev) =>
        prev.map((item, i) => {
          if (i !== index) return item;
          if ("product" in item) return { ...item, price: value };
          if ("voucher" in item) {
            return { ...item, price: value, voucher: { value: value } };
          }
          return item;
        })
      );
    },
    []
  );
  const addVoucherToSaleItems = useCallback(() => {
    if (!visitDTO.absence) {
      const newSaleItem: NewSaleItem = { voucher: { value: 0 }, price: 0 };
      setChosenProducts((prev) => [...prev, newSaleItem]);
    }
  }, [visitDTO]);

  const serviceCatalogColumns = useMemo<Column<BaseService>[]>(
    () => [
      {
        key: "Nazwa",
        grow: true,
        render: (item) => (
          <span className="flex align-items-center g-05 dl-name">
            <span
              className="dl-dot"
              style={{
                backgroundColor: item.category?.color
                  ? `rgb(${item.category.color})`
                  : undefined,
                boxShadow: item.category?.color
                  ? `0 0 6px rgba(${item.category.color}, 0.5)`
                  : undefined,
              }}
            />
            {item.name}
          </span>
        ),
      },
      {
        key: "Cena",
        width: "5rem",
        align: "center",
        render: (item) => <span className="sv-price">{item.price} zł</span>,
      },
    ],
    []
  );

  const productCatalogColumns = useMemo<Column<Product>[]>(
    () => [
      {
        key: "Nazwa",
        grow: true,
        render: (item) => (
          <span className="flex align-items-center g-05 dl-name">
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
            <MedicalMark vatRate={item.vatRate} />
            {item.name}
            <ProductVatTag vatRate={item.vatRate} />
          </span>
        ),
      },
      {
        key: "Cena",
        width: "5rem",
        align: "center",
        render: (item) =>
          item.sellingPrice ? <span className="vm-product-price">{item.sellingPrice} zł</span> : null,
      },
      {
        key: "Stan",
        width: "4rem",
        align: "center",
        render: (item) => <span className="dl-dim vm-supply">{item.supply}</span>,
      },
    ],
    []
  );

  const discountMode =
    visitDTO.isVip ||
    visitDTO.absence ||
    (visitDTO.serviceDiscounts?.length ?? 0) > 0;

  const visitTotal = visitPreview?.totalValue ?? 0;
  const paidTotal =
    Math.round(visitDTO.payments.reduce((sum, p) => sum + (p.amount ?? 0), 0) * 100) / 100;
  const newDebt = visitDTO.absence
    ? 0
    : Math.round((visitTotal - paidTotal) * 100) / 100;

  const handleSaveClick = () => {
    const error = validateVisitForm(visitDTO, products);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    if (newDebt > 0) {
      setIsDebtConfirmOpen(true);
      return;
    }
    handleVisitAction();
  };

  const vmFooter = (
    <div className="vm-footer flex align-items-center justify-end g-1 width-max">
      <div className="flex align-items-center g-05">
        <span className="qv-span totals">Do zapłaty:</span>
        <span className="qv-span totals">{visitTotal} zł</span>
      </div>
      <ActionButton
        text="Zapisz"
        className="modal-button"
        onClick={handleSaveClick}
      />
    </div>
  );

  const debtBadge = clientWithDebt && (
    <span className="vm-debt-badge flex align-items-center g-05" title="Klient zadłużony">
      <TriangleAlert className="dl-icon-1 c-red" />
      Klient Zadłużony
    </span>
  );

  const visitHeader = (
    <>
      <div className="vm-topbar flex align-items-center g-1">
        <div className="flex align-items-center g-05">
          <span className="qv-span">Podolog:</span>
          <DropdownSelect<Employee>
            items={employees}
            onChange={handleEmployeeSelect}
            value={visitDTO?.employee}
            placeholder="Nie wybrano"
            searchable={false}
            multiple={false}
            allowNew={false}
          />
        </div>
        <div className="flex align-items-center g-05">
          <span className="qv-span">Data:</span>
          {/* <DateInput
            onChange={handleVisitDate}
            selectedDate={visitDTO.date ?? new Date()}
          /> */}
          <WheelDateField
            title="Data wizyty"
            value={visitDTO.date ?? new Date()}
            onChange={handleVisitDate}
          />
        </div>
        <div className="vm-client flex align-items-center g-05 f-1">
          <span className="qv-span">Klient:</span>
          <DropdownSelect<Client>
            items={clients}
            onChange={handleClientChange}
            value={visitDTO?.client}
            getItemLabel={(c) => `${c.firstName} ${c.lastName}`}
            placeholder="Nie wybrano"
            searchable={true}
            multiple={false}
            className="clients"
            showNewPopup={true}
            newItemComponent={ClientModal as React.ComponentType<any>}
            newItemLabel="Dodaj klienta"
            newItemProps={{ onSelectClient: handleClientChange }}
          />
          {isNarrow && debtBadge}
        </div>
        {!isNarrow && debtBadge}
      </div>
      <div className="vm-checkers flex align-items-center">
        <div className="cat-seg-group flex align-items-center width-fit-content g-025">
          {canManageReceipt && (
            <button
              type="button"
              title="Klient otrzymał Paragon"
              className={`cat-seg vm-seg-receipt align-items-center pointer ${visitDTO.receipt ? "active" : ""}`}
              onClick={handleReceipt}
            >
              <Receipt className="dl-icon-1" />
              Paragon
            </button>
          )}
          <button
            type="button"
            className={`cat-seg vm-seg-boost align-items-center pointer ${visitDTO.isBoost ? "active" : ""}`}
            onClick={handleBoostVisit}
          >
            <Rocket className="dl-icon-1 icon-rocket" />
            Boost
          </button>
          <button
            type="button"
            className={`cat-seg vm-seg-vip align-items-center pointer ${visitDTO.isVip ? "active" : ""}`}
            onClick={handleVipVisit}
          >
            <Crown className="dl-icon-1 c-blue" />
            VIP
          </button>
          <button
            type="button"
            className={`cat-seg vm-seg-absence align-items-center pointer ${visitDTO.absence ? "active" : ""}`}
            onClick={handleAbsence}
          >
            <UserX className="dl-icon-1 c-red" />
            Nieobecność
          </button>
          <div className={`cat-seg vm-seg-expand vm-seg-delay align-items-center ${isDelayed ? "active" : ""}`}>
            <button type="button" className="vm-seg-btn flex align-items-center pointer" onClick={handleDelay}>
              <Clock className="dl-icon-1 icon-delay" />
              Spóźnienie
            </button>
            {isDelayed && (
              <span className="vm-seg-value flex align-items-center g-025">
                <DigitInput
                  onChange={handleDelayTime}
                  min={1}
                  max={120}
                  value={visitDTO.delayTime ?? 0}
                  className="in-xs"
                />
                <span className="qv-span f10">min</span>
              </span>
            )}
          </div>
        </div>
      </div>
    </>
  );

  return (
    <Modal
      dirtyValue={[visitDTO, chosenServices, chosenProducts, selectedDebtIds, isDelayed, customDiscountValue]}
      title="Nowa Wizyta"
      onClose={onClose}
      size="full"
      height="full"
      bodyClassName="nested"
      className={`vm-modal ${className}`}
      overlayClassName="vm-overlay"
      footer={isMobile ? vmFooter : undefined}
    >
      {isMobile && visitHeader}

      <div className="vm-workspace flex g-1 f-1 min-height-0">
        {!isMobile && (
        <div className="vm-catalog flex-column min-height-0">
          {visitHeader}
          <div className="vm-catalog-panel flex-column f-1 min-height-0">
            <div className="vm-catalog-head flex-column g-075">
              <div className="cat-seg-group vm-tabs flex align-items-center align-self-center width-fit-content g-025">
                <button
                  type="button"
                  className={`cat-seg vm-tab align-items-center pointer ${
                    catalogTab === "services" ? "active" : ""
                  }`}
                  onClick={() => setCatalogTab("services")}
                >
                  Usługi
                </button>
                <button
                  type="button"
                  className={`cat-seg vm-tab align-items-center pointer ${
                    catalogTab === "products" ? "active" : ""
                  }`}
                  onClick={() => setCatalogTab("products")}
                >
                  Produkty
                </button>
              </div>

              <div className="flex align-items-center space-between g-05">
                {catalogTab === "services" ? (
                  <>
                    <SearchBar
                      onKeywordChange={handleServiceKeywordChange}
                      resetTriggered={false}
                      placeholder="Szukaj usługi..."
                      className="pricelist"
                    />
                    <DropdownSelect<BaseServiceCategory>
                      items={serviceCategories}
                      value={selectedServiceCategories}
                      onChange={(selected) =>
                        handleFilterServicesByCategory(
                          selected
                            ? Array.isArray(selected)
                              ? selected
                              : [selected]
                            : null
                        )
                      }
                      placeholder="Kategorie"
                      searchable={false}
                      allowNew={false}
                      allowColors={true}
                      multiple={true}
                      className="vm-cat-select"
                    />
                  </>
                ) : (
                  <SearchBar
                    onKeywordChange={handleProductKeywordChange}
                    resetTriggered={false}
                    placeholder="Szukaj produktu..."
                    className="pricelist vm-search-full"
                  />
                )}
              </div>
            </div>

            {catalogTab === "services" ? (
              <DataList<BaseService>
                columns={serviceCatalogColumns}
                items={services}
                getRowKey={(item) => item.id}
                onRowClick={(serv) => setSelectedService(serv)}
                emptyText="Brak usług"
                className="vm-list"
                hideHeader
              />
            ) : (
              <DataList<Product>
                columns={productCatalogColumns}
                items={products}
                getRowKey={(item, index) => `${item.id}-${index}`}
                onRowClick={(prod) => setSelectedProduct(prod)}
                onScroll={handleScroll}
                isLoading={loading}
                emptyText="Brak produktów"
                className="vm-list"
                hideHeader
              />
            )}
          </div>
        </div>
        )}

        <div className="vm-right flex-column f-1 min-height-0">
          <div className="vm-paragon flex-column f-1 min-height-0 scrollbar g-075">
            <section className="vm-section flex-column g-05">
              <div className="vm-section-head flex align-items-center space-between">
                <span className="qv-span">Usługi ({chosenServices.length})</span>
                {isMobile && (
                  <ActionButton
                    text={isNarrow ? "Usługi" : "Wybierz usługi"}
                    iconTitle="Wybierz usługi"
                    onClick={() => setIsServicePickerOpen(true)}
                    disableBorder={true}
                  >
                    <CirclePlus className="dl-icon-1 c-green" />
                  </ActionButton>
                )}
              </div>
              {chosenServices.length > 0 ? (
                <CartServiceDataList
                  items={chosenServices}
                  visitPreview={visitPreview}
                  isBoost={visitDTO.isBoost}
                  discountMode={discountMode}
                  onRemoveByIndex={handleRemoveServiceByIndex}
                  onManageBoostByIndex={onManageBoostByIndex}
                  onFreezePrice={
                    visitDTO.absence || visitDTO.isVip ? undefined : freezePrice
                  }
                />
              ) : (
                <span
                  className="vm-empty pointer"
                  onClick={() =>
                    isMobile ? setIsServicePickerOpen(true) : setCatalogTab("services")
                  }
                >
                  Wybierz usługę z katalogu →
                </span>
              )}
            </section>

            <section className="vm-section flex-column g-05">
              <div className="vm-section-head flex align-items-center g-1">
                <span className="qv-span">Produkty ({chosenProducts.length})</span>
                <ActionButton
                  text={isCompact ? "Voucher" : "Sprzedaż Vouchera"}
                  iconTitle="Sprzedaż Vouchera"
                  onClick={addVoucherToSaleItems}
                  disabled={visitDTO.absence}
                  disableBorder={true}
                >
                  <CirclePlus className="dl-icon-1 c-green" />
                </ActionButton>
                {isMobile && (
                  <ActionButton
                    text={isNarrow ? "Produkty" : "Wybierz produkty"}
                    iconTitle="Wybierz produkty"
                    className="ml-auto"
                    onClick={() => setIsProductPickerOpen(true)}
                    disabled={visitDTO.absence}
                    disableBorder={true}
                  >
                    <CirclePlus className="dl-icon-1 c-green" />
                  </ActionButton>
                )}
              </div>
              {chosenProducts.length > 0 ? (
                <CartProductDataList
                  items={chosenProducts}
                  onRemoveByIndex={handleRemoveProductByIndex}
                  onPriceChange={handleSaleItemPriceChange}
                />
              ) : visitDTO.absence ? (
                <span className="vm-empty">
                  Sprzedaż niedostępna przy nieobecności
                </span>
              ) : (
                <span
                  className="vm-empty pointer"
                  onClick={() =>
                    isMobile ? setIsProductPickerOpen(true) : setCatalogTab("products")
                  }
                >
                  Wybierz produkt z katalogu →
                </span>
              )}
            </section>

            <section className="vm-section flex-column g-05">
              <div className="vm-section-head flex align-items-center g-05">
                {!isMobile && (
                  <span
                    className="vm-info flex align-items-center pointer"
                    title="Rabaty obejmują wyłącznie usługi - nie dotyczą sprzedaży produktów ani voucherów."
                  >
                    <Info className="dl-icon" />
                  </span>
                )}
                <span className="qv-span">
                  Rabaty ({visitDTO.serviceDiscounts?.length ?? 0})
                </span>
              </div>
              <div className="vm-discounts cat-seg-group flex align-items-center width-fit-content g-025">
                {getAvailableDiscountTypes().map((type) => {
                  const isCustom = type === VisitDiscountType.CUSTOM;
                  const isChecked =
                    visitDTO.serviceDiscounts?.some((d) => d.type === type) ??
                    false;
                  let isDisabled = false;
                  if (visitDTO.absence && visitDTO.isVip) {
                    isDisabled = true;
                  } else if (visitDTO.absence) {
                    isDisabled = type !== VisitDiscountType.CLIENT_DISCOUNT;
                  } else if (visitDTO.isVip) {
                    isDisabled = type !== VisitDiscountType.GOOGLE_REVIEW;
                  }
                  const label = discountLabelFor(
                    type,
                    visitDTO,
                    discountSettings as DiscountSettings
                  );
                  if (!isCustom) {
                    return (
                      <button
                        key={type}
                        type="button"
                        disabled={isDisabled}
                        className={`cat-seg align-items-center pointer ${isChecked ? "active" : ""}`}
                        onClick={() => handleDiscountClick(type)}
                      >
                        {label}
                      </button>
                    );
                  }
                  return (
                    <div
                      key={type}
                      className={`cat-seg vm-seg-expand align-items-center ${isChecked ? "active" : ""}`}
                    >
                      <button
                        type="button"
                        disabled={isDisabled}
                        className="vm-seg-btn flex align-items-center pointer"
                        onClick={() => handleDiscountClick(type)}
                      >
                        {label}
                      </button>
                      {isChecked && (
                        <span className="vm-seg-value flex align-items-center g-025">
                          <DigitInput
                            onChange={handleCustomDiscountValueChange}
                            min={1}
                            max={100}
                            value={customDiscountValue ?? 0}
                            className="in-xs"
                          />
                          <span className="qv-span f10">%</span>
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>

            {clientWithDebt && (
              <section className="vm-section flex-column g-05">
                <div className="vm-section-head flex align-items-center space-between">
                  <span className="qv-span">
                    Klient spłaca ({visitDTO.debtRedemptions.length})
                  </span>
                </div>
                <CartDebtDataList
                  items={clientDebts}
                  selectedIds={selectedDebtIds}
                  onSelect={handleDebtRedemption}
                />
              </section>
            )}

            {!visitDTO.absence && (
              <section className="vm-section flex-column g-05">
                <div className="vm-section-head flex align-items-center g-1">
                  <span className="qv-span">
                    Płatność ({visitDTO.payments.length})
                  </span>
                  <ActionButton
                    text="Płatność"
                    onClick={addPayment}
                    disableBorder={true}
                  >
                    <CirclePlus className="dl-icon-1 c-green" />
                  </ActionButton>
                </div>
                <div className="flex-column g-05">
                  {visitDTO.payments.map((payment, index) => (
                    <div
                      key={index}
                      className="vm-payment flex space-between align-items-center g-05"
                    >
                      <div className="flex g-05 align-items-center flex-wrap">
                        {isCompact ? (
                          <DropdownSelect<DropdownItem>
                            items={paymentItems}
                            value={
                              paymentItems.find(
                                (item) => item.id === payment.method
                              ) ?? null
                            }
                            onChange={(val) => {
                              handlePaymentMethodChange(index, val);
                              const picked = Array.isArray(val) ? val[0] : val;
                              if (picked?.id === PaymentMethod.VOUCHER) {
                                setVoucherPaymentIndex(index);
                                setVoucherSelectionPopup(true);
                              }
                            }}
                            disabledItemIds={visitDTO.payments
                              .filter((p, i) => i !== index && p.method != null)
                              .map((p) => p.method as PaymentMethod)}
                            placeholder="Metoda"
                            searchable={false}
                            allowNew={false}
                            multiple={false}
                            className="vm-payment-method"
                          />
                        ) : (
                        <div className="cat-seg-group flex align-items-center width-fit-content g-025">
                          {paymentItems.map((item) => {
                            const usedElsewhere = visitDTO.payments.some(
                              (p, i) => i !== index && p.method === item.id
                            );
                            const selected = payment.method === item.id;
                            return (
                              <button
                                key={item.id}
                                type="button"
                                disabled={usedElsewhere}
                                className={`cat-seg align-items-center ${
                                  usedElsewhere ? "not-allowed" : "pointer"
                                } ${selected ? "active" : ""}`}
                                onClick={() => {
                                  const willSelect = !selected;
                                  handlePaymentMethodChange(
                                    index,
                                    willSelect ? item : null
                                  );
                                  if (
                                    willSelect &&
                                    item.id === PaymentMethod.VOUCHER
                                  ) {
                                    setVoucherPaymentIndex(index);
                                    setVoucherSelectionPopup(true);
                                  }
                                }}
                              >
                                {item.name}
                              </button>
                            );
                          })}
                        </div>
                        )}
                        {payment.method === PaymentMethod.VOUCHER && (
                          <ActionButton
                            text={
                              payment.voucher != null
                                ? "Przypisano"
                                : "Przypisz voucher"
                            }
                            className={`vm-voucher-btn ${
                              payment.voucher != null ? "active-g" : "active-r"
                            }`}
                            onClick={() => {
                              setVoucherPaymentIndex(index);
                              setVoucherSelectionPopup(true);
                            }}
                          >
                            <Ticket
                              className={`dl-icon-1 ${
                                payment.voucher != null ? "c-green" : "c-red"
                              }`}
                            />
                          </ActionButton>
                        )}
                      </div>
                      <div className="flex g-05 align-items-center">
                        {payment.method !== PaymentMethod.VOUCHER &&
                          (() => {
                            const remaining = getRemainingAmount(index);
                            const filled =
                              payment.amount === remaining && payment.amount !== 0;
                            return (
                              <ActionButton
                                text={
                                  isCompact
                                    ? `${remaining} zł`
                                    : `Reszta (${remaining} zł)`
                                }
                                disableImg={true}
                                disabled={remaining <= 0}
                                iconTitle="Wpisz pozostałą kwotę do zapłaty"
                                className={`vm-fill ${filled ? "filled" : ""}`}
                                onClick={() => handlePaymentAmount(index, remaining)}
                              />
                            );
                          })()}
                        {payment.method === PaymentMethod.VOUCHER ? (
                          <span className="vm-amount-display">
                            {payment.amount} zł
                          </span>
                        ) : (
                          <>
                            <CostInput
                              onChange={(value) => handlePaymentAmount(index, value)}
                              className="in-sm vm-amount"
                              selectedCost={payment.amount}
                            />
                            <span className="qv-span f10">zł</span>
                          </>
                        )}
                        <ActionButton
                          iconTitle="Usuń Płatność"
                          onClick={() => handlePaymentRemove(index)}
                          disableText={true}
                          disableBorder={true}
                        >
                          <Trash2 className="dl-icon c-red" />
                        </ActionButton>
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            )}

            <section className="vm-section flex-column g-05">
              <div className="vm-section-head flex align-items-center space-between">
                <span className="qv-span">Notatki</span>
              </div>
              <TextInput
                value={visitDTO.notes ?? ""}
                rows={2}
                multiline={true}
                placeholder="Notatki do Wizyty..."
                onSelect={(note) => {
                  if (typeof note === "string") handleNotes(note);
                }}
              />
            </section>
          </div>
          {!isMobile && vmFooter}
        </div>
      </div>

      {isDebtConfirmOpen && (
        <Modal
          title="Niepełna płatność"
          onClose={() => setIsDebtConfirmOpen(false)}
          size="xs"
          confirmText="Zatwierdź"
          onConfirm={handleVisitAction}
        >
          <div className="remove-modal-body flex-column align-items-center text-align-center g-075">
            <TriangleAlert className="dl-icon-1 c-red remove-modal-icon" />
            <p className="remove-modal-warning m-0">
              Płatności pokrywają {paidTotal} zł z {visitTotal} zł. Po zapisaniu wizyty
              klientowi zostanie naliczone zadłużenie w wysokości {newDebt} zł.
            </p>
          </div>
        </Modal>
      )}

      {isMobile && isServicePickerOpen && (
        <ServicePickerModal
          services={services}
          onClose={() => setIsServicePickerOpen(false)}
          onConfirm={handleServicePicks}
          filters={
            <>
              <SearchBar
                onKeywordChange={handleServiceKeywordChange}
                resetTriggered={false}
                placeholder="Szukaj usługi..."
                className="pricelist"
              />
              <DropdownSelect<BaseServiceCategory>
                items={serviceCategories}
                value={selectedServiceCategories}
                onChange={(selected) =>
                  handleFilterServicesByCategory(
                    selected
                      ? Array.isArray(selected)
                        ? selected
                        : [selected]
                      : null
                  )
                }
                placeholder="Kategorie"
                searchable={false}
                allowNew={false}
                allowColors={true}
                multiple={true}
                className="vm-cat-select"
              />
            </>
          }
        />
      )}
      {isMobile && isProductPickerOpen && (
        <ProductPickerModal
          products={products}
          isLoading={loading}
          onScroll={handleScroll}
          onClose={() => setIsProductPickerOpen(false)}
          onConfirm={handleProductPicks}
          filters={
            <SearchBar
              onKeywordChange={handleProductKeywordChange}
              resetTriggered={false}
              placeholder="Szukaj produktu..."
              className="pricelist"
            />
          }
        />
      )}
      {variantsAddOnsPopupOpen && pendingService && (
        <ServiceVariantsModal
          pendingService={pendingService}
          onClose={() => setVariantsAddOnsPopupOpen(false)}
          onSelect={handleVariantSelect}
        />
      )}
      {voucherSelectionPopup && voucherPaymentIndex !== null && (
        <AvailableVouchersModal
          onClose={() => {
            setVoucherSelectionPopup(false);
            setVoucherPaymentIndex(null);
          }}
          attachedVoucher={
            visitDTO.payments[voucherPaymentIndex]?.voucher ?? null
          }
          onSave={(voucher) => {
            if (voucherPaymentIndex !== null) {
              handleAssignVoucher(voucherPaymentIndex, voucher);
            }
            setVoucherSelectionPopup(false);
            setVoucherPaymentIndex(null);
          }}
        />
      )}
    </Modal>
  );
}

export default VisitModal;
