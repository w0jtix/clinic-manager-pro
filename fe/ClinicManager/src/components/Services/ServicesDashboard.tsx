import { useAlert } from "../Alert/AlertProvider";
import { Blocks, CirclePlus } from "lucide-react";
import { useState, useCallback, useEffect } from "react";
import NavigationBar from "../NavigationBar";
import ActionButton from "../ActionButton";
import CategorySegments from "../CategorySegments";
import { BaseServiceCategory } from "../../models/categories";
import BaseServiceCategoryService from "../../services/BaseServiceCategoryService";
import CategoryManagerModal from "../Popups/CategoryManagerModal";
import { AlertType } from "../../models/alert";
import ServiceDataList from "./ServiceDataList";
import { BaseService } from "../../models/service";
import BaseServiceService from "../../services/BaseServiceService";
import ServiceModal from "../Popups/ServiceModal";
import RemoveModal from "../Popups/RemoveModal";
import { useRoles } from "../../hooks/useRoles";
import DropdownSelect from "../DropdownSelect";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export function ServicesDashboard() {
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const [categories, setCategories] = useState<BaseServiceCategory[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<
    BaseServiceCategory[]
  >([]);
  const [removeServiceId, setRemoveServiceId] = useState<number | null>(null);
  const [editServiceId, setEditServiceId] = useState<number | null>(null);
  const [services, setServices] = useState<BaseService[]>([]);
  const [isAddNewServicePopupOpen, setIsAddNewServicePopupOpen] =
    useState<boolean>(false);
  const [isCategoryPopupOpen, setIsCategoryPopupOpen] =
    useState<boolean>(false);
  const { isCompany } = useRoles();
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchCategories = async (): Promise<void> => {
    BaseServiceCategoryService.getCategories()
      .then((data) => setCategories(data))
      .catch((error) => {
        setCategories([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching categories:", error);
      });
  };

  const fetchServices = async (): Promise<void> => {
    const selectedCategoryIds =
      selectedCategories.length > 0
        ? selectedCategories.map((cat) => cat.id)
        : null;
    BaseServiceService.getServices({
      categoryIds: selectedCategoryIds,
    })
      .then((data) => setServices(data))
      .catch((error) => {
        setServices([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Services: ", error);
      });
  };

  const handleFilterByCategory = useCallback(
    (categories: BaseServiceCategory[] | null) => {
      setSelectedCategories(categories ?? []);
    },
    []
  );

  const handleServiceReorder = useCallback(
    (fromIndex: number, toIndex: number) => {
      const previous = services;
      const next = [...services];
      const [moved] = next.splice(fromIndex, 1);
      if (!moved) return;
      next.splice(toIndex, 0, moved);
      setServices(next);

      const afterServiceId = toIndex === 0 ? null : next[toIndex - 1].id;
      BaseServiceService.moveService(moved.id, afterServiceId).catch((error) => {
        setServices(previous);
        showAlert("Błąd zmiany kolejności!", AlertType.ERROR);
        console.error("Error moving Service: ", error);
      });
    },
    [services, showAlert]
  );

  const handleResetFiltersAndData = useCallback(() => {
    fetchCategories();
    setResetTriggered((prev) => !prev);
    setRemoveServiceId(null);
    setEditServiceId(null);
    setSelectedCategories([]);
  }, []);

  const handlePopupSuccess = useCallback(
    (message: string) => {
      showAlert(message, AlertType.SUCCESS);
      handleResetFiltersAndData();
    },
    [showAlert, handleResetFiltersAndData]
  );

  const handleServiceRemove = useCallback(async () => {
    if (!removeServiceId) return;
    BaseServiceService.deleteService(removeServiceId)
      .then(() => {
        handlePopupSuccess(
          `Usługa ${
            services.find((s) => s.id === removeServiceId)?.name
          } usunięta!`
        );
        setRemoveServiceId(null);
      })
      .catch((error) => {
        console.error("Error removing Service", error);
        showAlert("Błąd usuwania Usługi!", AlertType.ERROR);
      });
  }, [removeServiceId, services, showAlert, handlePopupSuccess]);

  useEffect(() => {
    fetchCategories();
    fetchServices();
  }, [resetTriggered, selectedCategories]);

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <section className="sv-toolbar width-93 flex align-items-center space-between g-1 mt-1 mb-1">
          <div className="flex align-items-center g-1">
            {isMobile ? (
              <DropdownSelect<BaseServiceCategory>
                items={categories}
                value={selectedCategories}
                onChange={(selected) =>
                  handleFilterByCategory(
                    selected as BaseServiceCategory[] | null
                  )
                }
                placeholder="Kategorie"
                searchable={false}
                allowNew={false}
                allowColors={true}
                className="categories"
                multiple={true}
              />
            ) : (
              <CategorySegments
                categories={categories}
                selectedCategories={selectedCategories}
                onSelect={(selected) =>
                  handleFilterByCategory(
                    selected as BaseServiceCategory[] | null
                  )
                }
                resetTriggered={resetTriggered}
              />
            )}
          </div>

          {isCompany && (
            <div className="sv-action-buttons flex align-items-center g-1">
              <ActionButton
                text={isMobile ? "Usługa" : "Nowa Usługa"}
                iconTitle={"Nowa Usługa"}
                onClick={() => setIsAddNewServicePopupOpen(true)}
              >
                <CirclePlus className="dl-icon-1 c-green" />
              </ActionButton>
              <ActionButton
                text="Kategorie"
                iconTitle={"Kategorie"}
                onClick={() => setIsCategoryPopupOpen(true)}
              >
                <Blocks className="dl-icon-1 c-yellow" />
              </ActionButton>
            </div>
          )}
        </section>

        <div className="width-93 flex-column f-1 min-height-0 mb-2">
          <ServiceDataList
            items={services}
            isCompany={isCompany}
            setRemoveServiceId={setRemoveServiceId}
            setEditServiceId={setEditServiceId}
            reorderable={isCompany}
            onReorder={handleServiceReorder}
            className="sv-list"
          />
        </div>

        {isAddNewServicePopupOpen && (
          <ServiceModal
            onClose={() => setIsAddNewServicePopupOpen(false)}
            onReset={handleResetFiltersAndData}
          />
        )}
        {editServiceId != null && (
          <ServiceModal
            onClose={() => setEditServiceId(null)}
            onReset={handleResetFiltersAndData}
            editServiceId={editServiceId}
          />
        )}
        {isCategoryPopupOpen && (
          <CategoryManagerModal
            kind="service"
            onClose={() => setIsCategoryPopupOpen(false)}
            onChanged={handleResetFiltersAndData}
          />
        )}
        {removeServiceId != null && (
          <RemoveModal
            onClose={() => setRemoveServiceId(null)}
            warningText={"Usunięcie spowoduje utratę informacji o Usłudze."}
            handleRemove={handleServiceRemove}
          />
        )}
      </div>
    </div>
  );
}

export default ServicesDashboard;
