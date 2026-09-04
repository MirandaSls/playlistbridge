export type Playlist = {
  id: string;
  name: string;
  trackCount: number;
  imageUrl?: string;
};

export type MatchStatus = "matched" | "partial" | "missing";

export type TrackMatch = {
  trackId?: string;
  videoId?: string;
  sourceTitle: string;
  sourceArtist: string;
  targetTitle?: string;
  targetArtist?: string;
  confidence: number;
  status: MatchStatus;
};

export type PreviewResult = {
  previewId: string;
  matches: TrackMatch[];
  matchedCount: number;
  totalCount: number;
  quotaRemaining?: number;
};

export type Privacy = "public" | "unlisted" | "private";
export type AuthProvider = "spotify" | "google";

export type SessionStatus = {
  spotifyConnected: boolean;
  googleConnected: boolean;
};

export type ConversionResult = {
  conversionId: string;
  url?: string;
  importedCount: number;
  skippedCount: number;
};

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

type Fetcher = typeof fetch;

type PlaylistPage = {
  content: Playlist[];
};

type BackendPreview = {
  playlistId: string;
  tracks: Array<{
    trackId: string;
    title: string;
    artist: string;
    candidates: Array<{
      videoId: string;
      title: string;
      channel: string;
      confidence: number;
      status: string;
    }>;
  }>;
};

type BackendConversion = {
  jobId: string;
  matchedTracks: number;
  skippedTracks: number;
  targetPlaylistUrl?: string;
};

const getMessage = (body: unknown, fallback: string) => {
  if (typeof body === "object" && body !== null && "message" in body && typeof body.message === "string") {
    return body.message;
  }
  return fallback;
};

const parseResponse = async <T>(response: Response): Promise<T> => {
  const body = (await response.json().catch(() => null)) as unknown;
  if (!response.ok) {
    throw new ApiError(getMessage(body, `Request failed (${response.status})`), response.status);
  }
  return body as T;
};

const unwrap = <T>(body: T | { data: T }): T => {
  if (typeof body === "object" && body !== null && "data" in body) {
    return body.data;
  }
  return body as T;
};

export const createApiClient = (fetcher: Fetcher = fetch, baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "") => {
  const base = baseUrl.replace(/\/$/, "");
  const request = async <T>(path: string, init?: RequestInit) => {
    const response = await fetcher(`${base}${path}`, {
      ...init,
      credentials: "include",
      headers: {
        Accept: "application/json",
        ...(init?.body ? { "Content-Type": "application/json" } : {}),
        ...init?.headers,
      },
    });
    return parseResponse<T>(response);
  };

  return {
    authorizationUrl(provider: AuthProvider) {
      return `${base}/api/auth/${provider}`;
    },
    async getSession() {
      return request<SessionStatus>("/api/auth/session");
    },
    async getHealth() {
      return request<{ status: string }>("/api/health");
    },
    async getPlaylists() {
      const body = await request<Playlist[] | { playlists: Playlist[] } | PlaylistPage>("/api/spotify/playlists");
      if (Array.isArray(body)) return body;
      if ("playlists" in body && Array.isArray(body.playlists)) return body.playlists;
      if ("content" in body && Array.isArray(body.content)) return body.content;
      return [];
    },
    async previewConversion(input: { playlistId: string }) {
      const body = await request<BackendPreview | PreviewResult | { preview: PreviewResult }>("/api/conversions/preview", {
        method: "POST",
        body: JSON.stringify(input),
      });
      const preview = body && typeof body === "object" && "preview" in body ? body.preview : body;
      if ("tracks" in preview && Array.isArray(preview.tracks)) {
        const matches = preview.tracks.map((track) => {
          const candidate = track.candidates[0];
          const status = candidate?.status.toLowerCase();
          return {
            trackId: track.trackId,
            videoId: candidate?.videoId,
            sourceTitle: track.title,
            sourceArtist: track.artist,
            targetTitle: candidate?.title,
            targetArtist: candidate?.channel,
            confidence: candidate?.confidence ?? 0,
            status: status === "matched" ? "matched" : status === "partial" ? "partial" : "missing",
          } satisfies TrackMatch;
        });
        return {
          previewId: preview.playlistId,
          matches,
          matchedCount: matches.filter((match) => match.status === "matched").length,
          totalCount: matches.length,
        } satisfies PreviewResult;
      }
      return unwrap(preview as PreviewResult);
    },
    async convert(input: {
      playlistId: string;
      title: string;
      privacyStatus: Privacy;
      selections: Array<{ trackId: string; videoId: string }>;
    }) {
      const body = await request<BackendConversion | ConversionResult | { conversion: ConversionResult }>("/api/conversions", {
        method: "POST",
        body: JSON.stringify(input),
      });
      const conversion = body && typeof body === "object" && "conversion" in body ? body.conversion : body;
      if ("jobId" in conversion) {
        return {
          conversionId: conversion.jobId,
          url: conversion.targetPlaylistUrl,
          importedCount: conversion.matchedTracks,
          skippedCount: conversion.skippedTracks,
        } satisfies ConversionResult;
      }
      return unwrap(conversion as ConversionResult);
    },
  };
};

export const togglePlaylistSelection = (selectedIds: string[], playlistId: string) =>
  selectedIds.includes(playlistId)
    ? selectedIds.filter((id) => id !== playlistId)
    : [...selectedIds, playlistId];

export const getSelectedPlaylists = (playlists: Playlist[], selectedIds: string[]) => {
  const selected = new Set(selectedIds);
  return playlists.filter((playlist) => selected.has(playlist.id));
};
