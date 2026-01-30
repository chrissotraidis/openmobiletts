# Open Mobile TTS - Complete Setup Guide

This guide walks you through setting up Open Mobile TTS from a fresh clone of the repository.

## What You're Setting Up

A **private, self-hosted** text-to-speech system with:
- **Server**: Runs on your computer/VPS and generates speech
- **Web App**: Access from any browser (mobile or desktop)
- **Android App** (coming soon): Native mobile app

## Prerequisites

### Required
- **Python 3.9-3.12** (check: `python --version`)
- **Node.js 18+** (check: `node --version`)
- **Git** (to clone the repository)

### Optional but Recommended
- **GPU** with 2GB+ VRAM (for faster generation)
- **espeak-ng** (Linux: `apt install espeak-ng`, Mac: `brew install espeak`)

## Step 1: Clone Repository

```bash
git clone https://github.com/yourusername/openmobiletts.git
cd openmobiletts
```

## Step 2: Set Up Server

### 2.1 Create Virtual Environment

```bash
cd server
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
```

### 2.2 Install Dependencies

```bash
pip install -r requirements.txt
```

This will take a few minutes. It installs ~500MB of dependencies including PyTorch.

### 2.3 Download TTS Model

```bash
python setup_models.py
```

This downloads the Kokoro TTS model (~320MB) to `~/.cache/kokoro/`. **This only happens once.**

Expected output:
```
Downloading Kokoro models...
Models cached at: ~/.cache/kokoro/
Testing TTS generation...
✓ TTS test successful!
```

### 2.4 Create Environment File

```bash
cp .env.example .env
```

### 2.5 Generate JWT Secret

```bash
openssl rand -hex 32
```

Copy the output (a long random string).

### 2.6 Generate Password Hash

Choose a secure password (NOT "testpassword123"), then:

```bash
python generate_password_hash.py your_secure_password_here
```

Copy the argon2 hash that's output.

### 2.7 Edit .env File

Open `server/.env` and fill in:

```bash
# REQUIRED - Paste the JWT secret you generated
JWT_SECRET=your_generated_secret_here

# REQUIRED - Choose your username
ADMIN_USERNAME=your_username

# REQUIRED - Paste the password hash you generated
ADMIN_PASSWORD_HASH=your_argon2_hash_here

# OPTIONAL - Everything else has good defaults
```

**IMPORTANT**: The `.env` file contains sensitive credentials and is NOT committed to git (it's in .gitignore).

### 2.8 Test Server

```bash
uvicorn src.main:app --host 0.0.0.0 --port 8000
```

You should see:
```
INFO: Uvicorn running on http://127.0.0.1:8000
INFO: Application startup complete.
```

Visit http://localhost:8000/health - should show: `{"status":"healthy"}`

**Keep this terminal open** (server is running).

## Step 3: Set Up Client

Open a **new terminal** (keep server running in the other one).

### 3.1 Install Dependencies

```bash
cd client  # From repo root
npm install
```

### 3.2 Create Environment File

```bash
cp .env.example .env
```

### 3.3 Edit .env File (Optional)

For local development, the default is fine:

```bash
VITE_API_URL=http://localhost:8000
```

For connecting to a remote server later:

```bash
VITE_API_URL=https://your-server.com
```

### 3.4 Start Development Server

```bash
npm run dev
```

You should see:
```
VITE ready in 422 ms
➜  Local:   http://localhost:5173/
```

## Step 4: Test the System

### 4.1 Open Web App

Visit http://localhost:5173

Should redirect to login page.

### 4.2 Login

Use the credentials you set in `server/.env`:
- Username: (what you set as ADMIN_USERNAME)
- Password: (the password you used to generate the hash)

### 4.3 Generate Speech

1. Enter text: "Hello, this is a test of the text to speech system"
2. Click "Generate Speech"
3. Wait 2-10 seconds (depending on CPU/GPU)
4. Audio should play automatically
5. Text should highlight as it speaks

### 4.4 Test History

1. Generate 2-3 different audio clips
2. Scroll down to "History" section
3. Click ▶️ to play any previous clip
4. Click ⬇️ to download
5. Refresh page - history should persist

## Step 5: Deployment (Optional)

### For VPS Deployment

1. **Server**: Deploy with Docker or systemd
   ```bash
   cd server
   docker build -t openmobiletts-server .
   docker run -d -p 8000:8000 \
     -e JWT_SECRET=your_secret \
     -e ADMIN_USERNAME=admin \
     -e ADMIN_PASSWORD_HASH=your_hash \
     openmobiletts-server
   ```

2. **Client**: Build and deploy to static hosting
   ```bash
   cd client
   npm run build
   # Deploy 'build' folder to Vercel/Netlify/Cloudflare Pages
   ```

3. **HTTPS**: Use Caddy, Nginx, or Cloudflare for HTTPS

### For Android App (Coming Soon)

See [ANDROID_APP_GUIDE.md](ANDROID_APP_GUIDE.md) for mobile app setup.

## Troubleshooting

### Server won't start

**Error: "No module named 'kokoro'"**
```bash
cd server
source venv/bin/activate
pip install -r requirements.txt
```

**Error: "JWT_SECRET not set"**
- Edit `server/.env` and add your JWT_SECRET

**Error: "espeak-ng not found"** (Linux only)
```bash
sudo apt-get install espeak-ng
```

### Client won't connect to server

**CORS errors in browser console**
- Check `server/.env` has correct CORS_ORIGINS
- Should include your client URL: `http://localhost:5173`

**"Failed to fetch"**
- Make sure server is running: http://localhost:8000/health
- Check `client/.env` has correct VITE_API_URL

### Login fails

**"Invalid username or password"**
- Double-check `server/.env` credentials
- Make sure you used `generate_password_hash.py` to create the hash
- Username and password are case-sensitive

### Audio doesn't play

**Audio generates but won't play**
- Check browser console (F12) for errors
- Try a different browser (Chrome recommended)
- Make sure you're not on a very old browser

**Audio only plays 30 seconds**
- This was a bug in earlier versions
- Make sure you're on the latest code
- Hard refresh: Ctrl+Shift+R (Cmd+Shift+R on Mac)

### Generation is very slow

**Expected speed on CPU**: 2-5x real-time
- 200 words = ~10 seconds generation time
- 1,000 words = ~1 minute generation time

**To speed up**:
- Use a machine with GPU (10-50x faster)
- Or use a cloud GPU instance

## Security Checklist

Before making your server publicly accessible:

- [ ] Changed default username from "admin"
- [ ] Generated strong password (not "testpassword123")
- [ ] Generated unique JWT_SECRET (not using example)
- [ ] Enabled HTTPS (required for production)
- [ ] Configured firewall to only allow necessary ports
- [ ] Set up regular backups
- [ ] Reviewed CORS_ORIGINS in .env

## Next Steps

1. **Customize voices**: See `docs/HOW_IT_WORKS.md` for voice options
2. **Adjust settings**: See `server/.env.example` for all options
3. **Deploy to VPS**: See server/README.md for deployment
4. **Build Android app**: See ANDROID_APP_GUIDE.md (coming soon)

## File Structure Reference

```
openmobiletts/
├── server/
│   ├── .env                  ← Your credentials (NOT in git)
│   ├── .env.example          ← Template with instructions
│   ├── requirements.txt      ← Python dependencies
│   ├── setup_models.py       ← Downloads TTS model
│   └── generate_password_hash.py  ← Creates password hash
├── client/
│   ├── .env                  ← Server URL (NOT in git)
│   ├── .env.example          ← Template
│   └── package.json          ← Node dependencies
└── docs/
    ├── QUICK_REFERENCE.md    ← Command cheat sheet
    ├── HOW_IT_WORKS.md       ← System explanation
    └── LIMITS_AND_CONSTRAINTS.md  ← Performance info
```

## Getting Help

- **Documentation**: See `docs/` folder for detailed guides
- **Issues**: Report bugs on GitHub Issues
- **Questions**: Check `docs/HOW_IT_WORKS.md` and `docs/QUICK_REFERENCE.md`

## Success Checklist

You've successfully set up Open Mobile TTS when:

- [ ] Server starts without errors
- [ ] http://localhost:8000/health returns `{"status":"healthy"}`
- [ ] Client loads at http://localhost:5173
- [ ] You can login with your credentials
- [ ] Text-to-speech generation works
- [ ] Audio plays in the browser
- [ ] History saves and loads correctly
- [ ] Download button works

**Congratulations! Your private TTS system is ready to use! 🎉**
