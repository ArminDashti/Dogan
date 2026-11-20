# syntax=docker/dockerfile:1

FROM golang:1.26-alpine AS builder

WORKDIR /src

COPY go.mod ./
COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /dogan-cv ./cmd/server

FROM alpine:3.21

RUN apk add --no-cache ca-certificates wget \
    && adduser -D -h /data -u 1000 appuser

WORKDIR /data

COPY --from=builder /dogan-cv /usr/local/bin/dogan-cv

USER appuser

EXPOSE 8090

ENV DOGAN_CV_LISTEN=:8090 \
    DOGAN_FRAMES_DIR=/data/frames

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8090/health || exit 1

ENTRYPOINT ["dogan-cv"]
