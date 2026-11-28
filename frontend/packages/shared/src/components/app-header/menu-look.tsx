"use client";

import { createContext, useContext } from "react";

/**
 * How the menu around a nested row is drawn: "grid" for the header's account
 * menu (the square ops grid panel), "plain" for the theme's menu, as in the
 * sidebar variant of the account menu. A row a host app passes into the
 * account menu, such as the language submenu, reads it to match the menu it
 * sits in without a prop in every app. Grid is the default, the look every
 * app's top bar uses.
 */
export type MenuLook = "grid" | "plain";

export const MenuLookContext = createContext<MenuLook>("grid");

export function useMenuLook(): MenuLook {
  return useContext(MenuLookContext);
}
