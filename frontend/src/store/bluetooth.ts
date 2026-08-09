/* eslint-disable @typescript-eslint/no-explicit-any */
import { create } from "zustand";
import { fetchBillDetail, type BillDetail } from "@/api/sales";
import type { BillOut } from "@/api/billing";

// Fallback type declarations for Web Bluetooth API when types are not installed in TypeScript env
type BluetoothDevice = any;
type BluetoothRemoteGATTCharacteristic = any;
type BluetoothRemoteGATTServer = any;

// Module-level cached references to avoid React render cycle interference
let activeDevice: BluetoothDevice | null = null;
let activeCharacteristic: BluetoothRemoteGATTCharacteristic | null = null;
let activeSerialPort: any = null;
let activeUsbDevice: any = null;
let activeUsbEndpoint: number = -1;
let activeUsbInterface: number = -1;

// Helpers to search for writable characteristic in primary services
async function findPrinterCharacteristic(server: BluetoothRemoteGATTServer): Promise<BluetoothRemoteGATTCharacteristic> {
  const commonServices = [
    "0000ffe0-0000-1000-8000-00805f9b34fb", // FFE0 (most common BLE printer service)
    "0000ffe1-0000-1000-8000-00805f9b34fb", // FFE1 (sometimes service instead of characteristic)
    "0000ff00-0000-1000-8000-00805f9b34fb", // FF00
    "000018f0-0000-1000-8000-00805f9b34fb", // 18F0
    "e7e1a121-481d-11e5-885d-feff819cdc9f", // Custom Serial
    "0000fee7-0000-1000-8000-00805f9b34fb", // WeChat/Printer
    "00001101-0000-1000-8000-00805f9b34fb", // SPP (Serial Port Profile) UUID
  ];

  // Try common service UUIDs first
  for (const serviceUuid of commonServices) {
    try {
      const service = await server.getPrimaryService(serviceUuid);
      const characteristics = await service.getCharacteristics();
      const writeChar = characteristics.find(
        (c: any) => c.properties.write || c.properties.writeWithoutResponse
      );
      if (writeChar) return writeChar;
    } catch {
      // Continue
    }
  }

  // Fallback: Query all services and find any writable characteristic
  const services = await server.getPrimaryServices();
  for (const service of services) {
    try {
      const characteristics = await service.getCharacteristics();
      const writeChar = characteristics.find(
        (c: any) => c.properties.write || c.properties.writeWithoutResponse
      );
      if (writeChar) return writeChar;
    } catch {
      // Continue
    }
  }

  throw new Error("No writable Bluetooth characteristic found for printing.");
}

// Throttled BLE chunk writer to prevent buffer overflows
async function writeInChunks(characteristic: BluetoothRemoteGATTCharacteristic, data: Uint8Array) {
  const chunkSize = 20; // BLE safest MTU payload size
  for (let i = 0; i < data.length; i += chunkSize) {
    const chunk = data.slice(i, i + chunkSize);
    if (characteristic.writeValueWithoutResponse) {
      await characteristic.writeValueWithoutResponse(chunk);
    } else {
      await characteristic.writeValue(chunk);
    }
    // Small delay to let the thermal printer's buffer process print heads
    await new Promise((resolve) => setTimeout(resolve, 15));
  }
}

// Throttled Web Serial chunk writer to prevent buffer overflows
async function writeSerialInChunks(writer: any, data: Uint8Array) {
  const chunkSize = 64; // safe block size
  for (let i = 0; i < data.length; i += chunkSize) {
    const chunk = data.slice(i, i + chunkSize);
    await writer.write(chunk);
    // Tiny delay to let the printer parse the buffer
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
}

// Throttled Web USB chunk writer to prevent buffer overflows
async function writeUsbInChunks(device: any, endpoint: number, data: Uint8Array) {
  const chunkSize = 512; // Safe bulk transfer size
  for (let i = 0; i < data.length; i += chunkSize) {
    const chunk = data.slice(i, i + chunkSize);
    await device.transferOut(endpoint, chunk);
    // Tiny micro-delay for cheap print boards
    await new Promise((resolve) => setTimeout(resolve, 2));
  }
}

// Clean non-ASCII characters to prevent garbled print outputs on cheap thermal printers
function cleanAscii(str: string): string {
  return str
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "") // remove accents
    .replace(/[\u2013\u2014]/g, "-") // dashes
    .replace(/[\u2018\u2019]/g, "'") // single quotes
    .replace(/[\u201c\u201d]/g, '"') // double quotes
    .replace(/₹/g, "Rs.")            // Rupee sign
    .replace(/[^\x00-\x7F]/g, "");    // strip any remaining non-ascii characters
}

// Compile a BillDetail object into binary ESC/POS formatting commands
export function compileEscPosReceipt(bill: BillDetail, autoCut: boolean): Uint8Array {
  const encoder = new TextEncoder();
  const chunks: Uint8Array[] = [];

  const addBytes = (bytes: number[]) => {
    chunks.push(new Uint8Array(bytes));
  };

  const addText = (text: string) => {
    chunks.push(encoder.encode(cleanAscii(text)));
  };

  // 1. Initialize printer
  addBytes([0x1b, 0x40]);

  // 2. Header (Center align, Bold, Double size)
  addBytes([0x1b, 0x61, 0x01]); // Center
  addBytes([0x1b, 0x45, 0x01]); // Bold ON
  addBytes([0x1d, 0x21, 0x11]); // Double height + Double width
  addText((bill.business_name || bill.shop_name || "NURSERY RECEIPT").toUpperCase() + "\n");

  // Reset character size & bold
  addBytes([0x1d, 0x21, 0x00]); // Normal size
  addBytes([0x1b, 0x45, 0x00]); // Bold OFF

  // Shop contact info
  if (bill.business_address) {
    addText(`${bill.business_address.trim()}\n`);
  }
  if (bill.business_phone) {
    addText(`Contact: ${bill.business_phone.trim()}\n`);
  }

  // Divider
  addText("------------------------------------------------\n");

  // 3. Metadata (Left align)
  addBytes([0x1b, 0x61, 0x00]); // Left
  addText(`Bill Ref : #${bill.bill_no ?? bill.id.slice(0, 8).toUpperCase()}\n`);
  
  const when = new Date(bill.created_at);
  const timeLabel = isNaN(when.getTime())
    ? ""
    : when.toLocaleString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric",
        hour: "numeric",
        minute: "2-digit",
        hour12: true,
      });
  if (timeLabel) {
    addText(`Date     : ${timeLabel}\n`);
  }
  if (bill.salesperson_email) {
    addText(`Staff    : ${bill.salesperson_email}\n`);
  }
  if (bill.customer_name) {
    let custVal = bill.customer_name;
    if (bill.customer_phone) {
      custVal += ` (${bill.customer_phone})`;
    }
    addText(`Customer : ${custVal}\n`);
  }
  if (bill.remarks) {
    addText(`Remarks  : ${bill.remarks}\n`);
  }

  addText("------------------------------------------------\n");

  // 4. Line Items Table (3-inch paper has 48 columns space)
  bill.items.forEach((item) => {
    // Row 1: Product Name (Bold)
    addBytes([0x1b, 0x45, 0x01]); // Bold item name
    addText(`${item.product_name}\n`);
    addBytes([0x1b, 0x45, 0x00]); // Bold OFF

    // Row 2: Details: Qty x Unit Price   [aligned-right] Line Total
    const qtyPrice = `  ${item.quantity} x Rs. ${parseFloat(item.unit_price || "0").toFixed(2)}`;
    const lineTotal = `Rs. ${parseFloat(item.line_total || "0").toFixed(2)}`;
    const padding = 48 - qtyPrice.length - lineTotal.length;

    if (padding > 0) {
      addText(`${qtyPrice}${" ".repeat(padding)}${lineTotal}\n`);
    } else {
      addText(`${qtyPrice}\n`);
      addBytes([0x1b, 0x61, 0x02]); // Right align
      addText(`${lineTotal}\n`);
      addBytes([0x1b, 0x61, 0x00]); // Left align
    }
  });

  addText("------------------------------------------------\n");

  // 5. Totals
  const addTotalRow = (label: string, val: string, isBig = false) => {
    if (isBig) {
      addBytes([0x1b, 0x45, 0x01]); // Bold
      addBytes([0x1d, 0x21, 0x11]); // Double height + Double width
    }
    const cols = isBig ? 24 : 48; // Double width consumes twice the horizontal columns space
    const padding = cols - label.length - val.length;

    if (padding > 0) {
      addText(`${label}${" ".repeat(padding)}${val}\n`);
    } else {
      addText(`${label}\n${" ".repeat(Math.max(0, cols - val.length))}${val}\n`);
    }

    if (isBig) {
      addBytes([0x1b, 0x45, 0x00]); // Bold OFF
      addBytes([0x1d, 0x21, 0x00]); // Normal size
    }
  };

  addTotalRow("Subtotal", `Rs. ${parseFloat(bill.subtotal || "0").toFixed(2)}`);
  
  const discountAmount = parseFloat(bill.discount_amount || "0");
  if (discountAmount > 0) {
    const discLabel = bill.discount_type === "percent"
      ? `Discount (${Number(bill.discount_value || 0)}%)`
      : "Discount";
    addTotalRow(discLabel, `-Rs. ${discountAmount.toFixed(2)}`);
  }

  addText("------------------------------------------------\n");
  addTotalRow("TOTAL", `Rs. ${parseFloat(bill.total || "0").toFixed(2)}`, true);
  addText("------------------------------------------------\n");

  // Payments Breakdown
  const cash = parseFloat(bill.cash_amount || "0");
  const upi = parseFloat(bill.upi_amount || "0");
  const due = bill.due_amount ? parseFloat(bill.due_amount) : 0;

  if (cash > 0) {
    addTotalRow("Paid via Cash", `Rs. ${cash.toFixed(2)}`);
  }
  if (upi > 0) {
    addTotalRow("Paid via UPI", `Rs. ${upi.toFixed(2)}`);
  }
  if (due > 0) {
    addTotalRow("Remaining Due", `Rs. ${due.toFixed(2)}`);
  }
  if (cash === 0 && upi === 0 && due === 0) {
    addTotalRow("Paid via Cash", `Rs. 0.00`);
  }

  addText("------------------------------------------------\n");

  // 6. Footer (Center align, feeds and paper cut)
  addBytes([0x1b, 0x61, 0x01]); // Center
  addText("Thank you for shopping with us!\n");
  addText("Please visit us again.\n\n\n\n");

  if (autoCut) {
    // Feed & Cut Paper (GS V 66 0)
    addBytes([0x1d, 0x56, 0x42, 0x00]);
  } else {
    // Feed extra lines for manual tear off
    addText("\n\n\n\n");
  }

  // Merge chunks into a single byte array
  const totalLength = chunks.reduce((acc, chunk) => acc + chunk.length, 0);
  const merged = new Uint8Array(totalLength);
  let offset = 0;
  chunks.forEach((chunk) => {
    merged.set(chunk, offset);
    offset += chunk.length;
  });

  return merged;
}

interface BluetoothState {
  status: "disconnected" | "connecting" | "connected";
  deviceName: string | null;
  connectionType: "bluetooth" | "serial" | "usb" | null;
  error: string | null;
  baudRate: number;
  autoCut: boolean;
  preferredConnectionType: "bluetooth" | "serial" | "usb" | null;
  setBaudRate: (rate: number) => void;
  setAutoCut: (enabled: boolean) => void;
  connect: () => Promise<void>;
  connectSerial: () => Promise<void>;
  connectUsb: () => Promise<void>;
  disconnect: () => void;
  printReceipt: (bill: BillDetail | BillOut) => Promise<void>;
  printTest: () => Promise<void>;
  onDeviceDisconnect: (type: "bluetooth" | "serial" | "usb") => void;
}

// Read settings from LocalStorage at load time safely
const getStoredBaudRate = () => {
  if (typeof window !== "undefined") {
    const val = localStorage.getItem("printer_baud_rate");
    if (val) return parseInt(val, 10);
  }
  return 9600;
};

const getStoredAutoCut = () => {
  if (typeof window !== "undefined") {
    const val = localStorage.getItem("printer_auto_cut");
    if (val !== null) return val !== "false";
  }
  return true;
};

const getStoredPrefConn = () => {
  if (typeof window !== "undefined") {
    return localStorage.getItem("printer_pref_conn") as "bluetooth" | "serial" | "usb" | null;
  }
  return null;
};

export const useBluetoothPrinter = create<BluetoothState>((set, get) => ({
  status: "disconnected",
  deviceName: null,
  connectionType: null,
  error: null,
  baudRate: getStoredBaudRate(),
  autoCut: getStoredAutoCut(),
  preferredConnectionType: getStoredPrefConn(),

  setBaudRate: (rate: number) => {
    if (typeof window !== "undefined") {
      localStorage.setItem("printer_baud_rate", String(rate));
    }
    set({ baudRate: rate });
  },

  setAutoCut: (enabled: boolean) => {
    if (typeof window !== "undefined") {
      localStorage.setItem("printer_auto_cut", String(enabled));
    }
    set({ autoCut: enabled });
  },

  connect: async () => {
    if (!(navigator as any).bluetooth) {
      set({ error: "Web Bluetooth API is not supported in this browser." });
      return;
    }

    set({ status: "connecting", connectionType: null, error: null });

    try {
      const device = await (navigator as any).bluetooth.requestDevice({
        filters: [
          { services: ["0000ffe0-0000-1000-8000-00805f9b34fb"] },
          { services: ["0000ffe1-0000-1000-8000-00805f9b34fb"] },
          { services: ["0000ff00-0000-1000-8000-00805f9b34fb"] },
          { services: ["000018f0-0000-1000-8000-00805f9b34fb"] },
          { namePrefix: "Printer" },
          { namePrefix: "PRINTER" },
          { namePrefix: "printer" },
          { namePrefix: "MTP" },
          { namePrefix: "POS" },
          { namePrefix: "Thermal" },
          { namePrefix: "XT" },
          { namePrefix: "XP" },
          { namePrefix: "RT" },
          { namePrefix: "T9" },
          { namePrefix: "PT" }
        ],
        optionalServices: [
          "0000ffe0-0000-1000-8000-00805f9b34fb", // FFE0
          "0000ffe1-0000-1000-8000-00805f9b34fb", // FFE1
          "0000ff00-0000-1000-8000-00805f9b34fb", // FF00
          "000018f0-0000-1000-8000-00805f9b34fb", // 18F0
          "e7e1a121-481d-11e5-885d-feff819cdc9f", // Custom Serial
          "0000fee7-0000-1000-8000-00805f9b34fb", // WeChat/Printer
          "00001101-0000-1000-8000-00805f9b34fb", // SPP
        ],
      });

      set({ deviceName: device.name || "Bluetooth Printer" });

      device.addEventListener("gattserverdisconnected", () => {
        activeDevice = null;
        activeCharacteristic = null;
        set({ status: "disconnected", deviceName: null, connectionType: null });
      });

      const server = await device.gatt?.connect();
      if (!server) {
        throw new Error("Could not connect to GATT Server.");
      }

      const characteristic = await findPrinterCharacteristic(server);

      activeDevice = device;
      activeCharacteristic = characteristic;

      if (typeof window !== "undefined") {
        localStorage.setItem("printer_pref_conn", "bluetooth");
      }

      set({
        status: "connected",
        connectionType: "bluetooth",
        preferredConnectionType: "bluetooth",
        error: null
      });
    } catch (err) {
      activeDevice = null;
      activeCharacteristic = null;
      const errMsg = err instanceof Error ? err.message : String(err);
      
      if (errMsg.includes("User cancelled") || errMsg.includes("cancelled the request")) {
        set({ status: "disconnected" });
      } else {
        set({ status: "disconnected", error: `Bluetooth connection failed: ${errMsg}` });
      }
    }
  },

  connectSerial: async () => {
    if (!(navigator as any).serial) {
      set({ error: "Web Serial API is not supported in this browser. Please use Google Chrome or Microsoft Edge." });
      return;
    }

    const { baudRate } = get();
    set({ status: "connecting", connectionType: null, error: null });

    try {
      const port = await (navigator as any).serial.requestPort();
      await port.open({ baudRate });
      activeSerialPort = port;

      if (typeof window !== "undefined") {
        localStorage.setItem("printer_pref_conn", "serial");
      }

      set({
        status: "connected",
        deviceName: `USB/Serial Port (${baudRate} Baud)`,
        connectionType: "serial",
        preferredConnectionType: "serial",
        error: null
      });
    } catch (err) {
      activeSerialPort = null;
      const errMsg = err instanceof Error ? err.message : String(err);
      
      if (errMsg.includes("User cancelled") || errMsg.includes("cancelled the request")) {
        set({ status: "disconnected" });
      } else {
        set({ status: "disconnected", error: `Serial connection failed: ${errMsg}` });
      }
    }
  },

  connectUsb: async () => {
    if (!(navigator as any).usb) {
      set({ error: "Web USB API is not supported in this browser." });
      return;
    }

    set({ status: "connecting", connectionType: null, error: null });

    try {
      // Prompt user to select USB device.
      // Use standard Printer class (7) and Vendor Specific Class (255) filters.
      const device = await (navigator as any).usb.requestDevice({
        filters: [
          { classCode: 7 },
          { classCode: 255 }
        ]
      });

      await device.open();
      
      // Auto configuration
      if (!device.configuration || device.configuration.configurationValue !== 1) {
        try {
          await device.selectConfiguration(1);
        } catch {
          // ignore configuration selection error if device is already active
        }
      }

      let interfaceNumber = -1;
      let endpointOutNumber = -1;

      // Scan configurations/interfaces/alternates to find Bulk OUT endpoint
      for (const config of device.configurations) {
        for (const iface of config.interfaces) {
          for (const alt of iface.alternates) {
            if (alt.interfaceClass === 7 || alt.interfaceClass === 255) {
              const ep = alt.endpoints.find((e: any) => e.direction === "out" && e.type === "bulk");
              if (ep) {
                interfaceNumber = iface.interfaceNumber;
                endpointOutNumber = ep.endpointNumber;
                break;
              }
            }
          }
          if (endpointOutNumber !== -1) break;
        }
        if (endpointOutNumber !== -1) break;
      }

      // Safe fallback search
      if (endpointOutNumber === -1) {
        for (const config of device.configurations) {
          for (const iface of config.interfaces) {
            for (const alt of iface.alternates) {
              const ep = alt.endpoints.find((e: any) => e.direction === "out" && e.type === "bulk");
              if (ep) {
                interfaceNumber = iface.interfaceNumber;
                endpointOutNumber = ep.endpointNumber;
                break;
              }
            }
            if (endpointOutNumber !== -1) break;
          }
          if (endpointOutNumber !== -1) break;
        }
      }

      if (endpointOutNumber === -1) {
        throw new Error("Could not find a valid bulk output endpoint on the selected USB device.");
      }

      await device.claimInterface(interfaceNumber);

      activeUsbDevice = device;
      activeUsbEndpoint = endpointOutNumber;
      activeUsbInterface = interfaceNumber;

      if (typeof window !== "undefined") {
        localStorage.setItem("printer_pref_conn", "usb");
      }

      set({
        status: "connected",
        deviceName: device.productName || "USB Printer Device",
        connectionType: "usb",
        preferredConnectionType: "usb",
        error: null
      });

    } catch (err) {
      activeUsbDevice = null;
      activeUsbEndpoint = -1;
      activeUsbInterface = -1;
      const errMsg = err instanceof Error ? err.message : String(err);
      
      if (errMsg.includes("User cancelled") || errMsg.includes("cancelled the request")) {
        set({ status: "disconnected" });
      } else {
        set({ status: "disconnected", error: `USB connection failed: ${errMsg}` });
      }
    }
  },

  disconnect: () => {
    if (activeSerialPort) {
      try {
        activeSerialPort.close();
      } catch {}
    }
    if (activeUsbDevice) {
      try {
        activeUsbDevice.releaseInterface(activeUsbInterface);
        activeUsbDevice.close();
      } catch {}
    }
    if (activeDevice && activeDevice.gatt?.connected) {
      activeDevice.gatt.disconnect();
    }
    activeDevice = null;
    activeCharacteristic = null;
    activeSerialPort = null;
    activeUsbDevice = null;
    activeUsbEndpoint = -1;
    activeUsbInterface = -1;
    set({ status: "disconnected", deviceName: null, connectionType: null, error: null });
  },

  printReceipt: async (bill: BillDetail | BillOut) => {
    const { status, connectionType, autoCut } = get();
    if (status !== "connected") {
      throw new Error("No printer connected.");
    }

    try {
      set({ error: null });
      let detail: BillDetail;

      if ("business_name" in bill) {
        detail = bill as BillDetail;
      } else {
        detail = await fetchBillDetail(bill.id);
      }

      const receiptBytes = compileEscPosReceipt(detail, autoCut);

      if (connectionType === "serial" && activeSerialPort) {
        const writer = activeSerialPort.writable.getWriter();
        await writeSerialInChunks(writer, receiptBytes);
        writer.releaseLock();
      } else if (connectionType === "usb" && activeUsbDevice && activeUsbEndpoint !== -1) {
        await writeUsbInChunks(activeUsbDevice, activeUsbEndpoint, receiptBytes);
      } else if (connectionType === "bluetooth" && activeCharacteristic) {
        await writeInChunks(activeCharacteristic, receiptBytes);
      } else {
        throw new Error("No active printer channel found.");
      }
    } catch (err) {
      const errMsg = err instanceof Error ? err.message : String(err);
      set({ error: `Print failed: ${errMsg}` });
      throw err;
    }
  },

  printTest: async () => {
    const { status, connectionType, autoCut } = get();
    if (status !== "connected") {
      throw new Error("No printer connected.");
    }

    try {
      set({ error: null });
      const encoder = new TextEncoder();
      const chunks: Uint8Array[] = [];

      // Initialize
      chunks.push(new Uint8Array([0x1b, 0x40]));
      
      // Double width/height Center Bold
      chunks.push(new Uint8Array([0x1b, 0x61, 0x01])); // Center
      chunks.push(new Uint8Array([0x1b, 0x45, 0x01])); // Bold ON
      chunks.push(new Uint8Array([0x1d, 0x21, 0x11])); // Double Size
      chunks.push(encoder.encode("TEST PRINT\n\n"));

      // Normal text
      chunks.push(new Uint8Array([0x1d, 0x21, 0x00])); // Normal size
      chunks.push(new Uint8Array([0x1b, 0x45, 0x00])); // Bold OFF
      chunks.push(encoder.encode("Thermal Printer Connected!\n"));
      
      let connLabel = "Bluetooth BLE";
      if (connectionType === "serial") connLabel = "USB / Serial COM";
      else if (connectionType === "usb") connLabel = "Direct USB (Web USB)";
      
      chunks.push(encoder.encode(`Connection: ${connLabel}\n`));
      chunks.push(encoder.encode("ESC/POS Commands: Working OK\n"));
      chunks.push(encoder.encode(`DateTime: ${new Date().toLocaleString()}\n`));
      chunks.push(encoder.encode("------------------------------------------------\n"));
      chunks.push(encoder.encode("123456789012345678901234567890123456789012345678\n")); // 48 chars alignment ruler
      chunks.push(encoder.encode("------------------------------------------------\n\n\n\n"));
      
      if (autoCut) {
        // Feed & cut
        chunks.push(new Uint8Array([0x1d, 0x56, 0x42, 0x00]));
      } else {
        chunks.push(encoder.encode("\n\n\n\n"));
      }

      const totalLength = chunks.reduce((acc, chunk) => acc + chunk.length, 0);
      const merged = new Uint8Array(totalLength);
      let offset = 0;
      chunks.forEach((chunk) => {
        merged.set(chunk, offset);
        offset += chunk.length;
      });

      if (connectionType === "serial" && activeSerialPort) {
        const writer = activeSerialPort.writable.getWriter();
        await writeSerialInChunks(writer, merged);
        writer.releaseLock();
      } else if (connectionType === "usb" && activeUsbDevice && activeUsbEndpoint !== -1) {
        await writeUsbInChunks(activeUsbDevice, activeUsbEndpoint, merged);
      } else if (connectionType === "bluetooth" && activeCharacteristic) {
        await writeInChunks(activeCharacteristic, merged);
      }
    } catch (err) {
      const errMsg = err instanceof Error ? err.message : String(err);
      set({ error: `Test print failed: ${errMsg}` });
      throw err;
    }
  },

  onDeviceDisconnect: (type: "bluetooth" | "serial" | "usb") => {
    const { connectionType } = get();
    if (connectionType === type) {
      set({ status: "disconnected", deviceName: null, connectionType: null });
    }
  }
}));

// Global event listeners to handle unexpected cord unplugs
if (typeof navigator !== "undefined" && (navigator as any).serial) {
  (navigator as any).serial.addEventListener("disconnect", (event: any) => {
    if (activeSerialPort && activeSerialPort === event.target) {
      activeSerialPort = null;
      useBluetoothPrinter.getState().onDeviceDisconnect("serial");
    }
  });
}

if (typeof navigator !== "undefined" && (navigator as any).usb) {
  (navigator as any).usb.addEventListener("disconnect", (event: any) => {
    if (activeUsbDevice && activeUsbDevice === event.device) {
      activeUsbDevice = null;
      activeUsbEndpoint = -1;
      activeUsbInterface = -1;
      useBluetoothPrinter.getState().onDeviceDisconnect("usb");
    }
  });
}
