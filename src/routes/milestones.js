const express = require("express");
const { db, boolToInt, getRegistrationCount } = require("../db");
const requireAdminKey = require("../middleware/adminAuth");
const { sendError } = require("../utils/errors");
const {
  isBoolean,
  isNonEmptyString,
  isPositiveInteger,
  normalizeString,
  parseId
} = require("../utils/validation");

const router = express.Router();

function serializeMilestone(milestone, currentCount) {
  return {
    ...milestone,
    reached: Boolean(milestone.reached),
    current_count: currentCount,
    progress: Math.min(100, Math.round((currentCount / milestone.target_count) * 100))
  };
}

router.get("/", (req, res) => {
  const currentCount = getRegistrationCount();
  const milestones = db.prepare("SELECT * FROM milestones ORDER BY target_count, id").all();
  return res.json(milestones.map((milestone) => serializeMilestone(milestone, currentCount)));
});

router.get("/:id/rewards", (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid milestone id.");

  const milestone = db.prepare("SELECT id FROM milestones WHERE id = ?").get(id);
  if (!milestone) return sendError(res, 404, "Milestone not found.");

  const rewards = db.prepare("SELECT * FROM rewards WHERE milestone_id = ? ORDER BY id").all(id);
  return res.json(rewards);
});

router.post("/", requireAdminKey, (req, res) => {
  const title = normalizeString(req.body.title);
  const targetCount = req.body.target_count;

  if (!isNonEmptyString(title)) return sendError(res, 400, "title is required.");
  if (!isPositiveInteger(targetCount)) {
    return sendError(res, 400, "target_count must be a positive integer.");
  }

  const currentCount = getRegistrationCount();
  const result = db
    .prepare("INSERT INTO milestones (title, target_count, reached) VALUES (?, ?, ?)")
    .run(title, targetCount, boolToInt(currentCount >= targetCount));
  const milestone = db.prepare("SELECT * FROM milestones WHERE id = ?").get(result.lastInsertRowid);

  return res.status(201).json(serializeMilestone(milestone, currentCount));
});

router.put("/:id", requireAdminKey, (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid milestone id.");

  const existing = db.prepare("SELECT * FROM milestones WHERE id = ?").get(id);
  if (!existing) return sendError(res, 404, "Milestone not found.");

  const updates = [];
  const values = [];

  if (Object.prototype.hasOwnProperty.call(req.body, "title")) {
    const title = normalizeString(req.body.title);
    if (!isNonEmptyString(title)) return sendError(res, 400, "title must not be empty.");
    updates.push("title = ?");
    values.push(title);
  }

  if (Object.prototype.hasOwnProperty.call(req.body, "target_count")) {
    if (!isPositiveInteger(req.body.target_count)) {
      return sendError(res, 400, "target_count must be a positive integer.");
    }
    updates.push("target_count = ?");
    values.push(req.body.target_count);
  }

  if (Object.prototype.hasOwnProperty.call(req.body, "reached")) {
    if (!isBoolean(req.body.reached)) return sendError(res, 400, "reached must be a boolean.");
    updates.push("reached = ?");
    values.push(boolToInt(req.body.reached));
  }

  if (updates.length === 0) {
    return sendError(res, 400, "At least one updatable field is required.");
  }

  values.push(id);
  db.prepare(`UPDATE milestones SET ${updates.join(", ")} WHERE id = ?`).run(...values);

  const currentCount = getRegistrationCount();
  const milestone = db.prepare("SELECT * FROM milestones WHERE id = ?").get(id);
  return res.json(serializeMilestone(milestone, currentCount));
});

router.delete("/:id", requireAdminKey, (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid milestone id.");

  const result = db.prepare("DELETE FROM milestones WHERE id = ?").run(id);
  if (result.changes === 0) return sendError(res, 404, "Milestone not found.");

  return res.json({ message: "Milestone deleted." });
});

module.exports = router;
