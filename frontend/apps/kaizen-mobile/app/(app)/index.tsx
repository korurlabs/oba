import { Redirect } from "expo-router";

/** The app root opens the Home tab: sign-in and deep links that name no screen land here. */
export default function AppIndex() {
  return <Redirect href="/home" />;
}
