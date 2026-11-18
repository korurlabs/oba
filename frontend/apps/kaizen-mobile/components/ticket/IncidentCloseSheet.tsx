import { TriangleAlert } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import {
  incidentClosePrefill,
  incidentCloseToSend,
  type IncidentCloseValues,
} from "@/components/ticket/incidentCloseModel";
import {
  incidentCloseDraft,
  settleIncidentClose,
  useIncidentClose,
  type IncidentCloseRequest,
} from "@/components/ticket/incidentCloseStore";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTicketDetail } from "@/hooks/queries/ticketDetail";
import { useTranslation } from "@/i18n";
import { INCIDENT_TEXT_MAX } from "@/lib/schemas/tickets";

/** Rows each text field shows before it scrolls. */
const FIELD_ROWS = 4;

/**
 * Close incident: asks for the root cause analysis and lessons learned of an
 * incident that is being set to Done, because the API refuses the close
 * without both. The fields start from the texts typed for an earlier close
 * that did not land, else from the incident's stored texts; a new incident
 * starts empty. Confirm stays off until both hold more than white space.
 *
 * The screen beneath hands over the question through the incident close
 * store and gets the trimmed texts back once a confirmed sheet has closed.
 * Cancel, Back and the swipe down answer with nothing, so the screen leaves
 * the ticket as it was.
 * While the incident cannot be read the fields stay hidden: blank fields
 * confirmed then would replace texts the incident may already hold.
 */
export function IncidentCloseSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const latest: IncidentCloseRequest | null = useIncidentClose();
  // The question this sheet opened with; a later one belongs to the next sheet.
  const [request] = React.useState<IncidentCloseRequest | null>(latest);
  const detail = useTicketDetail(request?.ticket);
  const [rca, setRca] = React.useState<string | null>(null);
  const [lessons, setLessons] = React.useState<string | null>(null);
  const [touched, setTouched] = React.useState({ rca: false, lessons: false });
  const [confirmed, setConfirmed] = React.useState<boolean>(false);
  const answer = React.useRef<IncidentCloseValues | null>(null);

  // The answer goes out once the sheet has left the stack, however it closed:
  // the texts after a confirm, else nothing. The screen beneath may navigate
  // as soon as its write lands, and a sheet still on the stack would be the
  // route that navigation replaces.
  React.useEffect(
    () => () => {
      if (request) settleIncidentClose(request, answer.current);
    },
    [request]
  );

  const ready: boolean = !!request && (!request.ticket || !!detail.data);
  const prefill: IncidentCloseValues = incidentClosePrefill(
    request ? incidentCloseDraft(request.draftKey) : undefined,
    detail.data
  );
  const values: IncidentCloseValues = {
    rca: rca ?? prefill.rca,
    lessonsLearned: lessons ?? prefill.lessonsLearned,
  };
  const toSend: IncidentCloseValues | null = incidentCloseToSend(values);

  const confirm = () => {
    if (!request || !toSend) {
      setTouched({ rca: true, lessons: true });
      return;
    }
    answer.current = toSend;
    setConfirmed(true);
  };

  return (
    <BottomSheet
      title={t("ticketIncidentCloseTitle")}
      confirmLabel={t("ticketIncidentCloseConfirm")}
      onConfirm={confirm}
      confirmDisabled={!ready || !toSend}
      dirty={(rca !== null || lessons !== null) && !confirmed}
      scroll
    >
      <DismissWhen when={!request || confirmed} />
      <View className="gap-4 px-4 pb-6 pt-2">
        <Text variant="subhead" className="text-muted-foreground">
          {t("ticketIncidentCloseDescription")}
        </Text>
        {ready ? (
          <>
            <Card>
              <View className="flex-row items-start gap-2">
                <Icon as={TriangleAlert} size={16} className="mt-0.5 text-warning" />
                <Text variant="subhead" className="flex-1">
                  {t("ticketIncidentCloseWarning")}
                </Text>
              </View>
            </Card>
            <TextField
              label={t("ticketIncidentCloseRcaLabel")}
              required
              value={values.rca}
              onChangeText={setRca}
              onBlur={() => setTouched((current) => ({ ...current, rca: true }))}
              placeholder={t("ticketIncidentCloseRcaPlaceholder")}
              error={
                touched.rca && !values.rca.trim() ? t("ticketIncidentCloseRequired") : undefined
              }
              maxLength={INCIDENT_TEXT_MAX}
              multiline
              rows={FIELD_ROWS}
            />
            <TextField
              label={t("ticketIncidentCloseLessonsLabel")}
              required
              value={values.lessonsLearned}
              onChangeText={setLessons}
              onBlur={() => setTouched((current) => ({ ...current, lessons: true }))}
              placeholder={t("ticketIncidentCloseLessonsPlaceholder")}
              error={
                touched.lessons && !values.lessonsLearned.trim()
                  ? t("ticketIncidentCloseRequired")
                  : undefined
              }
              maxLength={INCIDENT_TEXT_MAX}
              multiline
              rows={FIELD_ROWS}
            />
          </>
        ) : detail.isError ? (
          <InlineError
            message={t("ticketIncidentCloseLoadFailed")}
            onRetry={() => void detail.refetch()}
          />
        ) : (
          <View className="items-center py-10">
            <ActivityIndicator color={colors.mutedForeground} />
          </View>
        )}
      </View>
    </BottomSheet>
  );
}
