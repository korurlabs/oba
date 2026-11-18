import type { UseQueryResult } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import * as React from "react";

import { showTicketChoice } from "@/components/ticket/ticketChoiceStore";
import type { ChoicePicker, TicketPicker } from "@/components/ticket/ticketPickers";
import { TICKET_CHOICE_PATHNAME } from "@/components/ticket/ticketRoutes";
import { toast } from "@/components/ui/toast";
import { useTranslation } from "@/i18n";

/**
 * The pickers of the ticket screen: the action-sheet picker shown now, a way
 * to open one built from a board list, and `openChoice`, which opens the
 * choice sheet of a type, status or priority. A list that is not loaded yet,
 * or failed, is read when the field is tapped and the picker opens once it
 * arrives; a failed read is toasted with Retry.
 */
export function useTicketPicker() {
  const { t } = useTranslation();
  const router = useRouter();
  const [picker, setPicker] = React.useState<TicketPicker | null>(null);

  const withOptions = React.useCallback(
    <T>(query: UseQueryResult<T>, show: (data: T) => void) => {
      if (query.data) {
        show(query.data);
        return;
      }
      const load = () => {
        // A refetch never rejects: a failed read comes back as `result.error`.
        void query.refetch().then((result) => {
          if (result.data) {
            show(result.data);
            return;
          }
          console.warn("[ticket] picker options failed", result.error?.message);
          toast.error(t("ticketOptionsError"), {
            action: { label: t("ticketRetry"), onPress: load },
          });
        });
      };
      load();
    },
    [t]
  );

  const closePicker = React.useCallback(() => setPicker(null), []);

  const openChoice = React.useCallback(
    (choice: ChoicePicker) => {
      showTicketChoice(choice);
      router.push({ pathname: TICKET_CHOICE_PATHNAME });
    },
    [router]
  );

  return { picker, setPicker, closePicker, withOptions, openChoice };
}
