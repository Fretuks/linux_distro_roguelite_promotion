const platformList = document.querySelector("#platform-list");
const registerForm = document.querySelector("#register-form");
const formMessage = document.querySelector("#form-message");
const registrationCount = document.querySelector("#registration-count");
const platformCount = document.querySelector("#platform-count");
const rewardCount = document.querySelector("#reward-count");

function setMessage(message, isError = false) {
  formMessage.textContent = message;
  formMessage.classList.toggle("error", isError);
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

function renderPlatforms(platforms) {
  platformList.innerHTML = "";
  platforms.forEach((platform) => {
    const label = document.createElement("label");
    label.className = "platform-option";
    label.innerHTML = `
      <input type="checkbox" name="platforms" value="${platform.id}">
      <span>${platform.name}</span>
    `;
    platformList.appendChild(label);
  });
  platformCount.textContent = platforms.length;
}

async function refreshStats() {
  const [count, rewards] = await Promise.all([
    requestJson("/registrations/count"),
    requestJson("/rewards")
  ]);
  registrationCount.textContent = count.count;
  rewardCount.textContent = rewards.length;
}

async function loadPageData() {
  try {
    const platforms = await requestJson("/platforms");
    renderPlatforms(platforms);
    await refreshStats();
  } catch (error) {
    setMessage(error.message, true);
  }
}

registerForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setMessage("Opening shell account...");

  const formData = new FormData(registerForm);
  const selectedPlatforms = formData.getAll("platforms").map(Number);

  if (selectedPlatforms.length === 0) {
    setMessage("Choose at least one platform.", true);
    return;
  }

  try {
    const user = await requestJson("/users", {
      method: "POST",
      body: JSON.stringify({
        username: formData.get("username"),
        email: formData.get("email"),
        newsletter_optin: formData.get("newsletter_optin") === "on"
      })
    });

    await Promise.all(
      selectedPlatforms.map((platformId) =>
        requestJson("/registrations", {
          method: "POST",
          body: JSON.stringify({
            user_id: user.id,
            platform_id: platformId
          })
        })
      )
    );

    registerForm.reset();
    setMessage("Pre-registration complete. Use Auth to unlock private game intel.");
    await refreshStats();
  } catch (error) {
    setMessage(error.message, true);
  }
});

loadPageData();
