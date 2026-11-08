package com.example.demo.usecase.support;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Single-instance fixed-minute policy. Old windows expire and tracked actors are strictly bounded. */
@Component
public class ClientEventRateLimiter {
    private static final int MAX_ACTORS=10_000;
    private final Map<Long,Window> windows=new HashMap<>();
    private long lastCleanup=Long.MIN_VALUE;
    public synchronized boolean acquire(long actorId,Instant now) {
        // Database waits can deliver older captured instants after newer requests; never rewind the active window.
        long minute=Math.max(lastCleanup,Math.floorDiv(now.getEpochSecond(),60));
        if(lastCleanup<minute) { windows.entrySet().removeIf(entry->entry.getValue().minute<minute);lastCleanup=minute; }
        var window=windows.get(actorId);
        if(window==null) {
            if(windows.size()>=MAX_ACTORS)return false;
            window=new Window(minute);windows.put(actorId,window);
        }
        if(window.count>=60)return false;
        window.count++;return true;
    }
    private static final class Window {
        private final long minute;
        private int count;
        Window(long minute) { this.minute=minute; }
    }
}
