const authForm = document.querySelector("#auth-form");
const authMessage = document.querySelector("#auth-message");
const authSession = document.querySelector("#auth-session");
const authLogout = document.querySelector("#auth-logout");
const privateIntel = document.querySelector("#private-intel");
const tokenStorageKey = "kernel-panic-player-token";

function setAuthMessage(message, isError = false) {
  authMessage.textContent = message;
  authMessage.classList.toggle("error", isError);
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || "Request failed.");
  }

  return data;
}

function authHeaders() {
  const token = localStorage.getItem(tokenStorageKey);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function renderAuthSession(username, groups = []) {
  const groupText = groups.length > 0 ? groups.join(", ") : "none";
  authSession.innerHTML = `
    <span class="terminal-label">session</span>
    <strong>${username || "not authenticated"}</strong>
    <span>role: ${groupText}</span>
  `;
}

function renderLockedIntel() {
  privateIntel.innerHTML = `
    <article>
      <h3>Access required</h3>
      <p>Log in with your registered shell account to reveal private build notes.</p>
    </article>
  `;
}

function renderGameDetails(details) {
  privateIntel.innerHTML = `
    <article>
      <h3>Starting distros</h3>
      <p>${details.available_starting_distros.join(", ")}</p>
    </article>
    <article>
      <h3>Language pool</h3>
      <p>${details.starting_languages.join(", ")}</p>
    </article>
    <article>
      <h3>Hidden enemies</h3>
      <p>${details.hidden_enemies.join(", ")}</p>
    </article>
    <article>
      <h3>Economy</h3>
      <p>${Object.entries(details.economy).map(([name, text]) => `${name}: ${text}`).join(" / ")}</p>
    </article>
    <article class="wide">
      <h3>${details.title}</h3>
      <p>${details.developer_note}</p>
    </article>
  `;
}

async function refreshAuthSession() {
  const token = localStorage.getItem(tokenStorageKey);
  if (!token) {
    renderAuthSession(null);
    renderLockedIntel();
    return;
  }

  try {
    const [session, details] = await Promise.all([
      requestJson("/auth/me", { headers: authHeaders() }),
      requestJson("/auth/game-details", { headers: authHeaders() })
    ]);
    renderAuthSession(session.username, session.groups || []);
    renderGameDetails(details);
  } catch (error) {
    localStorage.removeItem(tokenStorageKey);
    renderAuthSession(null);
    renderLockedIntel();
  }
}

authForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setAuthMessage("Checking registered shell account...");

  const formData = new FormData(authForm);
  try {
    const result = await requestJson("/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username: formData.get("username"),
        email: formData.get("email")
      })
    });
    localStorage.setItem(tokenStorageKey, result.access_token);
    authForm.reset();
    setAuthMessage("Authenticated. Private game intel unlocked.");
    await refreshAuthSession();
  } catch (error) {
    localStorage.removeItem(tokenStorageKey);
    renderAuthSession(null);
    renderLockedIntel();
    setAuthMessage(error.message, true);
  }
});

authLogout.addEventListener("click", () => {
  localStorage.removeItem(tokenStorageKey);
  renderAuthSession(null);
  renderLockedIntel();
  setAuthMessage("Session cleared.");
});

refreshAuthSession();
