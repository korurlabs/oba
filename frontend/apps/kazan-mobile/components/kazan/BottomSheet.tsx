import GorhomBottomSheet, {
  BottomSheetBackdrop,
  BottomSheetScrollView,
  BottomSheetView,
  type BottomSheetBackdropProps,
  type BottomSheetScrollViewMethods,
} from "@gorhom/bottom-sheet";
import type { NativeStackNavigationOptions } from "@react-navigation/native-stack";
import { usePreventRemove } from "@react-navigation/native";
import { useNavigation } from "expo-router";
import * as React from "react";
import {
  ActivityIndicator,
  Dimensions,
  Keyboard,
  LayoutAnimation,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
  type KeyboardEvent,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from "react-native";
import { initialWindowMetrics, useSafeAreaInsets } from "react-native-safe-area-context";

import { confirmDiscardChanges } from "@/components/kazan/ActionSheet";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

/*
 * Sheets are routes. iOS presents them as native form sheets with system
 * detents and grabber; Android presents a transparent modal route in which
 * this component draws a Material bottom sheet (28 dp corners on
 * `card-background`, footer Cancel and confirm). Either way Back, the swipe
 * down and Cancel all close the route, and a dirty form asks first.
 *
 * iOS form sheets lay their content out differently from pushed screens.
 * react-native-screens anchors the content only at the top, so it has no
 * height of its own, and it then sets the frame of the sheet's scroll view
 * natively to the sheet size. That native frame is right only when the header
 * and the scroll view are the two direct children of the sheet content, the
 * header first and never flattened. Otherwise the library falls back to
 * giving the scroll view the frame of the whole sheet, from its top edge, and
 * the scrolled content covers the header and the grabber. IosSheet therefore
 * renders the header and the body side by side, without a wrapper.
 *
 * A sheet with pinned footer actions (Filters: "Show 12 tickets") cannot add
 * the footer as a third sibling: the library warns and still sizes the scroll
 * view to everything below the header, so the rows run on behind the footer.
 * Its body is one unflattened, clipping View that holds the scroll view and
 * the footer instead. The library then finds no scroll view to size, the
 * footer sits below the scroll view in plain flex layout, and nothing can be
 * drawn over the header. That layout needs a bounded content height, which
 * only the `large` detent gives (see `sheetScreenOptions`).
 *
 * A sheet with steps keeps one of these layouts for all of them. The library
 * places the scroll view natively when the sheet lays out, not when a scroll
 * view mounts later, so a step that switches from the footer layout back to
 * `scroll` gets a scroll view that starts at the top of the sheet, over the
 * header. Because the steps share that scroll view, they also share its
 * offset: a sheet passes a new `scrollKey` per step so each step opens at its
 * top.
 */

type SheetDetent = "fit" | "medium" | "large";

const IOS_DETENTS: Record<SheetDetent, NativeStackNavigationOptions["sheetAllowedDetents"]> = {
  fit: "fitToContents",
  medium: [0.5, 1],
  large: [1],
};

const ANDROID_SNAP_POINTS: Record<SheetDetent, string[] | undefined> = {
  fit: undefined,
  medium: ["50%", "100%"],
  large: ["100%"],
};

/**
 * The screen options of a sheet route, for `<Stack.Screen name="…"
 * options={sheetScreenOptions("medium")} />` in the layout that owns it.
 */
function sheetScreenOptions(detent: SheetDetent = "large"): NativeStackNavigationOptions {
  if (Platform.OS === "ios") {
    return {
      presentation: "formSheet",
      sheetAllowedDetents: IOS_DETENTS[detent],
      sheetGrabberVisible: true,
      headerShown: false,
      // A full-height sheet never changes size, so its content is pinned to
      // the sheet bottom as well: a body with its own scroll view and a fixed
      // footer (Filters) then gets a bounded height. Resizable and fitted
      // sheets keep the library's top-only anchoring, which avoids a relayout
      // on every detent change.
      ...(detent === "large" ? { contentStyle: { bottom: 0 } } : {}),
    };
  }
  return {
    presentation: "transparentModal",
    animation: "fade",
    headerShown: false,
    contentStyle: { backgroundColor: "transparent" },
  };
}

type SheetContextValue = {
  /** Closes the sheet without the discard question, such as after a save. */
  dismiss: () => void;
};

const SheetContext = React.createContext<SheetContextValue | null>(null);

/** The sheet the calling screen renders in; throws outside a BottomSheet. */
function useSheet(): SheetContextValue {
  const value = React.useContext(SheetContext);
  if (!value) throw new Error("useSheet must be used inside <BottomSheet>");
  return value;
}

/** A text action in the sheet header, such as Reset on Filters. */
type SheetHeaderAction = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
};

type BottomSheetProps = {
  /** Empty for a sheet whose content names itself, such as a message's actions under its preview. */
  title: string;
  /** Default "Cancel". */
  cancelLabel?: string;
  /** The confirm button ("Save", "Create"); without it the sheet has Cancel only. */
  confirmLabel?: string;
  onConfirm?: () => void;
  confirmDisabled?: boolean;
  /** Shows a spinner on the confirm button and blocks it, while saving. */
  confirmLoading?: boolean;
  /** Runs before the sheet closes by Cancel; the default only closes. */
  onCancel?: () => void;
  /** The Android snap points; iOS takes them from `sheetScreenOptions`. Default `large`. */
  detent?: SheetDetent;
  /** Unsaved input: closing asks "Discard changes?" first, and swipe down is off. */
  dirty?: boolean;
  /** Wraps the content in a scroll view that works with the sheet's gesture. */
  scroll?: boolean;
  /**
   * The scrolling body returns to its top whenever this changes, such as on
   * each step of a sheet with steps. The steps keep one scroll view, so
   * without it a step would open where the user left the previous one.
   */
  scrollKey?: string;
  /**
   * Actions pinned under the scrolling body, such as a live "Show 12
   * tickets". The body then scrolls as with `scroll`, and on Android the
   * footer replaces the default Cancel and confirm. On iOS a footer needs the
   * `large` detent, whose content reaches the sheet bottom.
   */
  footer?: React.ReactNode;
  /**
   * iOS: an action in the leading header slot in place of Cancel, such as
   * Reset on Filters; swiping down still closes the sheet. Android has no
   * header actions, so the screen puts the same action in `footer`.
   */
  headerAction?: SheetHeaderAction;
  /**
   * iOS, a body without `scroll` or `footer` in a `large` sheet: the body
   * ends at the keyboard's top edge while it is shown, for content that
   * keeps its own caret in view, such as a web editor. Android sheets follow
   * the keyboard already.
   */
  avoidKeyboard?: boolean;
  children: React.ReactNode;
};

/** The content of a sheet route: header or footer actions and the body. */
function BottomSheet({
  title,
  cancelLabel,
  confirmLabel,
  onConfirm,
  confirmDisabled = false,
  confirmLoading = false,
  onCancel,
  detent = "large",
  dirty = false,
  scroll = false,
  scrollKey,
  footer,
  headerAction,
  avoidKeyboard = false,
  children,
}: BottomSheetProps) {
  const { t } = useTranslation();
  const navigation = useNavigation();
  const sheetRef = React.useRef<GorhomBottomSheet>(null);
  // Set once the user has chosen to leave: the next removal goes through.
  const leaving = React.useRef(false);

  usePreventRemove(dirty, ({ data }) => {
    if (leaving.current) {
      navigation.dispatch(data.action);
      return;
    }
    confirmDiscardChanges(() => {
      leaving.current = true;
      navigation.dispatch(data.action);
    });
  });

  const closeRoute = React.useCallback(() => {
    if (navigation.canGoBack()) navigation.goBack();
  }, [navigation]);

  const dismiss = React.useCallback(() => {
    leaving.current = true;
    if (Platform.OS === "android" && sheetRef.current) {
      sheetRef.current.close();
    } else {
      closeRoute();
    }
  }, [closeRoute]);

  const requestCancel = React.useCallback(() => {
    const close = () => {
      onCancel?.();
      dismiss();
    };
    if (dirty && !leaving.current) confirmDiscardChanges(close);
    else close();
  }, [dirty, dismiss, onCancel]);

  const context = React.useMemo(() => ({ dismiss }), [dismiss]);
  const resolvedCancel = cancelLabel ?? t("kitCancel");
  const confirmBlocked = confirmDisabled || confirmLoading;

  return (
    <SheetContext.Provider value={context}>
      {Platform.OS === "ios" ? (
        <IosSheet
          title={title}
          cancelLabel={resolvedCancel}
          confirmLabel={confirmLabel}
          onConfirm={onConfirm}
          confirmBlocked={confirmBlocked}
          confirmLoading={confirmLoading}
          onCancel={requestCancel}
          scroll={scroll}
          scrollKey={scrollKey}
          footer={footer}
          headerAction={headerAction}
          avoidKeyboard={avoidKeyboard}
        >
          {children}
        </IosSheet>
      ) : (
        <AndroidSheet
          sheetRef={sheetRef}
          title={title}
          cancelLabel={resolvedCancel}
          confirmLabel={confirmLabel}
          onConfirm={onConfirm}
          confirmBlocked={confirmBlocked}
          confirmLoading={confirmLoading}
          onCancel={requestCancel}
          onClosed={() => {
            leaving.current = true;
            closeRoute();
          }}
          detent={detent}
          dirty={dirty}
          scroll={scroll}
          scrollKey={scrollKey}
          footer={footer}
        >
          {children}
        </AndroidSheet>
      )}
    </SheetContext.Provider>
  );
}

type PlatformSheetProps = {
  title: string;
  cancelLabel: string;
  confirmLabel?: string;
  onConfirm?: () => void;
  confirmBlocked: boolean;
  confirmLoading: boolean;
  onCancel: () => void;
  scroll: boolean;
  scrollKey?: string;
  footer?: React.ReactNode;
  children: React.ReactNode;
};

type ScrollToTop = { scrollTo: (options: { y: number; animated: boolean }) => void };

/**
 * Scrolls the sheet body back to its top when `scrollKey` changes, before
 * the new content is drawn, so a new step never shows at the old offset.
 */
function useScrollToTopOnKey(
  scrollRef: React.RefObject<ScrollToTop | null>,
  scrollKey: string | undefined
) {
  const shownKey = React.useRef<string | undefined>(scrollKey);
  React.useLayoutEffect(() => {
    if (shownKey.current === scrollKey) return;
    shownKey.current = scrollKey;
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [scrollKey, scrollRef]);
}

/**
 * iOS: Cancel (or the header action), the title and a bold confirm in a bar
 * under the system grabber, on the opaque `screen` ground, and the body below
 * it. The bar and the body are returned as siblings, never wrapped (see the
 * note at the top). A hairline under the bar appears once the body scrolls.
 */
function IosSheet({
  title,
  cancelLabel,
  confirmLabel,
  onConfirm,
  confirmBlocked,
  confirmLoading,
  onCancel,
  scroll,
  scrollKey,
  footer,
  headerAction,
  avoidKeyboard,
  children,
}: PlatformSheetProps & { headerAction?: SheetHeaderAction; avoidKeyboard: boolean }) {
  const bottomInset: number = useIosSheetBottomInset();
  const keyboardHeight: number = useIosKeyboardHeight(avoidKeyboard);
  const colors = useThemeColors();
  const scrollRef = React.useRef<ScrollView>(null);
  useScrollToTopOnKey(scrollRef, scrollKey);
  const [scrolled, setScrolled] = React.useState<boolean>(false);
  const onScroll = React.useCallback((event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const next: boolean = event.nativeEvent.contentOffset.y > 0;
    setScrolled((previous) => (previous === next ? previous : next));
  }, []);

  return (
    <>
      {/* Not flattened: react-native-screens measures it to place the scroll view. */}
      <View
        collapsable={false}
        className={cn("bg-screen pt-3", scrolled ? "border-border" : "border-transparent")}
        style={{ borderBottomWidth: StyleSheet.hairlineWidth }}
      >
        <View className="min-h-11 flex-row items-center px-4">
          <View className="min-w-16 items-start">
            {headerAction ? (
              <HeaderTextButton
                label={headerAction.label}
                onPress={headerAction.onPress}
                disabled={headerAction.disabled ?? false}
              />
            ) : (
              <HeaderTextButton label={cancelLabel} onPress={onCancel} />
            )}
          </View>
          {title ? (
            <Text
              accessibilityRole="header"
              numberOfLines={1}
              className="flex-1 px-2 text-center font-sans-semibold text-headline text-foreground"
            >
              {title}
            </Text>
          ) : (
            <View className="flex-1" />
          )}
          <View className="min-w-16 items-end">
            {confirmLabel && onConfirm ? (
              confirmLoading ? (
                <ActivityIndicator size="small" color={colors.mutedForeground} />
              ) : (
                <HeaderTextButton
                  label={confirmLabel}
                  onPress={onConfirm}
                  disabled={confirmBlocked}
                  bold
                />
              )
            ) : null}
          </View>
        </View>
      </View>
      {footer ? (
        // Not flattened, so the scroll view never becomes a direct child of
        // the sheet, and clipping, so nothing scrolled is drawn over the header.
        <View collapsable={false} className="flex-1 overflow-hidden bg-screen">
          <ScrollView
            ref={scrollRef}
            className="flex-1"
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="interactive"
            automaticallyAdjustKeyboardInsets
            onScroll={onScroll}
            scrollEventThrottle={16}
            contentContainerStyle={{ paddingBottom: 16 }}
          >
            {children}
          </ScrollView>
          <View
            collapsable={false}
            className="border-border bg-screen px-4 pt-3"
            style={{ borderTopWidth: StyleSheet.hairlineWidth, paddingBottom: bottomInset + 8 }}
          >
            {footer}
          </View>
        </View>
      ) : scroll ? (
        <ScrollView
          ref={scrollRef}
          className="flex-1 bg-screen"
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          // Keeps the focused field above the keyboard, which covers the
          // lower part of a full-height sheet.
          automaticallyAdjustKeyboardInsets
          onScroll={onScroll}
          scrollEventThrottle={16}
          contentContainerStyle={{ paddingBottom: bottomInset + 16 }}
        >
          {children}
        </ScrollView>
      ) : (
        <View
          className="flex-1 bg-screen"
          style={{ paddingBottom: Math.max(bottomInset, keyboardHeight) }}
        >
          {children}
        </View>
      )}
    </>
  );
}

/**
 * The space an iOS sheet keeps free at its bottom edge: the home indicator.
 * A sheet opened from a tab's stack reads that tab's safe-area context, whose
 * bottom inset also counts the tab bar (83 pt instead of 34), although the
 * sheet covers the tab bar. The window's own inset is right for every sheet,
 * and the app is portrait only, so it does not change after launch.
 */
function useIosSheetBottomInset(): number {
  const insets = useSafeAreaInsets();
  return initialWindowMetrics?.insets.bottom ?? insets.bottom;
}

/**
 * How far the keyboard reaches up from the screen's bottom edge, 0 while it
 * is hidden or when `enabled` is off. A `large` sheet ends at the screen's
 * bottom edge, so this is the part of the sheet the keyboard covers. A view
 * inside the form sheet cannot measure that itself: its window position
 * leaves out the sheet's own offset from the top.
 */
function useIosKeyboardHeight(enabled: boolean): number {
  const [height, setHeight] = React.useState<number>(0);

  React.useEffect(() => {
    if (!enabled) return;
    const follow = (event: KeyboardEvent, next: number): void => {
      if (event.duration > 0) {
        LayoutAnimation.configureNext({
          duration: event.duration,
          update: { duration: event.duration, type: LayoutAnimation.Types.keyboard },
        });
      }
      setHeight(next);
    };
    const subscriptions = [
      Keyboard.addListener("keyboardWillChangeFrame", (event: KeyboardEvent) =>
        follow(event, Math.max(0, Dimensions.get("screen").height - event.endCoordinates.screenY))
      ),
      Keyboard.addListener("keyboardWillHide", (event: KeyboardEvent) => follow(event, 0)),
    ];
    return () => subscriptions.forEach((subscription) => subscription.remove());
  }, [enabled]);

  return enabled ? height : 0;
}

type HeaderTextButtonProps = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  /** The confirm action is bold; Cancel and other actions are regular. */
  bold?: boolean;
};

/** A text action of the iOS sheet header, 44 pt tall as a touch target. */
function HeaderTextButton({
  label,
  onPress,
  disabled = false,
  bold = false,
}: HeaderTextButtonProps) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      hitSlop={{ left: 10, right: 10 }}
      onPress={onPress}
      className={cn("min-h-11 justify-center active:opacity-60", disabled && "opacity-50")}
    >
      <Text
        numberOfLines={1}
        className={cn("text-body text-link", bold ? "font-sans-semibold" : "font-sans")}
      >
        {label}
      </Text>
    </Pressable>
  );
}

type AndroidSheetProps = PlatformSheetProps & {
  sheetRef: React.RefObject<GorhomBottomSheet | null>;
  onClosed: () => void;
  detent: SheetDetent;
  dirty: boolean;
};

/**
 * Android: a Material bottom sheet with the title on top and, at the foot,
 * Cancel and confirm or the screen's own footer.
 */
function AndroidSheet({
  sheetRef,
  title,
  cancelLabel,
  confirmLabel,
  onConfirm,
  confirmBlocked,
  confirmLoading,
  onCancel,
  onClosed,
  detent,
  dirty,
  scroll,
  scrollKey,
  footer: customFooter,
  children,
}: AndroidSheetProps) {
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const scrollRef = React.useRef<BottomSheetScrollViewMethods>(null);
  useScrollToTopOnKey(scrollRef, scrollKey);
  const snapPoints = ANDROID_SNAP_POINTS[detent];

  const renderBackdrop = React.useCallback(
    (backdropProps: BottomSheetBackdropProps) => (
      <BottomSheetBackdrop
        {...backdropProps}
        appearsOnIndex={0}
        disappearsOnIndex={-1}
        // The scrim token carries the per-scheme alpha, so the backdrop fades in to it in full.
        opacity={1}
        style={[backdropProps.style, { backgroundColor: colors.scrim }]}
        pressBehavior="none"
        onPress={onCancel}
      />
    ),
    [colors.scrim, onCancel]
  );

  const footer = customFooter ? (
    <View className="px-6 pt-3" style={{ paddingBottom: insets.bottom + 16 }}>
      {customFooter}
    </View>
  ) : (
    <View className="flex-row gap-3 px-6 pt-3" style={{ paddingBottom: insets.bottom + 16 }}>
      <Button variant="ghost" className="flex-1" onPress={onCancel}>
        {cancelLabel}
      </Button>
      {confirmLabel && onConfirm ? (
        <Button
          className="flex-1"
          disabled={confirmBlocked && !confirmLoading}
          loading={confirmLoading}
          onPress={onConfirm}
        >
          {confirmLabel}
        </Button>
      ) : null}
    </View>
  );

  const header = title ? (
    <Text
      accessibilityRole="header"
      numberOfLines={2}
      className="px-6 pb-3 pt-1 font-sans-semibold text-title-3 text-foreground"
    >
      {title}
    </Text>
  ) : null;

  return (
    <View className="flex-1">
      <GorhomBottomSheet
        ref={sheetRef}
        index={0}
        snapPoints={snapPoints}
        enableDynamicSizing={!snapPoints}
        topInset={insets.top}
        enablePanDownToClose={!dirty}
        keyboardBehavior="interactive"
        keyboardBlurBehavior="restore"
        android_keyboardInputMode="adjustResize"
        onClose={onClosed}
        backdropComponent={renderBackdrop}
        backgroundStyle={{ backgroundColor: colors.cardBackground, borderRadius: 28 }}
        handleIndicatorStyle={{ backgroundColor: colors.grabber, width: 32, height: 4 }}
      >
        {scroll || customFooter ? (
          <>
            {header}
            <BottomSheetScrollView
              ref={scrollRef}
              keyboardShouldPersistTaps="handled"
              style={{ flex: 1 }}
            >
              {children}
            </BottomSheetScrollView>
            {footer}
          </>
        ) : (
          <BottomSheetView style={snapPoints ? { flex: 1 } : undefined}>
            {header}
            <View className={cn(snapPoints && "flex-1")}>{children}</View>
            {footer}
          </BottomSheetView>
        )}
      </GorhomBottomSheet>
    </View>
  );
}

export { BottomSheet, sheetScreenOptions, useSheet };
export type { BottomSheetProps, SheetDetent, SheetHeaderAction };
