"use client";

import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import { useAuth } from "@/components/auth/auth-provider";
import { PageVisitTracker, trackEvent } from "@/lib/audit/track-event";

export function RouteTracker() {
  const { actor } = useAuth();
  const pathname = usePathname();
  const [visits] = useState(() => new PageVisitTracker());
  useEffect(() => {
    if (actor && visits.shouldTrack(`${actor.id}:${actor.role}`, pathname)) {
      trackEvent({ eventType: "PAGE_VIEW", action: "PAGE_ENTER", pagePath: pathname });
    }
  }, [actor, pathname, visits]);
  return null;
}
