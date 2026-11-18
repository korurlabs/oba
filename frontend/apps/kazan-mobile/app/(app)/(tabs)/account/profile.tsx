import { usePreventRemove } from "@react-navigation/native";
import { Stack, useNavigation, useRouter } from "expo-router";
import { Lock, MonitorSmartphone } from "lucide-react-native";
import * as React from "react";
import { KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";

import { HeaderSaveButton } from "@/components/account/HeaderSaveButton";
import { profileFieldError } from "@/components/account/accountModel";
import { useAccountActions } from "@/components/account/useAccountActions";
import { confirmDiscardChanges } from "@/components/kazan/ActionSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { Avatar } from "@/components/ui/avatar";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import { useMe, useUpdateProfile } from "@/hooks/queries/users";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";
import { isApiError, isValidationError } from "@/lib/api";
import {
  displayNameOf,
  isProfileDirty,
  PROFILE_NAME_MAX_LENGTH,
  profileFormSchema,
  profileFormValues,
  type ProfileFormValues,
} from "@/lib/schemas/users";

type FieldErrors = { firstName: string | null; lastName: string | null };

const NO_ERRORS: FieldErrors = { firstName: null, lastName: null };

/**
 * Profile: the user's first and last name, the email their Korur sign-in
 * manages, and links to the account console for the password and the active
 * sessions. Leaving with unsaved names asks first.
 */
export default function ProfileScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const navigation = useNavigation();
  const me = useMe(true);
  const updateProfile = useUpdateProfile();
  const online = useOnlineWriteGuard();
  const { openAccountConsole } = useAccountActions();

  const saved: ProfileFormValues = profileFormValues(me.data);
  // Null until the user types, so the fields follow the profile as it loads.
  const [edited, setEdited] = React.useState<ProfileFormValues | null>(null);
  const [errors, setErrors] = React.useState<FieldErrors>(NO_ERRORS);
  const values: ProfileFormValues = edited ?? saved;
  const dirty: boolean = edited !== null && isProfileDirty(saved, edited);
  const leaving = React.useRef<boolean>(false);

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

  const change = (field: keyof ProfileFormValues, text: string) => {
    setEdited({ ...values, [field]: text });
    setErrors((current) => ({ ...current, [field]: null }));
  };

  const save = () => {
    const parsed = profileFormSchema.safeParse(values);
    if (!parsed.success) {
      const tooLong: string = t("profileNameTooLong", { max: PROFILE_NAME_MAX_LENGTH });
      const failed = new Set(parsed.error.issues.map((issue) => issue.path[0]));
      setErrors({
        firstName: failed.has("firstName") ? tooLong : null,
        lastName: failed.has("lastName") ? tooLong : null,
      });
      return;
    }
    if (!online()) return;
    updateProfile.mutate(parsed.data, {
      onSuccess: () => {
        leaving.current = true;
        toast.success(t("profileSaved"));
        router.back();
      },
      onError: (error: Error) => {
        if (isValidationError(error)) {
          const fieldErrors: FieldErrors = {
            firstName: profileFieldError(error.fieldErrors, "firstName"),
            lastName: profileFieldError(error.fieldErrors, "lastName"),
          };
          setErrors(fieldErrors);
          // A refusal of a field this form does not show still needs a message.
          if (!fieldErrors.firstName && !fieldErrors.lastName) toast.error(t("profileSaveFailed"));
          return;
        }
        // Every other refused or failed request is toasted app-wide.
        if (!isApiError(error)) toast.error(t("profileSaveFailed"));
      },
    });
  };

  const saveButton = (
    <HeaderSaveButton
      label={t("profileSave")}
      accessibilityLabel={t("profileSaveLabel")}
      disabled={!dirty || !me.data}
      saving={updateProfile.isPending}
      onPress={save}
    />
  );

  const body = me.isPending ? (
    <SkeletonGroup className="mx-4 mt-6 gap-4">
      <SkeletonRow />
      <SkeletonRow />
    </SkeletonGroup>
  ) : me.isError ? (
    <EmptyState
      variant="error"
      title={t("profileLoadFailed")}
      onAction={() => void me.refetch()}
      className="mt-16"
    />
  ) : (
    <>
      <View className="items-center pt-6">
        <Avatar name={displayNameOf(me.data)} size={88} />
      </View>
      <View className="gap-4 px-4 pt-6">
        <TextField
          label={t("profileFirstName")}
          value={values.firstName}
          onChangeText={(text) => change("firstName", text)}
          error={errors.firstName}
          autoComplete="given-name"
          textContentType="givenName"
          autoCapitalize="words"
          returnKeyType="next"
          editable={!updateProfile.isPending}
        />
        <TextField
          label={t("profileLastName")}
          value={values.lastName}
          onChangeText={(text) => change("lastName", text)}
          error={errors.lastName}
          autoComplete="family-name"
          textContentType="familyName"
          autoCapitalize="words"
          returnKeyType="done"
          onSubmitEditing={dirty ? save : undefined}
          editable={!updateProfile.isPending}
        />
        <TextField
          label={t("profileEmail")}
          value={me.data.email}
          helper={t("profileEmailHelper")}
          editable={false}
          selectTextOnFocus
        />
      </View>
      <ListSection header={t("profileSecurity")}>
        <ListRow
          title={t("profilePasswordAndSignIn")}
          icon={Lock}
          accessory="external"
          onPress={() => openAccountConsole("signingIn")}
        />
        <ListRow
          title={t("profileActiveSessions")}
          icon={MonitorSmartphone}
          accessory="external"
          onPress={() => openAccountConsole("deviceActivity")}
        />
      </ListSection>
    </>
  );

  return (
    <>
      <Stack.Screen options={{ headerRight: () => saveButton }} />
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        className="flex-1 bg-screen"
      >
        <ScrollView
          contentInsetAdjustmentBehavior="automatic"
          keyboardShouldPersistTaps="handled"
          contentContainerClassName="pb-10"
        >
          {body}
        </ScrollView>
      </KeyboardAvoidingView>
    </>
  );
}
