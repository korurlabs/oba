"use client";

import { useGlobalShortcuts } from "@/hooks/use-shortcuts";

export function ShortcutListener() {
    useGlobalShortcuts();
    return null;
}
