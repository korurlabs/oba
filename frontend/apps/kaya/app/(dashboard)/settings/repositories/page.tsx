import type { Metadata } from "next";

import { RepositoriesSettings } from "@/features/settings/components";

export const metadata: Metadata = {
  title: "Repositories",
};

export default function RepositoriesSettingsRoute() {
  return <RepositoriesSettings />;
}
