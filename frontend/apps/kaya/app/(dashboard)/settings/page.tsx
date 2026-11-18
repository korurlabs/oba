import { redirect } from "next/navigation";

/** Settings opens on its first page. */
export default function SettingsRoute() {
  redirect("/settings/repositories");
}
