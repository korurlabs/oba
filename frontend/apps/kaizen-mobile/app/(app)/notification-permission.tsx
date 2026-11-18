import { useRouter } from "expo-router";
import { AtSign, Bell, Siren, Users } from "lucide-react-native";
import * as React from "react";
import { BackHandler, ScrollView, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { ListRow, ListSection } from "@/components/kaizen/List";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useRegisterPushDevice } from "@/hooks/queries/push";
import { useTranslation } from "@/i18n";
import { markPrePromptSeen, requestPushPermission, type PushPermission } from "@/lib/push";

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

async function rememberAnswer(): Promise<void> {
  try {
    await markPrePromptSeen();
  } catch (error: unknown) {
    // The choice still applies now; the pre-prompt may show once more next start.
    console.warn("[push] remembering the pre-prompt answer failed", messageOf(error));
  }
}

/**
 * The pre-prompt, shown once after the first sign-in before the system
 * dialog: it says which three kinds of message notify, so the user decides
 * knowing that Kaizen stays quiet otherwise. "Turn on notifications" asks the
 * system and registers this phone; "Not now" skips until Notification
 * settings. Either answer is remembered on this phone; Android's back
 * button counts as "Not now".
 */
export default function NotificationPermissionScreen() {
  const { t, language } = useTranslation();
  const router = useRouter();
  const register = useRegisterPushDevice();
  const [asking, setAsking] = React.useState<boolean>(false);

  const close = () => {
    if (router.canGoBack()) router.back();
    else router.replace("/home");
  };

  const turnOn = async () => {
    setAsking(true);
    await rememberAnswer();
    let permission: PushPermission;
    try {
      permission = await requestPushPermission();
    } catch (error: unknown) {
      console.warn("[push] asking for the notification permission failed", messageOf(error));
      toast.error(t("pushPermissionFailed"));
      setAsking(false);
      close();
      return;
    }
    if (!permission.granted) {
      toast.info(t("pushPermissionDenied"));
      setAsking(false);
      close();
      return;
    }
    try {
      await register.mutateAsync(language);
      toast.success(t("pushTurnedOn"));
    } catch (error: unknown) {
      // The hook logged the cause; the next start registers again.
      toast.error(t("pushRegisterFailed"));
      console.warn("[push] the pre-prompt could not register this device", messageOf(error));
    }
    setAsking(false);
    close();
  };

  const notNow = async () => {
    await rememberAnswer();
    close();
  };

  const busy: boolean = asking || register.isPending;

  // Back leaves like "Not now"; while the answer is being applied it does nothing.
  React.useEffect(() => {
    const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
      if (busy) return true;
      void rememberAnswer();
      return false;
    });
    return () => subscription.remove();
  }, [busy]);

  return (
    <SafeAreaView className="flex-1 bg-screen" edges={["top", "bottom"]}>
      <ScrollView contentContainerClassName="flex-grow justify-center pb-6 pt-10">
        <View className="items-center gap-3 px-8">
          <View className="size-16 items-center justify-center rounded-xl bg-primary-light">
            <Icon as={Bell} size={32} className="text-primary-fill" />
          </View>
          <Text
            accessibilityRole="header"
            className="text-center font-sans-bold text-title-2 text-foreground"
          >
            {t("pushPermissionTitle")}
          </Text>
          <Text className="text-center font-sans text-body text-muted-foreground">
            {t("pushPermissionBody")}
          </Text>
        </View>
        <ListSection>
          <ListRow
            icon={AtSign}
            title={t("pushChannelMentionsReplies")}
            subtitle={t("pushPermissionMentionsReplies")}
            accessory="none"
          />
          <ListRow
            icon={Users}
            title={t("pushChannelEveryoneHere")}
            subtitle={t("pushPermissionEveryoneHere")}
            accessory="none"
          />
          <ListRow
            icon={Siren}
            iconTone="bg-destructive"
            title={t("pushChannelUrgentIncidents")}
            subtitle={t("pushPermissionUrgentIncidents")}
            accessory="none"
          />
        </ListSection>
      </ScrollView>
      <View className="gap-3 px-6 pb-4">
        <Button block loading={busy} onPress={() => void turnOn()}>
          {t("pushPermissionTurnOn")}
        </Button>
        <Button variant="ghost" block disabled={busy} onPress={() => void notNow()}>
          {t("pushPermissionNotNow")}
        </Button>
      </View>
    </SafeAreaView>
  );
}
