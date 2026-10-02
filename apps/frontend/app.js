const config = window.APP_CONFIG || { env: "unknown", pollIntervalMs: 1000 };
const USERS = ["user-1", "user-2", "user-3"];
const TERMINAL = new Set(["COMPLETED", "REJECTED", "FAILED"]);

// 이 브라우저가 요청한 결제만 추적한다 (id -> payment)
const tracked = new Map();

document.getElementById("env").textContent = `env: ${config.env}`;

async function api(path, options = {}) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const servedBy = res.headers.get("X-Served-By") || "-";
  const body = res.status === 204 || res.status === 404 ? null : await res.json().catch(() => null);
  return { status: res.status, body, servedBy };
}

async function requestPayment(form) {
  const data = Object.fromEntries(new FormData(form));
  data.amount = Number(data.amount);
  const { status, body, servedBy } = await api("/api/payments", {
    method: "POST",
    body: JSON.stringify(data),
  });
  if (status !== 202) {
    alert(`요청 실패: HTTP ${status}`);
    return;
  }
  tracked.set(body.id, { ...body, servedBy });
  render();
}

async function pollPayments() {
  const pending = [...tracked.values()].filter((p) => !TERMINAL.has(p.status));
  await Promise.all(
    pending.map(async (p) => {
      const { status, body, servedBy } = await api(`/api/payments/${p.id}`);
      // 404 = 요청을 받지 않은 다른 replica가 응답했고, 아직 결과 이벤트를 못 받은 상태 → 계속 ACCEPTED
      if (status === 200) {
        tracked.set(p.id, { ...body, servedBy });
      } else {
        tracked.set(p.id, { ...p, servedBy: `${servedBy} (404)` });
      }
    })
  );
  render();
}

async function refreshBalances() {
  const results = await Promise.all(USERS.map((u) => api(`/api/balances/${u}`)));
  document.getElementById("balances").innerHTML = results
    .map((r, i) => `<div>${USERS[i]}<b>${r.body ? r.body.balance.toLocaleString() : "-"}</b></div>`)
    .join("");
}

function render() {
  const rows = [...tracked.values()]
    .sort((a, b) => (a.requestedAt < b.requestedAt ? 1 : -1))
    .slice(0, 30)
    .map(
      (p) => `<tr>
        <td class="id">${p.id.slice(0, 8)}</td>
        <td>${p.userId}</td>
        <td>${p.type}</td>
        <td>${p.amount.toLocaleString()}</td>
        <td class="status ${p.status}">${p.status}${p.reason ? ` · ${p.reason}` : ""}</td>
        <td>${p.balanceAfter != null ? p.balanceAfter.toLocaleString() : "-"}</td>
        <td>${p.servedBy}</td>
      </tr>`
    )
    .join("");
  document.getElementById("payments").innerHTML = rows;
}

document.getElementById("payment-form").addEventListener("submit", (e) => {
  e.preventDefault();
  requestPayment(e.target);
});

document.getElementById("burst").addEventListener("click", () => {
  const form = document.getElementById("payment-form");
  for (let i = 0; i < 10; i++) requestPayment(form);
});

setInterval(pollPayments, config.pollIntervalMs);
setInterval(refreshBalances, 2000);
refreshBalances();
