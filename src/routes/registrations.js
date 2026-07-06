const express = require("express");
const { db, getRegistrationCount, updateReachedMilestones } = require("../db");
const { sendError, mapSqliteError } = require("../utils/errors");
const { isPositiveInteger, parseId } = require("../utils/validation");

const router = express.Router();

router.post("/", (req, res) => {
  const userId = req.body.user_id;
  const platformId = req.body.platform_id;

  if (!isPositiveInteger(userId)) return sendError(res, 400, "user_id is required.");
  if (!isPositiveInteger(platformId)) return sendError(res, 400, "platform_id is required.");

  const user = db.prepare("SELECT id FROM users WHERE id = ?").get(userId);
  if (!user) return sendError(res, 400, "User does not exist.");

  const platform = db.prepare("SELECT id FROM platforms WHERE id = ?").get(platformId);
  if (!platform) return sendError(res, 400, "Platform does not exist.");

  try {
    const result = db
      .prepare("INSERT INTO registrations (user_id, platform_id) VALUES (?, ?)")
      .run(userId, platformId);

    // Every successful registration can unlock one or more community milestones.
    updateReachedMilestones();

    const registration = db
      .prepare("SELECT * FROM registrations WHERE id = ?")
      .get(result.lastInsertRowid);
    return res.status(201).json(registration);
  } catch (error) {
    const mapped = mapSqliteError(error, "User is already registered for this platform.");
    if (mapped) return sendError(res, mapped.status, mapped.message);
    throw error;
  }
});

router.get("/count", (req, res) => {
  return res.json({ count: getRegistrationCount() });
});

router.delete("/:id", (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid registration id.");

  const result = db.prepare("DELETE FROM registrations WHERE id = ?").run(id);
  if (result.changes === 0) return sendError(res, 404, "Registration not found.");

  return res.json({ message: "Registration deleted." });
});

module.exports = router;
