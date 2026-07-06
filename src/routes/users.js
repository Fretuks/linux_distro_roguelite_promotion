const express = require("express");
const { db, boolToInt } = require("../db");
const { sendError, mapSqliteError } = require("../utils/errors");
const {
  isBoolean,
  isNonEmptyString,
  isValidEmail,
  normalizeString,
  parseId
} = require("../utils/validation");

const router = express.Router();

function serializeUser(user) {
  return {
    ...user,
    newsletter_optin: Boolean(user.newsletter_optin)
  };
}

router.post("/", (req, res) => {
  const username = normalizeString(req.body.username);
  const email = normalizeString(req.body.email);
  const newsletterOptin = req.body.newsletter_optin ?? false;

  if (!isNonEmptyString(username)) {
    return sendError(res, 400, "username is required.");
  }

  if (!isValidEmail(email)) {
    return sendError(res, 400, "A valid email is required.");
  }

  if (!isBoolean(newsletterOptin)) {
    return sendError(res, 400, "newsletter_optin must be a boolean.");
  }

  try {
    const result = db
      .prepare("INSERT INTO users (username, email, newsletter_optin) VALUES (?, ?, ?)")
      .run(username, email, boolToInt(newsletterOptin));
    const user = db.prepare("SELECT * FROM users WHERE id = ?").get(result.lastInsertRowid);
    return res.status(201).json(serializeUser(user));
  } catch (error) {
    const mapped = mapSqliteError(error, "username or email already exists.");
    if (mapped) return sendError(res, mapped.status, mapped.message);
    throw error;
  }
});

router.get("/:id", (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid user id.");

  const user = db.prepare("SELECT * FROM users WHERE id = ?").get(id);
  if (!user) return sendError(res, 404, "User not found.");

  return res.json(serializeUser(user));
});

router.put("/:id", (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid user id.");

  const existing = db.prepare("SELECT * FROM users WHERE id = ?").get(id);
  if (!existing) return sendError(res, 404, "User not found.");

  const updates = [];
  const values = [];

  if (Object.prototype.hasOwnProperty.call(req.body, "username")) {
    const username = normalizeString(req.body.username);
    if (!isNonEmptyString(username)) return sendError(res, 400, "username must not be empty.");
    updates.push("username = ?");
    values.push(username);
  }

  if (Object.prototype.hasOwnProperty.call(req.body, "email")) {
    const email = normalizeString(req.body.email);
    if (!isValidEmail(email)) return sendError(res, 400, "A valid email is required.");
    updates.push("email = ?");
    values.push(email);
  }

  if (Object.prototype.hasOwnProperty.call(req.body, "newsletter_optin")) {
    if (!isBoolean(req.body.newsletter_optin)) {
      return sendError(res, 400, "newsletter_optin must be a boolean.");
    }
    updates.push("newsletter_optin = ?");
    values.push(boolToInt(req.body.newsletter_optin));
  }

  if (updates.length === 0) {
    return sendError(res, 400, "At least one updatable field is required.");
  }

  try {
    values.push(id);
    db.prepare(`UPDATE users SET ${updates.join(", ")} WHERE id = ?`).run(...values);
    const user = db.prepare("SELECT * FROM users WHERE id = ?").get(id);
    return res.json(serializeUser(user));
  } catch (error) {
    const mapped = mapSqliteError(error, "username or email already exists.");
    if (mapped) return sendError(res, mapped.status, mapped.message);
    throw error;
  }
});

router.delete("/:id", (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid user id.");

  const result = db.prepare("DELETE FROM users WHERE id = ?").run(id);
  if (result.changes === 0) return sendError(res, 404, "User not found.");

  return res.json({ message: "User deleted." });
});

router.get("/:id/registrations", (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid user id.");

  const user = db.prepare("SELECT id FROM users WHERE id = ?").get(id);
  if (!user) return sendError(res, 404, "User not found.");

  const registrations = db
    .prepare(
      `SELECT registrations.id, registrations.user_id, registrations.platform_id,
              platforms.name AS platform_name, registrations.created_at
       FROM registrations
       JOIN platforms ON platforms.id = registrations.platform_id
       WHERE registrations.user_id = ?
       ORDER BY registrations.id`
    )
    .all(id);

  return res.json(registrations);
});

module.exports = router;
