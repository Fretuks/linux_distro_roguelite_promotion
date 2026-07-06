const express = require("express");
const { db } = require("../db");
const { sendError } = require("../utils/errors");
const { parseId } = require("../utils/validation");

const router = express.Router();

router.get("/", (req, res) => {
  const platforms = db.prepare("SELECT * FROM platforms ORDER BY id").all();
  return res.json(platforms);
});

router.get("/:id", (req, res) => {
  const id = parseId(req.params.id);
  if (!id) return sendError(res, 400, "Invalid platform id.");

  const platform = db.prepare("SELECT * FROM platforms WHERE id = ?").get(id);
  if (!platform) return sendError(res, 404, "Platform not found.");

  return res.json(platform);
});

module.exports = router;
