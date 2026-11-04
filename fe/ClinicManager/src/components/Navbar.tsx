import { useEffect, useState } from 'react'
import { Menu, X } from 'lucide-react'
import NavbarLogoContainer from './NavbarLogoContainer'
import NavbarMenu from './NavbarMenu'
import Socials from './Socials'
import ActionButton from './ActionButton'
import UserMenu from './UserMenu'
import { useBreakpoint } from "../hooks/useBreakpoint";

// Desktop keeps the original hover-expand sidebar untouched. Below the
// breakpoint there is no hover event to expand it on, and the collapsed
// icon-only width doesn't leave room for content anyway - so mobile gets its
// own sticky top bar plus an off-canvas drawer opened by tap.
export function Navbar () {
  const { isMobile } = useBreakpoint();
  const [hovered, setHovered] = useState<boolean>(false);
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);

  useEffect(() => {
    if (!isMobile) setDrawerOpen(false);
  }, [isMobile]);

  useEffect(() => {
    if (!drawerOpen) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDrawerOpen(false);
    };
    document.addEventListener('keydown', handleKeyDown);
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = '';
    };
  }, [drawerOpen]);

  if (isMobile) {
    return (
      <>
        <div className="navbar-topbar flex align-items-center space-between">
          <span className="navbar-topbar-left flex align-items-center">
            <ActionButton
              iconTitle={drawerOpen ? 'Zamknij menu' : 'Otwórz menu'}
              onClick={() => setDrawerOpen((prev) => !prev)}
              disableText={true}
              disableBorder={true}
            >
              <Menu className="dl-icon-1" />
            </ActionButton>
            <NavbarLogoContainer to="/" className="navbar-topbar-logo" />
          </span>
          <UserMenu />
        </div>

        {drawerOpen && (
          <div
            className="navbar-drawer-backdrop"
            onClick={() => setDrawerOpen(false)}
          />
        )}
        <div className={`navbar-drawer flex-column g-15 ${drawerOpen ? 'open' : ''}`}>
          <div className="navbar-drawer-head flex align-items-center space-between">
            <NavbarLogoContainer to="/" />
            <ActionButton
              iconTitle="Zamknij menu"
              onClick={() => setDrawerOpen(false)}
              disableText={true}
              disableBorder={true}
            >
              <X className="dl-icon-1" />
            </ActionButton>
          </div>
          <NavbarMenu expanded={true} onNavigate={() => setDrawerOpen(false)} />
          <Socials />
        </div>
      </>
    );
  }

  return (
    <div className="navbar-slot relative height-max">
      <div
        className={`navbar absolute height-max g-15 flex-column align-items-center ${hovered ? 'expanded' : 'collapsed'}`}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <NavbarLogoContainer to="/"/>
        <NavbarMenu expanded={hovered}/>
        <Socials/>
      </div>
    </div>
  )
}

export default Navbar
