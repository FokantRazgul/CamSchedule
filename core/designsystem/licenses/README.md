# Bundled fonts

Both fonts are licensed under the SIL Open Font License 1.1 (full texts alongside).

| File(s) in `src/main/res/font` | Family | Source |
|---|---|---|
| `cormorant_garamond_*.ttf` | Cormorant Garamond 400, 400 italic, 500, 600 (Latin subset) | `@fontsource/cormorant-garamond` 5.3.0 |
| `hanken_grotesk_*.ttf` | Hanken Grotesk 400, 500, 600 (Latin subset) | `@fontsource/hanken-grotesk` 5.3.0 |

The Fontsource WOFF files were converted to TTF with fontTools, without other changes.
Cormorant figures are proportional old-style by default; the type scale switches on `lnum`/`tnum`
for numerals. Hanken Grotesk figures are tabular by default.
