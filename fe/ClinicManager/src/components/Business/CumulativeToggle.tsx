import { Sigma } from "lucide-react";
import ActionButton from "../ActionButton";
import { useBreakpoint } from "../../hooks/useBreakpoint";

export interface CumulativeToggleProps {
  active: boolean;
  onToggle: () => void;
}

export function CumulativeToggle({ active, onToggle }: CumulativeToggleProps) {
  const { isNarrow } = useBreakpoint();

  return (
    <ActionButton
      disableImg={true}
      text="Narastający"
      iconTitle="Narastający"
      disableText={isNarrow}
      onClick={onToggle}
      className={`cum-toggle ${active ? "chart-selected" : ""} ${isNarrow ? "chart-icon-btn" : ""}`}
    >
      {isNarrow && <Sigma className="dl-icon-1" />}
    </ActionButton>
  );
}

export default CumulativeToggle;
