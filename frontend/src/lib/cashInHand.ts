import { useEffect, useState } from "react";

/**
 * Per-device preference: show "Cash in Hand" as a running all-time total that
 * carries over day to day, instead of just today's cash. Stored in localStorage
 * (this browser only); the running number itself is computed server-side.
 */
const KEY = "plantbill_cash_in_hand_cumulative";

function read(): boolean {
  try {
    return localStorage.getItem(KEY) === "1";
  } catch {
    return false;
  }
}

export function useCashInHandCumulative(): [boolean, (v: boolean) => void] {
  const [on, setOn] = useState<boolean>(read);

  useEffect(() => {
    const handler = (e: StorageEvent) => {
      if (e.key === KEY) setOn(e.newValue === "1");
    };
    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
  }, []);

  const set = (v: boolean) => {
    setOn(v);
    try {
      localStorage.setItem(KEY, v ? "1" : "0");
    } catch {
      /* ignore */
    }
    // storage events only fire in *other* tabs; notify this tab's listeners too.
    window.dispatchEvent(new StorageEvent("storage", { key: KEY, newValue: v ? "1" : "0" }));
  };

  return [on, set];
}
