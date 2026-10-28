# Rebuild gosu 1.20 with the patched Go standard library; keep PostgreSQL 17's
# official entrypoint, data directory, UID and volume layout.
FROM golang:1.27.3-alpine AS gosu-build
RUN apk add --no-cache git
WORKDIR /src
RUN git init . \
    && git remote add origin https://github.com/tianon/gosu.git \
    && git fetch --depth 1 origin 29b939aa5bd4dc588af765b8248f6e39150d2565 \
    && git checkout --detach FETCH_HEAD \
    && test "$(git rev-parse HEAD)" = 29b939aa5bd4dc588af765b8248f6e39150d2565
ENV CGO_ENABLED=0 GOTOOLCHAIN=local
RUN go build -trimpath -ldflags='-w' -o /out/gosu .

FROM postgres:17-alpine
RUN apk upgrade --no-cache
COPY --from=gosu-build /out/gosu /usr/local/bin/gosu
RUN gosu nobody id && gosu postgres sh -c 'test "$(id -u)" = 70'
