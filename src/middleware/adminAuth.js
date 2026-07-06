function requireAdminKey(req, res, next) {
  const expectedKey = process.env.ADMIN_KEY;
  const providedKey = req.get("X-Admin-Key");

  if (!expectedKey || providedKey !== expectedKey) {
    return res.status(401).json({ error: "Missing or invalid admin key." });
  }

  next();
}

module.exports = requireAdminKey;
