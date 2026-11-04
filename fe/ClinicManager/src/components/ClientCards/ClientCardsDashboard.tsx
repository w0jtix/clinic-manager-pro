import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CirclePlus, Eye, Flag, UserX } from "lucide-react";
import NavigationBar from "../NavigationBar";
import SearchBar from "../SearchBar";
import ActionButton from "../ActionButton";
import { DataList, Column } from "../DataList";
import ClientCardService from "../../services/ClientCardService";
import { ClientCardListItem } from "../../models/client-card";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { useBreakpoint } from "../../hooks/useBreakpoint";
import { useRestorableState } from "../../hooks/useRestorableState";
import { formatDate } from "../../utils/dateUtils";
import NewClientCardModal from "./NewClientCardModal";

const PAGE_SIZE = 25;
const LOAD_MORE_THRESHOLD_PX = 120;

export function ClientCardsDashboard() {
  const { isMobile } = useBreakpoint();
  const navigate = useNavigate();
  const { showAlert } = useAlert();
  const [keyword, setKeyword] = useRestorableState<string>("clientCards.keyword", "");
  const [items, setItems] = useState<ClientCardListItem[]>([]);
  const [page, setPage] = useState<number>(0);
  const [isLast, setIsLast] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(true);
  const [newCardOpen, setNewCardOpen] = useState<boolean>(false);
  const requestIdRef = useRef<number>(0);

  const fetchPage = useCallback(
    (pageToLoad: number, append: boolean) => {
      const requestId = ++requestIdRef.current;
      setLoading(true);
      ClientCardService.searchCards({ keyword }, pageToLoad, PAGE_SIZE)
        .then((data) => {
          if (requestId !== requestIdRef.current) return;
          const content = data?.content ?? [];
          setItems((prev) => (append ? [...prev, ...content] : content));
          setIsLast(data?.last ?? true);
          setPage(pageToLoad);
        })
        .catch((error) => {
          if (requestId !== requestIdRef.current) return;
          console.error("Error fetching Client Cards: ", error);
          if (!append) setItems([]);
          setIsLast(true);
          showAlert("Błąd", AlertType.ERROR);
        })
        .finally(() => {
          if (requestId === requestIdRef.current) setLoading(false);
        });
    },
    [keyword, showAlert]
  );

  useEffect(() => {
    fetchPage(0, false);
  }, [fetchPage]);

  const handleScroll = useCallback(
    (e: React.UIEvent<HTMLDivElement>) => {
      const el = e.currentTarget;
      if (loading || isLast) return;
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - LOAD_MORE_THRESHOLD_PX) {
        fetchPage(page + 1, true);
      }
    },
    [loading, isLast, page, fetchPage]
  );

  const openCard = useCallback(
    (item: ClientCardListItem) => navigate(`/client-cards/${item.id}`),
    [navigate]
  );

  const mobileColumns = useMemo<Column<ClientCardListItem>[]>(
    () => [
      {
        key: "client",
        grow: true,
        render: (item) => (
          <span className="exp-m-stack flex-column mw-0">
            <span className="flex align-items-center g-05 mw-0">
              <span className={`dl-name ${item.isDeleted ? "cc-deleted-name" : ""}`}>
                {item.lastName} {item.firstName}
              </span>
              {item.isDeleted && (
                <span className="cc-deleted-badge flex align-items-center" title="Klient usunięty">
                  <UserX className="dl-icon c-red" />
                </span>
              )}
            </span>
            {(item.birthDate || item.phoneNumber) && (
              <span className="dl-dim tnum">
                {[
                  item.birthDate ? `ur. ${formatDate(item.birthDate)}` : null,
                  item.phoneNumber ? `tel. ${item.phoneNumber}` : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </span>
            )}
          </span>
        ),
      },
      {
        key: "preview",
        width: "1.9rem",
        align: "center",
        render: (item) => (
          <ActionButton
            iconTitle="Otwórz kartę klienta"
            disableText={true}
            disableBorder={true}
            onClick={(e) => {
              e.stopPropagation();
              openCard(item);
            }}
          >
            <Eye className="dl-icon" />
          </ActionButton>
        ),
      },
    ],
    [openCard]
  );

  const columns = useMemo<Column<ClientCardListItem>[]>(
    () => [
      {
        key: "client",
        header: "Nazwisko i imię",
        grow: true,
        render: (item) => (
          <span className="cc-name flex align-items-center g-05 mw-0">
            <span className={`dl-name ${item.isDeleted ? "cc-deleted-name" : ""}`}>
              {item.lastName} {item.firstName}
            </span>
            {item.isDeleted && (
              <span className="cc-deleted-badge flex align-items-center g-025" title="Klient usunięty">
                <UserX className="dl-icon c-red" />
                Klient usunięty
              </span>
            )}
          </span>
        ),
      },
      {
        key: "birthDate",
        header: "Data ur.",
        width: "8rem",
        render: (item) => (
          <span className="dl-dim tnum">{item.birthDate ? formatDate(item.birthDate) : "-"}</span>
        ),
      },
      {
        key: "phone",
        header: "Telefon",
        width: "9rem",
        render: (item) => <span className="dl-dim tnum">{item.phoneNumber || "-"}</span>,
      },
      {
        key: "lastVisit",
        header: "Ostatnia wizyta",
        width: "9rem",
        render: (item) => (
          <span className="flex align-items-center g-05">
            <span className="dl-dim tnum">{item.lastVisitDate ? formatDate(item.lastVisitDate) : "-"}</span>
            {item.lastVisitAbsence && (
              <span className="flex" title="Nieobecność">
                <Flag className="dl-icon-1 c-red ccl-absence-flag" />
              </span>
            )}
          </span>
        ),
      },
    ],
    []
  );

  return (
    <div className="dashboard scrollbar mw-0 height-max flex f-1 align-items-center justify-center">
      <div className="dashboard-panel width-max height-max flex-column align-items-center">
        <NavigationBar showSearchbar={false} />

        <section className="cc-toolbar width-93 flex align-items-center space-between g-1 mt-1 mb-1">
          <SearchBar
            onKeywordChange={setKeyword}
            initialValue={keyword}
            placeholder="Szukaj: imię, nazwisko lub telefon"
            className="cc-search"
          />
          <ActionButton
            text={isMobile ? "Karta" : "Nowa karta"}
            iconTitle="Nowa karta"
            onClick={() =>
              isMobile
                ? navigate("/client-cards/new", { state: { initialText: keyword } })
                : setNewCardOpen(true)
            }
          >
            <CirclePlus className="dl-icon-1 c-green" />
          </ActionButton>
        </section>

        <div className="width-93 flex-column f-1 min-height-0 mb-2">
          <DataList<ClientCardListItem>
            className="cc-list"
            columns={isMobile ? mobileColumns : columns}
            items={items}
            getRowKey={(item) => item.id}
            hideHeader={isMobile}
            onRowClick={openCard}
            rowClassName={(item) => (item.isDeleted ? "cc-row-deleted" : "")}
            onScroll={handleScroll}
            isLoading={loading && items.length === 0}
            emptyText={keyword.trim() ? `Brak klienta „${keyword.trim()}”` : "Brak klientów"}
          />
        </div>

        {newCardOpen && (
          <NewClientCardModal
            initialText={keyword}
            onClose={() => setNewCardOpen(false)}
          />
        )}
      </div>
    </div>
  );
}

export default ClientCardsDashboard;
