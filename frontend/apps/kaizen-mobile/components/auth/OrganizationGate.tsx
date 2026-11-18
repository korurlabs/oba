import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Building2, LogOut, ShieldOff, UserCog } from "lucide-react-native";

import { decideOrganizationGate, type MeState } from "@/components/auth/organizationGateModel";
import { SessionLoading } from "@/components/auth/SessionLoading";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { Button } from "@/components/ui/button";
import { toast } from "@/components/ui/toast";
import { useAuth, type SignOutOutcome } from "@/contexts/AuthContext";
import { signOutWarningKey } from "@/contexts/signOutModel";
import { fetchMe, useMe, userKeys } from "@/hooks/queries/users";
import { useTranslation } from "@/i18n";
import { onOrganizationForbidden } from "@/lib/api";
import { openPlatformPage } from "@/lib/browser";
import { grantsKaizen } from "@/lib/kaizenAccess";
import {
  organizationAccessOf,
  watchOrganizationAccess,
  type OrganizationAccessLoss,
} from "@/lib/organizationAccess";
import { clearActiveOrganizationId, getActiveOrganizationId } from "@/lib/organizationStore";

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/**
 * Keeps the signed-in app on an organization the user belongs to.
 *
 * It reads `GET /users/me` and makes the stored, the default or the first
 * organization active (`decideOrganizationGate`). A user without an
 * organization gets the onboarding state, which opens Account to accept an
 * invitation or create one, and continues once an organization exists.
 *
 * Only an organization that grants Kaizen opens, because the API refuses every
 * Kaizen call in any other. A user whose organizations all withhold Kaizen
 * gets the no-access state, which checks again on request or after Account
 * closes. Like the onboarding state, it mounts none of the screens, push
 * handling, sockets or polls behind the gate, so nothing calls Kaizen there.
 *
 * A 403 for the active organization mid-session reads the user's
 * organizations again. When the user left the organization, the gate clears
 * it and says so. When the user still belongs to it but it no longer grants
 * Kaizen, the fresh list alone moves the user out, and the gate names the
 * organization once; a stored organization found without Kaizen on start or
 * on a foreground read is handled the same way. The screens unmount, and the
 * gate opens the default or first remaining organization that grants Kaizen,
 * or the onboarding or no-access state when none is left. A 403 for a missing
 * permission leaves the organization as it is.
 *
 * During sign-out it opens no organization: sign-out clears the stored one
 * while the membership list is still cached, and reopening it would store
 * the organization again for the next user and remount the signed-in app,
 * whose push registration then runs without a session and fails.
 */
export function OrganizationGate({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const { activeOrganizationId, switchOrganization, signOut, signOutMutation } = useAuth();
  const queryClient = useQueryClient();
  const meQuery = useMe(true);

  useEffect(
    () =>
      watchOrganizationAccess({
        subscribe: onOrganizationForbidden,
        // Read past the cache and write the answer into it, so the gate below
        // decides on the fresh list; a failure leaves the cached list alone.
        loadOrganizationAccess: async () => {
          const fresh = await fetchMe();
          queryClient.setQueryData(userKeys.me, fresh);
          return organizationAccessOf(fresh);
        },
        activeOrganizationId: getActiveOrganizationId,
        onAccessLost: async (_organizationId: string, loss: OrganizationAccessLoss) => {
          // The fresh list is already in the cache, so the gate below sees the
          // organization without Kaizen, names it once and moves the user out.
          // Clearing it here as well would let that render miss the name.
          if (loss === "kaizen") return;
          toast.warning(t("orgAccessLost"));
          await clearActiveOrganizationId();
        },
        onCheckFailed: () => toast.warning(t("orgCheckFailed")),
      }),
    [queryClient, t]
  );

  const me: MeState =
    meQuery.status === "success"
      ? {
          status: "success",
          organizations: meQuery.data.organizations ?? [],
          defaultOrganizationId: meQuery.data.defaultOrganizationId,
        }
      : { status: meQuery.status };
  const { view, target, kaizenLostIn } = decideOrganizationGate(activeOrganizationId, me);

  // Name an organization that stopped granting Kaizen once, before the switch
  // below moves the user out, however many renders the switch takes. The
  // organization is named again only after it has opened with Kaizen since.
  const kaizenLossAnnounced = useRef<string | null>(null);
  useEffect(() => {
    if (kaizenLostIn === null) {
      if (view === "app" && activeOrganizationId === kaizenLossAnnounced.current) {
        kaizenLossAnnounced.current = null;
      }
      return;
    }
    if (activeOrganizationId === null || kaizenLossAnnounced.current === activeOrganizationId) {
      return;
    }
    kaizenLossAnnounced.current = activeOrganizationId;
    toast.warning(t("kaizenAccessLost", { name: kaizenLostIn }));
  }, [kaizenLostIn, activeOrganizationId, view, t]);

  // Make the target organization active once per target, so a re-render while
  // the switch is stored does not start a second one.
  const appliedTarget = useRef<string | null | undefined>(undefined);
  const signingOut: boolean = signOutMutation.isPending;
  useEffect(() => {
    if (signingOut) return;
    if (target === undefined || target === activeOrganizationId) {
      appliedTarget.current = undefined;
      return;
    }
    if (appliedTarget.current === target) return;
    appliedTarget.current = target;
    const work: Promise<void> =
      target === null ? clearActiveOrganizationId() : switchOrganization(target);
    work.catch((error: unknown) => {
      console.warn("[organizationGate] storing the active organization failed", messageOf(error));
      toast.error(t("orgSwitchFailed"));
    });
  }, [target, activeOrganizationId, signingOut, switchOrganization, t]);

  // Every failure is logged with its cause, because the error screen below
  // shows only generic copy and a response that fails its schema looks like a
  // network failure there. With a stored organization the app is already open,
  // so a failed check of the membership list does not block it; the user
  // learns that the list is stale, and the next foreground refetch checks again.
  const appOpen: boolean = activeOrganizationId !== null;
  useEffect(() => {
    if (!meQuery.isError) return;
    console.warn("[organizationGate] loading the organizations failed", messageOf(meQuery.error));
    if (appOpen) toast.warning(t("orgCheckFailed"));
  }, [meQuery.isError, meQuery.error, meQuery.errorUpdatedAt, appOpen, t]);

  const runSignOut = () => {
    signOut().then(
      (outcome: SignOutOutcome) => {
        const warning = signOutWarningKey(outcome);
        if (warning) toast.warning(t(warning));
      },
      (error: unknown) => {
        console.warn("[organizationGate] sign-out incomplete", messageOf(error));
        toast.error(t("authSignOutIncomplete"));
      }
    );
  };

  if (view === "app") return <>{children}</>;
  if (view === "loading") return <SessionLoading />;

  if (view === "error") {
    return (
      <SafeAreaView className="flex-1 bg-screen">
        <EmptyState
          variant="error"
          title={t("orgLoadFailedTitle")}
          message={t("orgLoadFailedBody")}
          onAction={() => void meQuery.refetch()}
        />
        <View className="px-6 pb-4">
          <Button
            variant="ghost"
            block
            icon={LogOut}
            loading={signOutMutation.isPending}
            onPress={runSignOut}
          >
            {t("authSignOut")}
          </Button>
        </View>
      </SafeAreaView>
    );
  }

  if (view === "no-kaizen") {
    return (
      <NoKaizenAccess
        stillClosed={async () => {
          const result = await meQuery.refetch();
          if (result.isError) throw result.error;
          const organizations = result.data?.organizations ?? [];
          return organizations.length > 0 && !organizations.some(grantsKaizen);
        }}
        signingOut={signOutMutation.isPending}
        onSignOut={runSignOut}
      />
    );
  }

  return (
    <NoOrganization
      pendingInvitations={meQuery.data?.pendingInvitations?.length ?? 0}
      refetchMe={async () => {
        const result = await meQuery.refetch();
        if (result.isError) throw result.error;
        return (result.data?.organizations?.length ?? 0) > 0;
      }}
      signingOut={signOutMutation.isPending}
      onSignOut={runSignOut}
    />
  );
}

interface NoOrganizationProps {
  pendingInvitations: number;
  /** Reloads `GET /users/me` and says whether the user now belongs to an organization. */
  refetchMe: () => Promise<boolean>;
  signingOut: boolean;
  onSignOut: () => void;
}

/**
 * The signed-in user belongs to no organization. Joining and creating one
 * happen in the Account web app in the in-app browser; when it closes, the membership list is
 * read again and the gate opens the app as soon as there is an organization.
 */
function NoOrganization({
  pendingInvitations,
  refetchMe,
  signingOut,
  onSignOut,
}: NoOrganizationProps) {
  const { t } = useTranslation();
  const [opening, setOpening] = useState(false);

  const openOnboarding = async () => {
    setOpening(true);
    try {
      await openPlatformPage("onboarding");
    } catch (error: unknown) {
      console.warn("[organizationGate] opening Account onboarding failed", messageOf(error));
      toast.error(t("orgOnboardingOpenFailed"));
      setOpening(false);
      return;
    }
    try {
      const joined: boolean = await refetchMe();
      if (!joined) toast.info(t("orgOnboardingStillNone"));
    } catch (error: unknown) {
      console.warn("[organizationGate] reloading the organizations failed", messageOf(error));
      toast.error(t("orgLoadFailedTitle"));
    } finally {
      setOpening(false);
    }
  };

  return (
    <SafeAreaView className="flex-1 bg-screen">
      <EmptyState
        icon={Building2}
        title={t("orgOnboardingTitle")}
        message={
          pendingInvitations > 0
            ? t("orgOnboardingInvited", { count: pendingInvitations })
            : t("orgOnboardingBody")
        }
      />
      <View className="gap-3 px-6 pb-4">
        <Button block loading={opening} disabled={signingOut} onPress={() => void openOnboarding()}>
          {pendingInvitations > 0 ? t("orgOnboardingReviewInvitations") : t("orgOnboardingAction")}
        </Button>
        <Button
          variant="ghost"
          block
          icon={LogOut}
          loading={signingOut}
          disabled={opening}
          onPress={onSignOut}
        >
          {t("authSignOut")}
        </Button>
      </View>
    </SafeAreaView>
  );
}

interface NoKaizenAccessProps {
  /**
   * Reloads `GET /users/me` and says whether the user still belongs only to
   * organizations that withhold Kaizen.
   */
  stillClosed: () => Promise<boolean>;
  signingOut: boolean;
  onSignOut: () => void;
}

/**
 * The signed-in user belongs to organizations, but none grants Kaizen. Only an
 * owner or admin of an organization can grant it, so the screen explains that
 * and offers no action of its own beyond the Account web app, like the
 * no-access panel of the Kaizen web app. Admin is not offered: it is closed to
 * a member, who cannot grant Kaizen there. The screen checks again on request
 * or after the user returns from Account, where invitations are accepted and
 * organizations created, and the gate opens the app as soon as an
 * organization grants Kaizen.
 */
function NoKaizenAccess({ stillClosed, signingOut, onSignOut }: NoKaizenAccessProps) {
  const { t } = useTranslation();
  const [busy, setBusy] = useState<"check" | "account" | null>(null);

  const checkAgain = async () => {
    try {
      if (await stillClosed()) toast.info(t("kaizenClosedStill"));
    } catch (error: unknown) {
      console.warn("[organizationGate] reloading the organizations failed", messageOf(error));
      toast.error(t("orgLoadFailedTitle"));
    }
  };

  const recheck = async () => {
    setBusy("check");
    await checkAgain();
    setBusy(null);
  };

  const openAccount = async () => {
    setBusy("account");
    try {
      await openPlatformPage("profile");
    } catch (error: unknown) {
      console.warn("[organizationGate] opening Account failed", messageOf(error));
      toast.error(t("orgOnboardingOpenFailed"));
      setBusy(null);
      return;
    }
    await checkAgain();
    setBusy(null);
  };

  return (
    <SafeAreaView className="flex-1 bg-screen">
      <EmptyState icon={ShieldOff} title={t("kaizenClosedTitle")} message={t("kaizenClosedBody")} />
      <View className="gap-3 px-6 pb-4">
        <Button
          block
          loading={busy === "check"}
          disabled={busy !== null || signingOut}
          onPress={() => void recheck()}
        >
          {t("kaizenClosedCheckAgain")}
        </Button>
        <Button
          variant="outline"
          block
          icon={UserCog}
          loading={busy === "account"}
          disabled={busy !== null || signingOut}
          onPress={() => void openAccount()}
        >
          {t("kaizenClosedOpenAccount")}
        </Button>
        <Button
          variant="ghost"
          block
          icon={LogOut}
          loading={signingOut}
          disabled={busy !== null}
          onPress={onSignOut}
        >
          {t("authSignOut")}
        </Button>
      </View>
    </SafeAreaView>
  );
}
