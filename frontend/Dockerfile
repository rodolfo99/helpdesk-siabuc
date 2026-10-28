FROM node:24.22.1-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.31.0-alpine
# Apply available Alpine security fixes (including Brotli and curl).
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/helpdesk/browser /usr/share/nginx/html
EXPOSE 80
