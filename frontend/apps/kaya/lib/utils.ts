import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Joins Tailwind class names and resolves conflicts between them.
 *
 * @param inputs - The class names to join.
 * @returns One class string, with later conflicting classes winning.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
