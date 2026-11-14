"use client";

import { Client } from "@stomp/stompjs";
import { useEffect, useState } from "react";

type LiveUpdatesState<T> = {
  data: Readonly<Record<string, T>>;
  connected: boolean;
};

export function useLiveUpdates<T>(topics: string[] | null): LiveUpdatesState<T> {
  const [data, setData] = useState<Record<string, T>>({});
  const [connected, setConnected] = useState(false);
  const topicsKey = JSON.stringify(topics ?? []);

  useEffect(() => {
    setData({});
    setConnected(false);
    const subscriptions = JSON.parse(topicsKey) as string[];
    const allowedTopics = subscriptions.filter((topic) => topic.startsWith("/topic/"));
    if (allowedTopics.length === 0) return;

    let active = true;
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws`,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        if (active) setConnected(true);
        for (const topic of allowedTopics) {
          client.subscribe(topic, (message) => {
            if (!active) return;
            try {
              const update = JSON.parse(message.body) as T;
              setData((current) => ({ ...current, [topic]: update }));
            } catch {
              setConnected(false);
            }
          });
        }
      },
      onDisconnect: () => {
        if (active) setConnected(false);
      },
      onWebSocketClose: () => {
        if (active) setConnected(false);
      },
      onStompError: () => {
        if (active) setConnected(false);
      },
    });

    client.activate();
    return () => {
      active = false;
      void client.deactivate();
    };
  }, [topicsKey]);

  return { data, connected };
}
