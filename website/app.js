/* foyer — the live demo is a faithful port of the app's tier-0 router
   (Normalizer + FastRouter + AppIndex, ai/router) plus the plan renderer.
   Same rule as the app: structured actions, never raw intents. */

(function () {
  "use strict";

  /* ---- a sample phone ---------------------------------------------- */
  var APPS = [
    "spotify", "whatsapp", "camera", "chrome", "gemini", "instagram",
    "maps", "messages", "phone", "photos", "clock", "settings",
    "gaana", "youtube music",
  ];
  var MUSIC = ["spotify", "gaana", "youtube music"];
  var VERBS = ["open", "launch", "start", "run", "show", "go", "goto"];
  var SETTINGS = {
    wifi: "wifi settings", "wi-fi": "wifi settings", bluetooth: "bluetooth settings",
    airplane: "airplane mode", battery: "battery saver", display: "display settings",
    brightness: "display settings", sound: "sound settings", volume: "sound settings",
    location: "location settings", data: "data usage", developer: "developer options",
    accessibility: "accessibility settings",
  };

  /* ---- Normalizer ---------------------------------------------------- */
  function normalize(raw) {
    var t = raw.trim().replace(/\s+/g, " ").toLowerCase();
    var tokens = t.split(" ");
    while (tokens.length >= 2 && VERBS.indexOf(tokens[0]) >= 0 && tokens[0] === tokens[1]) {
      tokens.shift();
    }
    return tokens.join(" ");
  }

  /* ---- AppIndex.lookup (exact → prefix → subsequence) ------------------ */
  function subsequence(q, s) {
    var i = 0;
    for (var j = 0; j < s.length && i < q.length; j++) if (s[j] === q[i]) i++;
    return i === q.length;
  }
  function lookupApp(q) {
    if (!q) return null;
    for (var i = 0; i < APPS.length; i++) if (APPS[i] === q) return { app: APPS[i], confident: true };
    var pref = APPS.filter(function (a) { return a.indexOf(q) === 0; });
    if (pref.length === 1) return { app: pref[0], confident: true };
    if (pref.length > 1) return { app: pref[0], confident: false, candidates: pref };
    if (q.length >= 4) {
      var sub = APPS.filter(function (a) { return subsequence(q, a.replace(/\s/g, "")); });
      if (sub.length === 1) return { app: sub[0], confident: true };
      if (sub.length > 1) return { app: sub[0], confident: false, candidates: sub };
    }
    return null;
  }

  /* ---- FastRouter ------------------------------------------------------ */
  function route(text) {
    var t = normalize(text);
    if (!t) return [];
    var plan = [];
    // several independent tasks, like the app's multi-intent plans
    var parts = t.split(/\band\b|,|;/).map(function (p) { return p.trim(); }).filter(Boolean);

    parts.forEach(function (p) {
      var m;
      if ((m = p.match(/alarm (?:for |at )?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?/))) {
        var h = +m[1] % 24, min = m[2] ? +m[2] : 0;
        if (m[3] === "pm" && h < 12) h += 12;
        plan.push({ title: "set alarm " + h + ":" + (min < 10 ? "0" : "") + min, kind: "clock", json: { action: "alarm", hour: h, minute: min } });
        return;
      }
      if ((m = p.match(/timer (?:for )?(\d+)\s*(min|minute|minutes|sec|seconds|s|m)?/))) {
        var secs = +m[1] * (/^s/.test(m[2] || "m") ? 1 : 60);
        plan.push({ title: "timer " + Math.round(secs / 60) + "m", kind: "clock", json: { action: "timer", seconds: secs } });
        return;
      }
      if ((m = p.match(/^(?:message|whatsapp|text) (\w+)(?: (?:that |saying )?(.+))?/))) {
        plan.push({ title: "message " + m[1], sub: m[2] ? "“" + m[2] + "”" : "compose", kind: "whatsapp", json: { action: "message", to: m[1], text: m[2] || "" } });
        return;
      }
      if ((m = p.match(/(?:turn (?:on|off) |enable |disable |open )?([a-z-]+)(?: settings| mode)?$/)) && SETTINGS[m[1]]) {
        plan.push({ title: SETTINGS[m[1]], kind: "setting", json: { action: "setting", key: m[1] } });
        return;
      }
      if (/how many|list (?:all )?(?:my )?/.test(p) && /music|apps|games/.test(p)) {
        plan.push({ title: "you have " + MUSIC.length + " music apps: " + MUSIC.join(", "), kind: "answer", json: { action: "find_apps", query: "music" } });
        return;
      }
      var stripped = p;
      var first = p.split(" ")[0];
      if (VERBS.indexOf(first) >= 0) stripped = p.split(" ").slice(1).join(" ");
      var r = lookupApp(stripped);
      if (r && r.confident) {
        plan.push({ title: "open " + r.app, kind: "app", json: { action: "open_app", app: r.app } });
      } else if (r) {
        plan.push({ title: "did you mean " + r.candidates.slice(0, 3).join(", ") + "?", kind: "ask", json: { action: "open_app", app: r.app } });
      } else {
        plan.push({ title: "search web for “" + p + "”", kind: "web", json: { action: "web", query: p } });
      }
    });
    return plan;
  }

  /* ---- render like the app renders a Command -------------------------- */
  var planEl = document.getElementById("plan");
  var noteEl = document.getElementById("plan-note");
  function render(text) {
    var plan = route(text);
    planEl.innerHTML = "";
    plan.forEach(function (step) {
      var row = document.createElement("div");
      row.className = "ui-row";
      var left = document.createElement("span");
      left.className = "ui-title";
      left.textContent = step.title;
      row.appendChild(left);
      var right = document.createElement("span");
      if (step.kind === "app" || step.kind === "setting" || step.kind === "clock" || step.kind === "whatsapp") {
        right.className = "ui-go";
        right.textContent = "go";
      } else {
        right.className = "ui-kind";
        right.textContent = step.kind;
      }
      row.appendChild(right);
      if (step.sub) {
        var sub = document.createElement("div");
        sub.className = "ui-sub";
        sub.textContent = step.sub;
        row.appendChild(sub);
      }
      planEl.appendChild(row);
    });
    if (plan.length) {
      var code = plan.map(function (s) { return JSON.stringify(s.json); }).join(" ");
      noteEl.innerHTML = "the structured plan, exactly as the app parses it: <code></code>";
      noteEl.querySelector("code").textContent = code;
    } else {
      noteEl.textContent = "type a command on the dark side.";
    }
  }

  var cmd = document.getElementById("cmd");
  if (cmd && planEl) {
    render(cmd.value);
    cmd.addEventListener("input", function () { render(cmd.value); });
    document.querySelectorAll(".chips button").forEach(function (b) {
      b.addEventListener("click", function () {
        cmd.value = b.textContent;
        render(cmd.value);
        cmd.focus();
      });
    });
  }

  /* ---- hero reel: the pinned story -------------------------------------- */
  var hero = document.querySelector(".hero");
  var track = document.getElementById("reel");
  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (hero && track && !reduced && window.matchMedia("(min-width: 761px)").matches) {
    var frames = track.children.length;
    var tick = function () {
      var r = hero.getBoundingClientRect();
      var span = r.height - window.innerHeight;
      var p = Math.min(1, Math.max(0, -r.top / span));
      var frameH = track.children[0].getBoundingClientRect().height + 24;
      var centre = window.innerHeight / 2 - frameH / 2;
      track.style.transform = "translateY(" + (centre - p * (frames - 1) * frameH) + "px)";
    };
    tick();
    window.addEventListener("scroll", function () { requestAnimationFrame(tick); }, { passive: true });
    window.addEventListener("resize", tick);
  } else if (track) {
    track.style.transform = "none";
  }

  /* ---- entrances ----------------------------------------------------------- */
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
    });
  }, { threshold: 0.18 });
  document.querySelectorAll(".rise").forEach(function (el, i) {
    el.style.transitionDelay = (i % 3) * 70 + "ms";
    io.observe(el);
  });
})();
