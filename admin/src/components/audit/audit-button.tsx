"use client";

import { usePathname } from "next/navigation";
import { Button } from "@/components/ui/button";
import { runAuditedAction } from "@/lib/audit/track-event";

type AuditButtonProps = Omit<React.ComponentProps<typeof Button>, "onClick" | "asChild"> & {
  action: "USER_SUSPEND_CLICK" | "USER_ACTIVATE_CLICK";
  resourceType: "USER";
  resourceId: string;
  onClick: (interactionId: string) => void;
};

export function AuditButton({ action, resourceType, resourceId, onClick, ...props }: AuditButtonProps) {
  const pathname = usePathname();
  return <Button {...props} onClick={() => runAuditedAction({
    eventType: "BUTTON_CLICK", action, resourceType, resourceId, pagePath: pathname,
  }, onClick)} />;
}
