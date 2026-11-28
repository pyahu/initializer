# 1. The verticals share one repository

Status: accepted.

## Context

One team, or a few teams that work closely, own billing orders and often change two of them together: a field in a
contract and the vertical that reads it.

## Decision

Keep them in one repository and one Gradle build, as the Pyahu Initializer's monorepo guide describes. Each
vertical owns its code, contract, database, image and deployment; they share build rules, versions, libraries and
contracts only, and the build enforces it.

## Consequences

- A contract and its consumers change in one pull request, and a library upgrade is one pull request for all.
- A team that stops working with the others takes its vertical's folder to a repository of its own.
