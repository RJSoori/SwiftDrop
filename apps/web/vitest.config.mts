import { defineConfig } from "vitest/config";

// Next compiles JSX itself; tests need the same automatic runtime to render components.
export default defineConfig({ esbuild: { jsx: "automatic" } });
