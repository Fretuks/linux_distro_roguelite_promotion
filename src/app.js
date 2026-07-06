const express = require("express");
const cors = require("cors");
const { initializeDatabase } = require("./db");

const usersRouter = require("./routes/users");
const platformsRouter = require("./routes/platforms");
const registrationsRouter = require("./routes/registrations");
const milestonesRouter = require("./routes/milestones");
const rewardsRouter = require("./routes/rewards");

initializeDatabase();

const app = express();

app.use(cors());
app.use(express.json());

app.get("/", (req, res) => {
  res.json({ message: "Pre-registration API is running." });
});

app.use("/users", usersRouter);
app.use("/platforms", platformsRouter);
app.use("/registrations", registrationsRouter);
app.use("/milestones", milestonesRouter);
app.use("/rewards", rewardsRouter);

app.use((req, res) => {
  res.status(404).json({ error: "Route not found." });
});

app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && err.status === 400 && "body" in err) {
    return res.status(400).json({ error: "Invalid JSON body." });
  }

  console.error(err);
  res.status(500).json({ error: "Internal server error." });
});

const port = process.env.PORT || 3000;

app.listen(port, () => {
  console.log(`Pre-registration API listening on port ${port}`);
});
