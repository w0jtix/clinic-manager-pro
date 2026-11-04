import { useEffect, useState, useCallback } from "react";
import { CirclePlus, RotateCw } from "lucide-react";
import { useAlert } from "../Alert/AlertProvider";
import ActionButton from "../ActionButton";
import NavigationBar from "../NavigationBar";
import SearchBar from "../SearchBar";
import RemoveModal from "../Popups/RemoveModal";
import { AlertType } from "../../models/alert";
import VoucherService from "../../services/VoucherService";
import { Voucher, VoucherStatus, VoucherFilterDTO } from "../../models/voucher";
import VoucherDataList from "./VoucherDataList";
import VoucherModal from "../Popups/VoucherModal";
import { Action } from "../../models/action";
import FilterDropdown from "../FilterDropdown";
import CycleFilter, { CycleFilterTone } from "../CycleFilter";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";

const EMPTY_FILTER: VoucherFilterDTO = {
  status: null,
  keyword: "",
};

const getStatusLabel = (status?: VoucherStatus | null): string => {
  if (status == null) return "Wszystkie";
  if (status === VoucherStatus.ACTIVE) return "Aktywne";
  if (status === VoucherStatus.USED) return "Zrealizowane";
  return "Nieaktywne";
};

const getStatusTone = (status?: VoucherStatus | null): CycleFilterTone | null => {
  if (status == null) return null;
  if (status === VoucherStatus.ACTIVE) return "green";
  if (status === VoucherStatus.USED) return "grey";
  return "red";
};

export function VouchersDashboard() {
  const [isAddNewVoucherPopupOpen, setIsAddNewVoucherPopupOpen] =
    useState<boolean>(false);
  const [editVoucherId, setEditVoucherId] = useState<string | number | null>(
    null
  );
  const [removeVoucherId, setRemoveVoucherId] = useState<string | number | null>(
    null
  );
  const [vouchers, setVouchers] = useState<Voucher[]>([]);
  const [filter, setFilter] = useRestorableState<VoucherFilterDTO>("vouchers.filter", EMPTY_FILTER);
  const [resetTriggered, setResetTriggered] = useState<boolean>(false);
  const { showAlert } = useAlert();
  const { isMobile } = useBreakpoint();

  const fetchVouchers = async (): Promise<void> => {
    VoucherService.getVouchers(filter)
      .then((data) => {
        const sortedData = data.sort(
          (a, b) =>
            new Date(b.issueDate).getTime() - new Date(a.issueDate).getTime()
        );
        setVouchers(sortedData);
      })
      .catch((error) => {
        setVouchers([]);
        showAlert("Błąd", AlertType.ERROR);
        console.error("Error fetching Vouchers: ", error);
      });
  };

  const handleKeywordChange = useCallback((newKeyword: string) => {
    setFilter((prev) => ({
      ...prev,
      keyword: newKeyword,
    }));
  }, []);

  const toggleStatus = useCallback(() => {
    setFilter((prev) => {
      let nextStatus: VoucherStatus | null = null;

      if (prev.status === null) nextStatus = VoucherStatus.ACTIVE;
      else if (prev.status === VoucherStatus.ACTIVE)
        nextStatus = VoucherStatus.USED;
      else if (prev.status === VoucherStatus.USED)
        nextStatus = VoucherStatus.EXPIRED;
      else nextStatus = null;

      return { ...prev, status: nextStatus };
    });
  }, []);

  const handleResetFilters = useCallback(() => {
    setFilter(EMPTY_FILTER);
    setResetTriggered((prev) => !prev);
  }, []);

  const handleVoucherRemove = useCallback(async (): Promise<void> => {
    try {
      if (removeVoucherId != null) {
        await VoucherService.deleteVoucher(removeVoucherId);
        showAlert("Pomyślnie usunięto voucher!", AlertType.SUCCESS);
        setRemoveVoucherId(null);
        fetchVouchers();
      }
    } catch (error) {
      showAlert("Błąd usuwania vouchera!", AlertType.ERROR);
    }
  }, [removeVoucherId, showAlert]);

  useEffect(() => {
    fetchVouchers();
  }, [filter]);

  const searchField = (
    <SearchBar
      onKeywordChange={handleKeywordChange}
      resetTriggered={resetTriggered}
      initialValue={filter.keyword ?? ""}
    />
  );

  const statusField = (
    <CycleFilter
      label="Status:"
      value={getStatusLabel(filter.status)}
      tone={getStatusTone(filter.status)}
      iconTitle="Status Vouchera"
      onClick={toggleStatus}
      className="align-self-end"
    />
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <section className="cl-toolbar width-93 flex align-items-center space-between g-1 mt-1 mb-1">
          <div className="flex align-items-center g-075">
            {isMobile ? (
              <FilterDropdown
                label="Filtry"
                className="rv-filters"
                count={[!!filter.keyword, filter.status !== null].filter(Boolean).length}
              >
                {searchField}
                <div className="fd-row flex align-items-center g-05">
                  {statusField}
                </div>
              </FilterDropdown>
            ) : (
              <>
                {searchField}
                {statusField}
              </>
            )}
            <ActionButton
              disableText={true}
              iconTitle={"Resetuj filtry"}
              onClick={handleResetFilters}
              className={"reset align-self-end"}
            >
              <RotateCw className="dl-icon icon-control" />
            </ActionButton>
          </div>

          <ActionButton
            text={isMobile ? "Voucher" : "Nowy Voucher"}
            iconTitle={"Nowy Voucher"}
            onClick={() => setIsAddNewVoucherPopupOpen(true)}
          >
            <CirclePlus className="dl-icon-1 c-green" />
          </ActionButton>
        </section>

        <div className="width-93 flex-column f-1 min-height-0 mb-2">
          <VoucherDataList
            className="vc-list"
            items={vouchers}
            setEditVoucherId={setEditVoucherId}
            setRemoveVoucherId={setRemoveVoucherId}
          />
        </div>

        {isAddNewVoucherPopupOpen && (
          <VoucherModal
            onClose={() => {
              setIsAddNewVoucherPopupOpen(false);
              fetchVouchers();
            }}
            action={Action.CREATE}
            className=""
          />
        )}
        {editVoucherId != null && (
          <VoucherModal
            onClose={() => {
              setEditVoucherId(null);
              fetchVouchers();
            }}
            voucherId={editVoucherId}
            action={Action.EDIT}
            className=""
          />
        )}
        {removeVoucherId != null && (
          <RemoveModal
            onClose={() => setRemoveVoucherId(null)}
            handleRemove={handleVoucherRemove}
            warningText={
              "Usunięcie spowoduje utratę informacji o Voucherze.\n\nVoucher zakupiony podczas Wizyty może być usunięty tylko wraz z Wizytą."
            }
          />
        )}
      </div>
    </div>
  );
}

export default VouchersDashboard;
