import Image from "next/image";

export type KorurLogoProps = {
  className?: string;
};

/** The Korur wordmark shown at the start of the top bar. */
export function KorurLogo({ className = "h-5 w-auto" }: KorurLogoProps) {
  return (
    <Image
      src="/assets/logo-korur.png"
      alt="Korur"
      width={686}
      height={159}
      className={className}
      priority
    />
  );
}
