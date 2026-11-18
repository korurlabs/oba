import Image from "next/image";

export type OtakLogoProps = {
  className?: string;
};

/** The Otak wordmark, a line-drawn yurt beside the name, shown at the start of the top bar. */
export function OtakLogo({ className = "h-5 w-auto" }: OtakLogoProps) {
  return (
    <Image
      src="/assets/logo-otak.svg"
      alt="Otak"
      width={549}
      height={164}
      className={className}
      priority
      unoptimized
    />
  );
}
