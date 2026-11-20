# Regeln für Agenten

## Geheimnisse

Vor jedem `git commit` den Secret-Scan ausführen. Der Hook `.githooks/pre-commit` macht das, sobald `git config core.hooksPath .githooks` gesetzt ist.

- `git commit --no-verify` und jede andere Umgehung des Hooks sind verboten.
- Fehlt gitleaks oder schlägt der Scan fehl, wird nicht committet.
- Nicht ins Repo: Home-Assistant-Tokens, JWTs, WebUntis-Secrets, `otpauth://`, Keystores (`*.jks`, `*.keystore`), `local.properties`, `.env`, private Keys, Passwörter.
- Die Konfiguration steht in `.gitleaks.toml`. Dieselbe Prüfung läuft in der GitHub-Action `secret-scan`.
