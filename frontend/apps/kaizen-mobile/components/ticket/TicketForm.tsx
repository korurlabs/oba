import { useHeaderHeight } from "@react-navigation/elements";
import * as React from "react";
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  View,
  type LayoutChangeEvent,
  type RefreshControlProps,
} from "react-native";

import { useKeyboardOverlap } from "@/components/chat/useKeyboardOverlap";
import { ActionSheet } from "@/components/kaizen/ActionSheet";
import { TicketDescription } from "@/components/ticket/TicketDescription";
import { TicketFields } from "@/components/ticket/TicketFields";
import { TicketHeader } from "@/components/ticket/TicketHeader";
import type { TicketPicker } from "@/components/ticket/ticketPickers";
import { useTranslation } from "@/i18n";

/** A part of the ticket screen that can be scrolled into view. */
export type TicketFormPart = "header" | "fields" | "description";

/** What the screen can ask of the form, such as to show a field Create found missing. */
export interface TicketFormHandle {
  scrollTo: (part: TicketFormPart) => void;
}

type TicketFormProps = {
  ref?: React.Ref<TicketFormHandle>;
  header: React.ComponentProps<typeof TicketHeader>;
  /** Remounts the header, such as for an empty, focused title after "Create another". */
  headerKey?: React.Key;
  fields: React.ComponentProps<typeof TicketFields>;
  /** The iOS calendar of the due-date picker, under the fields while it is open. */
  dueCalendar: React.ReactNode;
  /** The description card; its ticket key comes from the header. */
  description: Omit<React.ComponentProps<typeof TicketDescription>, "ticketKey">;
  /** The action-sheet picker shown now, such as the statuses. */
  picker: TicketPicker | null;
  onClosePicker: () => void;
  /** Notices above the header, such as a list that failed to load. */
  top?: React.ReactNode;
  /** What follows the description in the scroll view. */
  children?: React.ReactNode;
  /** What stays pinned under the scroll view, such as the comment composer. */
  footer?: React.ReactNode;
  /** More sheets of the screen, such as the ticket menu. */
  overlays?: React.ReactNode;
  refreshControl?: React.ReactElement<RefreshControlProps>;
};

/**
 * The ticket screen's body, for a saved ticket and for a new one alike: the
 * header, the field list, the due-date calendar, the description and the
 * picker sheet. A saved ticket adds its sub-tickets, comments and composer;
 * a new one its notices and "Create another".
 */
export function TicketForm({
  ref,
  header,
  headerKey,
  fields,
  dueCalendar,
  description,
  picker,
  onClosePicker,
  top,
  children,
  footer,
  overlays,
  refreshControl,
}: TicketFormProps) {
  const { t } = useTranslation();
  const headerHeight: number = useHeaderHeight();
  const scroll = React.useRef<ScrollView>(null);
  // Android draws edge to edge, so the window does not shrink for the keyboard
  // and the composer or a focused title would sit under it; pad by the overlap.
  const { overlap, viewRef, onLayout } = useKeyboardOverlap();
  const android: boolean = Platform.OS === "android";
  // Where the field list and the description start in the scroll content, from their layout.
  const offsets = React.useRef<Record<TicketFormPart, number>>({
    header: 0,
    fields: 0,
    description: 0,
  });
  const measure = (part: TicketFormPart) => (event: LayoutChangeEvent) => {
    offsets.current[part] = event.nativeEvent.layout.y;
  };
  React.useImperativeHandle(
    ref,
    () => ({
      scrollTo: (part: TicketFormPart) =>
        scroll.current?.scrollTo({ y: Math.max(0, offsets.current[part] - 8), animated: true }),
    }),
    []
  );

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-screen"
      behavior={Platform.OS === "ios" ? "padding" : undefined}
      keyboardVerticalOffset={headerHeight}
    >
      <View
        ref={android ? viewRef : undefined}
        onLayout={android ? onLayout : undefined}
        className="flex-1"
        style={android ? { paddingBottom: overlap } : undefined}
      >
        <ScrollView
          ref={scroll}
          contentContainerClassName="gap-5 pb-6"
          keyboardShouldPersistTaps="handled"
          refreshControl={refreshControl}
        >
          {top}
          <TicketHeader key={headerKey} {...header} />
          <View onLayout={measure("fields")}>
            <TicketFields {...fields} />
          </View>
          {dueCalendar}
          <View onLayout={measure("description")}>
            <TicketDescription {...description} ticketKey={header.ticket.key} />
          </View>
          {children}
        </ScrollView>
        {footer}
      </View>
      <ActionSheet
        visible={!!picker}
        onClose={onClosePicker}
        title={picker?.title}
        actions={picker?.actions ?? []}
        cancelLabel={t("kitCancel")}
      />
      {overlays}
    </KeyboardAvoidingView>
  );
}
