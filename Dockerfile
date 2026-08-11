# Open Mobile TTS — Full-stack Docker image
# Builds the SvelteKit client and runs the FastAPI server.
#
# Usage:
#   docker build -t openmobiletts .
#   docker run -p 8000:8000 openmobiletts

# --- Stage 1: Build the SvelteKit client ---
FROM node:20-slim AS client-builder
WORKDIR /app/client
COPY client/package.json client/package-lock.json ./
RUN npm ci
COPY client/ ./
RUN npm run build

# --- Stage 2: Python runtime ---
FROM python:3.11-slim

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    espeak-ng \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY VERSION ./VERSION
COPY models/ ./models/

# Install Python dependencies
COPY server/requirements.lock ./requirements.lock
RUN pip install --no-cache-dir --require-hashes -r requirements.lock

# Copy server code
COPY server/src/ ./src/

# Copy built client from stage 1
COPY --from=client-builder /app/client/build ./client-build

# Create upload directory
RUN mkdir -p /tmp/openmobiletts_uploads

# Set environment
ENV HOST=0.0.0.0
ENV PORT=8000
ENV WORKERS=1
ENV STATIC_DIR=/app/client-build
ENV HF_HOME=/root/.cache/huggingface

EXPOSE 8000

CMD ["sh", "-c", "uvicorn src.main:app --host ${HOST} --port ${PORT} --workers ${WORKERS}"]
