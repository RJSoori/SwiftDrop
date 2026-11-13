import { AssistantPanel } from "@/components/assistant/assistant-panel";

/** Customer area only: adds the delivery assistant without touching the customer pages. */
export default function CustomerLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      {children}
      <AssistantPanel />
    </>
  );
}
