const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function isNonEmptyString(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function isValidEmail(value) {
  return isNonEmptyString(value) && EMAIL_PATTERN.test(value.trim());
}

function isBoolean(value) {
  return typeof value === "boolean";
}

function isPositiveInteger(value) {
  return Number.isInteger(value) && value > 0;
}

function parseId(value) {
  const id = Number(value);
  return Number.isInteger(id) && id > 0 ? id : null;
}

function normalizeString(value) {
  return typeof value === "string" ? value.trim() : value;
}

module.exports = {
  isNonEmptyString,
  isValidEmail,
  isBoolean,
  isPositiveInteger,
  parseId,
  normalizeString
};
