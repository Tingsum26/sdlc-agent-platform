import { describe, expect, it } from "vitest";
import { SDLC_CHANNELS } from "../src/types";

describe("M2 workflow types", () => {
  it("exports the four channel values for the fictional epic", () => {
    expect(SDLC_CHANNELS).toEqual(["API", "WEB", "IOS", "ANDROID"]);
  });
});
