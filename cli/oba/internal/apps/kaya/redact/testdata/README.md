# Redaction corpus of the Python recorder

Recorded from the `redact` function of Kaya's former Python recorder (deleted by spec 18; the commit below still holds it) at commit `22749e58080f79657556b1ab7f45edaf5a82c8b9` (Python 3.14.8) by the generator named in `../../capture/testdata/README.md`, in the same run.

- `redact.json`: 86 named strings (`name`, `input`, `expected`). They cover the three first rules (`--token` and `--password`, `-p`, `Authorization:`), the lookbehind edge cases of rule 4 (a name that ends in TOKEN, SECRET, PASSWORD or KEY, where the name run starts, glued names, empty values, quoting, unterminated quotes, `MONKEY=`), Unicode whitespace (`U+00A0`, `U+2003`) and Python's Unicode case folding (`ſecret=`, `paſſword=`, Kelvin sign in `KEY=`). The Go output must equal `expected` exactly.
- `generated.json`: `seed` 18, the `pool` of fragments, and 600 `cases` made by `random.Random(18)` joining 2 to 14 fragments. The Go output must redact at least what `expected` redacted: wherever `expected` holds `[redacted]` the Go output holds it as well, and no value `expected` kept is exposed by a weaker match. Equality is not required for this file.
