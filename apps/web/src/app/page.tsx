import Link from "next/link";
import { Brand } from "@/components/brand";
import { buttonVariants } from "@/components/ui/button";
import { TrackForm } from "./track-form";

export default function Home() {
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between gap-3 px-5 py-5">
        <Brand />
        <nav className="flex items-center gap-2">
          <Link href="/login" className={buttonVariants({ variant: "ghost", size: "lg" })}>
            Log in
          </Link>
          <Link href="/signup" className={buttonVariants({ size: "lg" })}>
            Sign up
          </Link>
        </nav>
      </header>

      <main className="mx-auto grid w-full max-w-6xl flex-1 content-center gap-10 px-5 pb-16 pt-6 lg:grid-cols-[1.3fr_1fr] lg:items-center">
        <div>
          <h1 className="display text-balance text-[2.6rem] sm:text-6xl xl:text-7xl">
            From Colombo to Jaffna, know where your parcel is.
          </h1>
          <p className="mt-6 max-w-xl text-lg text-muted-foreground">
            SwiftDrop collects, sorts and delivers parcels island-wide. Enter a waybill number to see where a delivery
            is right now.
          </p>
        </div>

        <TrackForm />
      </main>

      <footer className="border-t">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 px-5 py-6 text-sm text-muted-foreground">
          <p>Selling online? Send your orders with SwiftDrop.</p>
          <Link href="/signup/merchant" className="font-medium text-brand underline-offset-4 hover:underline">
            Open a merchant account
          </Link>
        </div>
      </footer>
    </div>
  );
}
