import type { LucideIcon, LucideProps } from "lucide-react-native";
import { cssInterop } from "nativewind";
import * as React from "react";

import { TextClassContext } from "@/components/ui/text";
import { cn } from "@/lib/utils";

/** Icon sizes of the design system: 20 in rows and bars, 16 in meta rows. */
export const ICON_SIZE = { row: 20, meta: 16 } as const;

type IconProps = LucideProps & {
  as: LucideIcon;
} & React.RefAttributes<LucideIcon>;

function IconImpl({ as: IconComponent, ...props }: IconProps) {
  return <IconComponent {...props} />;
}

// A `size-*` class becomes the icon's size prop, and `text-*` its colour.
cssInterop(IconImpl, {
  className: {
    target: "style",
    nativeStyleToProp: {
      height: "size",
      width: "size",
    },
  },
});

/**
 * A lucide icon that takes NativeWind classes. It inherits the text colour of
 * the container it sits in (a Button label, a Badge), like a `Text` does.
 *
 * @example <Icon as={ChevronRight} size={16} className="text-muted-foreground" />
 */
function Icon({
  as: IconComponent,
  className,
  size = ICON_SIZE.row,
  strokeWidth = 2,
  ...props
}: IconProps) {
  const textClass = React.useContext(TextClassContext);
  return (
    <IconImpl
      as={IconComponent}
      className={cn("text-foreground", textClass, className)}
      size={size}
      strokeWidth={strokeWidth}
      {...props}
    />
  );
}

export { Icon };
export type { IconProps };
