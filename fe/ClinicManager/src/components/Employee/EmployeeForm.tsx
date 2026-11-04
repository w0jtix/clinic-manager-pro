import TextInput from "../TextInput";
import React, { useCallback } from "react";
import { EmploymentType, NewEmployee } from "../../models/employee";
import { Action } from "../../models/action";
import { Slider } from "../Slider";
import DropdownSelect from "../DropdownSelect";

export interface EmployeeFormProps {
  employeeDTO: NewEmployee;
  setEmployeeDTO: React.Dispatch<React.SetStateAction<NewEmployee>>;
  className?: string;
  action: Action;
}

const employmentTypeLabels: Record<EmploymentType, string> = {
  [EmploymentType.QUARTER]: "1/4 etatu",
  [EmploymentType.HALF]: "1/2 etatu",
  [EmploymentType.THREE_QUARTERS]: "3/4 etatu",
  [EmploymentType.FULL]: "Pełny etat",
};

const employmentTypeOptions = Object.values(EmploymentType).map((type) => ({
  id: type,
  name: employmentTypeLabels[type],
}));

export function EmployeeForm({
  employeeDTO,
  setEmployeeDTO,
  className = "",
  action,
}: EmployeeFormProps) {
  const handleEmployeeName = useCallback((name: string) => {
    setEmployeeDTO((prev) => ({
      ...prev,
      name,
    }));
  }, []);
  const handleLastName = useCallback((lastName: string) => {
    setEmployeeDTO((prev) => ({
      ...prev,
      lastName: lastName || "",
    }));
  }, []);
  const handleEmploymentTypeChange = useCallback(
    (
      selected:
        | { id: string; name: string }
        | { id: string; name: string }[]
        | null
    ) => {
      const selectedItem = Array.isArray(selected) ? selected[0] : selected;
      setEmployeeDTO((prev) => ({
        ...prev,
        employmentType:
          (selectedItem?.id as EmploymentType) || EmploymentType.HALF,
      }));
    },
    []
  );
  const handleBonusChange = useCallback((bonusPercent: number) => {
    setEmployeeDTO((prev) => ({
      ...prev,
      bonusPercent,
    }));
  }, []);
  const handleSaleBonusChange = useCallback((saleBonusPercent: number) => {
    setEmployeeDTO((prev) => ({
      ...prev,
      saleBonusPercent,
    }));
  }, []);

  return (
    <div className={`emp-form flex-column ${className}`}>
      <div className="emp-form-row flex align-items-center space-between g-10px">
        <label className="emp-form-label">Imię:</label>
        <TextInput
          dropdown={false}
          value={employeeDTO.name}
          onSelect={(inputName) => {
            if (typeof inputName === "string") {
              handleEmployeeName(inputName);
            }
          }}
        />
      </div>
      <div className="emp-form-row flex align-items-center space-between g-10px">
        <label className="emp-form-label">Nazwisko:</label>
        <TextInput
          dropdown={false}
          value={employeeDTO.lastName}
          placeholder=""
          onSelect={(lastName) => {
            if (typeof lastName === "string") {
              handleLastName(lastName);
            }
          }}
        />
      </div>
      <div className="emp-form-row flex align-items-center space-between g-10px">
        <label className="emp-form-label">Etat:</label>
        <DropdownSelect
          items={employmentTypeOptions}
          placeholder="Wymiar etatu"
          value={
            employmentTypeOptions.find(
              (opt) => opt.id === employeeDTO.employmentType
            ) || null
          }
          allowNew={false}
          searchable={false}
          onChange={handleEmploymentTypeChange}
        />
      </div>
      <div className="emp-form-sliders flex-column">
        <Slider
          onChange={handleBonusChange}
          min={0}
          max={100}
          step={1}
          unit={" %"}
          value={employeeDTO.bonusPercent || 0}
          label={"Premia utarg:"}
        />
        <Slider
          onChange={handleSaleBonusChange}
          min={0}
          max={100}
          step={1}
          unit={" %"}
          value={employeeDTO.saleBonusPercent || 0}
          label={"Premia sprzedaż:"}
        />
        {action === Action.EDIT && (
          <p className="emp-form-note">
            Zmiana premii obowiązuje od 1. dnia miesiąca, w którym ją
            wprowadzasz.
          </p>
        )}
      </div>
    </div>
  );
}

export default EmployeeForm;
