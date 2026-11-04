import {
  ExpenseCategory,
  ExpenseCategoryCode,
  NewCompanyExpense,
  SalaryComponent,
  getSalaryComponentDisplay,
} from "../models/expense";
import { VatRate } from "../models/vatrate";
import { MONTHS, getPreviousMonth, getPreviousMonthYear, getCurrentMonth, getCurrentYear } from "./dateUtils";
import ExpenseService from "../services/ExpenseService";

export const getPayoutPeriod = (month: number, year: number): { month: number; year: number } => ({
  month: month === 12 ? 1 : month + 1,
  year: month === 12 ? year + 1 : year,
});

const round2 = (value: number): number => Math.round(value * 100) / 100;

export const buildSalaryExpense = async (
  employeeId: number,
  month: number,
  year: number,
  bonusAmount: number,
  saleBonusAmount: number | null
): Promise<NewCompanyExpense> => {
  const template = await getCategoryTemplate(ExpenseCategory.SALARY);
  const expenseItems = (template.expenseItems ?? [])
    .filter(
      (item) =>
        item.salaryComponent !== SalaryComponent.SALE_BONUS || saleBonusAmount != null
    )
    .map((item) => {
      if (item.salaryComponent === SalaryComponent.BONUS) {
        return { ...item, price: round2(bonusAmount) };
      }
      if (item.salaryComponent === SalaryComponent.SALE_BONUS) {
        return { ...item, price: round2(saleBonusAmount ?? 0) };
      }
      return item;
    });

  const payout = getPayoutPeriod(month, year);
  const now = new Date();
  const isPayoutMonthNow =
    now.getFullYear() === payout.year && now.getMonth() + 1 === payout.month;

  return {
    source: `Wypłata za ${MONTHS[month - 1].name} ${year}`,
    expenseDate: isPayoutMonthNow
      ? now.toISOString().split("T")[0]
      : `${payout.year}-${String(payout.month).padStart(2, "0")}-10`,
    category: ExpenseCategory.SALARY,
    employeeId,
    expenseItems,
  };
};



export const getCategoryTemplate = async (category: ExpenseCategoryCode): Promise<Partial<NewCompanyExpense>> => {
  switch (category) {
    case ExpenseCategory.SALARY: {
      let salaryItem: NewCompanyExpense["expenseItems"][0] = {
        name: getSalaryComponentDisplay(SalaryComponent.BASE),
        quantity: 1,
        vatRate: VatRate.VAT_NP,
        price: 0,
        salaryComponent: SalaryComponent.BASE,
      };
      try {
        const latestExpense = await ExpenseService.getLatestExpenseByCategory(category);
        const found = latestExpense?.expenseItems?.find(item => item.name.startsWith("Wypłata"));
        if (found) salaryItem = { ...salaryItem, name: found.name, quantity: found.quantity, vatRate: found.vatRate, price: found.price };
      } catch (error) {
        console.error("Error fetching latest SALARY expense:", error);
      }
      return {
        source: `Wypłata za ${getPreviousMonth()} ${getPreviousMonthYear()}`,
        employeeId: null,
        expenseItems: [
          salaryItem,
          {
            name: getSalaryComponentDisplay(SalaryComponent.BONUS),
            quantity: 1,
            vatRate: VatRate.VAT_NP,
            price: 0,
            salaryComponent: SalaryComponent.BONUS,
          },
          {
            name: getSalaryComponentDisplay(SalaryComponent.SALE_BONUS),
            quantity: 1,
            vatRate: VatRate.VAT_NP,
            price: 0,
            salaryComponent: SalaryComponent.SALE_BONUS,
          },
        ],
      };
    }
    case ExpenseCategory.RENT: {
      let expenseItems: NewCompanyExpense["expenseItems"] = [
        {
          name: "Czynsz",
          quantity: 1,
          vatRate: VatRate.VAT_NP,
          price: 0,
        },
      ];
      try {
        const latestExpense = await ExpenseService.getLatestExpenseByCategory(category);
        const rentItem = latestExpense?.expenseItems?.find(item => item.name.startsWith("Czynsz"));
        if (rentItem) {
          expenseItems = [
            {
              name: rentItem.name,
              quantity: rentItem.quantity,
              vatRate: rentItem.vatRate,
              price: rentItem.price,
            },
          ];
        }
      } catch (error) {
        console.error("Error fetching latest RENT expense:", error);
      }
      return {
        source: `Czynsz-${getCurrentMonth()} ${getCurrentYear()}`,
        expenseItems,
      };
    }
    case ExpenseCategory.ZUS: {
      let expenseItems: NewCompanyExpense["expenseItems"] = [
        {
          name: "ZUS",
          quantity: 1,
          vatRate: VatRate.VAT_NP,
          price: 0,
        },
      ];
      try {
        const latestExpense = await ExpenseService.getLatestExpenseByCategory(category);
        const zusItem = latestExpense?.expenseItems?.find(item => item.name === "ZUS");
        if (zusItem) {
          expenseItems = [
            {
              name: zusItem.name,
              quantity: zusItem.quantity,
              vatRate: zusItem.vatRate,
              price: zusItem.price,
            },
          ];
        }
      } catch (error) {
        console.error("Error fetching latest ZUS expense:", error);
      }
      return {
        source: "ZUS",
        expenseItems,
      };
    }
    case ExpenseCategory.TAXES: {
      return {
        source: "Urząd Skarbowy",
        expenseItems:[{
          name: "PIT-36",
          quantity: 1,
          vatRate: VatRate.VAT_NP,
          price: 0,
        },
      {
          name: "PIT-4",
          quantity: 1,
          vatRate: VatRate.VAT_NP,
          price: 0,
        },
      {
          name: "VAT",
          quantity: 1,
          vatRate: VatRate.VAT_NP,
          price: 0,
        }],
      };
    }
    case ExpenseCategory.PRODUCTS:
      return {
        source: "",
        expenseItems: [],
      };
    default:
      return {};
  }
};