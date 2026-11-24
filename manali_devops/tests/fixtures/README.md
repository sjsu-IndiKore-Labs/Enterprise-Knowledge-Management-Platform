# Fixture apps (test-only)

Minimal "hello health" apps with the same layout the real services will have. They exist only so
`make test-docker` / CI can prove the Dockerfile templates build, run as non-root and pass their health checks
before the real code exists. Never deploy them.
