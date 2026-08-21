# ============================================================
# FILE: XRC/server/Dockerfile
# ============================================================
FROM node:20-alpine

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci --only=production

# Copy server source
COPY src/ ./src/
COPY .env ./

# Create data directory
RUN mkdir -p /app/data

EXPOSE 3000

CMD ["node", "src/index.js"]
