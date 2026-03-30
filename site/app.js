const sample = [
  { ts: "2026-03-30T12:00:00Z", channel: "system", event: "agent_start", agent: "agent-parser", repo: "parser-lib", summary: "Agent booted with session-seeded ontology" },
  { ts: "2026-03-30T12:00:04Z", channel: "planning", event: "plan_generated", summary: "Investigate tokenizer boundary conditions", confidence: 0.72 },
  { ts: "2026-03-30T12:00:08Z", channel: "execution", event: "apply_patch", summary: "Adjust loop boundary condition" },
  { ts: "2026-03-30T12:00:11Z", channel: "evaluation", event: "failure_analysis", summary: "unicode test still failing", confidence: 0.66 },
  { ts: "2026-03-30T12:00:18Z", channel: "test", event: "run", result: "pass", details: { passed: 49, failed: 0 } },
  { ts: "2026-03-30T12:00:20Z", channel: "metrics", event: "usage", details: { cost_usd: 0.42, loops: 2 } }
];

function derive(events) {
  return {
    trajectory: events
      .filter((event) => event.channel === "execution")
      .map((event) => event.summary || event.event),
    confidence_curve: events
      .map((event) => event.confidence)
      .filter((value) => typeof value === "number"),
    channels: events.reduce((acc, event) => {
      acc[event.channel] = (acc[event.channel] || 0) + 1;
      return acc;
    }, {})
  };
}

function render(events) {
  const overview = document.getElementById("overview");
  const eventsNode = document.getElementById("events");
  const channelsNode = document.getElementById("channels");
  const derivedNode = document.getElementById("derived");
  const latest = events.at(-1) || {};
  const latestTest = [...events].reverse().find((event) => event.channel === "test") || {};
  const latestMetrics = [...events].reverse().find((event) => event.channel === "metrics") || {};
  const latestConfidence = [...events].reverse().find((event) => typeof event.confidence === "number");
  const model = derive(events);

  overview.innerHTML = "";
  [
    ["Agent", events[0]?.agent || "orwelliana"],
    ["Repo", events[0]?.repo || "session"],
    ["Status", latestTest.result === "pass" ? "green" : "working"],
    ["Loops", latestMetrics.details?.loops ?? "?"],
    ["Confidence", latestConfidence?.confidence ?? "n/a"],
    ["Cost", latestMetrics.details?.cost_usd ? `$${latestMetrics.details.cost_usd}` : "n/a"]
  ].forEach(([label, value]) => {
    const card = document.createElement("div");
    card.className = "stat";
    card.innerHTML = `<span class="label">${label}</span><span class="value">${value}</span>`;
    overview.appendChild(card);
  });

  eventsNode.innerHTML = "";
  events.slice(-6).forEach((event) => {
    const node = document.createElement("div");
    node.className = "event";
    node.innerHTML = `
      <strong>${event.channel}.${event.event}</strong>
      <div>${event.summary || "No summary"}</div>
      <small>${event.ts}</small>
    `;
    eventsNode.appendChild(node);
  });

  channelsNode.innerHTML = "";
  Object.entries(model.channels).forEach(([channel, count]) => {
    const item = document.createElement("li");
    item.textContent = `${channel}: ${count}`;
    channelsNode.appendChild(item);
  });

  derivedNode.textContent = JSON.stringify({
    trajectory: model.trajectory,
    confidence_curve: model.confidence_curve
  }, null, 2);

  if (latest.repo) {
    document.title = `Orwelliana • ${latest.repo}`;
  }
}

function parseJsonl(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

document.getElementById("file-input").addEventListener("change", async (event) => {
  const [file] = event.target.files;
  if (!file) return;
  const text = await file.text();
  render(parseJsonl(text));
});

render(sample);
