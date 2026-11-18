import Image from "next/image";

export type KayaLogoProps = {
  className?: string;
};

/** The Kaya wordmark, a line-drawn rock beside the name, shown at the start of the top bar. */
export function KayaLogo({ className = "h-5 w-auto" }: KayaLogoProps) {
  return (
    <Image
      src="/assets/logo-kaya.svg"
      alt="Kaya"
      width={622}
      height={164}
      className={className}
      priority
      unoptimized
    />
  );
}
