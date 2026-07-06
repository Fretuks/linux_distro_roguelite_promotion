function sendError(res, status, message) {
  return res.status(status).json({ error: message });
}

function mapSqliteError(error, conflictMessage) {
  if (error && error.code === "SQLITE_CONSTRAINT_UNIQUE") {
    return { status: 409, message: conflictMessage };
  }

  if (error && error.code === "SQLITE_CONSTRAINT_FOREIGNKEY") {
    return { status: 400, message: "Referenced resource does not exist." };
  }

  return null;
}

module.exports = {
  sendError,
  mapSqliteError
};
