# Security Checklist - Before Pushing to GitHub

Run through this checklist before pushing code to GitHub to ensure no sensitive data is exposed.

## ✅ Files to Check

### 1. Environment Files (.env)

**Status**: ✅ SAFE - In .gitignore

```bash
# These files should NEVER be committed:
server/.env          ← Contains real credentials
client/.env          ← Contains server URL

# These SHOULD be committed (templates):
server/.env.example  ← Safe template
client/.env.example  ← Safe template
```

**Verify**:
```bash
git status | grep "\.env$"
# Should return nothing (files ignored)
```

### 2. Source Code

**Files checked**: ✅ CLEAN

- ❌ No hardcoded passwords
- ❌ No real JWT secrets
- ❌ No API keys
- ✅ Only placeholder text in UI

**Verified files**:
- `client/src/routes/login/+page.svelte` - Removed "Default: admin / testpassword123"
- `server/src/config.py` - Uses environment variables only
- `server/src/auth.py` - No hardcoded secrets

### 3. Configuration Files

**Status**: ✅ SAFE

All configs use environment variables or .env files (which are gitignored).

### 4. Documentation

**Status**: ✅ SAFE

- README.md - No sensitive data
- Setup guides - Instructs users to generate own credentials
- Docs folder - No secrets

### 5. Test Files

**Status**: ✅ SAFE

Test files use mock credentials only.

## ✅ .gitignore Coverage

Verify these are in .gitignore:

```bash
# Environment variables
.env
.env.local
.env.*.local
.env.production
✅ CONFIRMED

# Logs (might contain sensitive debug info)
*.log
logs/
✅ CONFIRMED

# Temporary files
*.tmp
tmp/
✅ CONFIRMED

# Database files (might contain user data)
*.db
*.sqlite
✅ CONFIRMED

# Model cache
.cache/
models/
✅ CONFIRMED

# Uploads (user documents)
uploads/
✅ CONFIRMED
```

## ✅ Public Repository Safety

### What IS safe to commit:

✅ Source code
✅ .env.example files (templates)
✅ Documentation
✅ Tests
✅ Requirements.txt / package.json
✅ Docker files
✅ GitHub workflows
✅ README and guides

### What is NOT safe to commit:

❌ .env files with real credentials
❌ Private keys or certificates
❌ JWT secrets
❌ Password hashes
❌ API keys
❌ User data
❌ Generated audio files
❌ Uploaded documents
❌ Database files
❌ Log files with sensitive info

## 🔒 Recommended: Pre-Commit Hook

Create `.git/hooks/pre-commit`:

```bash
#!/bin/bash

echo "Running security checks..."

# Check for .env files
if git diff --cached --name-only | grep -E "\.env$"; then
    echo "❌ ERROR: Attempting to commit .env file!"
    echo "Only .env.example should be committed."
    exit 1
fi

# Check for potential secrets in staged files
if git diff --cached | grep -E "(password|secret|key|token).*=.*['\"][^'\"]{8,}"; then
    echo "⚠️  WARNING: Potential secret detected in staged changes!"
    echo "Please review carefully."
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo "✅ Security checks passed"
exit 0
```

Make it executable:
```bash
chmod +x .git/hooks/pre-commit
```

## 🔍 Final Check Before Push

Run these commands:

```bash
# 1. Check no .env files staged
git status | grep "\.env$"
# Should show nothing

# 2. Check what's being committed
git diff --cached --name-only
# Review the list

# 3. Search for potential secrets in staged changes
git diff --cached | grep -i "password\|secret\|key"
# Review any matches (should only be variable names, not values)

# 4. Verify .gitignore is working
git check-ignore server/.env client/.env
# Should output both file paths (meaning they're ignored)
```

## ✅ First-Time Repository Setup

When first pushing to GitHub:

```bash
# 1. Remove any accidentally tracked files
git rm --cached server/.env
git rm --cached client/.env

# 2. Ensure .gitignore is committed
git add .gitignore
git commit -m "Add .gitignore for security"

# 3. Add safe files only
git add server/.env.example
git add client/.env.example
git add SETUP_GUIDE.md
git add SECURITY_CHECKLIST.md

# 4. Review what will be pushed
git log --oneline
git diff origin/main

# 5. Push
git push origin main
```

## 🚨 If Secrets Are Accidentally Committed

### DO NOT just delete the file and commit again!

Git history will still contain the secrets. Instead:

1. **Change the exposed credentials immediately**
   ```bash
   # Generate new JWT secret
   openssl rand -hex 32

   # Update server/.env with new secret
   # Regenerate password hash if exposed
   ```

2. **Remove from Git history**
   ```bash
   # Use BFG Repo Cleaner or git filter-branch
   git filter-branch --force --index-filter \
     "git rm --cached --ignore-unmatch server/.env" \
     --prune-empty --tag-name-filter cat -- --all

   # Force push (if remote exists)
   git push origin --force --all
   ```

3. **Verify removal**
   ```bash
   git log -- server/.env
   # Should show nothing
   ```

## 📋 Pre-Push Checklist

Before every `git push`:

- [ ] No `.env` files in `git status`
- [ ] No `*.log` files in `git status`
- [ ] No sensitive data in staged changes
- [ ] `.env.example` files are up to date
- [ ] SETUP_GUIDE.md reflects current setup process
- [ ] Test that someone could clone and run with just the .example files

## ✅ Current Status

**Last checked**: 2026-01-30

**Files verified clean**:
- ✅ All source code
- ✅ All documentation
- ✅ .env.example files
- ✅ Configuration files
- ✅ Test files

**Sensitive files properly ignored**:
- ✅ server/.env
- ✅ client/.env
- ✅ *.log files
- ✅ uploads/
- ✅ *.db files

**Repository is safe to push to GitHub** ✅

## 🔗 Additional Resources

- [GitHub: Removing sensitive data](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
- [Git-secrets tool](https://github.com/awslabs/git-secrets)
- [BFG Repo Cleaner](https://rtyley.github.io/bfg-repo-cleaner/)
