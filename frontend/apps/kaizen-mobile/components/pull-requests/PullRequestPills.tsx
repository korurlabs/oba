import * as React from "react";
import { View } from "react-native";

import {
  GITHUB_STATE_STYLE,
  SHARE_STATUS_STYLE,
  type PullRequestPillStyle,
} from "@/components/pull-requests/pullRequestModel";
import { Badge } from "@/components/ui/badge";
import { Text } from "@/components/ui/text";
import { useTranslation } from "@/i18n";
import type { GithubPullRequestState, PullRequestShareStatus } from "@/lib/schemas/pullRequests";
import { cn } from "@/lib/utils";

function Pill({ style }: { style: PullRequestPillStyle }) {
  const { t } = useTranslation();
  return (
    <Badge className={style.className}>
      <View className={cn("size-1.5 rounded-full", style.dotClassName)} />
      <Text numberOfLines={1}>{t(style.labelKey)}</Text>
    </Badge>
  );
}

/** The team's status of a share. */
export function PullRequestStatusPill({ status }: { status: PullRequestShareStatus }) {
  return <Pill style={SHARE_STATUS_STYLE[status]} />;
}

/** A pull request's state on GitHub, in the picker. */
export function GithubStatePill({ state }: { state: GithubPullRequestState }) {
  return <Pill style={GITHUB_STATE_STYLE[state]} />;
}
