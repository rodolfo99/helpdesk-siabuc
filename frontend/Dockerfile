FROM node:26.12.1-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.30.6-alpine
# Apply available Alpine security fixes (including Brotli and curl).
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/helpdesk/browser /usr/share/nginx/html
EXPOSE 80
