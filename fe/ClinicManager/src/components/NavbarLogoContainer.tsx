import { Link } from "react-router-dom";
import { Cross } from "lucide-react";

export interface NavbarLogoContainerProps {
  title?: string;
  subtitle?: string;
  to?: string;
  className?: string;
}

export function NavbarLogoContainer ({
  title = "ClinicManager",
  subtitle = "PRO",
  to,
  className = "",
}: NavbarLogoContainerProps) {

  const content = (
    <>
      <span className="menu-icon-slot logo flex align-items-center justify-center">
        <Cross />
      </span>
      <section className="text-field flex align-items-center g-5px">
        <h1>{title}</h1>
        {subtitle && <h2 className="pc-y">{subtitle}</h2>}
      </section>
    </>
  );


  const containerClassName = `navbar-logo-container ${className} flex align-items-center justify-start g-15px width-max`;

  if (to) {
    return (
      <Link to={to} className={`${containerClassName} pointer`} title="Strona główna">
        {content}
      </Link>
    );
  }

  return <div className={containerClassName}>{content}</div>;
}

export default NavbarLogoContainer
