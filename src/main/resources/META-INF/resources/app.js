const milestoneList = document.querySelector("#milestone-list");
const registrationCount = document.querySelector("#registration-count");
const platformCount = document.querySelector("#platform-count");
const rewardCount = document.querySelector("#reward-count");

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

function renderMilestones(milestones) {
  milestoneList.innerHTML = "";
  milestones.forEach((milestone) => {
    const card = document.createElement("article");
    card.className = "milestone-card";
    card.innerHTML = `
      <h3>${milestone.title}</h3>
      <div class="progress-track" aria-label="${milestone.progress}% progress">
        <div class="progress-fill" style="width: ${milestone.progress}%"></div>
      </div>
      <div class="milestone-meta">
        <span>${milestone.current_count} / ${milestone.target_count}</span>
        <span class="${milestone.reached ? "reached" : ""}">${milestone.reached ? "unlocked" : `${milestone.progress}%`}</span>
      </div>
    `;
    milestoneList.appendChild(card);
  });
}

async function refreshStats() {
  const [count, milestones, rewards] = await Promise.all([
    requestJson("/registrations/count"),
    requestJson("/milestones"),
    requestJson("/rewards")
  ]);

  registrationCount.textContent = count.count;
  rewardCount.textContent = rewards.length;
  renderMilestones(milestones);
}

async function loadPageData() {
  try {
    const platforms = await requestJson("/platforms");
    platformCount.textContent = platforms.length;
    await refreshStats();
  } catch (error) {
    milestoneList.innerHTML = `<article class="milestone-card"><h3>Connection failed</h3><p>${error.message}</p></article>`;
  }
}

loadPageData();
