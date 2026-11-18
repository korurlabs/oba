import { Badge } from "@oba/ui/sky/badge";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";

import { PROVIDER_LABELS, type AccountProvider } from "../schemas";

const NOT_CONNECTED = "Not connected";

/**
 * What the provider itself will report once Kaya reads usage from it. Until
 * then the card names the fields only: it shows no value, so nothing on it
 * can be mistaken for real provider data.
 */
export const PROVIDER_USAGE_FIELDS: readonly { group: string; fields: readonly string[] }[] = [
  { group: "Plan", fields: ["Plan", "Status", "Renews on"] },
  { group: "Limits", fields: ["Used of limit", "Resets at"] },
  { group: "Spend", fields: ["This period", "Credits left"] },
  { group: "Seats", fields: ["Used of total"] },
];

/** The provider-reported usage of an account, which Kaya does not read yet. */
export function AccountProviderUsage({ provider }: { provider: AccountProvider }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Provider usage</CardTitle>
        <CardAction>
          <Badge variant="muted">{NOT_CONNECTED}</Badge>
        </CardAction>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <p className="text-muted-foreground text-sm">
          Kaya does not read usage from {PROVIDER_LABELS[provider]} yet.
        </p>
        {PROVIDER_USAGE_FIELDS.map(({ group, fields }) => (
          <section key={group} aria-label={group} className="flex flex-col gap-1">
            <h3 className="text-sm font-medium">{group}</h3>
            <dl className="divide-y">
              {fields.map((field: string) => (
                <div key={field} className="flex items-baseline justify-between gap-4 py-1.5">
                  <dt className="text-muted-foreground text-sm">{field}</dt>
                  <dd className="text-muted-foreground text-sm">{NOT_CONNECTED}</dd>
                </div>
              ))}
            </dl>
          </section>
        ))}
      </CardContent>
    </Card>
  );
}
