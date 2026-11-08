import test from "node:test";
import assert from "node:assert/strict";
import { QueryClient, isCancelledError } from "@tanstack/react-query";
import { SessionBoundary } from "../../src/lib/auth/session.ts";

test("session reset cancels old me request and prevents its late response from entering cache", async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const boundary = new SessionBoundary();
  let resolveOld!: (actor: {id: number}) => void;
  let signal: AbortSignal | undefined;
  const pending = client.fetchQuery({ queryKey: ["auth", "me", 0], queryFn: ({signal: requestSignal}) => {
    signal = requestSignal;
    return new Promise<{id:number}>((resolve) => { resolveOld = resolve; });
  } }).catch((error) => error);
  client.setQueryData(["actor", 1, "ADMIN", "users"], ["private data"]);
  const next = await boundary.reset(client);
  assert.equal(signal?.aborted, true);
  client.setQueryData(["auth", "me", next], {id: 2});
  resolveOld({id: 1});
  assert.equal(isCancelledError(await pending),true);
  assert.equal(client.getQueryData(["auth","me",0]),undefined);
  assert.equal(client.getQueryData(["actor",1,"ADMIN","users"]),undefined);
  assert.deepEqual(client.getQueryData(["auth","me",next]),{id:2});
  client.clear();
});

test("only the newest session boundary can publish actor state", async () => {
  const client = new QueryClient();const boundary = new SessionBoundary();
  const first = await boundary.reset(client);
  const second = await boundary.reset(client);
  assert.equal(boundary.isCurrent(first),false);assert.equal(boundary.isCurrent(second),true);
  client.clear();
});
