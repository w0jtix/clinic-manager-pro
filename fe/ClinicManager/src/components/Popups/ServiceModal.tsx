import { useCallback, useState, useEffect } from "react";
import { CirclePlus, X } from "lucide-react";
import {
  BaseService,
  NewBaseService,
  NewServiceVariant,
  ServiceVariant,
} from "../../models/service";
import Modal from "../Modal";
import ActionButton from "../ActionButton";
import TextInput from "../TextInput";
import DigitInput from "../DigitInput";
import CostInput from "../CostInput";
import DropdownSelect from "../DropdownSelect";
import { BaseServiceCategory } from "../../models/categories";
import BaseServiceCategoryService from "../../services/BaseServiceCategoryService";
import { useAlert } from "../Alert/AlertProvider";
import { Action } from "../../models/action";
import { AlertType } from "../../models/alert";
import { validateServiceForm } from "../../utils/validators";
import BaseServiceService from "../../services/BaseServiceService";
import { extractServiceErrorMessage } from "../../utils/errorHandler";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface ServiceModalProps {
  onClose: () => void;
  onReset: () => void;
  editServiceId?: number;
  className?: string;
}

export function ServiceModal({
  onClose,
  onReset,
  editServiceId,
  className = "",
}: ServiceModalProps) {
  const { isCompact } = useBreakpoint();
  const [fetchedService, setFetchedService] = useState<BaseService | null>(null);
  const [categories, setCategories] = useState<BaseServiceCategory[]>([]);
  const [serviceDTO, setServiceDTO] = useState<NewBaseService | BaseService>({
    name: "",
    price: 0,
    duration: 0,
    variants: [],
    category: null,
  });
  const { showAlert } = useAlert();

  const action = editServiceId ? Action.EDIT : Action.CREATE;

  const fetchCategories = async (): Promise<void> => {
    BaseServiceCategoryService.getCategories()
      .then((data) => setCategories(data))
      .catch((error) => {
        setCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching categories: ", error);
      });
  };

  const fetchServiceById = async (serviceId: number) => {
    BaseServiceService.getServiceById(serviceId)
      .then((data) => {
        setFetchedService(data);
        setServiceDTO(data);
      })
      .catch((error) => {
        console.error("Error fetching service: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  useEffect(() => {
    if (editServiceId) {
      fetchServiceById(editServiceId);
    }
  }, [editServiceId]);

  const handleCategoryChange = useCallback(
    (selected: BaseServiceCategory[] | null) => {
      setServiceDTO((prev) => ({
        ...prev,
        category: selected && selected.length > 0 ? selected[0] : null,
      }));
    },
    []
  );

  const handleNameChange = useCallback((name: string) => {
    setServiceDTO((prev) => ({ ...prev, name }));
  }, []);

  const handlePriceChange = useCallback((price: number) => {
    setServiceDTO((prev) => ({ ...prev, price }));
  }, []);

  const handleDurationChange = useCallback((duration: number | null) => {
    setServiceDTO((prev) => ({ ...prev, duration: duration ?? 0 }));
  }, []);

  const handleAddVariant = useCallback(() => {
    setServiceDTO((prev) => ({
      ...prev,
      variants: [
        ...(prev.variants || []),
        { name: "", price: 0, duration: 0 },
      ],
    }));
  }, []);

  const handleRemoveVariant = useCallback((indexToRemove: number) => {
    setServiceDTO((prev) => ({
      ...prev,
      variants: prev.variants.filter((_, index) => index !== indexToRemove),
    }));
  }, []);

  const handleVariantChange = useCallback(
    (
      index: number,
      key: keyof (ServiceVariant | NewServiceVariant),
      value: string | number
    ) => {
      setServiceDTO((prev) => ({
        ...prev,
        variants: prev.variants.map((variant, i) =>
          i === index ? { ...variant, [key]: value } : variant
        ),
      }));
    },
    []
  );

  const handleServiceAction = useCallback(async () => {
    const error = validateServiceForm(serviceDTO, action, fetchedService);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      if (action === Action.CREATE) {
        await BaseServiceService.createService(serviceDTO as NewBaseService);
        showAlert(`Usługa ${serviceDTO.name} utworzona!`, AlertType.SUCCESS);
      } else {
        await BaseServiceService.updateService(
          (serviceDTO as BaseService).id,
          serviceDTO as BaseService
        );
        showAlert(`Usługa ${serviceDTO.name} zaktualizowana!`, AlertType.SUCCESS);
      }
      onReset();
      onClose();
    } catch (error) {
      console.error(
        `Error ${action === Action.CREATE ? "creating" : "updating"} service:`,
        error
      );
      const errorMessage = extractServiceErrorMessage(error, action);
      showAlert(errorMessage, AlertType.ERROR);
    }
  }, [serviceDTO, action, fetchedService, onReset, onClose, showAlert]);

  return (
    <Modal
      dirtyValue={serviceDTO}
      title={action === Action.CREATE ? "Dodaj Nową Usługę" : "Edytuj Usługę"}
      onClose={onClose}
      size="lg"
      onConfirm={handleServiceAction}
      className={`modal-overflow ${className}`}
    >
      <div className="flex-column g-1">
        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Nazwa:</span>
          <TextInput
            dropdown={false}
            className="in-full"
            value={serviceDTO.name}
            onSelect={(inputName) => {
              if (typeof inputName === "string") handleNameChange(inputName);
            }}
          />
        </div>

        <div className="pf-row sv-cat-row flex align-items-center g-1">
          <span className="pf-label">Kategoria:</span>
          <DropdownSelect<BaseServiceCategory>
            items={categories}
            value={serviceDTO.category}
            onChange={(selected) =>
              handleCategoryChange(
                selected ? (Array.isArray(selected) ? selected : [selected]) : null
              )
            }
            placeholder="Wybierz kategorię"
            searchable={false}
            allowNew={false}
            allowColors={true}
            multiple={false}
            className="sv-category"
          />
        </div>

        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Czas trwania (min):</span>
          <DigitInput
            onChange={handleDurationChange}
            value={serviceDTO.duration}
            max={999}
            className="in-xs"
          />
        </div>

        <div className="pf-row flex align-items-center g-1">
          <span className="pf-label">Cena:</span>
          <CostInput
            onChange={handlePriceChange}
            selectedCost={serviceDTO.price}
            className="in-sm"
          />
        </div>

        <section className="sv-variants flex-column g-075">
          <div className="flex align-items-center space-between g-1">
            <span className="sv-variants-title">Warianty usługi</span>
            <ActionButton text={"Dodaj wariant"} onClick={handleAddVariant}>
              <CirclePlus className="dl-icon-1 c-green" />
            </ActionButton>
          </div>

          {serviceDTO.variants.length > 0 && (
            <div className="ur-list scrollbar flex-column">
              {serviceDTO.variants.map((variant, index) => (
                <div
                  key={index}
                  className={`ur-item sv-variant-item width-max ${
                    isCompact
                      ? "flex-column g-05"
                      : "flex align-items-center g-075"
                  }`}
                >
                  {isCompact ? (
                    <>
                      <div className="flex align-items-center width-max g-05">
                        <TextInput
                          dropdown={false}
                          className="in-full"
                          placeholder={`Wariant ${index + 1}`}
                          value={variant.name}
                          onSelect={(value) => {
                            if (typeof value === "string")
                              handleVariantChange(index, "name", value);
                          }}
                        />
                        <ActionButton
                          iconTitle={"Usuń Wariant"}
                          onClick={() => handleRemoveVariant(index)}
                          disableText={true}
                          disableBorder={true}
                          className="ur-remove"
                        >
                          <X className="dl-icon-1" />
                        </ActionButton>
                      </div>
                      <div className="sv-variant-meta flex align-items-center">
                        <div className="flex align-items-center g-05">
                          <span className="ir-label">Czas (min):</span>
                          <DigitInput
                            onChange={(value) =>
                              handleVariantChange(index, "duration", value ?? 0)
                            }
                            value={variant.duration}
                            max={999}
                            className="in-xs"
                          />
                        </div>
                        <div className="flex align-items-center g-05">
                          <span className="ir-label">Cena:</span>
                          <CostInput
                            onChange={(value) =>
                              handleVariantChange(index, "price", value)
                            }
                            selectedCost={variant.price}
                            className="in-sm"
                          />
                        </div>
                      </div>
                    </>
                  ) : (
                    <>
                      <ActionButton
                        iconTitle={"Usuń Wariant"}
                        onClick={() => handleRemoveVariant(index)}
                        disableText={true}
                        disableBorder={true}
                        className="ur-remove"
                      >
                        <X className="dl-icon-1" />
                      </ActionButton>
                      <TextInput
                        dropdown={false}
                        className="in-full"
                        placeholder={`Wariant ${index + 1}`}
                        value={variant.name}
                        onSelect={(value) => {
                          if (typeof value === "string")
                            handleVariantChange(index, "name", value);
                        }}
                      />
                      <span className="ir-label">Czas (min):</span>
                      <DigitInput
                        onChange={(value) =>
                          handleVariantChange(index, "duration", value ?? 0)
                        }
                        value={variant.duration}
                        max={999}
                        className="in-xs"
                      />
                      <span className="ir-label">Cena:</span>
                      <CostInput
                        onChange={(value) =>
                          handleVariantChange(index, "price", value)
                        }
                        selectedCost={variant.price}
                        className="in-sm"
                      />
                    </>
                  )}
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </Modal>
  );
}

export default ServiceModal;
