const sample = [
  { ts: "2026-03-30T12:00:00Z", channel: "system", event: "agent_start", agent: "agent-parser", repo: "parser-lib", summary: "Agent booted with session-seeded ontology" },
  { ts: "2026-03-30T12:00:01Z", channel: "conversation", event: "message", session: "demo", summary: "You are attached to parser-lib. Keep notes terse.", details: { role: "system", content: "You are attached to parser-lib. Keep notes terse.", content_chars: 48 } },
  { ts: "2026-03-30T12:00:02Z", channel: "conversation", event: "message", session: "demo", summary: "Find the current failures.", details: { role: "user", content: "Find the current failures.", content_chars: 26 } },
  { ts: "2026-03-30T12:00:03Z", channel: "conversation", event: "message", session: "demo", summary: "Three tests are failing in tokenizer, parser, and lexer.", details: { role: "assistant", content: "Three tests are failing in tokenizer, parser, and lexer.", content_chars: 57 } },
  { ts: "2026-03-30T12:00:04Z", channel: "planning", event: "plan_generated", summary: "Investigate tokenizer boundary conditions", confidence: 0.72 },
  { ts: "2026-03-30T12:00:08Z", channel: "execution", event: "apply_patch", summary: "Adjust loop boundary condition" },
  { ts: "2026-03-30T12:00:11Z", channel: "evaluation", event: "failure_analysis", summary: "unicode test still failing", confidence: 0.66 },
  { ts: "2026-03-30T12:00:18Z", channel: "test", event: "run", result: "pass", details: { passed: 49, failed: 0 } },
  { ts: "2026-03-30T12:00:20Z", channel: "metrics", event: "usage", details: { cost_usd: 0.42, loops: 2 } }
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

function boundedConversationWindow(events, options = {}) {
  const session = options.session || latestSession(events);
  const limit = options.limit ?? 6;
  const chars = options.chars ?? 4000;
  const messages = conversationEvents(events, session);
  const systemAnchor = [...messages].reverse().find((event) => event.details?.role === "system");
  const nonSystem = messages.filter((event) => event.details?.role !== "system");
  const picked = [];
  let usedChars = 0;

  for (const event of [...nonSystem].reverse()) {
    if (picked.length >= limit) break;
    const size = (event.details?.content || "").length;
    if (picked.length === 0 || usedChars + size <= chars) {
      picked.push(event);
      usedChars += size;
    }
  }

  const window = picked.reverse();
  if (systemAnchor) {
    window.unshift(systemAnchor);
  }

  return {
    session,
    messages: window.map((event) => ({
      ts: event.ts,
      role: event.details?.role || "unknown",
      content: event.details?.content || event.summary || "",
      chars: (event.details?.content || "").length
    })),
    chars: window.reduce((sum, event) => sum + (event.details?.content || "").length, 0)
  };
}

function derive(events) {
  const convo = boundedConversationWindow(events);
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
      latest_session: convo.session,
      window_chars: convo.chars
    },
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
  const conversationNode = document.getElementById("conversation");
  const derivedNode = document.getElementById("derived");
  const latest = events.at(-1) || {};
  const latestTest = [...events].reverse().find((event) => event.channel === "test") || {};
  const latestMetrics = [...events].reverse().find((event) => event.channel === "metrics") || {};
  const latestConfidence = [...events].reverse().find((event) => typeof event.confidence === "number");
  const model = derive(events);
  const convo = boundedConversationWindow(events);

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

  conversationNode.innerHTML = "";
  if (convo.messages.length === 0) {
    conversationNode.innerHTML = `<div class="empty">No conversation events in this trace.</div>`;
  } else {
    const meta = document.createElement("div");
    meta.className = "conversation-meta";
    meta.textContent = `session ${convo.session || "n/a"} • ${convo.messages.length} messages • ${convo.chars} chars`;
    conversationNode.appendChild(meta);

    convo.messages.forEach((message) => {
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
  }

  derivedNode.textContent = JSON.stringify({
    trajectory: model.trajectory,
    confidence_curve: model.confidence_curve,
    conversation: model.conversation
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
