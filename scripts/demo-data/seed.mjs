const API = (process.env.API_URL ?? "http://localhost:8080/api").replace(/\/$/, "");
const ADMIN_USER = process.env.ADMIN_USER;
const ADMIN_PASS = process.env.ADMIN_PASS;
const DEMO_PASSWORD = process.env.DEMO_PASSWORD ?? "DemoHaslo2026!";
const DAYS_BACK = Number(process.env.DAYS_BACK ?? 180);
const VISIT_YEAR = 2026;
const VISIT_MONTH = 10;
const MAX_DEBTS = 2;

if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(API + "/")) {
  console.error(`API_URL must point to localhost, got: ${API}`);
  process.exit(1);
}
if (!ADMIN_USER || !ADMIN_PASS) {
  console.error("Set ADMIN_USER and ADMIN_PASS (the local administrator account).");
  process.exit(1);
}

let seed = 20261102;
const rand = () => {
  seed |= 0;
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const pick = (list) => list[Math.floor(rand() * list.length)];
const chance = (p) => rand() < p;
const int = (min, max) => min + Math.floor(rand() * (max - min + 1));

const iso = (date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
const daysAgo = (n) => {
  const d = new Date();
  d.setHours(12, 0, 0, 0);
  d.setDate(d.getDate() - n);
  return d;
};

let failures = 0;
async function api(token, method, path, body) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    failures++;
    console.warn(`  ! ${method} ${path} -> ${res.status} ${text.slice(0, 200)}`);
    return null;
  }
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return {};
  }
}

async function login(username, password) {
  const data = await api(null, "POST", "/auth/login", { username, password });
  if (!data?.token) {
    throw new Error(`Login failed for ${username}. 2FA must be off for demo accounts.`);
  }
  return data.token;
}

const EMPLOYEES = [
  { name: "Anna", lastName: "Nowak", employmentType: "FULL", bonusPercent: 20, saleBonusPercent: 10, user: "anna.nowak" },
  { name: "Katarzyna", lastName: "Wiśniewska", employmentType: "FULL", bonusPercent: 18, saleBonusPercent: 10, user: "katarzyna.w" },
  { name: "Magdalena", lastName: "Kowalczyk", employmentType: "THREE_QUARTERS", bonusPercent: 15, saleBonusPercent: 8, user: "magda.k" },
  { name: "Joanna", lastName: "Zielińska", employmentType: "HALF", bonusPercent: 15, saleBonusPercent: 8, user: "joanna.z" },
];

const SERVICE_CATEGORIES = [
  { name: "Podologia", color: "59,130,246", services: [
    { name: "Konsultacja podologiczna", price: 150, duration: 30 },
    { name: "Terapia wrastającego paznokcia", price: 220, duration: 60, variants: [
      { name: "Jeden paznokieć", price: 220, duration: 60 },
      { name: "Dwa paznokcie", price: 320, duration: 90 },
    ] },
    { name: "Usunięcie odcisku", price: 180, duration: 45 },
    { name: "Klamra ortonyksyjna", price: 260, duration: 60 },
  ] },
  { name: "Pedicure", color: "34,197,94", services: [
    { name: "Pedicure podologiczny", price: 200, duration: 60 },
    { name: "Pedicure leczniczy", price: 240, duration: 75 },
    { name: "Opracowanie pięt", price: 140, duration: 40 },
  ] },
  { name: "Diagnostyka", color: "168,85,247", services: [
    { name: "Badanie stóp cukrzycowych", price: 190, duration: 45 },
    { name: "Analiza chodu", price: 250, duration: 60 },
  ] },
  { name: "Wkładki", color: "249,115,22", services: [
    { name: "Wkładki ortopedyczne - pomiar", price: 300, duration: 45 },
    { name: "Odbiór i dopasowanie wkładek", price: 0, duration: 20 },
  ] },
];

const BRANDS = ["Pedicura", "DermaFoot", "Calluna", "OrthoStep", "Nailvia"];

const PRODUCTS = [
  { name: "Krem z mocznikiem 10%", brand: "DermaFoot", price: 49, purchase: 24, volume: 75 },
  { name: "Krem na pękające pięty", brand: "Calluna", price: 55, purchase: 27, volume: 75 },
  { name: "Krem przeciwgrzybiczy", brand: "DermaFoot", price: 62, purchase: 31, volume: 50 },
  { name: "Balsam chłodzący", brand: "Pedicura", price: 45, purchase: 21, volume: 100 },
  { name: "Pianka do stóp", brand: "Calluna", price: 69, purchase: 34, volume: 125 },
  { name: "Olejek do skórek", brand: "Nailvia", price: 39, purchase: 17, volume: 15 },
  { name: "Serum na paznokcie", brand: "Nailvia", price: 59, purchase: 28, volume: 15 },
  { name: "Spray antybakteryjny", brand: "Pedicura", price: 42, purchase: 19, volume: 100 },
  { name: "Separator palców", brand: "OrthoStep", price: 29, purchase: 11, volume: null },
  { name: "Opaska na halluksa", brand: "OrthoStep", price: 79, purchase: 36, volume: null },
  { name: "Plastry na odciski", brand: "Calluna", price: 25, purchase: 10, volume: null },
  { name: "Wkładki żelowe", brand: "OrthoStep", price: 89, purchase: 41, volume: null },
];

const FIRST_NAMES_F = ["Agnieszka", "Barbara", "Ewa", "Małgorzata", "Monika", "Natalia", "Paulina", "Zofia", "Helena", "Teresa", "Karolina", "Aleksandra", "Julia", "Maria", "Beata", "Renata"];
const FIRST_NAMES_M = ["Piotr", "Tomasz", "Marek", "Krzysztof", "Andrzej", "Paweł", "Michał", "Jan", "Stanisław", "Grzegorz", "Adam", "Łukasz"];
const LAST_NAMES_F = ["Kowalska", "Wójcik", "Kamińska", "Lewandowska", "Dąbrowska", "Szymańska", "Woźniak", "Kozłowska", "Jankowska", "Mazur", "Krawczyk", "Piotrowska", "Grabowska", "Pawłowska", "Michalska", "Król"];
const LAST_NAMES_M = ["Kowalski", "Wójcik", "Kamiński", "Lewandowski", "Dąbrowski", "Szymański", "Woźniak", "Kozłowski", "Jankowski", "Mazur", "Krawczyk", "Piotrowski"];

const QUESTIONNAIRE = [
  { label: "Choroby przewlekłe", labelEn: "Chronic conditions", type: "SECTION" },
  { label: "Cukrzyca", labelEn: "Diabetes", type: "CHECKBOX" },
  { label: "Nadciśnienie", labelEn: "Hypertension", type: "CHECKBOX" },
  { label: "Choroby krążenia", labelEn: "Circulatory disorders", type: "CHECKBOX" },
  { label: "Choroby tarczycy", labelEn: "Thyroid disorders", type: "CHECKBOX" },
  { label: "Alergie i leki", labelEn: "Allergies and medication", type: "SECTION" },
  { label: "Alergie", labelEn: "Allergies", type: "TEXT" },
  { label: "Przyjmowane leki", labelEn: "Current medication", type: "TEXT" },
  { label: "Leki przeciwzakrzepowe", labelEn: "Anticoagulants", type: "CHECKBOX" },
  { label: "Stan stóp", labelEn: "Foot condition", type: "SECTION" },
  { label: "Grzybica stóp lub paznokci", labelEn: "Athlete's foot or nail fungus", type: "CHECKBOX" },
  { label: "Wrastające paznokcie", labelEn: "Ingrown nails", type: "CHECKBOX" },
  { label: "Brodawki wirusowe", labelEn: "Plantar warts", type: "CHECKBOX" },
  { label: "Dodatkowe informacje", labelEn: "Additional information", type: "TEXT" },
];

const LOREM_WORDS = (
  "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore " +
  "magna aliqua enim ad minim veniam quis nostrud exercitation ullamco laboris nisi aliquip ex ea commodo " +
  "consequat duis aute irure in reprehenderit voluptate velit esse cillum fugiat nulla pariatur excepteur sint " +
  "occaecat cupidatat non proident sunt culpa qui officia deserunt mollit anim id est laborum"
).split(" ");
const loremSentence = () => {
  const words = Array.from({ length: int(6, 14) }, () => pick(LOREM_WORDS));
  return words.join(" ").replace(/^./, (c) => c.toUpperCase()) + ".";
};
const lorem = () => Array.from({ length: int(1, 12) }, loremSentence).join(" ");

async function main() {
  console.log(`Demo data -> ${API}`);
  const admin = await login(ADMIN_USER, ADMIN_PASS);

  console.log("Questionnaire fields");
  await api(admin, "PUT", "/card-settings/questionnaire", {
    englishEnabled: true,
    fields: QUESTIONNAIRE.map((f) => ({ ...f, archived: false })),
  });

  console.log("Employees and accounts");
  const roles = (await api(admin, "GET", "/roles/all")) ?? [];
  const userRole = roles.find((r) => r.name === "ROLE_USER");
  const employees = [];
  for (const e of EMPLOYEES) {
    const created = await api(admin, "POST", "/employee", {
      name: e.name,
      lastName: e.lastName,
      employmentType: e.employmentType,
      bonusPercent: e.bonusPercent,
      saleBonusPercent: e.saleBonusPercent,
    });
    if (!created) continue;
    employees.push({ ...created, user: e.user });
    await api(admin, "POST", "/auth/register", { username: e.user, password: DEMO_PASSWORD, role: ["ROLE_USER"] });
  }
  const users = (await api(admin, "GET", "/user/all")) ?? [];
  for (const emp of employees) {
    const u = users.find((x) => x.username === emp.user);
    if (!u) continue;
    await api(admin, "PUT", `/user/${u.id}`, {
      id: u.id,
      username: u.username,
      avatar: u.avatar,
      roles: userRole ? [userRole] : u.roles,
      employee: { id: emp.id, name: emp.name, lastName: emp.lastName },
    });
  }
  const tokens = new Map();
  for (const emp of employees) {
    try {
      tokens.set(emp.id, await login(emp.user, DEMO_PASSWORD));
    } catch (err) {
      console.warn(`  ! ${err.message}`);
    }
  }

  console.log("Services");
  const services = [];
  for (const cat of SERVICE_CATEGORIES) {
    const category = await api(admin, "POST", "/service-categories", { name: cat.name, color: cat.color });
    if (!category) continue;
    for (const s of cat.services) {
      const created = await api(admin, "POST", "/services", {
        name: s.name,
        price: s.price,
        duration: s.duration,
        category: { id: category.id },
        variants: (s.variants ?? []).map((v) => ({ ...v, isDeleted: false })),
      });
      if (created) services.push(created);
    }
  }

  console.log("Products");
  const systemCategory = ((await api(admin, "GET", "/categories/all")) ?? []).find((c) => c.system);
  if (!systemCategory) throw new Error("System product category not found.");
  const brands = new Map();
  const createdBrands = (await api(admin, "POST", "/brands/batch", BRANDS.map((name) => ({ name })))) ?? [];
  createdBrands.forEach((b) => brands.set(b.name, b.id));
  const products = [];
  for (const p of PRODUCTS) {
    const created = await api(admin, "POST", "/products", {
      name: p.name,
      category: { id: systemCategory.id },
      brand: { id: brands.get(p.brand) },
      supply: int(25, 60),
      sellingPrice: p.price,
      vatRate: "VAT_23",
      volume: p.volume,
      unit: p.volume ? "ML" : null,
      lowStockThreshold: 5,
      fallbackNetPurchasePrice: p.purchase,
      fallbackVatRate: "VAT_23",
      description: "",
    });
    if (created) products.push(created);
  }

  console.log("Clients");
  const clients = [];
  for (let i = 0; i < 40; i++) {
    const female = chance(0.7);
    const firstName = female ? pick(FIRST_NAMES_F) : pick(FIRST_NAMES_M);
    const lastName = female ? pick(LAST_NAMES_F) : pick(LAST_NAMES_M);
    const birth = daysAgo(int(22, 82) * 365 + int(0, 364));
    const created = await api(admin, "POST", "/clients", {
      firstName,
      lastName,
      phoneNumber: `+48 6${int(0, 9)}${int(0, 9)} ${String(int(100, 999))} ${String(int(100, 999))}`,
      birthDate: iso(birth),
      signedRegulations: chance(0.8),
      boostClient: false,
      redFlag: chance(0.05),
    });
    if (created) clients.push(created);
  }

  console.log("Visits");
  const visitsByClient = new Map();
  let visitCount = 0;
  let debtCount = 0;
  const daysInMonth = new Date(VISIT_YEAR, VISIT_MONTH, 0).getDate();
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(VISIT_YEAR, VISIT_MONTH - 1, day, 12);
    if (date.getDay() === 0) continue;
    const perDay = date.getDay() === 6 ? int(2, 4) : int(4, 9);
    for (let v = 0; v < perDay; v++) {
      const client = pick(clients);
      const employee = pick(employees);
      const service = pick(services);
      const variant = service.variants?.length ? pick(service.variants) : null;
      const price = variant ? variant.price : service.price;
      const items = [{
        service,
        serviceVariant: variant,
        name: service.name,
        duration: variant ? variant.duration : service.duration,
        price,
        finalPrice: price,
        boostItem: false,
      }];
      const saleItems = chance(0.35)
        ? Array.from({ length: int(1, 2) }, () => {
            const product = pick(products);
            return { product, name: product.name, price: product.sellingPrice };
          })
        : [];
      const draft = {
        client: { id: client.id },
        employee: { id: employee.id },
        date: iso(date),
        items,
        sale: saleItems.length ? { items: saleItems } : null,
        serviceDiscounts: [],
        debtRedemptions: [],
        isBoost: false,
        isVip: false,
        absence: false,
        receipt: true,
        payments: [],
      };
      const preview = await api(admin, "POST", "/visits/preview", draft);
      if (!preview) continue;
      const total = Number(preview.totalValue ?? 0);
      const method = pick(["CARD", "CARD", "CARD", "CASH", "BLIK", "TRANSFER"]);
      const isDebt = debtCount < MAX_DEBTS && total > 0 && chance(0.015);
      const amount = isDebt ? Math.round(total * 0.5) : total;
      const created = await api(tokens.get(employee.id) ?? admin, "POST", "/visits", {
        ...draft,
        payments: total > 0 ? [{ method, amount }] : [],
      });
      if (!created) continue;
      visitCount++;
      if (isDebt) debtCount++;
      const list = visitsByClient.get(client.id) ?? [];
      list.push({ date: iso(date), employeeId: employee.id, service: service.name });
      visitsByClient.set(client.id, list);
    }
  }
  console.log(`  ${visitCount} visits, ${debtCount} with debt`);

  console.log("Client cards");
  const cardClients = [...visitsByClient.entries()]
    .filter(([, visits]) => visits.length >= 2)
    .slice(0, 15);
  for (const [clientId, visits] of cardClients) {
    const client = clients.find((c) => c.id === clientId);
    await api(admin, "PUT", `/cards/${clientId}/client-data`, {
      firstName: client.firstName,
      lastName: client.lastName,
      birthDate: client.birthDate,
      phoneNumber: client.phoneNumber,
      redFlag: client.redFlag,
      signedRegulations: true,
      remarks: chance(0.4) ? "Pacjentka preferuje wizyty w godzinach porannych." : "",
    });
    const questionnaire = await api(admin, "GET", `/cards/${clientId}/questionnaire`);
    if (questionnaire?.items) {
      const answers = questionnaire.items
        .filter((item) => item.type !== "SECTION")
        .map((item) => {
          if (item.type === "CHECKBOX") return { fieldId: item.fieldId, checked: chance(0.3), text: null };
          const texts = {
            Alergie: pick(["Brak", "Penicylina", "Lateks", "Brak znanych alergii"]),
            "Przyjmowane leki": pick(["Metformina", "Brak", "Euthyrox 50", "Bisoprolol"]),
            "Dodatkowe informacje": pick(["", "Ból przy dłuższym chodzeniu", "Uprawia bieganie"]),
          };
          return { fieldId: item.fieldId, checked: null, text: texts[item.label] ?? "" };
        });
      await api(admin, "PUT", `/cards/${clientId}/questionnaire`, {
        expectedVersion: questionnaire.version ?? null,
        answers,
      });
    }
  }

  console.log("Therapy entries");
  let entryCount = 0;
  for (const [clientId, visits] of visitsByClient.entries()) {
    const uniqueDays = [...new Map(visits.map((v) => [v.date, v])).values()];
    for (const visit of uniqueDays) {
      const token = tokens.get(visit.employeeId) ?? admin;
      const created = await api(token, "POST", `/cards/${clientId}/therapy-entries`, {
        entryDate: visit.date,
        content: lorem(),
      });
      if (created) entryCount++;
    }
  }
  console.log(`  ${entryCount} entries`);

  console.log("Reviews and vouchers");
  for (const client of clients.slice(0, 18)) {
    await api(admin, "POST", "/reviews", {
      client: { id: client.id },
      source: pick(["GOOGLE", "BOOKSY"]),
      issueDate: iso(daysAgo(int(5, DAYS_BACK))),
      isUsed: chance(0.4),
    });
  }
  for (const client of clients.slice(20, 26)) {
    const issue = daysAgo(int(10, 120));
    const expiry = new Date(issue);
    expiry.setMonth(expiry.getMonth() + 6);
    await api(admin, "POST", "/vouchers", {
      client: { id: client.id },
      value: pick([100, 150, 200, 300]),
      issueDate: iso(issue),
      expiryDate: iso(expiry),
    });
  }

  console.log("Expenses");
  for (let month = 0; month < Math.ceil(DAYS_BACK / 30); month++) {
    const base = daysAgo(month * 30 + 5);
    const date = iso(base);
    await api(admin, "POST", "/company-expenses", {
      source: "Wynajem lokalu",
      expenseDate: date,
      category: "RENT",
      currency: "PLN",
      expenseItems: [{ name: "Czynsz za lokal", quantity: 1, vatRate: "VAT_23", price: 4200 }],
    });
    await api(admin, "POST", "/company-expenses", {
      source: "Dostawca mediów",
      expenseDate: iso(daysAgo(month * 30 + 9)),
      category: "SERVICES",
      currency: "PLN",
      expenseItems: [
        { name: "Energia elektryczna", quantity: 1, vatRate: "VAT_23", price: int(380, 520) },
        { name: "Internet", quantity: 1, vatRate: "VAT_23", price: 89 },
      ],
    });
    await api(admin, "POST", "/company-expenses", {
      source: "Hurtownia podologiczna",
      expenseDate: iso(daysAgo(month * 30 + 14)),
      category: "SUPPLIES",
      currency: "PLN",
      expenseItems: [
        { name: "Frezy jednorazowe", quantity: int(2, 5), vatRate: "VAT_23", price: 64.99 },
        { name: "Rękawiczki nitrylowe", quantity: int(3, 6), vatRate: "VAT_8", price: 32.5 },
        { name: "Środek do dezynfekcji", quantity: 2, vatRate: "VAT_23", price: 79 },
      ],
    });
    const productItems = Array.from({ length: 3 }, () => {
      const p = pick(products);
      return { name: p.name, productId: p.id, quantity: int(4, 12), vatRate: "VAT_23", price: Number(p.fallbackNetPurchasePrice ?? 20) };
    });
    await api(admin, "POST", "/company-expenses", {
      source: "Hurtownia kosmetyczna",
      expenseDate: iso(daysAgo(month * 30 + 18)),
      category: "PRODUCTS",
      currency: "PLN",
      expenseItems: productItems,
    });
    for (const emp of employees) {
      await api(admin, "POST", "/company-expenses", {
        source: `${emp.name} ${emp.lastName}`,
        expenseDate: iso(daysAgo(month * 30 + 2)),
        category: "SALARY",
        employeeId: emp.id,
        currency: "PLN",
        expenseItems: [
          { name: "Wynagrodzenie podstawowe", quantity: 1, vatRate: "VAT_ZW", price: emp.employmentType === "FULL" ? 4800 : 2900, salaryComponent: "BASE" },
        ],
      });
    }
  }
  await api(admin, "POST", "/company-expenses", {
    source: "Cloud Software Ltd",
    expenseDate: iso(daysAgo(12)),
    category: "OTHER",
    currency: "EUR",
    invoiceNumber: "CS-2026-1042",
    seller: { nip: "IE1234567AB", name: "Cloud Software Ltd", address: "1 Demo Street, Dublin" },
    expenseItems: [{ name: "Subskrypcja oprogramowania", quantity: 1, vatRate: "VAT_NP", price: 18 }],
  });

  console.log(`Done. Failed requests: ${failures}`);
  console.log(`Demo accounts: ${EMPLOYEES.map((e) => e.user).join(", ")} / ${DEMO_PASSWORD}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
