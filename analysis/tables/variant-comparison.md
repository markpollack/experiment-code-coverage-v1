# Variant Comparison — Full Suite Run 2026-03-03

## Summary

| Variant | Pass% | Avg Cov% | Avg T3 | Avg Eff | Total Cost | Avg Cost |
|---------|-------|----------|--------|---------|------------|----------|
| control | 100.0% | 95.6 | 0.62 | 0.878 | $4.57 | $0.91 |
| variant-a | 100.0% | 85.7 | 0.8 | 0.937 | $4.17 | $0.83 |
| variant-b | 100.0% | 95.6 | 0.697 | 0.837 | $4.98 | $1.0 |
| variant-c | 100.0% | 88.4 | 0.757 | 0.823 | $4.55 | $0.91 |

## Per-Item T3 Practice Adherence

| Item | Control | Var-A | Var-B | Var-C |
|------|---------|-------|-------|-------|
| gs-accessing-data-jpa | 0.6 | **0.7** | 0.65 | 0.68 |
| gs-messaging-stomp-websocket | 0.5 | 0.6 | 0.5 | **0.65** |
| gs-reactive-rest-service | 0.5 | **0.93** | 0.7 | 0.85 |
| gs-rest-service | 0.67 | **0.88** | 0.85 | 0.82 |
| gs-securing-web | 0.83 | **0.88** | 0.78 | 0.78 |

## Per-Item Efficiency

| Item | Control | Var-A | Var-B | Var-C |
|------|---------|-------|-------|-------|
| gs-accessing-data-jpa | 0.766 | 0.951 | 0.951 | 0.76 |
| gs-messaging-stomp-websocket | 0.972 | 0.978 | 0.972 | 0.787 |
| gs-reactive-rest-service | 0.837 | 0.871 | 0.855 | 0.742 |
| gs-rest-service | 0.869 | 0.939 | 0.76 | 0.963 |
| gs-securing-web | 0.946 | 0.948 | 0.647 | 0.864 |

## Per-Item Coverage (%)

| Item | Control | Var-A | Var-B | Var-C |
|------|---------|-------|-------|-------|
| gs-accessing-data-jpa | 94.6 | 94.6 | 94.6 | 94.6 |
| gs-messaging-stomp-websocket | 92.3 | 92.3 | 92.3 | 84.6 |
| gs-reactive-rest-service | 100.0 | 78.9 | 100.0 | 100.0 |
| gs-rest-service | 100.0 | 71.4 | 100.0 | 71.4 |
| gs-securing-web | 91.3 | 91.3 | 91.3 | 91.3 |

## Diagnostic Framework

| Dimension | Control | Var-A | Delta (C→A) | Var-B | Var-C | Interpretation |
|-----------|---------|-------|-------------|-------|-------|----------------|
| T3 Adherence | 0.620 | 0.800 | +0.180 | 0.697 | 0.757 | Prompt structure > KB for practice quality |
| Efficiency | 0.878 | 0.937 | +0.059 | 0.837 | 0.823 | Hardened prompt = fewer build errors = more efficient |
| Cost | 0.91 | 0.83 | +0.08 | 1.00 | 0.91 | KB injection costs more (reading KB files) |
