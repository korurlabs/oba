import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Application from "expo-application";
import Constants from "expo-constants";
import { Platform } from "react-native";

import { api, readData, setPushDeviceId } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { getExpoPushToken } from "@/lib/push";
import { pushDeviceRequest } from "@/lib/pushModel";
import {
  pushDeviceResponseSchema,
  pushPreferencesSchema,
  type PushDeviceRegisterRequest,
  type PushDeviceResponse,
  type PushPreferences,
} from "@/lib/schemas/push";

/**
 * Query keys of push. Not organization-scoped: the switches are the user's,
 * the same in every organization.
 */
export const pushKeys = {
  all: ["push"] as const,
  preferences: ["push", "preferences"] as const,
  updatePreferences: ["push", "preferences", "update"] as const,
};

/**
 * `PUT /push/devices`. Not organization-scoped: one registration serves
 * every organization of the user. Silent, because a failed registration on
 * launch is reported once by the caller, not by the app-wide request toast
 * on every start.
 */
export async function putPushDevice(
  request: PushDeviceRegisterRequest
): Promise<PushDeviceResponse> {
  const response = await api.put(API_ROUTES.push.devices, request, { _silentErrors: true });
  return readData(pushDeviceResponseSchema, response.data);
}

/**
 * Registers this phone's Expo push token for the signed-in user and keeps the
 * device id, so sign-out can unregister the phone and the signed-out user
 * gets no more pushes here. Sent on every launch and after a token or
 * language change: the server updates the row of a known token and moves a
 * token registered by another user to this one.
 *
 * Rejects when the token, the request or storing the id fails; the caller
 * logs and reports it.
 */
export async function registerPushDevice(language: string): Promise<PushDeviceResponse> {
  const expoPushToken: string = await getExpoPushToken();
  const device: PushDeviceResponse = await putPushDevice(
    pushDeviceRequest({
      expoPushToken,
      os: Platform.OS,
      deviceName: Constants.deviceName,
      appVersion: Application.nativeApplicationVersion ?? Constants.expoConfig?.version,
      language,
    })
  );
  await setPushDeviceId(device.id);
  return device;
}

/** `registerPushDevice` as a mutation, for the pre-prompt's pending state. */
export function useRegisterPushDevice() {
  return useMutation<PushDeviceResponse, Error, string>({
    mutationFn: registerPushDevice,
    onError: (error: Error) => {
      console.warn("[push] registering this device failed", error.message);
    },
  });
}

/** `GET /push/preferences`: all true for a user who never saved them. */
export async function fetchPushPreferences(): Promise<PushPreferences> {
  const response = await api.get(API_ROUTES.push.preferences);
  return readData(pushPreferencesSchema, response.data);
}

/** The user's push switches, for Notification settings. */
export function usePushPreferences() {
  return useQuery({ queryKey: pushKeys.preferences, queryFn: fetchPushPreferences });
}

/**
 * `PUT /push/preferences` with all five switches. A switch applies at once:
 * the cache takes the new set before the request, and a failure puts the
 * previous set back, so the switch flips back. While the user flips several
 * switches quickly, only the last save writes the server's answer and
 * rereads, so an earlier answer never flips a later switch back for a moment.
 * Silent, because the screen says that the setting was not saved.
 */
export function useUpdatePushPreferences() {
  const queryClient = useQueryClient();
  const lastSave = (): boolean =>
    queryClient.isMutating({ mutationKey: pushKeys.updatePreferences }) === 1;
  return useMutation<PushPreferences, Error, PushPreferences, { previous?: PushPreferences }>({
    mutationKey: pushKeys.updatePreferences,
    mutationFn: async (preferences: PushPreferences) => {
      const response = await api.put(API_ROUTES.push.preferences, preferences, {
        _silentErrors: true,
      });
      return readData(pushPreferencesSchema, response.data);
    },
    onMutate: async (preferences: PushPreferences) => {
      await queryClient.cancelQueries({ queryKey: pushKeys.preferences });
      const previous: PushPreferences | undefined = queryClient.getQueryData<PushPreferences>(
        pushKeys.preferences
      );
      queryClient.setQueryData<PushPreferences>(pushKeys.preferences, preferences);
      return { previous };
    },
    onSuccess: (saved: PushPreferences) => {
      if (lastSave()) queryClient.setQueryData<PushPreferences>(pushKeys.preferences, saved);
    },
    onError: (error: Error, _preferences, context) => {
      if (context?.previous) {
        queryClient.setQueryData<PushPreferences>(pushKeys.preferences, context.previous);
      }
      console.warn("[push] saving the notification settings failed", error.message);
    },
    onSettled: () =>
      lastSave() ? queryClient.invalidateQueries({ queryKey: pushKeys.preferences }) : undefined,
  });
}
