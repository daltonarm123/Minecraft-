# ServerCore Player Portal MVP

The player portal is served by the existing FastAPI network service at `/portal`.

## Included in this MVP

- Discord OAuth login with a secure HTTP-only session cookie
- Linked Minecraft profile and synchronized player statistics
- Derived achievements based on wins, kills, and rating
- Public duel leaderboard
- Hosted membership checkout redirect
- Server-authoritative membership status updates
- Player support ticket submission and staff status updates
- Feature proposals with one vote per Discord account
- Short-lived Minecraft account-link codes
- `/link <code>` command in the NeoForge adapter

## Required environment variables

### Network API

```env
SERVERCORE_API_KEY=replace-with-a-long-random-secret
SERVERCORE_ALLOWED_ORIGINS=https://your-domain.example
SERVERCORE_COOKIE_SECURE=true

DISCORD_CLIENT_ID=
DISCORD_CLIENT_SECRET=
DISCORD_REDIRECT_URI=https://your-domain.example/auth/discord/callback

SERVERCORE_MEMBERSHIP_CHECKOUT_URL=https://your-payment-provider.example/checkout
```

### Minecraft server

```env
SERVERCORE_API_URL=https://your-domain.example
SERVERCORE_API_KEY=replace-with-the-same-secret
```

## Discord application setup

1. Create an application in the Discord Developer Portal.
2. Open OAuth2 settings.
3. Add the exact redirect URI configured in `DISCORD_REDIRECT_URI`.
4. Copy the client ID and client secret to the API environment.
5. Keep the client secret out of Git and server logs.

The portal requests only the `identify` OAuth scope.

## Account-link flow

1. The member logs into `/portal` with Discord.
2. They select **Link Minecraft account**.
3. The API creates a six-character code that expires after ten minutes.
4. The member joins the Minecraft server and runs `/link CODE`.
5. The NeoForge adapter sends the code, Minecraft UUID, and username to the protected API endpoint.
6. The portal can then display that player's synchronized profile and achievements.

## Membership flow

The MVP uses a provider-neutral hosted checkout URL rather than embedding a specific payment SDK.

1. Configure `SERVERCORE_MEMBERSHIP_CHECKOUT_URL` with a Stripe Payment Link, Stripe Checkout page, Whop checkout, or another hosted subscription page.
2. The portal adds `client_reference_id=<discord_user_id>` to the checkout URL.
3. A verified payment webhook or staff automation updates membership status through:

```http
PUT /api/portal/admin/memberships/{discord_user_id}
X-ServerCore-Key: <secret>
Content-Type: application/json

{
  "plan": "founder",
  "status": "ACTIVE",
  "current_period_end": "2026-08-22T00:00:00Z"
}
```

Do not grant server access from an unverified browser redirect. Membership activation must come from a verified payment webhook or an authenticated staff action.

## Production blockers

The portal is a functional MVP, not yet production-safe for paid launch.

- `NetworkStore` and `PortalStore` are in memory and lose data when the API restarts.
- PostgreSQL migrations and repositories are still required.
- Payment-provider webhook signature verification is not implemented.
- Staff ticket management currently uses API endpoints rather than a dedicated admin UI.
- Rate limiting, CSRF protection for cross-origin deployments, monitoring, and audit retention still need production hardening.
- The NeoForge mod and API must be tested together on a private server before public release.

## Recommended launch order

1. Deploy PostgreSQL-backed storage.
2. Configure Discord OAuth over HTTPS.
3. Test account linking on the private server.
4. Connect a payment provider and verify signed webhooks.
5. Add membership enforcement to the Minecraft join flow.
6. Run a private beta before charging the broader Discord community.
