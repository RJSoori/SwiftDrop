import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { GoogleButton } from "./google-button";

describe("GoogleButton", () => {
  it("links to the Google authorisation start with the given label", () => {
    const html = renderToStaticMarkup(<GoogleButton label="Sign up with Google" />);

    expect(html).toContain('href="/api/v1/auth/oauth2/authorize/google"');
    expect(html).toContain("Sign up with Google");
  });
});
