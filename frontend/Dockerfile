# --- build stage: Node inside the image, so no local Node is needed ---
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Empty = same-origin: the browser calls /api/... on whatever host serves the page, and nginx
# (see nginx.conf) forwards it to the backend. Override only to point the build at another API.
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# --- runtime stage: nginx serving dist/, non-root (listens on 8080 inside the container) ---
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
