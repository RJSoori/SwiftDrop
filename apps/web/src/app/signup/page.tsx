import Link from "next/link";
import { AuthShell } from "@/components/auth-shell";
import { SignupForm } from "@/components/signup-form";

export default function SignupPage() {
  return (
    <AuthShell
      title="Create your account"
      intro="Send parcels and follow them to the door."
      footer={
        <div className="grid gap-2">
          <p>
            Already have an account?{" "}
            <Link href="/login" className="font-medium text-brand underline-offset-4 hover:underline">
              Log in
            </Link>
          </p>
          <p>
            Sending orders for a business?{" "}
            <Link href="/signup/merchant" className="font-medium text-brand underline-offset-4 hover:underline">
              Apply for a merchant account
            </Link>
          </p>
        </div>
      }
    >
      <SignupForm />
    </AuthShell>
  );
}
