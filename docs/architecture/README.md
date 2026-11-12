# Architecture

This directory owns system topology, trust boundaries, data flows and architecture decisions.

## Current runtime topology

- React/Vite frontend built as an Nginx image from `frontend/Dockerfile`.
- Spring Boot API built from the repository-root `Dockerfile`; the Docker build context is the repository root.
- MySQL 8.4 is the relational store and Flyway owns schema evolution.
- Mailpit is a local-only SMTP sink.
- Docker Compose is the supported local orchestration layer.

The current topology is a single API and frontend service. Private evidence uses persistent named volumes locally.
Multi-instance shared-file storage requires a separately approved architecture; container filesystems are not durable.

Preserve existing API routes and backward compatibility unless an approved requirement explicitly changes them.
Document new runtime services, queues, storage providers and trust boundaries here.
