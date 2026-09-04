"use client";

import { useEffect, useReducer, useState } from "react";

import { ApiError, createApiClient, type AuthProvider, type Privacy } from "../lib/api";
import { flowReducer, initialFlowState, selectedPlaylistsFor } from "../lib/flow";

const api = createApiClient();

const steps = [
  ["connect", "Source"],
  ["select", "Playlist"],
  ["review", "Review"],
  ["destination", "Destination"],
] as const;

const prettyError = (error: unknown) => {
  if (error instanceof ApiError && error.status === 429) {
    return "Conversion limit reached. Your quota resets soon; try again then.";
  }
  if (error instanceof Error) return error.message;
  return "Connection failed. Check API base URL and try again.";
};

const formatCount = (number: number) => new Intl.NumberFormat("en-US").format(number);

function Mark({ type }: { type: "spotify" | "youtube" | "bridge" }) {
  return <span className={`mark mark-${type}`} aria-hidden="true">{type === "bridge" ? "↔" : type === "spotify" ? "●" : "▶"}</span>;
}

function ProgressRail({ current }: { current: string }) {
  const currentIndex = steps.findIndex(([step]) => step === current);
  return (
    <nav className="progress-rail" aria-label="Conversion progress">
      {steps.map(([step, label], index) => (
        <div className={`rail-step ${index <= currentIndex ? "is-active" : ""}`} key={step}>
          <span className="rail-dot">{index < currentIndex ? "✓" : String(index + 1).padStart(2, "0")}</span>
          <span>{label}</span>
        </div>
      ))}
    </nav>
  );
}

function PlaylistList({
  playlists,
  selectedIds,
  onToggle,
}: {
  playlists: typeof initialFlowState.playlists;
  selectedIds: string[];
  onToggle: (id: string) => void;
}) {
  return (
    <div className="playlist-list" role="list" aria-label="Spotify playlists">
      {playlists.map((playlist) => {
        const selected = selectedIds.includes(playlist.id);
        const unavailable = selectedIds.length > 0 && !selected;
        return (
          <button
            className={`playlist-row ${selected ? "is-selected" : ""}`}
            type="button"
            aria-pressed={selected}
            disabled={unavailable}
            onClick={() => onToggle(playlist.id)}
            key={playlist.id}
          >
            <span className="playlist-art" aria-hidden="true">{playlist.name.slice(0, 1).toUpperCase()}</span>
            <span className="playlist-copy">
              <strong>{playlist.name}</strong>
              <small>{formatCount(playlist.trackCount)} tracks</small>
            </span>
            <span className="selection-indicator" aria-hidden="true">{selected ? "✓" : "＋"}</span>
          </button>
        );
      })}
    </div>
  );
}

export default function HomePage() {
  const [state, dispatch] = useReducer(flowReducer, initialFlowState);
  const [playlistName, setPlaylistName] = useState("");
  const [session, setSession] = useState({ spotifyConnected: false, googleConnected: false });
  const [authError, setAuthError] = useState<string | null>(null);

  const loadPlaylists = async () => {
    dispatch({ type: "playlists/loading" });
    try {
      await api.getHealth();
      const playlists = await api.getPlaylists();
      dispatch({ type: "playlists/success", playlists });
    } catch (error) {
      dispatch({ type: "playlists/error", message: prettyError(error) });
    }
  };

  const authorize = (provider: AuthProvider) => {
    window.location.assign(api.authorizationUrl(provider));
  };

  useEffect(() => {
    let disposed = false;
    const hydrateSession = async () => {
      try {
        const nextSession = await api.getSession();
        if (disposed) return;
        setSession(nextSession);
        if (nextSession.spotifyConnected && nextSession.googleConnected) {
          await loadPlaylists();
        }
      } catch (error) {
        if (!disposed) setAuthError(prettyError(error));
      }
    };
    void hydrateSession();
    return () => {
      disposed = true;
    };
  }, []);

  const preview = async () => {
    const playlist = selectedPlaylistsFor(state)[0];
    if (!playlist) return;
    dispatch({ type: "preview/loading" });
    try {
      const result = await api.previewConversion({ playlistId: playlist.id });
      setPlaylistName(playlist.name);
      dispatch({ type: "preview/success", preview: result });
    } catch (error) {
      dispatch({ type: "preview/error", message: prettyError(error) });
    }
  };

  const convert = async () => {
    const playlist = selectedPlaylistsFor(state)[0];
    const selections = state.preview?.matches.flatMap((match) =>
      match.trackId && match.videoId ? [{ trackId: match.trackId, videoId: match.videoId }] : [],
    ) ?? [];
    if (!playlist || selections.length === 0) {
      dispatch({ type: "conversion/error", message: "No matched tracks available for conversion." });
      return;
    }
    dispatch({ type: "conversion/loading" });
    try {
      const result = await api.convert({
        playlistId: playlist.id,
        title: playlistName || playlist.name,
        privacyStatus: state.privacy,
        selections,
      });
      dispatch({ type: "conversion/success", result });
    } catch (error) {
      dispatch({ type: "conversion/error", message: prettyError(error) });
    }
  };

  const isLoadingPlaylists = state.connection === "loading";
  const selectedPlaylist = selectedPlaylistsFor(state)[0];
  const isWorking = state.step === "review" && !state.preview;

  return (
    <main className="shell">
      <header className="topbar">
        <a className="wordmark" href="#top" aria-label="PlaylistBridge home">
          <span className="wordmark-glyph" aria-hidden="true"><i /><i /><i /><i /></span>
          <span>PlaylistBridge</span>
        </a>
        <div className="topbar-note"><span className="status-pip" /> secure transfer workspace</div>
      </header>

      <section className="intro" id="top">
        <div>
          <p className="eyebrow">Spotify → YouTube / 01</p>
          <h1>Move your listening,<br /><em>keep your intent.</em></h1>
        </div>
        <p className="intro-copy">One quiet workspace for bringing playlists across. We’ll show every match before anything moves.</p>
      </section>

      <ProgressRail current={state.step} />

      <section className="workspace" aria-live="polite">
        <div className="workspace-main">
          {state.step === "connect" && (
            <div className="stage stage-connect">
              <div className="stage-kicker"><span>01</span> source account</div>
              <div className="connection-visual" aria-hidden="true"><Mark type="spotify" /><Mark type="bridge" /><Mark type="youtube" /></div>
              <h2>Start with your Spotify playlists.</h2>
              <p className="stage-lede">PlaylistBridge reads Spotify metadata and uses your Google account to create YouTube playlists. Neither provider receives changes from this step.</p>
              {(state.error || authError) && <div className="alert alert-error" role="alert">{state.error || authError}</div>}
              <div className="provider-actions">
                <button className={`button ${session.spotifyConnected ? "button-secondary" : "button-primary"}`} type="button" onClick={() => authorize("spotify")} disabled={isLoadingPlaylists}>
                  {session.spotifyConnected ? <>Spotify connected <span aria-hidden="true">✓</span></> : <>Connect Spotify <span aria-hidden="true">↗</span></>}
                </button>
                <button className={`button ${session.googleConnected ? "button-secondary" : "button-primary"}`} type="button" onClick={() => authorize("google")} disabled={isLoadingPlaylists}>
                  {session.googleConnected ? <>Google connected <span aria-hidden="true">✓</span></> : <>Connect Google <span aria-hidden="true">↗</span></>}
                </button>
              </div>
              {session.spotifyConnected && session.googleConnected ? (
                <button className="button button-primary" type="button" onClick={loadPlaylists} disabled={isLoadingPlaylists}>
                  {isLoadingPlaylists ? <><span className="spinner" aria-hidden="true" /> Loading playlists</> : <>Load Spotify playlists <span aria-hidden="true">→</span></>}
                </button>
              ) : <p className="fine-print">Connect both accounts. OAuth returns here with tokens kept only by backend session.</p>}
            </div>
          )}

          {state.step === "select" && (
            <div className="stage stage-select">
              <div className="stage-heading"><div><div className="stage-kicker"><span>02</span> choose a source</div><h2>Which playlist is coming with you?</h2></div><span className="count-label">{formatCount(state.playlists.length)} available</span></div>
              {state.error && <div className="alert alert-error" role="alert">{state.error}</div>}
              {state.connection === "error" ? (
                <div className="empty-state"><span className="empty-glyph">!</span><h3>Can’t reach Spotify right now.</h3><p>{state.error}</p><button className="button button-secondary" type="button" onClick={loadPlaylists}>Try again</button></div>
              ) : state.playlists.length === 0 ? (
                <div className="empty-state"><span className="empty-glyph">∅</span><h3>No playlists found.</h3><p>Create a playlist in Spotify, then reconnect to bring it here.</p><button className="button button-secondary" type="button" onClick={loadPlaylists}>Refresh playlists</button></div>
              ) : (
                <>
                  <PlaylistList playlists={state.playlists} selectedIds={state.selectedIds} onToggle={(playlistId) => dispatch({ type: "playlist/toggle", playlistId })} />
                  <div className="stage-footer"><p className="fine-print">Choose one playlist. You can rename it before conversion.</p><button className="button button-primary" type="button" disabled={!selectedPlaylist} onClick={preview}>Review matches <span aria-hidden="true">→</span></button></div>
                </>
              )}
            </div>
          )}

          {state.step === "review" && (
            <div className="stage stage-review">
              <div className="stage-kicker"><span>03</span> match review</div>
              {isWorking ? <><h2>Finding the closest YouTube versions.</h2><div className="loading-line"><span className="spinner" aria-hidden="true" /> Comparing titles, artists, and duration…</div></> : state.preview && <>
                <div className="stage-heading"><div><h2>Here’s what will move.</h2><p className="stage-lede">Review low-confidence matches before choosing where this playlist lands.</p></div><div className="match-score"><strong>{state.preview.matchedCount}</strong><span>of {state.preview.totalCount} matched</span></div></div>
                {state.preview.matches.length === 0 ? <div className="empty-state compact"><span className="empty-glyph">—</span><h3>Preview is empty.</h3><p>No tracks were returned for this playlist.</p></div> : <div className="match-list" role="list" aria-label="Track matches">{state.preview.matches.map((match, index) => <div className={`match-row match-${match.status}`} role="listitem" key={`${match.sourceTitle}-${index}`}><span className="match-state" aria-hidden="true">{match.status === "matched" ? "✓" : match.status === "partial" ? "~" : "×"}</span><span><strong>{match.sourceTitle}</strong><small>{match.sourceArtist}</small></span><span className="match-target">{match.targetTitle ? <><strong>{match.targetTitle}</strong><small>{match.targetArtist}</small></> : <small>No close match found</small>}</span></div>)}</div>}
                {state.preview.quotaRemaining !== undefined && state.preview.quotaRemaining < 3 && <div className="alert alert-warn" role="status">{state.preview.quotaRemaining} conversion{state.preview.quotaRemaining === 1 ? "" : "s"} left in your current quota.</div>}
                <div className="stage-footer"><p className="fine-print">Only matched tracks will be sent to YouTube.</p><button className="button button-primary" type="button" onClick={() => dispatch({ type: "review/continue" })}>Choose destination <span aria-hidden="true">→</span></button></div>
              </>}
            </div>
          )}

          {state.step === "destination" && (
            <div className="stage stage-destination">
              <div className="stage-kicker"><span>04</span> destination</div>
              <h2>Give it a place to land.</h2>
              <p className="stage-lede">YouTube playlist privacy defaults to private. Change it only if you want others to find it.</p>
              {state.error && <div className="alert alert-error" role="alert">{state.error}</div>}
              <label className="field-label" htmlFor="playlist-name">Playlist name</label>
              <input id="playlist-name" className="text-input" value={playlistName} onChange={(event) => setPlaylistName(event.target.value)} maxLength={120} />
              <fieldset className="privacy-options"><legend className="field-label">Visibility on YouTube</legend>{(["private", "unlisted", "public"] as Privacy[]).map((privacy) => <label className={`privacy-option ${state.privacy === privacy ? "is-selected" : ""}`} key={privacy}><input type="radio" name="privacy" value={privacy} checked={state.privacy === privacy} onChange={() => dispatch({ type: "destination/privacy", privacy })} /><span><strong>{privacy[0].toUpperCase() + privacy.slice(1)}</strong><small>{privacy === "private" ? "Only you can watch" : privacy === "unlisted" ? "Anyone with link can watch" : "Visible to everyone"}</small></span></label>)}</fieldset>
              <button className="button button-primary" type="button" onClick={convert} disabled={state.conversion === "loading"}>{state.conversion === "loading" ? <><span className="spinner" aria-hidden="true" /> Creating playlist</> : <>Create YouTube playlist <span aria-hidden="true">→</span></>}</button>
            </div>
          )}

          {state.step === "converting" && <div className="stage stage-progress"><div className="stage-kicker"><span>05</span> in motion</div><div className="progress-orbit" aria-hidden="true"><span /></div><h2>Your playlist is crossing over.</h2><p className="stage-lede">We’re creating the YouTube playlist and adding the matches you approved. Keep this tab open.</p><div className="progress-track"><span /></div><p className="progress-caption">Adding tracks securely…</p></div>}

          {state.step === "complete" && state.result && <div className="stage stage-complete"><div className="complete-check" aria-hidden="true">✓</div><div className="stage-kicker">transfer complete</div><h2>Your playlist is ready.</h2><p className="stage-lede">{formatCount(state.result.importedCount)} tracks moved to YouTube{state.result.skippedCount ? ` · ${formatCount(state.result.skippedCount)} skipped` : ""}.</p>{state.result.url ? <a className="button button-primary" href={state.result.url} target="_blank" rel="noreferrer">Open on YouTube <span aria-hidden="true">↗</span></a> : <p className="fine-print">YouTube link will appear in your account shortly.</p>}<button className="text-button" type="button" onClick={() => dispatch({ type: "reset" })}>Move another playlist</button></div>}
        </div>

        <aside className="workspace-aside">
          <div className="aside-block"><p className="eyebrow">Transfer notes</p><p className="aside-copy">Your Spotify account stays untouched. PlaylistBridge only requests read access.</p></div>
          <div className="aside-block aside-bottom"><p className="eyebrow">Status</p><div className="status-line"><span className={`status-pip ${session.spotifyConnected ? "is-good" : ""}`} />{session.spotifyConnected ? "Spotify connected" : "Waiting for Spotify"}</div><div className="status-line"><span className={`status-pip ${session.googleConnected ? "is-good" : ""}`} />{session.googleConnected ? "Google connected" : "Waiting for Google"}</div><div className="status-line"><span className={`status-pip ${state.preview ? "is-good" : ""}`} />{state.preview ? "Matches reviewed" : "Match review pending"}</div></div>
        </aside>
      </section>

      <footer className="footer"><span>PlaylistBridge</span><span>Read-only by design · v0.1</span></footer>
    </main>
  );
}
