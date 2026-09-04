import { getSelectedPlaylists, type ConversionResult, type Playlist, type PreviewResult } from "./api";

export type FlowStep = "connect" | "select" | "review" | "destination" | "converting" | "complete";
export type ConnectionState = "idle" | "loading" | "connected" | "error";
export type ConversionState = "idle" | "loading" | "success" | "error";

export type FlowState = {
  step: FlowStep;
  connection: ConnectionState;
  playlists: Playlist[];
  selectedIds: string[];
  preview: PreviewResult | null;
  privacy: "public" | "unlisted" | "private";
  conversion: ConversionState;
  result: ConversionResult | null;
  error: string | null;
};

export const initialFlowState: FlowState = {
  step: "connect",
  connection: "idle",
  playlists: [],
  selectedIds: [],
  preview: null,
  privacy: "private",
  conversion: "idle",
  result: null,
  error: null,
};

export type FlowAction =
  | { type: "playlists/loading" }
  | { type: "playlists/success"; playlists: Playlist[] }
  | { type: "playlists/error"; message: string }
  | { type: "playlist/toggle"; playlistId: string }
  | { type: "preview/loading" }
  | { type: "preview/success"; preview: PreviewResult }
  | { type: "preview/error"; message: string }
  | { type: "review/continue" }
  | { type: "destination/privacy"; privacy: FlowState["privacy"] }
  | { type: "conversion/loading" }
  | { type: "conversion/success"; result: ConversionResult }
  | { type: "conversion/error"; message: string }
  | { type: "reset" };

export const flowReducer = (state: FlowState, action: FlowAction): FlowState => {
  switch (action.type) {
    case "playlists/loading":
      return { ...state, connection: "loading", error: null };
    case "playlists/success":
      return { ...state, connection: "connected", playlists: action.playlists, step: "select", error: null };
    case "playlists/error":
      return { ...state, connection: "error", error: action.message };
    case "playlist/toggle": {
      const selectedIds = state.selectedIds.includes(action.playlistId)
        ? state.selectedIds.filter((id) => id !== action.playlistId)
        : [...state.selectedIds, action.playlistId];
      return { ...state, selectedIds, error: null };
    }
    case "preview/loading":
      return { ...state, step: "review", error: null };
    case "preview/success":
      return { ...state, step: "review", preview: action.preview, error: null };
    case "preview/error":
      return { ...state, step: "select", error: action.message };
    case "review/continue":
      return { ...state, step: "destination", error: null };
    case "destination/privacy":
      return { ...state, privacy: action.privacy };
    case "conversion/loading":
      return { ...state, step: "converting", conversion: "loading", error: null };
    case "conversion/success":
      return { ...state, step: "complete", conversion: "success", result: action.result, error: null };
    case "conversion/error":
      return { ...state, step: "destination", conversion: "error", error: action.message };
    case "reset":
      return initialFlowState;
  }
};

export const selectedPlaylistsFor = (state: FlowState) => getSelectedPlaylists(state.playlists, state.selectedIds);
