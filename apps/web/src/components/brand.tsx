import Link from "next/link";
import { cn } from "cn";

/** Wordmark: the amber block is a strip of parcel tape. */
export function Brand({ className, href = "/" }: { className?: string; href?: string }) {
  return (
    <Link href={href} className={cn("inline-flex items-center gap-2 text-lg font-extrabold", className)}>
      <span aria-hidden className="h-3.5 w-6 -skew-x-12 rounded-[2px] bg-tape" />
      <span className="[font-stretch:125%] tracking-tight">SwiftDrop</span>
    </Link>
  );
}
