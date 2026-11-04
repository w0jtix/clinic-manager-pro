import React, { useCallback, useMemo } from "react";
import { Link } from "react-router-dom";
import {
  Flag,
  Rocket,
  TriangleAlert,
  Percent,
  Coins,
  Ticket,
  SquarePen,
  Trash2,
} from "lucide-react";
import DataList, { Column } from "../DataList";
import ActionButton from "../ActionButton";
import RowActionMenu from "../RowActionMenu";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { Client } from "../../models/client";
import { useUser } from "../User/UserProvider";
import { useRoles } from "../../hooks/useRoles";
import googleIcon from "../../assets/google.png";

export interface ClientDataListProps {
  items: Client[];
  setRemoveClientId?: (clientId: number | null) => void;
  setSelectedClientId?: (clientId: number | null) => void;
  onRowClick?: (client: Client) => void;
  isLoading?: boolean;
  className?: string;
}

export function ClientDataList({
  items,
  setRemoveClientId,
  setSelectedClientId,
  onRowClick,
  isLoading = false,
  className = "",
}: ClientDataListProps) {
  const { user } = useUser();
  const { isMaster } = useRoles();
  const { isMobile, isCompact } = useBreakpoint();

  const handleOnClickEdit = useCallback(
    (e: React.MouseEvent, item: Client) => {
      e.stopPropagation();
      setSelectedClientId?.(item.id);
    },
    [setSelectedClientId]
  );

  const handleOnClickRemove = useCallback(
    (e: React.MouseEvent, item: Client) => {
      e.stopPropagation();
      setRemoveClientId?.(item.id);
    },
    [setRemoveClientId]
  );

  const canRemove = useCallback(
    (item: Client): boolean =>
      item.createdBy === user?.id ||
      isMaster,
    [user?.id, isMaster]
  );

  const columns = useMemo<Column<Client>[]>(() => {
    const flagsColumn: Column<Client> = {
        key: "flags",
        header: "",
        width: isCompact ? "3rem" : isMobile ? "7rem" : "9rem",
        render: (item) => (
          <span className="cl-flags flex align-items-center g-05">
            {item.redFlag && (
              <span className="flex" title="Klient RedFlag">
                <Flag className="dl-icon-1 cl-flag c-red" />
              </span>
            )}
            {item.boostClient && (
              <span className="flex" title="Klient z Boosta">
                <Rocket className="dl-icon-1 cl-flag boost" />
              </span>
            )}
            {!item.signedRegulations && (
              <span className="flex" title="Klient nie podpisał Regulaminu">
                <TriangleAlert className="dl-icon-1 pc-y" />
              </span>
            )}
            {item.discount && (
              <span className="flex" title="Klient ma przypisaną stałą Zniżkę">
                <Percent className="dl-icon-1 cl-flag discount" />
              </span>
            )}
            {item.hasDebts && (
              <span className="flex" title="Klient posiada Dług">
                <Coins className="dl-icon-1 cl-flag debt" />
              </span>
            )}
            {item.hasActiveGoogleReview && (
              <img
                src={googleIcon}
                alt="Google"
                title="Klientowi przysługuje rabat za opinię Google"
                className="rv-source google"
              />
            )}
            {item.hasActiveVoucher && (
              <span className="flex" title="Klient posiada aktywny Voucher">
                <Ticket className="dl-icon-1 cl-flag voucher" />
              </span>
            )}
          </span>
        ),
    };

    const clientColumn: Column<Client> = {
      key: "client",
      header: "Klient",
      grow: true,
      render: (item) => (
        <Link
          to={`/client/${item.id}`}
          className="dl-name pd-product-link"
          title="Otwórz stronę klienta"
          onClick={(e) => e.stopPropagation()}
        >
          {item.firstName} {item.lastName}
        </Link>
      ),
    };

    const discountColumn: Column<Client> = {
      key: "discount",
      header: "Zniżka",
      width: isMobile ? "4rem" : "9rem",
      align: "center",
      render: (item) => (
        <span className="dl-dim">{item.discount?.name ?? "-"}</span>
      ),
    };

    const visitsColumn: Column<Client> = {
      key: "visitsCount",
      header: "Wizyty",
      width: isMobile ? "2rem" : "6rem",
      align: "center",
      render: (item) => item.visitsCount,
    };

    const optionsColumn: Column<Client> = {
      key: "options",
      header: "Opcje",
      width: isMobile ? "1.9rem" : "6rem",
      align: "center",
      render: (item) =>
        isMobile ? (
          <div className="flex align-items-center justify-center">
            <RowActionMenu
              items={[
                {
                  label: "Edytuj",
                  icon: <SquarePen className="dl-icon c-blue" />,
                  onClick: () => setSelectedClientId?.(item.id),
                },
                ...(canRemove(item)
                  ? [
                      {
                        label: "Usuń",
                        icon: <Trash2 className="dl-icon c-red" />,
                        onClick: () => setRemoveClientId?.(item.id),
                        danger: true,
                      },
                    ]
                  : []),
              ]}
            />
          </div>
        ) : (
          <div className="flex align-items-center justify-center g-05">
            <ActionButton
              iconTitle={"Edytuj Klienta"}
              onClick={(e) => handleOnClickEdit(e, item)}
              disableText={true}
              disableBorder={true}
            >
              <SquarePen className="dl-icon c-blue" />
            </ActionButton>
            {canRemove(item) && (
              <ActionButton
                iconTitle={"Usuń Klienta"}
                onClick={(e) => handleOnClickRemove(e, item)}
                disableText={true}
                disableBorder={true}
              >
                <Trash2 className="dl-icon c-red" />
              </ActionButton>
            )}
          </div>
        ),
    };

    if (isMobile) {
      return [
        clientColumn,
        flagsColumn,
        discountColumn,
        ...(isCompact ? [] : [visitsColumn]),
        optionsColumn,
      ];
    }

    return [
      flagsColumn,
      clientColumn,
      discountColumn,
      visitsColumn,
      optionsColumn,
    ];
  }, [
    canRemove,
    isMobile,
    isCompact,
    handleOnClickEdit,
    handleOnClickRemove,
    setSelectedClientId,
    setRemoveClientId,
  ]);

  return (
    <DataList<Client>
      columns={columns}
      items={items}
      getRowKey={(item) => item.id}
      onRowClick={onRowClick}
      isLoading={isLoading}
      hideHeader={isMobile}
      emptyText="Brak klientów"
      className={className}
    />
  );
}

export default ClientDataList;
