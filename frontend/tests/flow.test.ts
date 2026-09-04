import { describe, expect, it, vi } from "vitest";

import {
  createApiClient,
  getSelectedPlaylists,
  togglePlaylistSelection,
} from "../src/lib/api";
import { flowReducer, initialFlowState } from "../src/lib/flow";

describe("PlaylistBridge API client", () => {
  it("uses configurable API base and requests playlists", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ playlists: [{ id: "p1", name: "Morning", trackCount: 12 }] }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    const client = createApiClient(fetcher, "https://bridge.test/");

    await expect(client.getPlaylists()).resolves.toEqual([
      { id: "p1", name: "Morning", trackCount: 12 },
    ]);
    expect(fetcher).toHaveBeenCalledWith("https://bridge.test/api/spotify/playlists", {
      credentials: "include",
      headers: { Accept: "application/json" },
    });
  });

  it("unwraps backend paginated playlist responses", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({
        content: [{ id: "p1", name: "Morning", trackCount: 12 }],
        page: 0,
        size: 50,
        totalElements: 1,
        totalPages: 1,
      }), { status: 200 }),
    );
    const client = createApiClient(fetcher, "https://bridge.test");

    await expect(client.getPlaylists()).resolves.toEqual([
      { id: "p1", name: "Morning", trackCount: 12 },
    ]);
  });

  it("posts preview and conversion requests with JSON bodies", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({
          playlistId: "p1",
          tracks: [{
            trackId: "t1",
            title: "North",
            artist: "Hollow",
            candidates: [{ videoId: "v1", title: "North", channel: "Hollow", confidence: 0.96, status: "MATCHED" }],
          }],
        }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({
          jobId: "conversion-p1",
          status: "COMPLETED",
          playlistId: "p1",
          title: "Morning",
          privacyStatus: "private",
          totalTracks: 1,
          selectedTracks: 1,
          matchedTracks: 1,
          skippedTracks: 0,
        }), {
          status: 200,
          headers: { "content-type": "application/json" },
        }),
      );
    const client = createApiClient(fetcher, "https://bridge.test");

    await expect(client.previewConversion({ playlistId: "p1" })).resolves.toMatchObject({
      previewId: "p1",
      matchedCount: 1,
      totalCount: 1,
      matches: [{ sourceTitle: "North", sourceArtist: "Hollow", targetTitle: "North", status: "matched" }],
    });
    await client.convert({
      playlistId: "p1",
      title: "Morning",
      privacyStatus: "private",
      selections: [{ trackId: "t1", videoId: "v1" }],
    });

    expect(fetcher).toHaveBeenNthCalledWith(1, "https://bridge.test/api/conversions/preview", {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({ playlistId: "p1" }),
    });
    expect(fetcher).toHaveBeenNthCalledWith(2, "https://bridge.test/api/conversions", {
      method: "POST",
      credentials: "include",
      headers: { Accept: "application/json", "Content-Type": "application/json" },
      body: JSON.stringify({
        playlistId: "p1",
        title: "Morning",
        privacyStatus: "private",
        selections: [{ trackId: "t1", videoId: "v1" }],
      }),
    });
  });

  it("turns backend failures into an error with status", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: "Quota reached" }), { status: 429 }),
    );
    const client = createApiClient(fetcher, "https://bridge.test");

    await expect(client.getHealth()).rejects.toMatchObject({ status: 429, message: "Quota reached" });
  });

  it("includes session credentials and exposes OAuth redirects", async () => {
    const fetcher = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ spotifyConnected: true, googleConnected: false }), { status: 200 }),
    );
    const client = createApiClient(fetcher, "https://bridge.test");

    await expect(client.getSession()).resolves.toEqual({ spotifyConnected: true, googleConnected: false });
    expect(fetcher).toHaveBeenCalledWith("https://bridge.test/api/auth/session", {
      credentials: "include",
      headers: { Accept: "application/json" },
    });
    expect(client.authorizationUrl("google")).toBe("https://bridge.test/api/auth/google");
  });
});

describe("playlist selection", () => {
  const playlists = [
    { id: "p1", name: "Morning", trackCount: 12 },
    { id: "p2", name: "Focus", trackCount: 8 },
  ];

  it("toggles one playlist without changing other selections", () => {
    expect(togglePlaylistSelection([], "p1")).toEqual(["p1"]);
    expect(togglePlaylistSelection(["p1", "p2"], "p1")).toEqual(["p2"]);
  });

  it("returns selected playlist records in original order", () => {
    expect(getSelectedPlaylists(playlists, ["p2", "p1"])).toEqual(playlists);
  });
});

describe("conversion flow state", () => {
  it("moves from playlist loading to selection and keeps selected ids", () => {
    const loaded = flowReducer(initialFlowState, {
      type: "playlists/success",
      playlists: [{ id: "p1", name: "Morning", trackCount: 12 }],
    });
    const selected = flowReducer(loaded, { type: "playlist/toggle", playlistId: "p1" });

    expect(selected.connection).toBe("connected");
    expect(selected.step).toBe("select");
    expect(selected.selectedIds).toEqual(["p1"]);
  });

  it("moves to review after preview and continues to destination explicitly", () => {
    const state = flowReducer(initialFlowState, {
      type: "preview/success",
      preview: {
        previewId: "preview-1",
        matches: [{ sourceTitle: "North", sourceArtist: "Hollow", status: "partial", confidence: 0.62 }],
        matchedCount: 0,
        totalCount: 1,
      },
    });

    expect(state.step).toBe("review");
    expect(state.preview?.matches[0].status).toBe("partial");

    const destination = flowReducer(state, { type: "review/continue" });
    expect(destination.step).toBe("destination");
  });
});
