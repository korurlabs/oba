"use client";

import { useEffect, useState } from "react";

/**
 * The current time in milliseconds, renewed every `intervalMs`, so countdowns,
 * ages and passed deadlines update while a page stays open.
 */
export function useNow(intervalMs: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
