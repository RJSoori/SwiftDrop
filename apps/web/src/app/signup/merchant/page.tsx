import Link from "next/link";
import { AuthShell } from "@/components/auth-shell";
import { SignupForm } from "@/components/signup-form";

export default function MerchantSignupPage() {
  return (
    <AuthShell
      title="Apply for a merchant account"
      intro="Book pickups for your orders and track every delivery. We check your business details before the account is activated."
      footer={
        <>
          Already applied?{" "}
          <Link href="/login" className="font-medium text-brand underline-offset-4 hover:underline">
            Log in to see your status
          </Link>
        </>
      }
    >
      <SignupForm merchant />
    </AuthShell>
  );
}
