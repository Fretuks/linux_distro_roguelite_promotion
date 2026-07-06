const express = require("express");
const { db } = require("../db");
const requireAdminKey = require("../middleware/adminAuth");
const { sendError, mapSqliteError } = require("../utils/errors");
const { isNonEmptyString, isPositiveInteger, normalizeString, parseId } = require("../utils/validation");

const router = express.Router();

function validateOptionalString(value, fieldName, res) {
  if (value !== null && value !== undefined && typeof value !== "string") {
    sendError(res, 400, `${fieldName} must be a string or null.`);
    return false;
  }
  return true;
}

router.get("/", (req, res) => {
  const rewards = db.prepare("SELECT * FROM rewards ORDER BY id").all();
  return res.json(rewards);
});

router.get("/milestone/:milestoneId", (req, res) => {
  const milestoneId = parseId(req.params.milestoneId);
  if (!milestoneId) return sendError(res, 400, "Invalid milestone id.");

  const milestone = db.prepare("SELECT id FROM milestones WHERE id = ?").get(milestoneId);
  if (!milestone) return sendError(res, 404, "Milestone not found.");

  const rewards = db
    .prepare("SELECT * FROM rewards WHERE milestone_id = ? ORDER BY id")
    .all(milestoneId);
  return res.json(rewards);
});

router.post("/", requireAdminKey, (req, res) => {
  const name = normalizeString(req.body.name);
  const description = req.body.description == null ? null : normalizeString(req.body.description);
  const imageUrl = req.body.image_url == null ? null : normalizeString(req.body.image_url);
  const milestoneId = req.body.milestone_id;

  if (!isNonEmptyString(name)) return sendError(res, 400, "name is required.");
  if (!validateOptionalString(description, "description", res)) return undefined;
  if (!validateOptionalString(imageUrl, "image_url", res)) return undefined;
  if (!isPositiveInteger(milestoneId)) {
    return sendError(res, 400, "milestone_id must be a positive integer.");
  }

  const milestone = db.prepare("SELECT id FROM milestones WHERE id = ?").get(milestoneId);
  if (!milestone) return sendError(res, 400, "Milestone does not exist.");

  try {
    const result = db
      .prepare(
        "INSERT INTO rewards (milestone_id, name, description, image_url) VALUES (?, ?, ?, ?)"
      )
      .run(milestoneId, name, description, imageUrl);
    const reward = db.prepare("SELECT * FROM rewards WHERE id = ?").get(result.lastInsertRowid);
    return res.status(201).json(reward);
  } catch (error) {
    const mapped = mapSqliteError(error, "Reward conflict.");
    if (mapped) return sendError(res, mapped.status, mapped.message);
    throw error;
  }
});

router.put("/:id", requireAdminKey, (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid reward id.");

  const existing = db.prepare("SELECT * FROM rewards WHERE id = ?").get(id);
  if (!existing) return sendError(res, 404, "Reward not found.");

  const updates = [];
  const values = [];

  if (Object.prototype.hasOwnProperty.call(req.body, "name")) {
    const name = normalizeString(req.body.name);
    if (!isNonEmptyString(name)) return sendError(res, 400, "name must not be empty.");
    updates.push("name = ?");
    values.push(name);
  }

  if (Object.prototype.hasOwnProperty.call(req.body, "description")) {
    const description = req.body.description == null ? null : normalizeString(req.body.description);
    if (!validateOptionalString(description, "description", res)) return undefined;
    updates.push("description = ?");
    values.push(description);
  }

  if (Object.prototype.hasOwnProperty.call(req.body, "image_url")) {
    const imageUrl = req.body.image_url == null ? null : normalizeString(req.body.image_url);
    if (!validateOptionalString(imageUrl, "image_url", res)) return undefined;
    updates.push("image_url = ?");
    values.push(imageUrl);
  }

  if (Object.prototype.hasOwnProperty.call(req.body, "milestone_id")) {
    if (!isPositiveInteger(req.body.milestone_id)) {
      return sendError(res, 400, "milestone_id must be a positive integer.");
    }
    const milestone = db.prepare("SELECT id FROM milestones WHERE id = ?").get(req.body.milestone_id);
    if (!milestone) return sendError(res, 400, "Milestone does not exist.");
    updates.push("milestone_id = ?");
    values.push(req.body.milestone_id);
  }

  if (updates.length === 0) {
    return sendError(res, 400, "At least one updatable field is required.");
  }

  values.push(id);
  db.prepare(`UPDATE rewards SET ${updates.join(", ")} WHERE id = ?`).run(...values);

  const reward = db.prepare("SELECT * FROM rewards WHERE id = ?").get(id);
  return res.json(reward);
});

router.delete("/:id", requireAdminKey, (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid reward id.");

  const result = db.prepare("DELETE FROM rewards WHERE id = ?").run(id);
  if (result.changes === 0) return sendError(res, 404, "Reward not found.");

  return res.json({ message: "Reward deleted." });
});

module.exports = router;
