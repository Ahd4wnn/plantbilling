# Plantora User Flows & Role-Based Access Control (RBAC)

This guide documents the role definitions, permissions, routing behaviors, and user flows of the Plantora Billing System.

---

## 1. System Role Definitions & Home Routes

The application defines three user roles. Upon logging in, users are automatically routed to their role-specific landing page:

| Role | Target Persona | Home Route | Primary Dashboard |
| :--- | :--- | :--- | :--- |
| **Site Admin** (`admin`) | Overall Platform Administrator | `/admin` | Shops management, cross-shop audit metrics, and database admin. |
| **Nursery Owner** (`shop_owner`) | Business Owner / Shop Admin | `/app/products` | Catalog setup, salesperson management, business profiles, and sales analytics. |
| **Salesperson** (`salesperson`) | Cashier / POS Counter Operator | `/app/bill` | Cashier POS screen, product grid, cart checkouts, and customer registration. |

---

## 2. Role-Based Permissions Matrix

The tables below map application features to roles on both the frontend and backend layers.

### Frontend UI Permissions

| Screen / Feature | Admin (`admin`) | Shop Owner (`shop_owner`) | Salesperson (`salesperson`) |
| :--- | :---: | :---: | :---: |
| **Create/Edit Shop Profile** | **Yes** | **No** | **No** |
| **Reset Shop Owner Passwords** | **Yes** | **No** | **No** |
| **POS Billing Terminal** | **No** *(Redirects)* | **No** *(Redirects)* | **Yes** |
| **Catalog Management (Add/Edit)** | **No** | **Yes** | **Yes** *(Read-Only catalog view)* |
| **Sales Reports & daily summaries** | **Yes** *(All shops)* | **Yes** *(Own shop)* | **Yes** *(Own shop)* |
| **Create/Deactivate Sales Staff** | **No** | **Yes** | **No** |
| **Edit Invoices / Bills** | **Yes** | **Yes** | **No** |
| **Delete Invoices / Bills** | **Yes** | **No** | **No** |
| **Manage WhatsApp Message Templates** | **No** | **Yes** | **No** |

### Backend API Gateways
Located in `backend/app/auth/dependencies.py`, the backend enforces role security via FastAPI dependencies:

- **`require_admin`**: Allowed for `ROLE_ADMIN` only.
  * Used for: Shop creation, site-wide client listings, and invoice deletion.
- **`require_shop_owner_only`**: Allowed for `ROLE_SHOP_OWNER` only.
  * Used for: Staff creation, credentials reset, de-activating staff, and updating business profile metadata.
- **`require_shop_owner_or_admin`**: Allowed for `ROLE_ADMIN` and `ROLE_SHOP_OWNER`.
  * Used for: Modifying checked-out bills, deleting customer records, and clearing expenses.
- **`require_shop_owner`**: Allowed for `ROLE_SHOP_OWNER` and `ROLE_SALESPERSON`.
  * Used for: Accessing product catalog, check out bills, logging customers, and logging expenses.
- **`require_shop_or_admin`**: Allowed for `ROLE_ADMIN`, `ROLE_SHOP_OWNER`, and `ROLE_SALESPERSON`.
  * Used for: Fetching daily sales summaries, list bills, and reading customer ledgers.

---

## 3. Step-by-Step User Flows

### Flow A: The Salesperson Flow (Billing & Checkout)

1. **Authentication**:
   - The salesperson signs in at `/login`.
   - The router detects the `salesperson` role and forwards them to the home route `/app/bill`.
2. **Cashier Operation (Ring up items)**:
   - **Scanning**: The cashier points the device camera at a plant barcode using the **Barcode Scanner**. The scanner parses the code, queries the catalog, and inserts the matching product into the cart.
   - **Manual Selection**: The cashier browses the product grid. They use **Text Search** or **Voice Search** (utilizing the phonetic normalizer) to find items like "Bonsai Ficus". Tapping an item adds it to the cart.
   - **Quick Add**: If a custom/non-catalog item is purchased, the cashier taps the "Quick Add" button, inputs a custom description (e.g., *"Red Rose Hybrid Large"*) and price, and adds it to the cart.
3. **Cart Configurations**:
   - The cashier taps the Cart Bar to open the checkout drawer.
   - They adjust quantities, review unit prices, and apply flat or percentage-based discounts.
4. **Checkout Settlement**:
   - The cashier adds customer details (name and phone number) if they want to track credit or send WhatsApp alerts.
   - The payment is settled. The cashier can choose a single payment method or split the bill across Cash, UPI (shows dynamically computed QR code), and Due Balance (adds debt to the customer ledger).
   - Tapping "Submit" triggers `create_bill` on the backend, generating an invoice number.
5. **Receipt Output**:
   - Tapping "Print Receipt" prints the bill to their local physical device (RawBT Bluetooth, iOS AirPrint, or Desktop system print) based on local config preferences.
   - Tapping "Share on WhatsApp" queues a Meta template invoice to the customer's phone.

---

### Flow B: The Shop Owner Flow (Management & Operations)

1. **Catalog Setup**:
   - The owner logs in and land on `/app/products`.
   - They can tap "Add Product" to add items individually, or choose "Bulk Upload" to parse an Excel spreadsheet / CSV catalog along with a ZIP folder of product images.
2. **Financial Audits**:
   - The owner navigates to the **Sales** tab to inspect daily transactions. They can filter by date, payment methods, or salespeople.
   - They review category-wide performance metrics and check the nursery's running daily profit margins.
3. **Expense Logger**:
   - The owner logs daily business costs (such as *fertilizer*, *soil truck*, *staff salaries*, or *transport*) to compute correct net profit margins.
4. **Staff Management**:
   - The owner goes to `/app/more`.
   - Under the "Salespeople" panel, they add cashier accounts, generate random secure temporary passwords, reset credentials, or deactivate staff accounts.
5. **Shop Settings**:
   - Under the "Business Details" panel, the owner updates their shop name, address, business phone, and UPI details.
   - Under the "WhatsApp Settings" panel, they toggle auto-sending of invoices on checkout, enable A4 PDF generation, and customize the template strings.

---

### Flow C: The Platform Admin Flow (Platform Oversight)

1. **Shop Control**:
   - The admin logs in and lands on `/admin/shops`.
   - They can view all nurseries registered on the platform, create new shop records, assign store owners, and edit license active statuses.
2. **Client Audit Ledger**:
   - The admin monitors total platform billing volumes, audits customer credit logs, and checks overall system health metrics.
3. **Superuser Interventions**:
   - If an invoice is created incorrectly and must be deleted from the database, the admin opens the bill details from `/admin/sales` and triggers the "Delete" button (only accessible to admins).
