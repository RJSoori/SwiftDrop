"use client";

import { useMutation } from "@tanstack/react-query";
import { MessageCircle, Send, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { chatErrorMessage, pendingLabel, sendChat } from "@/lib/assistant";

type Line = { from: "you" | "assistant"; text: string };

const SUGGESTIONS = ["Where is my parcel?", "How much to send 2 kg to Kandy?"];

/** Floating button + side sheet for the customer delivery assistant (ai-assistant-service). */
export function AssistantPanel() {
  const [open, setOpen] = useState(false);
  const [lines, setLines] = useState<Line[]>([]);
  const [draft, setDraft] = useState("");
  const [sessionId, setSessionId] = useState<string>();
  const [asked, setAsked] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const openButtonRef = useRef<HTMLButtonElement>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const chat = useMutation({
    mutationFn: sendChat,
    onSuccess: (response) => {
      if (response.sessionId) setSessionId(response.sessionId);
      setLines((current) => [...current, { from: "assistant", text: response.reply }]);
    },
  });

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [lines, chat.isPending]);

  function close() {
    setOpen(false);
    openButtonRef.current?.focus();
  }

  function ask(message: string) {
    const text = message.trim();
    if (!text || chat.isPending) return;
    setLines((current) => [...current, { from: "you", text }]);
    setAsked(text);
    setDraft("");
    chat.mutate({ sessionId, message: text });
  }

  return (
    <>
      <Button
        ref={openButtonRef}
        size="icon-lg"
        className="fixed right-5 bottom-5 z-40 size-12 rounded-full shadow-lg"
        aria-label="Open the delivery assistant"
        aria-expanded={open}
        aria-controls="assistant-panel"
        onClick={() => setOpen(true)}
        hidden={open}
      >
        <MessageCircle aria-hidden className="size-5" />
      </Button>

      {open && (
        <section
          id="assistant-panel"
          role="dialog"
          aria-label="Delivery assistant"
          className="fixed inset-y-0 right-0 z-50 flex w-full flex-col border-l bg-background shadow-xl sm:w-96"
          onKeyDown={(e) => e.key === "Escape" && close()}
        >
          <header className="flex items-center justify-between border-b px-4 py-3">
            <div>
              <h2 className="font-semibold">Delivery assistant</h2>
              <p className="text-xs text-muted-foreground">Ask about your parcels or a delivery price.</p>
            </div>
            <Button variant="ghost" size="icon" aria-label="Close the assistant" onClick={close}>
              <X aria-hidden />
            </Button>
          </header>

          <div role="log" aria-live="polite" className="flex-1 space-y-3 overflow-y-auto px-4 py-4 text-sm">
            {lines.length === 0 && (
              <div className="space-y-2">
                <p className="text-muted-foreground">Try asking:</p>
                {SUGGESTIONS.map((s) => (
                  <Button key={s} variant="outline" size="sm" className="mr-2" onClick={() => ask(s)}>
                    {s}
                  </Button>
                ))}
              </div>
            )}
            {lines.map((line, i) => (
              <p
                key={i}
                className={
                  "max-w-[85%] rounded-lg px-3 py-2 whitespace-pre-wrap " +
                  (line.from === "you" ? "ml-auto bg-primary text-primary-foreground" : "bg-muted")
                }
              >
                <span className="sr-only">{line.from === "you" ? "You: " : "Assistant: "}</span>
                {line.text}
              </p>
            ))}
            {chat.isPending && <p className="text-muted-foreground italic">{pendingLabel(asked)}</p>}
            {chat.isError && (
              <p role="alert" className="text-destructive">
                {chatErrorMessage(chat.error)}
              </p>
            )}
            <div ref={endRef} />
          </div>

          <form
            className="flex gap-2 border-t px-4 py-3"
            onSubmit={(e) => {
              e.preventDefault();
              ask(draft);
            }}
          >
            <Input
              ref={inputRef}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Type your question"
              aria-label="Your question"
              maxLength={2000}
            />
            <Button type="submit" size="icon" aria-label="Send" disabled={chat.isPending || !draft.trim()}>
              <Send aria-hidden />
            </Button>
          </form>
        </section>
      )}
    </>
  );
}
