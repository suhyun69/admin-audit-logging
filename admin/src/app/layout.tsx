import type { Metadata } from "next";
import { AuthProvider } from "@/components/auth/auth-provider";
import { QueryProvider } from "@/components/query-provider";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Admin Audit", template: "%s · Admin Audit" },
  description: "관리자 활동을 기록하고 확인하는 감사 콘솔",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="ko"><body><QueryProvider><AuthProvider>{children}</AuthProvider></QueryProvider></body></html>;
}
