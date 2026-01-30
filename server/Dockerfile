# Open Mobile TTS Server - Docker Image
FROM python:3.11-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    espeak-ng \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY src/ ./src/

# Create upload directory
RUN mkdir -p /tmp/openmobiletts_uploads

# Expose port
EXPOSE 8000

# Environment variables (override with -e or docker-compose)
ENV HOST=0.0.0.0
ENV PORT=8000
ENV WORKERS=1

# Run the application
CMD ["sh", "-c", "uvicorn src.main:app --host ${HOST} --port ${PORT} --workers ${WORKERS}"]
