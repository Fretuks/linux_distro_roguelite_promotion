# Linux Distro Roguelite Promotion API

REST-API fuer eine Pre-Register-Page eines kostenlosen Games. Spieler registrieren sich mit Benutzername und E-Mail, waehlen Plattformen aus und schalten ueber Community-Meilensteine Belohnungen frei.

## Installation

```bash
npm install
```

## Start

```bash
ADMIN_KEY=secret npm start
```

Unter PowerShell:

```powershell
$env:ADMIN_KEY = "secret"
npm start
```

Der Server laeuft standardmaessig auf Port `3000`. Mit `PORT` kann der Port ueberschrieben werden.

```powershell
$env:PORT = "4000"
$env:ADMIN_KEY = "secret"
npm start
```

Beim ersten Start wird `database.sqlite` erstellt und automatisch mit diesen Daten befuellt:

- Plattformen: PC, PlayStation, Xbox, Nintendo Switch, Mobile
- Meilensteine: 1,000 und 5,000 Community-Registrierungen
- Je eine Beispiel-Belohnung pro Meilenstein

Alle Fehler haben das Format:

```json
{ "error": "beschreibende Meldung" }
```

## Endpunkte

### Health Check

```bash
curl http://localhost:3000/
```

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

`POST /milestones` erstellt einen Meilenstein. Admin-Key erforderlich.

```bash
curl -X POST http://localhost:3000/milestones \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: secret" \
  -d "{\"title\":\"10,000 community registrations\",\"target_count\":10000}"
```

`PUT /milestones/{id}` aktualisiert beliebige Teilfelder. Admin-Key erforderlich.

```bash
curl -X PUT http://localhost:3000/milestones/1 \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: secret" \
  -d "{\"title\":\"1,000 pre-registrations\",\"reached\":false}"
```

`DELETE /milestones/{id}` loescht einen Meilenstein und dessen Belohnungen. Admin-Key erforderlich.

```bash
curl -X DELETE http://localhost:3000/milestones/1 \
  -H "X-Admin-Key: secret"
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

`POST /rewards` erstellt eine Belohnung. Admin-Key erforderlich.

```bash
curl -X POST http://localhost:3000/rewards \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: secret" \
  -d "{\"milestone_id\":1,\"name\":\"Beta Hat\",\"description\":\"Cosmetic launch hat\",\"image_url\":\"https://example.com/rewards/beta-hat.png\"}"
```

`PUT /rewards/{id}` aktualisiert beliebige Teilfelder. Admin-Key erforderlich.

```bash
curl -X PUT http://localhost:3000/rewards/1 \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: secret" \
  -d "{\"description\":\"Updated reward description\"}"
```

`DELETE /rewards/{id}` loescht eine Belohnung. Admin-Key erforderlich.

```bash
curl -X DELETE http://localhost:3000/rewards/1 \
  -H "X-Admin-Key: secret"
```

## Statuscodes

- `200`: Erfolgreiche Abfrage, Aktualisierung oder Loeschung
- `201`: Ressource erstellt
- `400`: Ungueltige Eingaben oder nicht existierende Fremdschluessel
- `401`: Fehlender oder falscher Admin-Key
- `404`: Ressource oder Route nicht gefunden
- `409`: Konflikt, z. B. doppelte E-Mail, doppelter Benutzername oder doppelte Nutzer-Plattform-Kombination
