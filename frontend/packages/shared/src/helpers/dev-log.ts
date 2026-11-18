function getEnvTag() {
  return typeof window === "undefined" ? "SERVER" : "CLIENT";
}

export function devLog(...args: unknown[]) {
  if (process.env.NODE_ENV === "development") {
    console.log(`[DEBUG][${getEnvTag()}]:`, ...args);
  }
}

export function devError(...args: unknown[]) {
  if (process.env.NODE_ENV === "development") {
    console.error(`[ERROR][${getEnvTag()}]:`, ...args);
  }
}
