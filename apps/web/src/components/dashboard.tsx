"use client";

export function PageHeading({ title, intro }: { title: string; intro?: string }) {
  return (
    <div className="max-w-2xl">
      <h1 className="display text-3xl sm:text-4xl">{title}</h1>
      {intro && <p className="mt-3 text-muted-foreground">{intro}</p>}
    </div>
  );
}
