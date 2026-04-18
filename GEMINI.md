<!-- code-review-graph MCP tools -->

## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool                        | Use when                                               |
| --------------------------- | ------------------------------------------------------ |
| `detect_changes`            | Reviewing code changes — gives risk-scored analysis    |
| `get_review_context`        | Need source snippets for review — token-efficient      |
| `get_impact_radius`         | Understanding blast radius of a change                 |
| `get_affected_flows`        | Finding which execution paths are impacted             |
| `query_graph`               | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes`     | Finding functions/classes by name or keyword           |
| `get_architecture_overview` | Understanding high-level codebase structure            |
| `refactor_tool`             | Planning renames, finding dead code                    |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.

---

# Stile di comunicazione

1. Usa la caveman compression per tutte le risposte: elimina le parole grammaticali (un, una, il, la, i, le, è, sono, era, erano, quindi, tuttavia, al fine di, essenzialmente, abbastanza, molto)
2. Mantieni tutti i fatti: numeri, nomi, date, termini tecnici, vincoli, percorsi file, nomi API
3. Massimo 2-5 parole per frase. Un concetto per frase.
4. Usa solo la forma attiva. "calcola valore" non "il valore viene calcolato"
5. Usa verbi d'azione: fai, crea, correggi, controlla, esegui, aggiungi — non: facilitare, ottimizzare, sfruttare
6. Mantieni i dettagli tecnici specifici: "O(log n)", "porta 3000", "pytest", "Bazel" — non comprimere mai questi
7. In caso di dubbio, mantieni più informazioni anziché meno

# Cosa non comprimere mai

8. Blocchi di codice — non modificare mai sintassi o nomi di variabili
9. Messaggi di errore — mantieni verbatim
10. Percorsi file e URL — mantieni verbatim
