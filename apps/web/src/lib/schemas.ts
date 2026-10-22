import { z } from "zod";

// Mirrors services/auth-service/.../api/dto/ValidationRules.java. Change both together.
export const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;
export const PHONE = /^(?:0|\+94)[1-9]\d{8}$/;

const email = z.email("Enter a valid email address").max(254);
const password = z.string().regex(PASSWORD, "Use 8 to 72 characters with at least one letter and one number");
const phone = z.string().trim().regex(PHONE, "Use a Sri Lankan number like 0771234567 or +94771234567");
const fullName = z.string().trim().min(1, "Enter your full name").max(120, "Keep it under 120 characters");

export const loginSchema = z.object({
  email: z.email("Enter a valid email address"),
  password: z.string().min(1, "Enter your password"),
});

export const signupSchema = z.object({ fullName, email, phone, password });

export const merchantSignupSchema = signupSchema.extend({
  businessName: z.string().trim().min(1, "Enter your business name").max(160, "Keep it under 160 characters"),
  registrationNo: z.string().trim().min(1, "Enter your business registration number").max(40, "Keep it under 40 characters"),
});

export const createStaffSchema = signupSchema.extend({
  role: z.enum(["COURIER", "DISPATCHER"], "Choose courier or dispatcher"),
});

export type LoginInput = z.infer<typeof loginSchema>;
export type SignupInput = z.infer<typeof signupSchema>;
export type MerchantSignupInput = z.infer<typeof merchantSignupSchema>;
export type CreateStaffInput = z.infer<typeof createStaffSchema>;
