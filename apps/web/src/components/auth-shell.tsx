import { Brand } from "@/components/brand";

/** Layout for login and signup: one narrow column, a strip of tape across the top. */
export function AuthShell({ title, intro, children, footer }: {
  title: string;
  intro?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <div className="min-h-dvh border-t-[6px] border-tape">
      <div className="mx-auto w-full max-w-md px-5 py-10 sm:py-16">
        <Brand />
        <h1 className="display mt-10 text-3xl sm:text-4xl">{title}</h1>
        {intro && <p className="mt-3 text-muted-foreground">{intro}</p>}
        <div className="mt-8">{children}</div>
        {footer && <div className="mt-8 border-t pt-6 text-sm text-muted-foreground">{footer}</div>}
      </div>
    </div>
  );
}
