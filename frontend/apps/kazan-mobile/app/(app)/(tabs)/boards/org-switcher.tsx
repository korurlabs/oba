import { useQueryClient } from "@tanstack/react-query";
import { Plus } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { userKeys, useMe } from "@/hooks/queries/users";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { toast } from "@/components/ui/toast";
import { useAuth } from "@/contexts/AuthContext";
import { useTranslation, type TranslationKey } from "@/i18n";
import { openPlatformPage } from "@/lib/browser";
import { grantsKazan } from "@/lib/kazanAccess";

const ROLE_LABEL: Record<string, TranslationKey> = {
  OWNER: "orgRoleOwner",
  ADMIN: "orgRoleAdmin",
  MEMBER: "orgRoleMember",
  GUEST: "orgRoleGuest",
};

/**
 * The Organizations sheet of the Boards tab: switch the active organization,
 * or open the Account web app to create one. Like the web organization
 * selector it offers no link to Admin, which refuses a user who manages no
 * organization; a manager reaches Admin from the Account tab.
 */
export default function OrgSwitcherSheet() {
  const { t } = useTranslation();
  return (
    <BottomSheet title={t("orgSwitcherTitle")} cancelLabel={t("homeClose")} detent="medium" scroll>
      <OrgSwitcherContent />
    </BottomSheet>
  );
}

function OrgSwitcherContent() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const { dismiss } = useSheet();
  const { activeOrganizationId, switchOrganization } = useAuth();
  const me = useMe(true);
  const [switching, setSwitching] = React.useState<string | null>(null);

  const choose = (organizationId: string) => {
    if (organizationId === activeOrganizationId) {
      dismiss();
      return;
    }
    setSwitching(organizationId);
    switchOrganization(organizationId)
      .then(() => dismiss())
      .catch((error: unknown) => {
        console.warn(
          "[boards] organization switch failed",
          error instanceof Error ? error.message : error
        );
        toast.error(t("orgSwitchFailed"));
      })
      .finally(() => setSwitching(null));
  };

  // Any user may create an organization in Account; on return the list is
  // read again, so the new organization shows at once.
  const openCreateOrganization = () => {
    openPlatformPage("newOrganization")
      .then(() => queryClient.invalidateQueries({ queryKey: userKeys.me }))
      .catch((error: unknown) => {
        console.warn(
          "[boards] the web page failed to open",
          error instanceof Error ? error.message : error
        );
        toast.error(t("homeOpenFailed"));
      });
  };

  // Only organizations that grant Kazan can be opened, as in the web selector;
  // the API refuses every Kazan call in the others.
  const memberships = me.data?.organizations ?? [];
  const organizations = memberships.filter(grantsKazan);
  const listFooter: string =
    organizations.length > 0
      ? t("orgSwitcherFooter")
      : memberships.length > 0
        ? t("orgSwitcherNoKazan")
        : t("orgSwitcherEmpty");

  return (
    <View className="pb-6">
      {me.isPending ? (
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      ) : me.isError ? (
        <InlineError
          message={t("orgLoadFailedTitle")}
          onRetry={() => void me.refetch()}
          className="mx-4"
        />
      ) : (
        <ListSection footer={listFooter}>
          {organizations.map((organization) => (
            <ListRow
              key={organization.id}
              title={organization.name}
              subtitle={
                ROLE_LABEL[organization.role] ? t(ROLE_LABEL[organization.role]) : undefined
              }
              avatar={{ name: organization.name }}
              accessory="check"
              checked={organization.id === activeOrganizationId}
              disabled={switching !== null}
              onPress={() => choose(organization.id)}
            />
          ))}
        </ListSection>
      )}
      <ListSection footer={t("orgSwitcherWebFooter")}>
        <ListRow
          title={t("orgSwitcherCreate")}
          icon={Plus}
          accessory="external"
          onPress={openCreateOrganization}
        />
      </ListSection>
    </View>
  );
}
