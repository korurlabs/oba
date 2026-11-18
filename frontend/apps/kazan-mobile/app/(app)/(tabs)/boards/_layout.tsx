import { Stack } from "expo-router";

import { sheetScreenOptions } from "@/components/kazan/BottomSheet";
import { offlineScreenLayout } from "@/components/kazan/OfflineBanner";
import { stackHeaderOptions } from "@/components/kazan/stackHeader";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";

/**
 * Links from other tabs (Home's board rows, "Create your first board") land
 * on a board or a sheet with the boards list under it, so Back returns there.
 */
export const unstable_settings = { initialRouteName: "index" };

/**
 * The Boards tab: the boards list, a board, its settings and their Labels,
 * Projects, Milestones, Child boards and Members lists pushed over it, and the
 * organization switcher, New board, Rename, Filters, the sprint sheets, Move
 * to sprint, the Label, Project and Milestone sheets, Add existing boards, Add
 * member and Delete board as sheets. The list draws its own title row (TabRootHeader), like
 * every tab root, so it hides the native bar; its title still names the Back
 * button of the screens pushed over it.
 *
 * A board keeps its view tabs and sprint picker above its lists, outside any
 * scroll view, so its bar stays opaque: with a search field iOS otherwise
 * makes the bar translucent and lays the board out from the top of the
 * screen, behind the bar and its search field.
 */
export default function BoardsLayout() {
  const { t } = useTranslation();
  const colors = useThemeColors();

  return (
    <Stack screenLayout={offlineScreenLayout} screenOptions={stackHeaderOptions(colors)}>
      <Stack.Screen
        name="index"
        options={{
          title: t("tabBoards"),
          headerShown: false,
        }}
      />
      <Stack.Screen
        name="board/[boardId]/index"
        options={{ title: "", headerTransparent: false }}
      />
      <Stack.Screen name="board/[boardId]/settings" options={{ title: t("boardSettings") }} />
      <Stack.Screen name="board/[boardId]/labels" options={{ title: t("boardSettingsLabels") }} />
      <Stack.Screen
        name="board/[boardId]/projects"
        options={{ title: t("boardSettingsProjects") }}
      />
      <Stack.Screen
        name="board/[boardId]/milestones"
        options={{ title: t("boardSettingsMilestones") }}
      />
      <Stack.Screen
        name="board/[boardId]/child-boards"
        options={{ title: t("boardSettingsChildBoards") }}
      />
      <Stack.Screen name="board/[boardId]/members" options={{ title: t("boardSettingsMembers") }} />
      <Stack.Screen name="board/[boardId]/label-form" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="board/[boardId]/project-form" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="board/[boardId]/milestone-form" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/add-child-boards" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/add-member" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/delete-board" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/filters" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/create-sprint" options={sheetScreenOptions("large")} />
      <Stack.Screen
        name="board/[boardId]/join-parent-sprint"
        options={sheetScreenOptions("large")}
      />
      <Stack.Screen name="board/[boardId]/edit-sprint" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/complete-sprint" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/move-to-sprint" options={sheetScreenOptions("large")} />
      <Stack.Screen name="board/[boardId]/bulk-move" options={sheetScreenOptions("large")} />
      <Stack.Screen name="org-switcher" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="new-board" options={sheetScreenOptions("large")} />
      <Stack.Screen name="rename-board" options={sheetScreenOptions("medium")} />
    </Stack>
  );
}
