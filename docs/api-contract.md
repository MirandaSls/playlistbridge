# PlaylistBridge API contract

This document defines the browser-to-backend HTTP boundary. Backend base URL is configured by the frontend variable `NEXT_PUBLIC_API_BASE_URL`; local value is `http://localhost:8080`. All paths below include the `/api` prefix and return JSON.

## Services and authentication

- Spring Boot backend listens on `8080`.
- Next.js frontend listens on `3000`.
- Browser requests use the backend origin from `NEXT_PUBLIC_API_BASE_URL`.
- OAuth login is handled by the backend. Requests that require a provider account use the authenticated session or equivalent secure backend credential; browser code must not receive client secrets or provider refresh tokens.
- Backend allows the frontend origin listed in `CORS_ALLOWED_ORIGINS`.

Unless noted otherwise, successful responses use HTTP `200`. Validation failures use `400`, provider authorization failures use `502`, missing provider configuration uses `503`, and provider quota/rate-limit responses use `429`.

Errors use this shape:

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/conversions",
  "fieldErrors": {}
}
```

Clients should branch on HTTP status and use `fieldErrors` when validation fails.

## `GET /api/health`

Liveness endpoint for local checks, Docker health probes, and reverse proxies. It must not require OAuth or expose credentials.

Response `200`:

```json
{
  "status": "ok"
}
```

## `GET /api/spotify/playlists`

Returns playlists visible to the authenticated Spotify account. Pagination uses `page` and `size` query parameters.

Query parameters:

| Name | Type | Default | Constraints |
| --- | --- | ---: | --- |
| `page` | integer | `0` | `0` or greater |
| `size` | integer | `20` | `1`–`100` |

Response `200`:

```json
{
  "content": [
    {
      "id": "spotify-playlist-id",
      "name": "Road Trip",
      "description": "Optional description",
      "trackCount": 42,
      "isPublic": false,
      "imageUrl": "https://example.test/image.jpg"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

No audio bytes are returned. Track data remains provider metadata.

## `POST /api/conversions/preview`

Validates a requested transfer and returns a dry-run summary. This endpoint must not create a YouTube playlist or download audio.

Request:

```json
{
  "playlistId": "spotify-playlist-id"
}
```

Response `200`:

```json
{
  "playlistId": "spotify-playlist-id",
  "tracks": [
    {
      "trackId": "spotify-track-id",
      "title": "Track title",
      "artist": "Artist",
      "candidates": [
        {
          "videoId": "youtube-video-id",
          "title": "Track title",
          "channel": "Artist",
          "confidence": 0.96,
          "status": "MATCHED"
        }
      ]
    }
  ]
}
```

A preview can call provider metadata/search APIs, subject to provider quotas.

## `POST /api/conversions`

Creates a YouTube playlist from a previously authorized request. Backend transfers metadata and provider IDs only; it does not download or process audio.

Request:

```json
{
  "playlistId": "spotify-playlist-id",
  "title": "Road Trip",
  "privacyStatus": "private",
  "selections": [
    { "trackId": "spotify-track-id", "videoId": "youtube-video-id" }
  ]
}
```

`privacyStatus` accepts `private`, `unlisted`, or `public`. `selections` contains approved source/video pairs from preview.

Response `202` for a completed conversion:

```json
{
  "jobId": "conversion-spotify-playlist-id",
  "status": "COMPLETED",
  "playlistId": "spotify-playlist-id",
  "title": "Road Trip",
  "privacyStatus": "private",
  "targetPlaylistId": "youtube-playlist-id",
  "targetPlaylistUrl": "https://www.youtube.com/playlist?list=youtube-playlist-id",
  "totalTracks": 42,
  "selectedTracks": 40,
  "matchedTracks": 40,
  "skippedTracks": 2
}
```

Provider quota exhaustion should return `429` with `PROVIDER_RATE_LIMIT` or `PROVIDER_QUOTA_EXCEEDED`. Partial results must identify unmatched tracks; backend must not claim `completed` when playlist creation failed.

## OAuth redirect contract

Local callback URIs:

| Provider | URI | Environment variable |
| --- | --- | --- |
| Spotify | `http://localhost:8080/login/oauth2/code/spotify` | `SPOTIFY_REDIRECT_URI` |
| Google | `http://localhost:8080/login/oauth2/code/google` | `GOOGLE_REDIRECT_URI` |

Register exact URI strings with provider consoles. Production deployments must replace `http://localhost` with an HTTPS public backend hostname in both provider settings and environment. `BACKEND_PUBLIC_URL`, `FRONTEND_PUBLIC_URL`, and `CORS_ALLOWED_ORIGINS` must describe that deployment's actual origins.

## Provider quotas and privacy

Spotify and YouTube own rate limits and quotas. Conversion preview and creation can consume provider API quota; callers should surface `429` and retry only according to provider guidance. PlaylistBridge does not bypass quotas.

Backend should retain only tokens and metadata required for active conversions, protect secrets, and support sign-out plus deletion of stored tokens and conversion records. Local deletion cannot remove playlists already created at providers; users must delete those through Spotify or YouTube. Operators must include logs and backups in retention and deletion procedures.
