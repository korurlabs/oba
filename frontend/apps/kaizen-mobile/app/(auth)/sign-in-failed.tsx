import { View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useLocalSearchParams } from "expo-router";
import { LogIn, TriangleAlert } from "lucide-react-native";

import { useSignInAction } from "@/components/auth/useSignInAction";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { SIGN_IN_FAILED_CODE } from "@/contexts/signInErrors";
import { useTranslation } from "@/i18n";

/**
 * "Signing in failed": the Keycloak round trip or the code exchange failed.
 * The OAuth error code (such as `invalid_grant`) is shown so support can tell
 * the causes apart. "Sign in again" restarts the flow; cancelling it returns to
 * Sign in.
 */
export default function SignInFailedScreen() {
  const { t } = useTranslation();
  const { code } = useLocalSearchParams<{ code?: string }>();
  const { start, pending } = useSignInAction();
  const errorCode: string = typeof code === "string" && code !== "" ? code : SIGN_IN_FAILED_CODE;

  return (
    <SafeAreaView className="flex-1 bg-screen">
      <View className="flex-1 items-center justify-center gap-3.5 px-8">
        <View className="size-16 items-center justify-center rounded-full bg-error-light">
          <Icon as={TriangleAlert} size={30} className="text-error" />
        </View>
        <Text variant="title-2" className="text-center">
          {t("authSignInFailedTitle")}
        </Text>
        <Text className="text-center text-muted-foreground">{t("authSignInFailedBody")}</Text>
        <View className="rounded-md bg-muted px-2.5 py-1.5">
          <Text selectable className="font-mono-medium text-footnote text-muted-foreground">
            {t("authErrorCode", { code: errorCode })}
          </Text>
        </View>
        <View className="self-stretch pt-3">
          <Button block icon={LogIn} loading={pending} onPress={() => void start()}>
            {t("authSignInAgain")}
          </Button>
        </View>
      </View>
    </SafeAreaView>
  );
}
