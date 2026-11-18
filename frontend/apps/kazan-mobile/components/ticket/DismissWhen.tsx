import * as React from "react";

import { useSheet } from "@/components/kazan/BottomSheet";

/**
 * Closes the sheet it is rendered in once `when` turns true, such as after a
 * save: the sheet's own close animation runs and no discard question is
 * asked. Renders nothing.
 */
export function DismissWhen({ when }: { when: boolean }) {
  const { dismiss } = useSheet();
  React.useEffect(() => {
    if (when) dismiss();
  }, [dismiss, when]);
  return null;
}
