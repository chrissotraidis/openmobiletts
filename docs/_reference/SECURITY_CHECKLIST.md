# Security Checklist

Open Mobile TTS has **no authentication** — it is designed as a local-only app. Security is handled at the network level, not the application level. CORS is enabled (`allow_origins=["*"]`) to support the Android app connecting over WiFi.

## For Local Use (Default)

No action needed. The app runs on `localhost:8000` and is only accessible from your machine.

## For Android Use

The server binds to `0.0.0.0:8000` so Android devices on the same WiFi network can connect. This means any device on your local network can access the server. If this is a concern:

- Use a trusted, private WiFi network
- Consider firewall rules to restrict access to your phone's IP only
- The CORS wildcard is intentional — the Android WebView needs cross-origin access

## For Network/VPS Deployment

If you expose the app beyond localhost (e.g., on a VPS or home network):

### Before Deploying

- [ ] Use a reverse proxy (Caddy, Nginx) with HTTPS
- [ ] Restrict access via firewall rules or VPN
- [ ] Consider adding reverse proxy authentication (e.g., Caddy `basicauth`, Nginx `auth_basic`)
- [ ] Do NOT expose port 8000 directly to the internet without a proxy

### Files to Check Before Pushing to GitHub

- [ ] No `.env` files in `git status` (they're in `.gitignore`)
- [ ] No `*.log` files staged
- [ ] `.env.example` files contain only placeholder values

### What IS Safe to Commit

✅ Source code
✅ `.env.example` files (templates with defaults only)
✅ Documentation
✅ Tests
✅ Docker files

### What is NOT Safe to Commit

❌ `.env` files with real configuration
❌ Log files
❌ Uploaded documents
❌ Generated audio files

## .gitignore Coverage

The following are already in `.gitignore`:

```
.env / .env.local / .env.*.local    # Environment files
*.log / logs/                        # Log files
*.db / *.sqlite                      # Database files
.cache/ / models/                    # Model cache
uploads/                             # User documents
node_modules/                        # Dependencies
venv/ / .venv/                       # Virtual environments
```

## If Sensitive Data Is Accidentally Committed

1. **Remove from Git history** using BFG Repo Cleaner or `git filter-branch`
2. **Force push** to overwrite remote history
3. See [GitHub docs on removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
