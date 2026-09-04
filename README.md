# PlaylistBridge

PlaylistBridge is a self-hosted open-source service that reads Spotify playlist metadata and creates matching YouTube playlists. It transfers track identity and playlist structure only; it never downloads, stores, streams, or converts audio.

## Architecture

The deployment has two services:

| Service | Technology | Port | Role |
| --- | --- | ---: | --- |
| `backend` | Java Spring Boot | `8080` | OAuth callbacks, Spotify metadata, conversion orchestration, HTTP API |
| `frontend` | Next.js | `3000` | Browser UI; calls backend through `NEXT_PUBLIC_API_BASE_URL` |

API routes are documented in [docs/api-contract.md](docs/api-contract.md). The frontend should call the backend using an absolute base URL, such as `http://localhost:8080`; do not hard-code a second API origin in frontend code.

## Quick start with Docker

Prerequisites: Docker Engine with Docker Compose v2, a Spotify application, and a Google Cloud OAuth client with YouTube Data API v3 enabled.

1. Copy `.env.example` to `.env`.
2. Fill in `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, and `GOOGLE_CLIENT_SECRET`.
3. Keep local URLs unless running behind a reverse proxy.
4. Register the redirect URIs below with both providers.
5. Start services:

   ```bash
   docker compose up --build
   ```

Open `http://localhost:3000`. Check backend availability at `http://localhost:8080/api/health`.

Compose expects `./backend` and `./frontend` to contain their respective Docker build contexts. Keep credentials in `.env`; `.env` is ignored by Git.

## Local development

Run backend and frontend in separate terminals. Install Java, Maven (or use the repository Maven wrapper), Node.js, and npm first.

```bash
# terminal 1
cd backend
./mvnw spring-boot:run

# terminal 2
cd frontend
npm ci
npm run dev
```

Set the variables from `.env.example` in your shell or IDE. Frontend development server uses port `3000`; Spring Boot uses port `8080`. If frontend and backend run on different origins, set `CORS_ALLOWED_ORIGINS=http://localhost:3000` in backend configuration.

## OAuth setup

### Spotify

1. Create an application in the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Set its redirect URI to `http://localhost:8080/login/oauth2/code/spotify`.
3. Copy the client ID and secret into `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET`.
4. Production deployments must use an HTTPS callback on the public backend hostname and set the exact same value in `SPOTIFY_REDIRECT_URI` and the Spotify application settings.

PlaylistBridge needs playlist metadata scopes, including private and collaborative playlist reads where the account grants them. Spotify authorization can still omit playlists the account cannot access.

### Google / YouTube

1. Create or select a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable YouTube Data API v3.
3. Configure the OAuth consent screen and create a Web application OAuth client.
4. Set its redirect URI to `http://localhost:8080/login/oauth2/code/google`.
5. Copy the client ID and secret into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
6. Use `https://www.googleapis.com/auth/youtube` scope for playlist creation.

Google OAuth consent, publishing status, test users, and account restrictions can limit who can authorize. YouTube OAuth remains required for playlist creation.

Redirect URIs must match character-for-character, including scheme, host, port, path, and trailing slash behavior. `localhost` and `127.0.0.1` are different OAuth redirect hosts.

## Configuration contract

| Variable | Required | Default / local value | Used by |
| --- | --- | --- | --- |
| `BACKEND_PUBLIC_URL` | yes outside local | `http://localhost:8080` | backend public origin and generated links |
| `CORS_ALLOWED_ORIGINS` | yes outside local | `http://localhost:3000` | backend CORS allowlist |
| `FRONTEND_PUBLIC_URL` | yes outside local | `http://localhost:3000` | backend browser redirect origin |
| `GOOGLE_CLIENT_ID` | for Google auth | empty | Spring Boot backend |
| `GOOGLE_CLIENT_SECRET` | for Google auth | empty | Spring Boot backend |
| `GOOGLE_REDIRECT_URI` | for Google auth | local callback above | Spring Boot backend |
| `NEXT_PUBLIC_API_BASE_URL` | yes | `http://localhost:8080` | Next.js frontend |
| `SPOTIFY_CLIENT_ID` | for Spotify auth | empty | Spring Boot backend |
| `SPOTIFY_CLIENT_SECRET` | for Spotify auth | empty | Spring Boot backend |
| `SPOTIFY_REDIRECT_URI` | for Spotify auth | local callback above | Spring Boot backend |
| `SPRING_PROFILES_ACTIVE` | no | `prod` in Compose | Spring Boot backend |

Never commit populated `.env` files, client secrets, OAuth tokens, or API keys. For production, use a secret manager and HTTPS at the reverse proxy.

## API and quotas

The stable HTTP boundary is in [docs/api-contract.md](docs/api-contract.md): health, Spotify playlist listing, conversion preview, and conversion creation. Spotify and YouTube enforce provider-side rate limits and quotas; YouTube playlist writes consume quota units, and a failed request may still consume quota. PlaylistBridge does not bypass, reset, or guarantee provider quotas. Add retry/backoff and monitor provider responses in production; exact quotas depend on each provider account and can change.

## Privacy and data deletion

Self-hosting means the operator controls the server, logs, backups, and retention policy. Store only OAuth tokens and playlist metadata needed for an active conversion, protect them at rest, and set a documented retention period. Provide a user-visible sign-out and deletion operation that revokes or deletes stored provider tokens and removes conversion metadata; deleting local data does not delete playlists already created on Spotify or YouTube.

Users can revoke PlaylistBridge access from Spotify account settings and Google Account third-party connections. Operators must also delete application logs, backups, and database records containing that user's identifiers when honoring a deletion request. Follow applicable privacy law and each provider's developer terms.

## What PlaylistBridge does not do

PlaylistBridge never downloads or stores audio. It transfers metadata such as playlist names, track titles, artists, album information, and provider IDs, then asks YouTube to create a playlist. It is not a music downloader, stream ripper, or audio conversion service.

## Validation

Run the repository packaging checks before sharing a deployment configuration:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\validate-config.tests.ps1
```

The check verifies required services, ports, environment variable names, and (when Docker Compose is installed) Compose's parsed configuration.
