"use client";

import type { ReactNode } from "react";
import { SidebarInset, SidebarProvider } from "@oba/ui/sky/sidebar";
import { useSidebarWidthContext } from "../sidebar-context";
import { ClassicSidebar } from "./classic-sidebar";
import { ContentSidebarTriggerBar } from "./content-sidebar-trigger";

/**
 * The classic page frame: no top bar, a full-height inset sidebar, and the
 * page as a bordered panel set into the sidebar's grey ground. The sidebar
 * toggle sits in the content area, in its own row or in the board bar.
 * `children` is the shell body shared with the modern frame.
 */
export function ClassicAppShell({ children }: { children: ReactNode }) {
  const { providerStyle, providerClassName } = useSidebarWidthContext();

  return (
    <SidebarProvider style={providerStyle} className={providerClassName}>
      <ClassicSidebar />
      <SidebarInset className="group/classic-content border-border h-svh min-w-0 overflow-hidden md:peer-data-[variant=inset]:h-[calc(100svh-1rem)] md:peer-data-[variant=inset]:border">
        <ContentSidebarTriggerBar />
        {children}
      </SidebarInset>
    </SidebarProvider>
  );
}
