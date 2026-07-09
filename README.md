# Linux Distro Roguelite Promotion

Quarkus-Backend und statische Pre-Register-Page fuer ein kostenloses Game. Spieler registrieren sich mit Benutzername und E-Mail, waehlen Plattformen aus und schalten ueber Community-Meilensteine Belohnungen frei.

## Voraussetzungen

- JDK 21
- Maven 3.9+ oder Quarkus CLI
- PostgreSQL 16+ oder Docker

## Start

PostgreSQL lokal mit Docker starten:

```bash
docker compose up -d postgres
```

```bash
ADMIN_USERNAME=admin ADMIN_PASSWORD=secret mvn quarkus:dev
```

Unter PowerShell:

```powershell
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "secret"
$env:DB_URL = "jdbc:postgresql://localhost:5432/linux_distro_roguelite_promotion"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
$env:TOKEN_SECRET = "<registration-token-secret>"
$env:SMTP_HOST = "mail.fretux.ch"
$env:SMTP_PORT = "587"
$env:SMTP_SECURE = "0"
$env:SMTP_USER = "no-reply@fretux.ch"
$env:SMTP_PASS = "<smtp-password>"
$env:MAIL_FROM = "Fretux <no-reply@fretux.ch>"
$env:BASE_URL = "localhost:3000"
mvn quarkus:dev
```

Der Server laeuft standardmaessig auf Port `3000`. Mit `PORT` kann der Port ueberschrieben werden.

```powershell
$env:PORT = "4000"
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "secret"
mvn quarkus:dev
```

Die Marketingseite ist unter `http://localhost:3000/` erreichbar. Die separate Registrierung liegt unter `http://localhost:3000/register.html`. Beim ersten Start legt das Backend die PostgreSQL-Tabellen automatisch an und befuellt sie mit diesen Daten:

- Plattformen: Windows, Linux, Mobile (Android)
- Meilensteine: 1,000 und 5,000 Community-Registrierungen
- Je eine Beispiel-Belohnung pro Meilenstein

Alle Fehler haben das Format:

```json
{ "error": "beschreibende Meldung" }
```

## Build

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## Datenbank

Die PostgreSQL-Verbindung wird ueber Umgebungsvariablen konfiguriert:

- `DB_URL`, Standard: `jdbc:postgresql://localhost:5432/linux_distro_roguelite_promotion`
- `DB_USERNAME`, Standard: `postgres`
- `DB_PASSWORD`, Standard: `postgres`

## Mailversand

`POST /users` verschickt nach erfolgreicher Registrierung eine Bestaetigungsmail. Die E-Mail-Adresse ist Pflicht und kann wegen des eindeutigen Datenbank-Constraints nur einmal verwendet werden. Wenn der Mailversand fehlschlaegt, wird der neu angelegte Nutzer wieder entfernt und die API antwortet mit `502`.

Der SMTP-Zugang wird ueber Umgebungsvariablen konfiguriert:

- `TOKEN_SECRET`: Secret fuer den signierten Registrierungslink
- `SMTP_HOST`, Standard: `mail.fretux.ch`
- `SMTP_PORT`, Standard: `587`
- `SMTP_SECURE`, Standard: `false`; fuer Port 587 kann `0` verwendet werden
- `SMTP_USER`, Standard: `no-reply@fretux.ch`
- `SMTP_PASS`: SMTP-Passwort
- `MAIL_FROM`, Standard: `Fretux <no-reply@fretux.ch>`
- `BASE_URL`, Standard: `http://localhost:3000`

In Tests ist `quarkus.mailer.mock=true` gesetzt, damit keine echte E-Mail verschickt wird.

Das Schema entspricht dem Klassendiagramm:

- `users`: `id`, `username`, `email`, `newsletter_optin`, `created_at`
- `platforms`: `id`, `name`
- `registrations`: `id`, `user_id`, `platform_id`, `created_at`
- `milestones`: `id`, `title`, `target_count`, `reached`
- `rewards`: `id`, `milestone_id`, `name`, `description`, `image_url`

Beziehungen:

- Ein Nutzer hat `0..*` Registrierungen.
- Eine Registrierung gehoert zu genau einem Nutzer und genau einer Plattform.
- Ein Meilenstein hat `0..*` Belohnungen.
- Beim Erstellen einer Registrierung werden erreichte Meilensteine anhand der Gesamtzahl aktualisiert.

## Endpunkte

### Health Check

```bash
curl http://localhost:3000/api/health
```

### Authentifizierung

`POST /auth/login` prueft registrierte Spieler anhand von Benutzername und E-Mail und erstellt ein signiertes JWT. Fuer Admin-Zugriff kann stattdessen `username` plus `password` aus `ADMIN_USERNAME`/`ADMIN_PASSWORD` verwendet werden.

```bash
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"tuxrunner\",\"email\":\"tux@example.com\"}"
```

`GET /auth/me`, `GET /auth/game-details` und geschuetzte Admin-Endpunkte erwarten ein gueltiges Bearer-Token.

```bash
curl http://localhost:3000/auth/game-details \
  -H "Authorization: Bearer <token>"
```

Die separate Login-Seite ist unter `http://localhost:3000/auth.html` erreichbar. Nach der Registrierung kann man sich dort mit Benutzername und E-Mail anmelden.

Swagger UI ist unter `http://localhost:3000/q/swagger-ui` verfuegbar und fuer Bearer-JWT konfiguriert.

### Benutzer

`POST /users` erstellt einen neuen vorregistrierten Nutzer.

```bash
curl -X POST http://localhost:3000/users \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"tuxrunner\",\"email\":\"tux@example.com\",\"newsletter_optin\":true}"
```

`GET /users/{id}` ruft ein Nutzerprofil ab.

```bash
curl http://localhost:3000/users/1
```

`PUT /users/{id}` aktualisiert beliebige Teilfelder.

```bash
curl -X PUT http://localhost:3000/users/1 \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"tuxrunner2\",\"newsletter_optin\":false}"
```

`DELETE /users/{id}` loescht den Nutzer und dessen Plattform-Registrierungen.

```bash
curl -X DELETE http://localhost:3000/users/1
```

### Plattformen

`GET /platforms` ruft alle Plattformen ab.

```bash
curl http://localhost:3000/platforms
```

`GET /platforms/{id}` ruft eine einzelne Plattform ab.

```bash
curl http://localhost:3000/platforms/1
```

### Registrierungen

`POST /registrations` registriert einen Nutzer fuer eine Plattform. Nach jedem erfolgreichen Request werden erreichte Meilensteine automatisch aktualisiert.

```bash
curl -X POST http://localhost:3000/registrations \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":1,\"platform_id\":1}"
```

`GET /registrations/count` liefert die Gesamtzahl aller Plattform-Registrierungen.

```bash
curl http://localhost:3000/registrations/count
```

`GET /users/{id}/registrations` ruft alle Plattform-Registrierungen eines Nutzers inklusive Plattformname ab.

```bash
curl http://localhost:3000/users/1/registrations
```

`DELETE /registrations/{id}` loescht eine einzelne Plattform-Registrierung.

```bash
curl -X DELETE http://localhost:3000/registrations/1
```

### Meilensteine

`GET /milestones` ruft alle Meilensteine ab. Jeder Eintrag enthaelt `current_count` und `progress`, wobei `progress` bei 100 gedeckelt ist.

```bash
curl http://localhost:3000/milestones
```

`POST /milestones` erstellt einen Meilenstein. Admin-JWT erforderlich.

```bash
curl -X POST http://localhost:3000/milestones \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d "{\"title\":\"10,000 community registrations\",\"target_count\":10000}"
```

`PUT /milestones/{id}` aktualisiert beliebige Teilfelder. Admin-JWT erforderlich.

```bash
curl -X PUT http://localhost:3000/milestones/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d "{\"title\":\"1,000 pre-registrations\",\"reached\":false}"
```

`DELETE /milestones/{id}` loescht einen Meilenstein und dessen Belohnungen. Admin-JWT erforderlich.

```bash
curl -X DELETE http://localhost:3000/milestones/1 \
  -H "Authorization: Bearer <token>"
```

### Belohnungen

`GET /rewards` ruft alle Belohnungen ab.

```bash
curl http://localhost:3000/rewards
```

`GET /milestones/{id}/rewards` ruft Belohnungen eines Meilensteins ab.

```bash
curl http://localhost:3000/milestones/1/rewards
```

`POST /rewards` erstellt eine Belohnung. Admin-JWT erforderlich.

```bash
curl -X POST http://localhost:3000/rewards \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d "{\"milestone_id\":1,\"name\":\"Beta Hat\",\"description\":\"Cosmetic launch hat\",\"image_url\":\"https://example.com/rewards/beta-hat.png\"}"
```

`PUT /rewards/{id}` aktualisiert beliebige Teilfelder. Admin-JWT erforderlich.

```bash
curl -X PUT http://localhost:3000/rewards/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d "{\"description\":\"Updated reward description\"}"
```

`DELETE /rewards/{id}` loescht eine Belohnung. Admin-JWT erforderlich.

```bash
curl -X DELETE http://localhost:3000/rewards/1 \
  -H "Authorization: Bearer <token>"
```

## Statuscodes

- `200`: Erfolgreiche Abfrage, Aktualisierung oder Loeschung
- `201`: Ressource erstellt
- `400`: Ungueltige Eingaben oder nicht existierende Fremdschluessel
- `401`: Fehlendes, ungueltiges oder abgelaufenes JWT bzw. falsche Login-Daten
- `404`: Ressource oder Route nicht gefunden
- `409`: Konflikt, z. B. doppelte E-Mail, doppelter Benutzername oder doppelte Nutzer-Plattform-Kombination
