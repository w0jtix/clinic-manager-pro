import SearchBar from "./SearchBar";
import UserMenu from "./UserMenu";
import { useBreakpoint } from "../hooks/useBreakpoint";

export interface NavigationBarProps {
  onKeywordChange?: (keyword: string) => void;
  resetTriggered?: boolean;
  className?: string;
  children?: React.ReactNode;
  showSearchbar?: boolean;
  replaceSearchbar?: boolean;
}

const NavigationBar = ({
  onKeywordChange,
  resetTriggered,
  className = "",
  children,
  showSearchbar = true,
  replaceSearchbar = false,
}: NavigationBarProps) => {
  const { isMobile } = useBreakpoint();
  const hasSearch =
    showSearchbar && onKeywordChange != undefined && resetTriggered != undefined;

  // UserMenu already lives in the mobile sticky top bar (Navbar.tsx), so it
  // would otherwise show up twice. Every current call site passes
  // showSearchbar={false} with no children, so on mobile there is nothing
  // left to render here - collapsing to null avoids an empty duplicate bar.
  // A future page that does pass search/children still gets them on mobile,
  // just without a second avatar dropdown.
  const showUserMenuHere = !isMobile;
  if (isMobile && !hasSearch && !children) {
    return null;
  }

  return (
    <div
      className={`navigation-bar mt-1 ${className} height-fit-content block justify-center relative width-93`}
    >
      <section
        className={`navigation-bar-interior flex align-items-center ${
          showSearchbar || replaceSearchbar ? "space-between" : "justify-end"
        } width-93 height-fit-content m-0-auto ${className}`}
      >
        {hasSearch && (
          <SearchBar
            onKeywordChange={onKeywordChange}
            resetTriggered={resetTriggered}
          />
        )}
        {children}
        {showUserMenuHere && <UserMenu />}
      </section>
    </div>
  );
};

export default NavigationBar;
