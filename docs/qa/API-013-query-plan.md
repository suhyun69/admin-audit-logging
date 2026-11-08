# API-013 — 감사 검색 10만 건 실행계획 검증

검증일: 2026-11-08. 격리 Testcontainers PostgreSQL에서 실제 `EXPLAIN (ANALYZE, BUFFERS)`를 실행했다.

## 환경과 데이터

- PostgreSQL 17.11, Debian ARM64 컨테이너, Testcontainers 1.21.4, Docker Desktop/Engine 29.2.1.
- 호스트 macOS ARM64, Java 21.0.10.
- Flyway V1/V2를 적용하고 감사 100,000행을 삽입한 뒤 `ANALYZE audit_logs` 수행.
- 시간은 2026-11-08T00:00:00Z에서 초 단위로 감소한다. 세 관리자는 밀집 분포이며 네 번째 관리자는 0.1%(100행), DATA_READ는 1%(1,000행)다.
- 합성 데이터의 metadata는 빈 객체다. 삽입 직후 warm buffer에서 단일 SQL 실행을 측정했으며 네트워크·Java 매핑·HTTP 응답 비용은 포함하지 않는다. API p95나 운영 처리량을 보장하는 부하 측정은 아니다.

## 결과

| 조회 | 실제 선택 인덱스 | 반환 | shared hit | Execution Time |
|---|---|---|---|---|
| 시간 범위 | idx_audit_time | 21행 | 4 | 0.027 ms |
| 밀집 관리자(약 1/3) | idx_audit_time + actor 필터 | 21행 | 4 | 0.029 ms |
| 희소 관리자(0.1%) | idx_audit_actor_time | 21행 | 27 | 0.093 ms |
| 이벤트 유형(1%) | idx_audit_type_time | 21행 | 24 | 0.056 ms |
| 희소 관리자 다음 커서 | idx_audit_actor_time + UUID/time tuple 조건 | 21행 | 27 | 0.084 ms |

측정한 조회는 모두 정렬된 Index Scan을 사용했고 별도 Sort/Seq Scan이 없었다. 밀집 관리자 조건에서는 시간 인덱스에서 40행을 추가로 거르는 편을 planner가 선택했다. 인덱스 선택을 강제하지 않았다. 희소 관리자·유형에는 각 복합 인덱스를 사용하며 커서 조건도 Index Cond에 포함된다.

조회 SQL은 기간 조건에 선택 필터를 추가하고 `(occurred_at,id) < (:cursorTime,:cursorId)`와 `ORDER BY occurred_at DESC,id DESC LIMIT size+1`을 사용한다. 측정한 다음 페이지 쿼리에 OFFSET은 없다. 테스트에서 커서 좌표를 고르는 초기 준비 SELECT에만 OFFSET 20을 사용한다.

## 재현

Java 21과 실행 중인 Docker가 있는 환경에서:

```bash
cd api
./gradlew test --tests '*AuditQueryPlanTest' --no-daemon
```

자동 생성 보고서는 `api/build/reports/audit-query-plan.txt`에 저장된다. 테스트 데이터는 local Compose DB가 아닌 폐기 가능한 PostgreSQL 컨테이너에만 생성한다. 실행 시간·커서 UUID는 실행마다 달라질 수 있다. 1천만 건·동시 부하·비동기 저장 비교는 후속 범위다.

## 실제 실행계획 원문

```text
PostgreSQL: PostgreSQL 17.11 (Debian 17.11-1.pgdg13+2) on aarch64-unknown-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit
JVM: 21.0.10; Mac OS X aarch64
Rows: 100000; ANALYZE completed; freshly populated isolated database (warm buffers)

TIME
Limit  (cost=0.42..2.10 rows=21 width=1622) (actual time=0.008..0.012 rows=21 loops=1)
  Buffers: shared hit=4
  ->  Index Scan using idx_audit_time on audit_logs  (cost=0.42..8032.60 rows=100000 width=1622) (actual time=0.007..0.010 rows=21 loops=1)
        Index Cond: ((occurred_at >= '2026-11-06 09:00:00+09'::timestamp with time zone) AND (occurred_at < '2026-11-08 09:00:00+09'::timestamp with time zone))
        Buffers: shared hit=4
Planning:
  Buffers: shared hit=275
Planning Time: 0.295 ms
Execution Time: 0.027 ms

DENSE_ACTOR
Limit  (cost=0.42..5.54 rows=21 width=1622) (actual time=0.007..0.014 rows=21 loops=1)
  Buffers: shared hit=4
  ->  Index Scan using idx_audit_time on audit_logs  (cost=0.42..8115.70 rows=33240 width=1622) (actual time=0.007..0.012 rows=21 loops=1)
        Index Cond: ((occurred_at >= '2026-11-06 09:00:00+09'::timestamp with time zone) AND (occurred_at < '2026-11-08 09:00:00+09'::timestamp with time zone))
        Filter: (actor_id = 2)
        Rows Removed by Filter: 40
        Buffers: shared hit=4
Planning:
  Buffers: shared hit=280
Planning Time: 0.255 ms
Execution Time: 0.029 ms

SPARSE_ACTOR
Limit  (cost=0.42..78.91 rows=21 width=1622) (actual time=0.029..0.077 rows=21 loops=1)
  Buffers: shared hit=27
  ->  Index Scan using idx_audit_actor_time on audit_logs  (cost=0.42..310.65 rows=83 width=1622) (actual time=0.028..0.076 rows=21 loops=1)
        Index Cond: ((actor_id = 4) AND (occurred_at >= '2026-11-06 09:00:00+09'::timestamp with time zone) AND (occurred_at < '2026-11-08 09:00:00+09'::timestamp with time zone))
        Buffers: shared hit=27
Planning:
  Buffers: shared hit=280
Planning Time: 0.312 ms
Execution Time: 0.093 ms

TYPE
Limit  (cost=0.42..31.86 rows=21 width=1622) (actual time=0.017..0.046 rows=21 loops=1)
  Buffers: shared hit=24
  ->  Index Scan using idx_audit_type_time on audit_logs  (cost=0.42..1367.25 rows=913 width=1622) (actual time=0.016..0.045 rows=21 loops=1)
        Index Cond: (((event_type)::text = 'DATA_READ'::text) AND (occurred_at >= '2026-11-06 09:00:00+09'::timestamp with time zone) AND (occurred_at < '2026-11-08 09:00:00+09'::timestamp with time zone))
        Buffers: shared hit=24
Planning:
  Buffers: shared hit=272
Planning Time: 0.249 ms
Execution Time: 0.056 ms

KEYSET
Limit  (cost=0.42..79.95 rows=21 width=1622) (actual time=0.023..0.065 rows=21 loops=1)
  Buffers: shared hit=27
  ->  Index Scan using idx_audit_actor_time on audit_logs  (cost=0.42..250.37 rows=66 width=1622) (actual time=0.022..0.063 rows=21 loops=1)
        Index Cond: ((actor_id = 4) AND (occurred_at >= '2026-11-06 09:00:00+09'::timestamp with time zone) AND (occurred_at < '2026-11-08 09:00:00+09'::timestamp with time zone) AND (ROW(occurred_at, id) < ROW('2026-11-08 03:10:00+09'::timestamp with time zone, '496944dd-739b-422b-9306-2482ada1cf77'::uuid)))
        Buffers: shared hit=27
Planning:
  Buffers: shared hit=274
Planning Time: 0.262 ms
Execution Time: 0.084 ms
```
