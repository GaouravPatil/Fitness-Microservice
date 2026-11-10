# AURA frontend

React + TypeScript client for the Fitness Microservice. It covers signup and
login, the daily/ weekly dashboard, activity logging and history, profile
preferences, and calorie estimates derived from each user's movement pattern.

## Stack

- React 19, TypeScript, Vite 8, React Router 7
- Three.js through `@react-three/fiber` and `@react-three/drei` (lazy-loaded)
- `lucide-react` icons and self-hosted Space Grotesk / Manrope variable fonts
- Oxlint for linting

## Features

- Token authentication against `userservice`, with a single-flight refresh flow
  and one automatic retry after a `401`.
- Dashboard with today's ring, 7-day burn chart, streak, weighted 14-day
  forecast, pattern-based next-session suggestion, and optional AI/rule-based
  coach notes.
- Activity history with type filters and a log form that estimates calories from
  MET values, sends an `Idempotency-Key`, and updates the list immediately.
- Profile with local body weight and daily goal, lifetime stats, session details,
  and a wearable-sync placeholder for future live data.
- Responsive desktop sidebar / mobile navigation and a reduced
  charcoal / off-white / lavender visual system.

## API integration

| Flow | Backend request |
|---|---|
| Sign in / sign up | `POST /api/users/login`, `POST /api/users/register` |
| Session bootstrap and profile | `GET /api/users/{id}` |
| Rotate session | `POST /api/users/refresh` |
| Sign out | `POST /api/users/logout` |
| Dashboard, history, profile stats | `GET /api/activities/user/{userId}?page=&size=&sort=startTime,desc` |
| Log a session | `POST /api/activities` with `Idempotency-Key` |
| Coach notes | `GET /api/recommendations/user/{userId}?page=0&size=3&sort=createdAt,desc` |

The browser never generates AI recommendations. Logging an activity publishes
the event to Kafka; `aiservice` generates and stores the recommendation
asynchronously. The dashboard reads the latest recommendations, labels them as
`AI coach` or `Rule-based coach` from the `provider` field, and falls back to
local pattern notes if the recommendation request fails or has no entries. Set
`AI_API_KEY`, `AI_BASE_URL`, and `AI_MODEL` in the backend `.env` for real AI
output; an empty API key uses the rules provider. The UI does not poll, so a new
recommendation may appear after a page refresh.

## Run locally

Requirements: Node 24 (or a Vite 8 compatible Node release) and the backend
services from the repository root running on ports 8081-8083.

```bash
cd frontend
cp .env.example .env
npm ci
npm run dev
```

Open `http://localhost:3000`. Vite proxies `/api/users`, `/api/activities`, and
`/api/recommendations` to the local HTTPS services and skips verification of the
self-signed development certificates, so the browser does not need to trust the
local CA.

`VITE_DEV_*` variables only choose the proxy targets; they are not sent to the
browser. Without a running backend the auth forms render but API actions show an
unreachable-service notice.

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | Vite development server on port 3000 |
| `npm run build` | Typecheck and produce `dist/` |
| `npm run typecheck` | TypeScript project build only |
| `npm run lint` | Oxlint |
| `npm run preview` | Serve the production build (no API proxy) |

## Performance

Always profile the production build, not the Vite development server. The dev
server serves unbundled modules with HMR overhead and can report a misleadingly
low Lighthouse score (often in the 30s) even when the deployed build scores
above 90.

```bash
npm run build
npm run preview -- --port 4174
npx lighthouse http://localhost:4174/login --only-categories=performance --view
```

Performance measures in this project:

- Route-level code splitting keeps dashboard, history, and profile JavaScript out
  of the initial auth bundle.
- The Three.js orb is loaded only on desktop auth screens. It is skipped below
  901 px and when `prefers-reduced-motion` is set; authenticated app pages use a
  static CSS background instead of a continuously running WebGL canvas.
- The 3D renderer caps DPR at 1.5 and uses a smaller particle field.
- Main app JS is ~88 KiB gzip; the desktop-only Three.js chunk is lazy.
- Sample headless mobile Lighthouse results: `/login` ~100 and an authenticated
  `/app` ~94; desktop `/login` ~93. Exact scores vary by machine and throttling.

## Configuration

Copy `.env.example` to `.env` for local development. Production values are baked
in at build time, so rebuild after changing them.

| Variable | Purpose |
|---|---|
| `VITE_DEV_USER_SERVICE_URL` | Dev proxy target for `/api/users` (default `https://localhost:8081`) |
| `VITE_DEV_ACTIVITY_SERVICE_URL` | Dev proxy target for `/api/activities` (default `https://localhost:8082`) |
| `VITE_DEV_RECOMMENDATION_SERVICE_URL` | Dev proxy target for `/api/recommendations` (default `https://localhost:8083`) |
| `VITE_USER_API_URL` | Production users API base; blank means same-origin `/api/users` |
| `VITE_ACTIVITY_API_URL` | Production activities API base; blank means `/api/activities` |
| `VITE_RECOMMENDATION_API_URL` | Production recommendations API base; blank means `/api/recommendations` |

The two supported production shapes are:

1. **Same origin (recommended).** Serve `dist/` and reverse-proxy
   `/api/users`, `/api/activities`, and `/api/recommendations` to the three
   services, leaving all `VITE_*_API_URL` values blank. No CORS changes are
   needed.
2. **Separate origins.** Set the three production URL variables at build time
   and add the frontend origin to `CORS_ALLOWED_ORIGINS` in the backend `.env`
   before restarting all services.

### Docker

```bash
docker build -t aura-frontend \
  --build-arg VITE_USER_API_URL=https://users.example.com \
  --build-arg VITE_ACTIVITY_API_URL=https://activities.example.com \
  --build-arg VITE_RECOMMENDATION_API_URL=https://recommendations.example.com \
  .
docker run --rm -p 3000:80 aura-frontend
```

The image serves only the SPA through nginx, including the history fallback for
client-side routes. Put it behind a reverse proxy if you want same-origin APIs.

### Vercel / Netlify

`vercel.json` and `public/_redirects` handle SPA route fallback. Set the three
`VITE_*_API_URL` project variables, then deploy with the repository root set to
`frontend/`. API paths are not rewritten to `index.html`.

## Estimation notes

- `weightKg` (default 70) and `dailyGoal` (default 500 kcal) live in
  `localStorage` under `aura.preferences.v1`; they are never sent to the server.
- Calories use `MET × weightKg × durationHours` and remain editable before
  logging.
- The burn forecast weights the last 14 days more heavily toward today and
  projects the next 7 days.
- Activity lists load the latest 100 sessions on the history page and 200 on the
  profile; this is not a full pagination UI.

## Auth and security

- The access token (15 min) and rotating refresh token (7 days) are stored in
  `localStorage` under `aura.session.v1`. This keeps the demo simple, but a
  production deployment with a stronger XSS threat model should use HttpOnly
  cookies and a backend-for-frontend instead.
- Logout calls `userservice` to revoke the refresh token, then clears the local
  session even if the service is unreachable.
- All authorization decisions remain server-side; the client only controls
  navigation and presentation.

## Known limits

- Smartwatch sync is intentionally a disabled placeholder; live Web Bluetooth
  streaming is not implemented.
- The desktop auth orb is a ~925 kB (245 KiB gzip) lazy-loaded Three.js chunk. It
  is not downloaded on mobile or inside authenticated app routes.
- There is no offline cache, push notifications, recommendation polling, or
  in-repo end-to-end test suite yet.
