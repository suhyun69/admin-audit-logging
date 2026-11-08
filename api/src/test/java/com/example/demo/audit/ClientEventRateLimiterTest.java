package com.example.demo.audit;

import com.example.demo.usecase.support.ClientEventRateLimiter;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class ClientEventRateLimiterTest {
    @Test void fixedWindowIsActorScopedAndExpiredStateIsReclaimed() {
        var limiter=new ClientEventRateLimiter();var now=Instant.parse("2026-11-08T00:00:30Z");
        for(int n=0;n<60;n++)assertThat(limiter.acquire(1,now)).isTrue();
        assertThat(limiter.acquire(1,now)).isFalse();assertThat(limiter.acquire(2,now)).isTrue();assertThat(limiter.acquire(1,now.plusSeconds(60))).isTrue();
    }
    @Test void outOfOrderOrBackwardClockCannotEraseTheCurrentQuota() {
        var limiter=new ClientEventRateLimiter();var current=Instant.parse("2026-11-08T00:01:30Z");
        for(int n=0;n<60;n++)assertThat(limiter.acquire(1,current)).isTrue();
        assertThat(limiter.acquire(2,current.minusSeconds(60))).isTrue();
        assertThat(limiter.acquire(1,current)).isFalse();assertThat(limiter.acquire(1,current.minusSeconds(60))).isFalse();
        assertThat(limiter.acquire(1,current.plusSeconds(60))).isTrue();
    }
    @Test void capacityIsBoundedAndNewMinuteReclaimsOldActors() {
        var limiter=new ClientEventRateLimiter();var now=Instant.parse("2026-11-08T00:00:30Z");
        for(long actor=1;actor<=10_000;actor++)assertThat(limiter.acquire(actor,now)).isTrue();
        assertThat(limiter.acquire(10_001,now)).isFalse();assertThat(limiter.acquire(10_001,now.plusSeconds(60))).isTrue();
    }
}
