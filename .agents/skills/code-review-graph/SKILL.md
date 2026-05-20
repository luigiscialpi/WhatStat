---
name: code-review-graph
description: Use when reviewing code, tracing impact, or exploring codebase structure via knowledge graph MCP tools. Applies to WhatStat Android project.
---

# Skill: code-review-graph

Usa questi tool MCP PRIMA di Grep/Glob/Read.
Graph: più veloce, meno token, restituisce contesto strutturale.

## Graph PRIMA in questi casi

- Esplora codice: `semantic_search_nodes` o `query_graph` invece di Grep
- Impatto modifiche: `get_impact_radius` (es. cambi a `ActiveDatabaseProvider`, `CacheManager`, `AnalyticsRepository`)
- Code review: `detect_changes` + `get_review_context` invece di leggere file interi
- Relazioni: `query_graph` con callers_of/callees_of/imports_of/tests_for
- Architettura: `get_architecture_overview` + `list_communities`

Fallback a Grep/Glob/Read solo se graph non copre il caso.

## Tool Reference

| Tool                        | Usa quando                                          |
| --------------------------- | --------------------------------------------------- |
| `detect_changes`            | Review modifiche — output con risk score            |
| `get_review_context`        | Snippet sorgente per review — token-efficient       |
| `get_impact_radius`         | Blast radius di una modifica                        |
| `get_affected_flows`        | Execution path impattati                            |
| `query_graph`               | Traccia callers, callees, imports, test, dipendenze |
| `semantic_search_nodes`     | Trova funzioni/classi per nome o keyword            |
| `get_architecture_overview` | Struttura codebase ad alto livello                  |
| `refactor_tool`             | Pianifica rename, trova dead code                   |

## Aree ad Alto Rischio (usa sempre `get_impact_radius`)

- `ActiveDatabaseProvider` — routing Root/Lite, qualsiasi modifica impatta tutti i DAO
- `CacheManager` — invalidazione cache, ordine operazioni critico (vedi `setLiteMode` prima di `clearCache`)
- `AnalyticsRepository` — logica business centrale, espone `isLiteMode` e `liteCacheVersion`
- `AppNavigation.kt` — grafi annidati, `autoNavigate`, `sharedChatDetailRoutes`
- `LiteModeManager` — coordinatore import, `finalizeOwnerSelection`, `slimSourceZip`
- `MessageDao` — query con `LAG()`, gestione `sender_jid_row_id = 0`, stop words

## Workflow

1. Graph auto-aggiorna su file changes (via hooks)
2. `detect_changes` per code review
3. `get_affected_flows` per capire impatto
4. `query_graph` pattern="tests_for" per check coverage
