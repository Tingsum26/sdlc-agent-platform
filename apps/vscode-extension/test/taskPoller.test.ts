import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { TaskPoller } from "../src/polling/taskPoller.js";

describe("TaskPoller", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("refreshes immediately, then uses foreground and background intervals", async () => {
    const refresh = vi.fn().mockResolvedValue(undefined);
    let foreground = true;
    const poller = new TaskPoller(refresh, () => foreground, () => true, { foregroundMs: 60_000, backgroundMs: 300_000 });

    poller.start();
    await vi.runOnlyPendingTimersAsync();
    expect(refresh).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(refresh).toHaveBeenCalledTimes(2);
    foreground = false;
    await vi.advanceTimersByTimeAsync(60_000);
    expect(refresh).toHaveBeenCalledTimes(3);
    await vi.advanceTimersByTimeAsync(299_999);
    expect(refresh).toHaveBeenCalledTimes(3);
    await vi.advanceTimersByTimeAsync(1);
    expect(refresh).toHaveBeenCalledTimes(4);
  });

  it("backs off after failure, refreshes on focus, stops cleanly, and does not poll signed-out users", async () => {
    let signedIn = false;
    const refresh = vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValue(undefined);
    const poller = new TaskPoller(refresh, () => true, () => signedIn, { foregroundMs: 60_000, backgroundMs: 300_000 });
    poller.start();
    await vi.runOnlyPendingTimersAsync();
    expect(refresh).not.toHaveBeenCalled();
    signedIn = true;
    await poller.onFocus();
    expect(refresh).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(120_000);
    expect(refresh).toHaveBeenCalledTimes(2);
    poller.stop();
    await vi.advanceTimersByTimeAsync(600_000);
    expect(refresh).toHaveBeenCalledTimes(2);
  });
});
