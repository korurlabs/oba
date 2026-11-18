import { Redirect, Stack } from "expo-router";

import { SessionLoading } from "@/components/auth/SessionLoading";
import { useAuth } from "@/contexts/AuthContext";

/**
 * The signed-out screens. Once a session exists (a sign-in finished, or one was
 * stored at start) the app opens; the screens themselves never navigate on
 * success.
 */
export default function AuthLayout() {
  const { status } = useAuth();

  if (status === "loading") return <SessionLoading />;
  if (status === "signed-in") return <Redirect href="/" />;
  return <Stack screenOptions={{ headerShown: false }} />;
}
