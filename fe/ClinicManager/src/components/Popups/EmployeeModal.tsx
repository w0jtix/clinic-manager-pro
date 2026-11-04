import { useState, useEffect, useCallback } from "react";
import Modal from "../Modal";
import EmployeeForm from "../Employee/EmployeeForm";
import { NewEmployee, Employee, EmploymentType } from "../../models/employee";
import { useAlert } from "../Alert/AlertProvider";
import { AlertType } from "../../models/alert";
import { Action } from "../../models/action";
import EmployeeService from "../../services/EmployeeService";
import { validateEmployeeForm } from "../../utils/validators";

export interface EmployeeModalProps {
  onClose: () => void;
  onSaved?: () => void;
  employeeId?: number | null;
  className?: string;
}

export function EmployeeModal({
  onClose,
  onSaved,
  employeeId,
  className = "",
}: EmployeeModalProps) {
  const [fetchedEmployee, setFetchedEmployee] = useState<Employee | null>(null);
  const [employeeDTO, setEmployeeDTO] = useState<NewEmployee>({
    name: "",
    lastName: "",
    employmentType: EmploymentType.HALF,
    bonusPercent: 15,
    saleBonusPercent: 30,
  });
  const { showAlert } = useAlert();
  const action = employeeId ? Action.EDIT : Action.CREATE;

  useEffect(() => {
    if (!employeeId) return;
    EmployeeService.getEmployeeById(employeeId)
      .then((data) => {
        setFetchedEmployee(data);
        setEmployeeDTO(data);
      })
      .catch((error) => {
        console.error("Error fetching employee: ", error);
        showAlert("Błąd!", AlertType.ERROR);
      });
  }, [employeeId]);

  const handleSave = useCallback(async () => {
    const error = validateEmployeeForm(employeeDTO, fetchedEmployee, action);
    if (error) {
      showAlert(error, AlertType.ERROR);
      return;
    }
    try {
      if (action === Action.CREATE) {
        await EmployeeService.createEmployee(employeeDTO as NewEmployee);
        showAlert(
          `Pracownik ${employeeDTO.name} ${employeeDTO.lastName} utworzony!`,
          AlertType.SUCCESS
        );
      } else if (employeeId) {
        await EmployeeService.updateEmployee(employeeId, employeeDTO as NewEmployee);
        showAlert("Pracownik zaktualizowany!", AlertType.SUCCESS);
      }
      onSaved?.();
      onClose();
    } catch (e) {
      showAlert(
        `Błąd ${action === Action.CREATE ? "tworzenia" : "aktualizacji"} pracownika!`,
        AlertType.ERROR
      );
    }
  }, [employeeDTO, fetchedEmployee, action, employeeId, showAlert, onSaved, onClose]);

  return (
    <Modal
      dirtyValue={employeeDTO}
      title={action === Action.CREATE ? "Nowy Pracownik" : "Edytuj Pracownika"}
      onClose={onClose}
      size="sm"
      onConfirm={handleSave}
      className={className}
    >
      <EmployeeForm
        employeeDTO={employeeDTO}
        setEmployeeDTO={setEmployeeDTO}
        action={action}
      />
    </Modal>
  );
}

export default EmployeeModal;
