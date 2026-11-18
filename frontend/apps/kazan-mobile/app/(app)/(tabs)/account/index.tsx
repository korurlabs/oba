import { canManageOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import * as Application from "expo-application";
import { useRouter } from "expo-router";
import { Building2, Globe, LogOut, Mail, Settings } from "lucide-react-native";
import * as React from "react";
import { Platform, RefreshControl, ScrollView } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { AccountHeader } from "@/components/account/AccountHeader";
import { accountSubtitle, versionLabel } from "@/components/account/accountModel";
import { useAccountActions } from "@/components/account/useAccountActions";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { OfflineBanner } from "@/components/kazan/OfflineBanner";
import { TAB_ROOT_CONTENT_TOP, TabRootHeader } from "@/components/kazan/TabRootHeader";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useAuth } from "@/contexts/AuthContext";
import { useMe } from "@/hooks/queries/users";
import { useTranslation } from "@/i18n";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { displayNameOf } from "@/lib/schemas/users";
import { cn } from "@/lib/utils";

const IS_IOS = Platform.OS === "ios";

/**
 * The Account tab: who is signed in, the invitations and organizations,
 * Settings, Kazan web and Sign out. Invitations are managed in the Account
 * web app and organizations in the Admin web app, so those rows open them in
 * the in-app browser. Admin refuses a user who manages no organization, so
 * the Organizations row shows, as on the web, only to an owner or admin of an
 * active organization.
 */
export default function AccountScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const me = useMe(true);
  const { signOutMutation } = useAuth();
  const activeOrganizationId: string | null = useActiveOrganizationId();
  const { openPlatformPage, openKazanWeb, confirmSignOut } = useAccountActions();
  const [refreshing, setRefreshing] = React.useState<boolean>(false);

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    me.refetch()
      .catch((error: unknown) => {
        // The error state below shows a failed read; this only records the pull.
        console.warn("[account] refresh failed", error instanceof Error ? error.message : error);
      })
      .finally(() => setRefreshing(false));
  }, [me]);

  const organizationName: string | null =
    me.data?.organizations?.find((org) => org.id === activeOrganizationId)?.name ?? null;
  const pendingCount: number = me.data?.pendingInvitations?.length ?? 0;
  const name: string = displayNameOf(me.data);
  const managesOrganizations: boolean = canManageOrganizations(me.data);

  const body = me.isPending ? (
    <SkeletonGroup className="mx-4 mt-2 gap-4">
      <SkeletonRow />
      <SkeletonRow />
      <SkeletonRow />
    </SkeletonGroup>
  ) : me.isError ? (
    <EmptyState
      variant="error"
      title={t("accountLoadFailed")}
      onAction={() => void me.refetch()}
      className="mt-16"
    />
  ) : (
    <>
      <AccountHeader
        name={name}
        subtitle={accountSubtitle(me.data.email, organizationName)}
        onPress={() => router.push("/account/profile")}
      />
      <ListSection
        footer={t(managesOrganizations ? "accountWebFooter" : "accountWebFooterInvitations")}
      >
        <ListRow
          title={t("accountInvitations")}
          icon={Mail}
          iconTone="bg-info"
          value={
            pendingCount > 0 ? t("accountInvitationsPending", { count: pendingCount }) : undefined
          }
          accessory="external"
          onPress={() => openPlatformPage("invitations")}
        />
        {managesOrganizations ? (
          <ListRow
            title={t("accountOrganizations")}
            icon={Building2}
            iconTone="bg-korur-blue-deep"
            value={organizationName ?? undefined}
            accessory="external"
            onPress={() => openPlatformPage("organizations")}
          />
        ) : null}
        <ListRow
          title={t("accountSettings")}
          icon={Settings}
          onPress={() => router.push("/account/settings")}
        />
      </ListSection>
      <ListSection>
        <ListRow
          title={t("accountOpenWeb")}
          icon={Globe}
          accessory="external"
          onPress={openKazanWeb}
        />
      </ListSection>
      <ListSection>
        <ListRow
          title={t("authSignOut")}
          icon={IS_IOS ? undefined : LogOut}
          center={IS_IOS}
          destructive
          disabled={signOutMutation.isPending}
          accessory="none"
          onPress={confirmSignOut}
        />
      </ListSection>
      <Text variant="footnote" className="mt-6 px-4 text-center text-muted-foreground">
        {versionLabel(
          t("appName"),
          Application.nativeApplicationVersion,
          Application.nativeBuildVersion,
          organizationName
        )}
      </Text>
    </>
  );

  return (
    <SafeAreaView edges={["top"]} className="flex-1 bg-screen">
      <TabRootHeader title={t("tabAccount")} />
      <OfflineBanner />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName={cn("pb-10", TAB_ROOT_CONTENT_TOP)}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      >
        {body}
      </ScrollView>
    </SafeAreaView>
  );
}
