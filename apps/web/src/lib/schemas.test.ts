import { describe, expect, it } from "vitest";
import { createStaffSchema, loginSchema, merchantSignupSchema, signupSchema } from "./schemas";

const valid = { fullName: "Nimal Perera", email: "nimal@example.lk", phone: "0771234567", password: "password1" };

const errorFields = (result: { success: boolean; error?: { issues: { path: PropertyKey[] }[] } }) =>
  result.success ? [] : result.error!.issues.map((i) => String(i.path[0]));

describe("signup schema (mirrors backend ValidationRules)", () => {
  it("accepts a valid customer", () => {
    expect(signupSchema.safeParse(valid).success).toBe(true);
  });

  it.each(["password1", "A1b2c3d4", "correct horse battery 9"])("accepts password %s", (password) => {
    expect(signupSchema.safeParse({ ...valid, password }).success).toBe(true);
  });

  it.each(["short1", "allletters", "12345678", "", "a1".repeat(37)])("rejects password %s", (password) => {
    expect(errorFields(signupSchema.safeParse({ ...valid, password }))).toContain("password");
  });

  it.each(["0771234567", "+94771234567", "0112345678", "+94112345678"])("accepts phone %s", (phone) => {
    expect(signupSchema.safeParse({ ...valid, phone }).success).toBe(true);
  });

  it.each(["771234567", "+9477123456", "077123456789", "0071234567", "+14155552671", "07712345ab"])(
    "rejects phone %s",
    (phone) => {
      expect(errorFields(signupSchema.safeParse({ ...valid, phone }))).toContain("phone");
    },
  );

  it("requires email and name", () => {
    expect(errorFields(signupSchema.safeParse({ ...valid, email: "not-an-email", fullName: "  " }))).toEqual(
      expect.arrayContaining(["email", "fullName"]),
    );
  });
});

describe("other schemas", () => {
  it("merchant signup needs business details", () => {
    expect(errorFields(merchantSignupSchema.safeParse(valid))).toEqual(["businessName", "registrationNo"]);
    expect(merchantSignupSchema.safeParse({ ...valid, businessName: "Lanka Goods", registrationNo: "PV-1" }).success).toBe(
      true,
    );
  });

  it("staff accounts are courier or dispatcher only", () => {
    expect(createStaffSchema.safeParse({ ...valid, role: "COURIER" }).success).toBe(true);
    expect(errorFields(createStaffSchema.safeParse({ ...valid, role: "ADMIN" }))).toEqual(["role"]);
  });

  it("login only checks presence and email shape", () => {
    expect(loginSchema.safeParse({ email: "a@b.lk", password: "x" }).success).toBe(true);
    expect(errorFields(loginSchema.safeParse({ email: "", password: "" }))).toEqual(["email", "password"]);
  });
});
