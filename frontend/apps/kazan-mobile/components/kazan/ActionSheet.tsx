import BottomSheet, {
  BottomSheetBackdrop,
  BottomSheetView,
  type BottomSheetBackdropProps,
} from "@gorhom/bottom-sheet";
import { Check } from "lucide-react-native";
import { useColorScheme } from "nativewind";
import * as React from "react";
import { ActionSheetIOS, Alert, Modal, Platform, Pressable, View } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  destructiveIndices,
  disabledIndices,
  orderActions,
  selectedLabel,
  type ActionSheetAction,
} from "@/components/kazan/actionSheetModel";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { translate, useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

type ActionSheetProps = {
  visible: boolean;
  /** Called when the sheet closes, with or without a choice. Set `visible` false here. */
  onClose: () => void;
  title?: string;
  /**
   * iOS only: a line under the title. Android action sheets carry no message.
   * Without one, a menu with a selected action reads "Current: {value}".
   */
  message?: string;
  actions: ActionSheetAction[];
  /** iOS only: the cancel button; Android dismisses by the scrim, a swipe or Back. */
  cancelLabel?: string;
};

/**
 * A menu of actions for one item (ticket, message, comment). iOS shows the
 * system action sheet; Android a bottom sheet of rows with leading icons, no
 * Cancel row and no message. Destructive actions are moved last.
 */
function ActionSheet(props: ActionSheetProps) {
  return Platform.OS === "ios" ? <IosActionSheet {...props} /> : <AndroidActionSheet {...props} />;
}

function IosActionSheet({
  visible,
  onClose,
  title,
  message,
  actions,
  cancelLabel,
}: ActionSheetProps) {
  const { t } = useTranslation();
  const { colorScheme } = useColorScheme();
  // The latest props, read when the sheet opens, without reopening it on
  // every parent render.
  const latest = React.useRef({ onClose, title, message, actions, cancelLabel, colorScheme });
  latest.current = { onClose, title, message, actions, cancelLabel, colorScheme };

  React.useEffect(() => {
    if (!visible) return;
    const current = latest.current;
    const ordered = orderActions(current.actions);
    const choice: string | null = selectedLabel(ordered);
    const options = [
      ...ordered.map((action) => action.label),
      current.cancelLabel ?? t("kitCancel"),
    ];
    ActionSheetIOS.showActionSheetWithOptions(
      {
        options,
        cancelButtonIndex: ordered.length,
        destructiveButtonIndex: destructiveIndices(ordered),
        disabledButtonIndices: disabledIndices(ordered),
        title: current.title,
        message:
          current.message ??
          (choice !== null ? t("kitCurrentChoice", { value: choice }) : undefined),
        userInterfaceStyle: current.colorScheme === "dark" ? "dark" : "light",
      },
      (index) => {
        latest.current.onClose();
        ordered[index]?.onPress();
      }
    );
  }, [visible, t]);

  return null;
}

function AndroidActionSheet({ visible, onClose, title, actions }: ActionSheetProps) {
  const colors = useThemeColors();
  const insets = useSafeAreaInsets();
  const sheetRef = React.useRef<BottomSheet>(null);
  // The chosen action runs once the sheet has closed, so a follow-up sheet or
  // dialog never opens under the one that is leaving.
  const pending = React.useRef<(() => void) | null>(null);
  const ordered = orderActions(actions);

  const handleClose = React.useCallback(() => {
    const action = pending.current;
    pending.current = null;
    onClose();
    action?.();
  }, [onClose]);

  const renderBackdrop = React.useCallback(
    (backdropProps: BottomSheetBackdropProps) => (
      <BottomSheetBackdrop
        {...backdropProps}
        appearsOnIndex={0}
        disappearsOnIndex={-1}
        pressBehavior="close"
        // The scrim token carries the per-scheme alpha, so the backdrop fades in to it in full.
        opacity={1}
        style={[backdropProps.style, { backgroundColor: colors.scrim }]}
      />
    ),
    [colors.scrim]
  );

  return (
    <Modal
      visible={visible}
      transparent
      statusBarTranslucent
      navigationBarTranslucent
      animationType="none"
      onRequestClose={() => sheetRef.current?.close()}
    >
      {/* A Modal is its own native window; gestures need their own root in it. */}
      <GestureHandlerRootView style={{ flex: 1 }}>
        <BottomSheet
          ref={sheetRef}
          index={0}
          enablePanDownToClose
          enableDynamicSizing
          // A long menu stops below the status bar instead of running under it.
          topInset={insets.top}
          onClose={handleClose}
          backdropComponent={renderBackdrop}
          backgroundStyle={{ backgroundColor: colors.cardBackground, borderRadius: 28 }}
          handleIndicatorStyle={{ backgroundColor: colors.grabber, width: 32, height: 4 }}
        >
          <BottomSheetView style={{ paddingBottom: insets.bottom + 8 }}>
            {title ? (
              <Text
                numberOfLines={2}
                className="px-6 pb-2 pt-1 font-sans-medium text-subhead text-muted-foreground"
              >
                {title}
              </Text>
            ) : null}
            {ordered.map((action) => (
              <Pressable
                key={action.label}
                accessibilityRole="button"
                accessibilityState={{ disabled: !!action.disabled, selected: !!action.selected }}
                disabled={action.disabled}
                android_ripple={{ color: colors.accent }}
                onPress={() => {
                  pending.current = action.onPress;
                  sheetRef.current?.close();
                }}
                className={cn(
                  "min-h-row-android flex-row items-center gap-4 px-6",
                  action.selected && "bg-accent",
                  action.disabled && "opacity-50"
                )}
              >
                {action.icon ? (
                  <Icon
                    as={action.icon}
                    size={24}
                    className={action.destructive ? "text-destructive" : "text-muted-foreground"}
                  />
                ) : null}
                <Text
                  className={cn(
                    "flex-1 font-sans text-callout",
                    action.destructive ? "text-destructive" : "text-foreground"
                  )}
                >
                  {action.label}
                </Text>
                {action.selected ? <Icon as={Check} size={24} className="text-foreground" /> : null}
              </Pressable>
            ))}
            <View className="h-2" />
          </BottomSheetView>
        </BottomSheet>
      </GestureHandlerRootView>
    </Modal>
  );
}

/**
 * Asks before throwing away unsaved input ("Discard changes" / "Keep
 * editing"). iOS asks with an action sheet, Android with a dialog. `onDiscard`
 * runs only when the user discards.
 */
function confirmDiscardChanges(onDiscard: () => void, onKeep?: () => void): void {
  const title = translate("kitDiscardTitle");
  const discard = translate("kitDiscardChanges");
  const keep = translate("kitKeepEditing");

  if (Platform.OS === "ios") {
    ActionSheetIOS.showActionSheetWithOptions(
      { title, options: [discard, keep], destructiveButtonIndex: 0, cancelButtonIndex: 1 },
      (index) => {
        if (index === 0) onDiscard();
        else onKeep?.();
      }
    );
    return;
  }
  Alert.alert(
    title,
    undefined,
    [
      { text: keep, style: "cancel", onPress: onKeep },
      { text: discard, style: "destructive", onPress: onDiscard },
    ],
    { cancelable: true, onDismiss: onKeep }
  );
}

export { ActionSheet, confirmDiscardChanges };
export type { ActionSheetAction, ActionSheetProps };
