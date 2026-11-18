import * as Haptics from "expo-haptics";
import * as React from "react";
import { View, type LayoutChangeEvent } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Svg, { Circle, Line, Path } from "react-native-svg";

import { burndownGeometry, formatIdeal, type BurndownGeometry } from "@/components/home/homeModel";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useDates } from "@/hooks/useDates";
import { useTranslation } from "@/i18n";
import type { HomeBurndownPoint } from "@/lib/schemas/home";

const CHART_HEIGHT = 176;
const CHART_PAD = 8;

type BurndownChartProps = {
  points: HomeBurndownPoint[];
  totalTickets: number;
  openTickets: number;
};

/**
 * Open tickets per sprint day against the even pace to zero, under a
 * gridline at the sprint's ticket total, with a dashed "Today" line and a
 * marker on today's count. Dragging along the chart shows the day, its open count and the even
 * pace, with a selection haptic on each new day. Days are Istanbul business
 * days, sent by the server as dates.
 */
function BurndownChart({ points, totalTickets, openTickets }: BurndownChartProps) {
  const { t, language } = useTranslation();
  const dates = useDates();
  const colors = useThemeColors();
  const [width, setWidth] = React.useState<number>(0);
  const [scrubIndex, setScrubIndex] = React.useState<number | null>(null);
  const lastIndex = React.useRef<number | null>(null);

  const geometry: BurndownGeometry = React.useMemo(
    () => burndownGeometry(points, width, CHART_HEIGHT, CHART_PAD),
    [points, width]
  );

  const scrubTo = React.useCallback(
    (x: number) => {
      const index: number = geometry.indexAt(x);
      if (index === lastIndex.current) return;
      lastIndex.current = index;
      setScrubIndex(index);
      Haptics.selectionAsync().catch((error: unknown) => {
        console.warn("[burndown] haptic failed", error instanceof Error ? error.message : error);
      });
    },
    [geometry]
  );

  const endScrub = React.useCallback(() => {
    lastIndex.current = null;
    setScrubIndex(null);
  }, []);

  const pan = React.useMemo(
    () =>
      Gesture.Pan()
        .runOnJS(true)
        .minDistance(0)
        .activeOffsetX([-4, 4])
        .failOffsetY([-12, 12])
        .onBegin((event) => scrubTo(event.x))
        .onUpdate((event) => scrubTo(event.x))
        .onFinalize(endScrub),
    [scrubTo, endScrub]
  );

  const onLayout = (event: LayoutChangeEvent) => setWidth(event.nativeEvent.layout.width);

  const first: HomeBurndownPoint | undefined = points[0];
  const last: HomeBurndownPoint | undefined = points[points.length - 1];
  const scrubbed: HomeBurndownPoint | undefined =
    scrubIndex !== null ? points[scrubIndex] : undefined;
  const today: HomeBurndownPoint | undefined =
    geometry.todayIndex >= 0 ? points[geometry.todayIndex] : undefined;
  const todayX: number | null = today ? geometry.x(geometry.todayIndex) : null;
  // The label sits beside the line on the side with room, so it never runs
  // off the chart on the first or the last day.
  const todayLabelOnLeft: boolean = todayX !== null && todayX > width / 2;

  const summary: string = t("homeBurndownSummary", { count: totalTickets, open: openTickets });
  const scrubLabel: string | null = scrubbed
    ? [
        dates.shortDate(scrubbed.date),
        scrubbed.remaining !== null
          ? t("homeBurndownScrubOpen", { count: scrubbed.remaining })
          : null,
        t("homeBurndownScrubIdeal", { value: formatIdeal(scrubbed.ideal, language) }),
      ]
        .filter((part): part is string => !!part)
        .join(" · ")
    : null;

  return (
    <View className="gap-2 px-4">
      <Text variant="subhead" className="text-muted-foreground" accessibilityLiveRegion="polite">
        {scrubLabel ?? summary}
      </Text>
      <GestureDetector gesture={pan}>
        <View
          onLayout={onLayout}
          accessible
          accessibilityRole="image"
          accessibilityLabel={summary}
          accessibilityHint={t("homeBurndownScrubHint")}
        >
          {/* "Today" takes its own line above the plot: the top gridline runs
              at the plot's top edge, where the label would cross it. */}
          {todayX !== null ? (
            <Text
              variant="caption-2"
              importantForAccessibility="no"
              accessibilityElementsHidden
              className="text-muted-foreground"
              style={
                width === 0
                  ? { opacity: 0 }
                  : todayLabelOnLeft
                    ? { alignSelf: "flex-end", marginRight: width - todayX + 4 }
                    : { marginLeft: todayX + 4 }
              }
            >
              {t("dateToday")}
            </Text>
          ) : null}
          <View style={{ height: CHART_HEIGHT }}>
            {width > 0 ? (
              <Svg width={width} height={CHART_HEIGHT}>
                <Line
                  x1={CHART_PAD}
                  x2={width - CHART_PAD}
                  y1={geometry.y(0)}
                  y2={geometry.y(0)}
                  stroke={colors.border}
                  strokeWidth={1}
                />
                {totalTickets > 0 ? (
                  <Line
                    x1={CHART_PAD}
                    x2={width - CHART_PAD}
                    y1={geometry.y(totalTickets)}
                    y2={geometry.y(totalTickets)}
                    stroke={colors.border}
                    strokeWidth={1}
                  />
                ) : null}
                {todayX !== null ? (
                  <Line
                    x1={todayX}
                    x2={todayX}
                    y1={0}
                    y2={CHART_HEIGHT - CHART_PAD}
                    stroke={colors.mutedForeground}
                    strokeWidth={1}
                    strokeDasharray="2 3"
                  />
                ) : null}
                <Path
                  d={geometry.idealPath}
                  stroke={colors.mutedForeground}
                  strokeWidth={1.5}
                  strokeDasharray="4 4"
                  fill="none"
                />
                <Path
                  d={geometry.actualPath}
                  stroke={colors.chart1}
                  strokeWidth={2.5}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                  fill="none"
                />
                {today && today.remaining !== null ? (
                  <Circle
                    cx={geometry.x(geometry.todayIndex)}
                    cy={geometry.y(today.remaining)}
                    r={4}
                    fill={colors.chart1}
                    stroke={colors.card}
                    strokeWidth={2}
                  />
                ) : null}
                {scrubIndex !== null ? (
                  <Line
                    x1={geometry.x(scrubIndex)}
                    x2={geometry.x(scrubIndex)}
                    y1={CHART_PAD}
                    y2={CHART_HEIGHT - CHART_PAD}
                    stroke={colors.foreground}
                    strokeWidth={1}
                  />
                ) : null}
                {scrubbed && scrubbed.remaining !== null && scrubIndex !== null ? (
                  <Circle
                    cx={geometry.x(scrubIndex)}
                    cy={geometry.y(scrubbed.remaining)}
                    r={5}
                    fill={colors.card}
                    stroke={colors.chart1}
                    strokeWidth={2}
                  />
                ) : null}
              </Svg>
            ) : null}
          </View>
        </View>
      </GestureDetector>
      <View className="flex-row justify-between">
        <Text variant="caption-2" className="text-muted-foreground">
          {first ? dates.shortDate(first.date) : ""}
        </Text>
        <Text variant="caption-2" className="text-muted-foreground">
          {last ? dates.shortDate(last.date) : ""}
        </Text>
      </View>
    </View>
  );
}

export { BurndownChart };
export type { BurndownChartProps };
