const sample = [
  {
    ts: "2026-03-30T12:00:00Z",
    channel: "system",
    event: "agent_start",
    agent: "orwelliana",
    repo: "Orwelliana",
    summary: "Operator booted against Orwelliana"
  },
  {
    ts: "2026-03-30T12:00:01Z",
    channel: "conversation",
    event: "message",
    session: "deploy",
    summary: "Deploy Orwelliana and verify externally.",
    details: {
      role: "system",
      content: "Deploy Orwelliana and verify externally.",
      content_chars: 38
    }
  },
  {
    ts: "2026-03-30T12:00:02Z",
    channel: "conversation",
    event: "message",
    session: "deploy",
    summary: "Pages failed before the site was provisioned.",
    details: {
      role: "assistant",
      content: "Pages failed before the site was provisioned.",
      content_chars: 44
    }
  },
  {
    ts: "2026-03-30T12:00:03Z",
    channel: "planning",
    event: "plan_generated",
    summary: "Inspect GitHub state before blaming the app",
    confidence: 0.78
  },
  {
    ts: "2026-03-30T12:00:04Z",
    channel: "execution",
    event: "deploy_doctor",
    summary: "Checked git status, Pages provisioning, Actions runs, and public URL",
    details: {
      checks: ["git", "pages", "actions", "http"]
    }
  },
  {
    ts: "2026-03-30T12:00:05Z",
    channel: "evaluation",
    event: "failure_analysis",
    summary: "Pages site missing, workflow could not create it",
    details: {
      remaining_failure: "github-pages",
      likely_cause: "site_not_provisioned"
    },
    confidence: 0.71
  },
  {
    ts: "2026-03-30T12:00:06Z",
    channel: "execution",
    event: "repair",
    summary: "Created GitHub Pages site with workflow publishing"
  },
  {
    ts: "2026-03-30T12:00:07Z",
    channel: "test",
    event: "run",
    result: "pass",
    details: {
      passed: 7,
      failed: 0
    }
  },
  {
    ts: "2026-03-30T12:00:08Z",
    channel: "metrics",
    event: "usage",
    details: {
      cost_usd: 0.18,
      loops: 2
    }
  }
];

function conversationEvents(events, session) {
  return events.filter((event) => {
    const isMessage = event.channel === "conversation" && event.event === "message";
    return isMessage && (!session || event.session === session);
  });
}

function latestSession(events) {
  return conversationEvents(events).at(-1)?.session || null;
}

function clipText(text, maxChars) {
  if (text.length <= maxChars) return text;
  if (maxChars <= 3) return text.slice(0, maxChars);
  return `${text.slice(0, maxChars - 3)}...`;
}

function boundedConversationWindow(events, options = {}) {
  const session = options.session || latestSession(events);
  const limit = options.limit ?? 6;
  const chars = options.chars ?? 4000;
  const messages = conversationEvents(events, session);
  const systemAnchor = [...messages].reverse().find((event) => event.details?.role === "system");
  const reservedChars = systemAnchor ? Math.min(chars, (systemAnchor.details?.content || "").length) : 0;
  const nonSystem = messages.filter((event) => event.details?.role !== "system");
  const picked = [];
  let usedChars = 0;

  for (const event of [...nonSystem].reverse()) {
    if (picked.length >= limit) break;
    const size = (event.details?.content || "").length;
    if (picked.length === 0 || reservedChars + usedChars + size <= chars) {
      picked.push(event);
      usedChars += size;
    }
  }

  const rawWindow = picked.reverse();
  if (systemAnchor) rawWindow.unshift(systemAnchor);

  const bounded = [];
  let remaining = chars;
  for (const event of rawWindow) {
    const content = event.details?.content || event.summary || "";
    const clipped = clipText(content, Math.max(remaining, 0));
    if (clipped.length > 0) {
      bounded.push({
        ts: event.ts,
        role: event.details?.role || "unknown",
        content: clipped,
        chars: clipped.length,
        truncated: clipped.length < content.length
      });
      remaining -= clipped.length;
    }
  }

  return {
    session,
    messages: bounded,
    chars: bounded.reduce((sum, message) => sum + message.chars, 0)
  };
}

function derive(events) {
  const conversation = boundedConversationWindow(events);
  const channels = events.reduce((acc, event) => {
    acc[event.channel] = (acc[event.channel] || 0) + 1;
    return acc;
  }, {});
  const failureManifold = events
    .filter((event) => event.channel === "evaluation")
    .reduce((acc, event) => {
      const key = event.details?.remaining_failure;
      if (key) {
        acc[key] = [event.details?.likely_cause || "unknown"];
      }
      return acc;
    }, {});

  return {
    trajectory: events
      .filter((event) => event.channel === "execution")
      .map((event) => event.summary || event.event),
    confidence_curve: events
      .map((event) => event.confidence)
      .filter((value) => typeof value === "number"),
    conversation: {
      sessions: [...new Set(conversationEvents(events).map((event) => event.session).filter(Boolean))],
      messages: conversationEvents(events).length,
      latest_session: conversation.session,
      window_chars: conversation.chars
    },
    failure_manifold: failureManifold,
    channels
  };
}

function doctorModel(events, model) {
  const latestTest = [...events].reverse().find((event) => event.channel === "test") || {};
  const latestMetrics = [...events].reverse().find((event) => event.channel === "metrics") || {};
  const failures = Object.keys(model.failure_manifold).length;
  const status = latestTest.result === "pass" && failures === 0 ? "ready" : failures > 0 ? "degraded" : "working";
  return {
    status,
    loops: latestMetrics.details?.loops ?? "?",
    checks: [
      ["Trace", `${events.length} events`],
      ["Conversation", `${model.conversation.messages} messages`],
      ["Surface", failures === 0 ? "no active failures" : `${failures} active causes`],
      ["Cost", latestMetrics.details?.cost_usd ? `$${latestMetrics.details.cost_usd}` : "n/a"]
    ]
  };
}

function setText(id, text) {
  document.getElementById(id).textContent = text;
}

function setTraceNote(text) {
  document.getElementById("loaded-trace").textContent = text;
}

function renderDoctorSummary(doctor) {
  const node = document.getElementById("doctor-summary");
  node.innerHTML = "";

  const badge = document.createElement("div");
  badge.className = `doctor-status ${doctor.status}`;
  badge.textContent = `deploy ${doctor.status}`;
  node.appendChild(badge);

  const facts = document.createElement("div");
  facts.className = "doctor-facts";
  doctor.checks.forEach(([label, value]) => {
    const fact = document.createElement("div");
    fact.className = "doctor-fact";
    fact.innerHTML = `<span class="point-label">${label}</span><strong>${value}</strong>`;
    facts.appendChild(fact);
  });
  node.appendChild(facts);
}

function renderOverview(events, model) {
  const overview = document.getElementById("overview");
  const latest = events.at(-1) || {};
  const latestTest = [...events].reverse().find((event) => event.channel === "test") || {};
  const latestMetrics = [...events].reverse().find((event) => event.channel === "metrics") || {};
  const latestConfidence = [...events].reverse().find((event) => typeof event.confidence === "number");

  overview.innerHTML = "";
  [
    ["Agent", events[0]?.agent || "orwelliana"],
    ["Repo", events[0]?.repo || "session"],
    ["Status", latestTest.result === "pass" ? "green" : "working"],
    ["Loops", latestMetrics.details?.loops ?? "?"],
    ["Confidence", latestConfidence?.confidence ?? "n/a"],
    ["Cost", latestMetrics.details?.cost_usd ? `$${latestMetrics.details.cost_usd}` : "n/a"],
    ["Sessions", model.conversation.sessions.length || 0],
    ["Messages", model.conversation.messages]
  ].forEach(([label, value]) => {
    const card = document.createElement("div");
    card.className = "stat";
    card.innerHTML = `<span class="label">${label}</span><span class="value">${value}</span>`;
    overview.appendChild(card);
  });

  if (latest.repo) {
    document.title = `Orwelliana • ${latest.repo}`;
  }
}

function renderEvents(events) {
  const eventsNode = document.getElementById("events");
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
}

function renderChannels(model) {
  const channelsNode = document.getElementById("channels");
  channelsNode.innerHTML = "";

  Object.entries(model.channels).forEach(([channel, count]) => {
    const item = document.createElement("li");
    item.textContent = `${channel}: ${count}`;
    channelsNode.appendChild(item);
  });
}

function renderConversation(events) {
  const conversationNode = document.getElementById("conversation");
  const conversation = boundedConversationWindow(events);
  conversationNode.innerHTML = "";

  if (conversation.messages.length === 0) {
    conversationNode.innerHTML = `<div class="empty">No conversation events in this trace.</div>`;
    return conversation;
  }

  const meta = document.createElement("div");
  meta.className = "conversation-meta";
  meta.textContent = `session ${conversation.session || "n/a"} • ${conversation.messages.length} messages • ${conversation.chars} chars`;
  conversationNode.appendChild(meta);

  conversation.messages.forEach((message) => {
    const item = document.createElement("div");
    item.className = `message role-${message.role}`;
    item.innerHTML = `
      <div class="message-head">
        <strong>${message.role}</strong>
        <small>${message.ts}</small>
      </div>
      <div>${message.content}</div>
    `;
    conversationNode.appendChild(item);
  });

  return conversation;
}

function renderTrajectory(model) {
  const node = document.getElementById("trajectory");
  node.innerHTML = "";

  if (model.trajectory.length === 0) {
    node.innerHTML = `<div class="empty">No execution trajectory in this trace.</div>`;
    return;
  }

  model.trajectory.forEach((step, index) => {
    const item = document.createElement("div");
    item.className = "trajectory-item";
    item.innerHTML = `<span class="trajectory-index">${index + 1}</span><span>${step}</span>`;
    node.appendChild(item);
  });
}

function renderDerived(model) {
  document.getElementById("derived").textContent = JSON.stringify({
    trajectory: model.trajectory,
    confidence_curve: model.confidence_curve,
    conversation: model.conversation,
    failure_manifold: model.failure_manifold
  }, null, 2);
}

function renderStatusStrip(model, conversation) {
  setText("status-headline", model.trajectory.at(-1) || "No execution steps yet");
  setText("status-session", model.conversation.latest_session || "n/a");
  setText("status-window", `${conversation.chars} chars`);
  setText("status-failure", `${Object.keys(model.failure_manifold).length} active causes`);
}

function render(events, options = {}) {
  const model = derive(events);
  const doctor = doctorModel(events, model);

  renderDoctorSummary(doctor);
  renderOverview(events, model);
  renderEvents(events);
  renderChannels(model);
  const conversation = renderConversation(events);
  renderTrajectory(model);
  renderDerived(model);
  renderStatusStrip(model, conversation);
  setTraceNote(options.traceLabel || "Viewing the bundled sample trace.");
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
  render(parseJsonl(text), { traceLabel: `Loaded trace: ${file.name}` });
});

render(sample, { traceLabel: "Viewing the bundled sample trace." });
