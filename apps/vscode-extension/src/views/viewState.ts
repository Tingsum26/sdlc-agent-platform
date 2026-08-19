export type Freshness = "LIVE" | "DELAYED" | "STALE" | "OFFLINE";
export type ViewState<T> =
  | { kind: "loading" }
  | { kind: "data"; data: T; at: number }
  | { kind: "error"; message: string };

export const LIVE_WINDOW_MS = 5 * 60_000;
export const DELAYED_WINDOW_MS = 15 * 60_000;

export function computeFreshness(at: number | undefined, now = Date.now()): Freshness {
  if (at === undefined) return "OFFLINE";
  const age = now - at;
  if (age <= LIVE_WINDOW_MS) return "LIVE";
  if (age <= DELAYED_WINDOW_MS) return "DELAYED";
  return "STALE";
}

export function toViewState<T>(state: ViewState<T>, now = Date.now()): ViewState<T> & { freshness: Freshness } {
  if (state.kind === "data") return { ...state, freshness: computeFreshness(state.at, now) };
  return { ...state, freshness: "OFFLINE" };
}
