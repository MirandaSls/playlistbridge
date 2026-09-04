# PlaylistBridge Backend

PlaylistBridge backend is a small Spring Boot 3 REST service targeting Java 21. It sits between a future web client and two external music providers: Spotify supplies source playlists and tracks; YouTube supplies candidate videos and, later, destination playlist writes.

## Architecture

HTTP controllers own route and request validation. `SpotifyService` and `ConversionService` own application behavior. Provider interfaces (`SpotifyProvider` and `YouTubeProvider`) form the seam around external APIs, so services never depend on SDKs, HTTP clients, OAuth details, or provider response shapes.

Provider records in `provider/model` are intentionally small internal models. DTO records in `api/dto` define the HTTP contract. `ProviderConfiguration` wires default adapters only when no test or application bean replaces them. This keeps deterministic test adapters isolated in test scope while allowing production integrations to be added without changing controllers.

`SpotifyProperties` reads `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, and `SPOTIFY_REDIRECT_URI` through `application.yml`. Secrets stay in configuration objects and never appear in responses or logs. Playlist reads and conversion preview/conversion return `503` with a structured JSON error when user OAuth access token is absent.

## API surface

- `GET /api/health` returns `{ "status": "ok", "service": "playlistbridge" }`.
- `GET /api/spotify/playlists?page=0&size=20` returns a page envelope. Page is zero-based; size is 1-100.
- `POST /api/conversions/preview` accepts `{ "playlistId": "..." }` and returns tracks with YouTube match candidates, confidence, and status.
- `POST /api/conversions` accepts playlist metadata plus `{ "trackId", "videoId" }` selections. Current scaffold returns a deterministic completed job/result summary with `202 Accepted`.

Invalid request bodies and query parameters use `ApiError` through `GlobalExceptionHandler`; field errors retain paths such as `selections[0].trackId`.

## Testing and lessons

`PlaylistBridgeApiTest` is an end-to-end MockMvc contract suite. `FakeProviderConfiguration` supplies stable playlist, track, and candidate data. New provider implementations should preserve those interfaces and add adapter-level tests before wiring network behavior.

Run from `backend/`:

```text
mvn test
mvn package
```

Current adapters intentionally do not make network calls. They provide safe empty results until Spotify OAuth refresh, Spotify Web API calls, YouTube search, and YouTube playlist creation are implemented behind the existing interfaces. Conversion state is synchronous and in-memory; no durable job store, retry policy, token refresh, or provider write operation exists yet.
