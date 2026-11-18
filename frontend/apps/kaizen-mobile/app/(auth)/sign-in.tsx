import { Image } from "expo-image";
import { View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { LogIn, TriangleAlert } from "lucide-react-native";

import { LanguagePill } from "@/components/auth/LanguagePill";
import { useSignInAction } from "@/components/auth/useSignInAction";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useAuth } from "@/contexts/AuthContext";
import { useTranslation } from "@/i18n";

/**
 * The signed-out start screen. "Sign in" opens the Keycloak login page in the
 * system auth session and returns here; the language pill sets the app language
 * and the login page's language. There is no sign-up here: realm `sensey` does
 * not allow self-registration.
 * After Keycloak ended the session, a notice says why the user is back here.
 */
export default function SignInScreen() {
  const { t } = useTranslation();
  const { signOutReason } = useAuth();
  const { start, pending } = useSignInAction();

  return (
    <SafeAreaView className="flex-1 bg-background px-6 pb-2 pt-2">
      <View className="flex-row justify-end">
        <LanguagePill />
      </View>

      <View className="flex-1 items-center justify-center gap-5">
        {/* The Korur mark, the same art as the splash screen; the app name
            below says what it is, so screen readers skip it. */}
        <Image
          source={require("@/assets/korur-mark.png")}
          style={{ width: 120, height: 120 }}
          contentFit="contain"
          accessible={false}
        />
        <View className="items-center gap-2">
          <Text variant="large-title" className="text-center">
            {t("appName")}
          </Text>
          <Text className="max-w-[300px] text-center text-muted-foreground">
            {t("authSignInTagline")}
          </Text>
        </View>
      </View>

      <View className="gap-3">
        {signOutReason === "session-ended" ? (
          <View
            accessibilityRole="alert"
            className="flex-row items-center gap-3 rounded-md bg-warning-light px-3 py-2.5"
          >
            <Icon as={TriangleAlert} size={18} className="text-foreground" />
            <Text variant="footnote" className="flex-1 text-foreground">
              {t("authSessionEnded")}
            </Text>
          </View>
        ) : null}
        <Button
          block
          icon={LogIn}
          loading={pending}
          disabled={pending}
          onPress={() => void start()}
        >
          {t("authSignIn")}
        </Button>
        <Text variant="footnote" className="mt-1.5 text-center text-muted-foreground">
          {t("authSignInHint")}
        </Text>
      </View>
    </SafeAreaView>
  );
}
