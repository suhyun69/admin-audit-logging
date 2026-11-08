import type { QueryClient } from "@tanstack/react-query";

/** Serializes session boundaries so an old completion cannot repopulate a new session cache. */
export class SessionBoundary {
  private epoch = 0;
  get current() { return this.epoch; }
  isCurrent(epoch: number) { return epoch === this.epoch; }
  async reset(queryClient: QueryClient) {
    const next = ++this.epoch;
    await queryClient.cancelQueries();
    if (this.isCurrent(next)) queryClient.clear();
    return next;
  }
}
