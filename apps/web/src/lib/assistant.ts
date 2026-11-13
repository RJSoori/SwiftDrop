import { z } from "zod";
import { api, ApiError } from "./api";

/** Mirrors the ai-assistant-service limits, so a bad message never leaves the browser. */
export const chatRequestSchema = z.object({
  sessionId: z.uuid().optional(),
  message: z.string().trim().min(1, "Type a question first").max(2000, "Keep it under 2000 characters"),
});

export const chatResponseSchema = z.object({
  // null only when the very first turn failed before a session existed
  sessionId: z.uuid().nullable(),
  reply: z.string(),
  toolsUsed: z.array(z.string()),
});

export type ChatRequest = z.input<typeof chatRequestSchema>;
export type ChatResponse = z.infer<typeof chatResponseSchema>;

export async function sendChat(input: ChatRequest): Promise<ChatResponse> {
  const request = chatRequestSchema.parse(input);
  const response = await api<unknown>("/assistant/chat", { method: "POST", json: request });
  return chatResponseSchema.parse(response);
}

/** What to show while the reply is on its way. The server picks the tool, so this is a best guess from the wording. */
export function pendingLabel(message: string): string {
  const text = message.toLowerCase();
  if (/\b(how much|price|cost|fee|quote|send)\b/.test(text)) return "Working out the price…";
  if (/\bsd-|\b(where|parcel|track|status|delivery)\b/.test(text)) return "Checking your parcel…";
  return "Thinking…";
}

export function chatErrorMessage(error: unknown): string {
  if (error instanceof z.ZodError) return error.issues[0]?.message ?? "Please check your message.";
  if (error instanceof ApiError && error.status === 401) return "Your session has ended. Please log in again.";
  if (error instanceof ApiError && error.status === 429) return "That's a lot of questions at once. Wait a moment and try again.";
  return "Sorry, I couldn't reach the assistant. Please try again.";
}
