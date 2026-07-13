# ServerCore Community Website

Static community site for the planned All The Mods 11 server. It displays the public API status and ranked leaderboard without requiring a JavaScript build tool.

## Configure

Edit `config.js` and set `apiBaseUrl` to the public Network API URL.

The API must include the website origin in `SERVERCORE_ALLOWED_ORIGINS`.

## Run locally

```bash
python -m http.server 8080
```

Then open `http://localhost:8080`. The default API URL is `http://localhost:8000`.

## Container

```bash
docker build -t servercore-website .
docker run --rm -p 8080:80 servercore-website
```
