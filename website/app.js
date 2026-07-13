"use strict";

const config = window.SERVERCORE_CONFIG ?? { apiBaseUrl: "http://localhost:8000" };
const apiBaseUrl = String(config.apiBaseUrl).replace(/\/$/, "");

const leaderboardBody = document.querySelector("#leaderboard-body");
const leaderboardError = document.querySelector("#leaderboard-error");
const refreshButton = document.querySelector("#refresh-leaderboard");
const statusIndicator = document.querySelector("#status-indicator");
const statusLabel = document.querySelector("#status-label");
const statusDescription = document.querySelector("#status-description");

function escapeText(value) {
  return String(value ?? "");
}

function formatPercent(value) {
  const number = Number(value);
  return Number.isFinite(number) ? `${(number * 100).toFixed(1)}%` : "0.0%";
}

function setStatus(state, label, description) {
  statusIndicator.dataset.state = state;
  statusLabel.textContent = label;
  statusDescription.textContent = description;
}

async function fetchJson(path) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { Accept: "application/json" }
  });
  if (!response.ok) {
    throw new Error(`Request failed with HTTP ${response.status}`);
  }
  return response.json();
}

async function loadHealth() {
  try {
    const health = await fetchJson("/health");
    setStatus(
      "online",
      "Online",
      `API version ${escapeText(health.version)} is responding normally.`
    );
  } catch (error) {
    setStatus("offline", "Offline", "The public API cannot be reached right now.");
    console.error("Unable to load ServerCore health", error);
  }
}

function renderLeaderboard(entries) {
  leaderboardBody.replaceChildren();
  if (!Array.isArray(entries) || entries.length === 0) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 5;
    cell.className = "empty";
    cell.textContent = "No ranked matches have been recorded yet.";
    row.append(cell);
    leaderboardBody.append(row);
    return;
  }

  for (const [index, entry] of entries.entries()) {
    const row = document.createElement("tr");
    const values = [
      `#${Number(entry.rank ?? index + 1)}`,
      escapeText(entry.username || "Unknown"),
      Number(entry.rating ?? 0).toLocaleString(),
      `${Number(entry.wins ?? 0)}–${Number(entry.losses ?? 0)}`,
      formatPercent(entry.win_rate)
    ];
    values.forEach((value, column) => {
      const cell = document.createElement("td");
      cell.textContent = value;
      if (column === 1) {
        cell.className = "player-name";
      }
      row.append(cell);
    });
    leaderboardBody.append(row);
  }
}

async function loadLeaderboard() {
  refreshButton.disabled = true;
  leaderboardError.hidden = true;
  try {
    const entries = await fetchJson("/leaderboard?limit=25");
    renderLeaderboard(entries);
  } catch (error) {
    renderLeaderboard([]);
    leaderboardError.textContent = "The leaderboard is temporarily unavailable.";
    leaderboardError.hidden = false;
    console.error("Unable to load leaderboard", error);
  } finally {
    refreshButton.disabled = false;
  }
}

refreshButton.addEventListener("click", loadLeaderboard);
void Promise.all([loadHealth(), loadLeaderboard()]);
