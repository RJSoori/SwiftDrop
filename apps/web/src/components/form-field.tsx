import type { FieldValues, Path, UseFormReturn } from "react-hook-form";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api";

type FieldProps = React.ComponentProps<"input"> & { id: string; label: string; error?: string; hint?: string };

export function FormField({ id, label, error, hint, ...input }: FieldProps) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} className="h-10" aria-invalid={!!error} aria-describedby={describedBy} {...input} />
      {error ? (
        <p id={`${id}-error`} className="text-sm text-destructive">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-sm text-muted-foreground">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export function FormError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
      {message}
    </p>
  );
}

/** Puts ProblemDetail field errors on the matching inputs; anything else becomes the form-level error. */
export function applyServerErrors<T extends FieldValues>(form: UseFormReturn<T>, error: unknown) {
  if (!(error instanceof ApiError)) {
    form.setError("root", { message: "Could not reach SwiftDrop. Check your connection and try again." });
    return;
  }
  const fields = Object.entries(error.fieldErrors);
  for (const [field, message] of fields) form.setError(field as Path<T>, { message });
  if (fields.length === 0) form.setError("root", { message: error.message });
}
