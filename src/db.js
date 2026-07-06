const path = require("path");
const Database = require("better-sqlite3");

const dbPath = path.join(__dirname, "..", "database.sqlite");
const db = new Database(dbPath);

db.pragma("foreign_keys = ON");

function boolToInt(value) {
  return value ? 1 : 0;
}

function createSchema() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      username TEXT UNIQUE NOT NULL,
      email TEXT UNIQUE NOT NULL,
      newsletter_optin INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS platforms (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT UNIQUE NOT NULL
    );

    CREATE TABLE IF NOT EXISTS registrations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id INTEGER NOT NULL,
      platform_id INTEGER NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
      FOREIGN KEY (platform_id) REFERENCES platforms(id),
      UNIQUE (user_id, platform_id)
    );

    CREATE TABLE IF NOT EXISTS milestones (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      title TEXT NOT NULL,
      target_count INTEGER NOT NULL,
      reached INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS rewards (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      milestone_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      description TEXT,
      image_url TEXT,
      FOREIGN KEY (milestone_id) REFERENCES milestones(id) ON DELETE CASCADE
    );
  `);
}

function seedDatabase() {
  const platforms = ["PC", "PlayStation", "Xbox", "Nintendo Switch", "Mobile"];
  const insertPlatform = db.prepare("INSERT OR IGNORE INTO platforms (name) VALUES (?)");
  platforms.forEach((platform) => insertPlatform.run(platform));

  // Seed example milestones only when the table is empty to avoid duplicate demo data.
  const milestoneCount = db.prepare("SELECT COUNT(*) AS count FROM milestones").get().count;
  if (milestoneCount === 0) {
    const insertMilestone = db.prepare(
      "INSERT INTO milestones (title, target_count, reached) VALUES (?, ?, 0)"
    );
    const insertReward = db.prepare(
      "INSERT INTO rewards (milestone_id, name, description, image_url) VALUES (?, ?, ?, ?)"
    );

    const first = insertMilestone.run("1,000 community registrations", 1000);
    insertReward.run(
      first.lastInsertRowid,
      "Founder Banner",
      "Exclusive in-game profile banner for early supporters.",
      "https://example.com/rewards/founder-banner.png"
    );

    const second = insertMilestone.run("5,000 community registrations", 5000);
    insertReward.run(
      second.lastInsertRowid,
      "Launch Loot Crate",
      "Free cosmetic crate unlocked for all players at launch.",
      "https://example.com/rewards/launch-loot-crate.png"
    );
  }
}

function initializeDatabase() {
  createSchema();
  seedDatabase();
}

function getRegistrationCount() {
  return db.prepare("SELECT COUNT(*) AS count FROM registrations").get().count;
}

function updateReachedMilestones() {
  const currentCount = getRegistrationCount();
  db.prepare(
    "UPDATE milestones SET reached = 1 WHERE reached = 0 AND target_count <= ?"
  ).run(currentCount);
}

module.exports = {
  db,
  initializeDatabase,
  getRegistrationCount,
  updateReachedMilestones,
  boolToInt
};
