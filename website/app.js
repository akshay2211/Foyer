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
      noteEl.textContent = plan.length === 1
        ? "one step, done on the phone itself."
        : plan.length + " steps from one sentence, all done on the phone itself.";
    } else {
      noteEl.textContent = "type something above.";
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

  /* ---- motion layer ---------------------------------------------------------
     the transition architecture borrows from niagaralauncher.com:
     a pinned feature reel scrubbed sideways by vertical scroll, a progress
     bar, staggered entrances, and scroll-linked hero / headline moves.
     transform + opacity only; all of it stands down for reduced motion. */

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  /* entrances: stagger siblings inside their own section, not globally */
  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); }
    });
  }, { threshold: 0.18 });
  var groupCounts = new Map();
  document.querySelectorAll(".rise").forEach(function (el) {
    var group = el.parentElement;
    var i = groupCounts.get(group) || 0;
    groupCounts.set(group, i + 1);
    el.style.transitionDelay = Math.min(i, 6) * 80 + "ms";
    io.observe(el);
  });

  /* hero headline: word-by-word rise on load */
  var heroHead = document.getElementById("hero-head");
  if (heroHead && !reduceMotion.matches) {
    var words = heroHead.textContent.trim().split(/\s+/);
    heroHead.textContent = "";
    words.forEach(function (word, i) {
      var span = document.createElement("span");
      span.className = "w";
      span.textContent = word;
      span.style.transitionDelay = i * 60 + "ms";
      heroHead.appendChild(span);
      if (i < words.length - 1) heroHead.appendChild(document.createTextNode(" "));
    });
    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        heroHead.querySelectorAll(".w").forEach(function (w) { w.classList.add("in"); });
      });
    });
  }

  /* the reel: pin the section, let vertical scroll drive the track sideways */
  var reel = document.querySelector(".reel");
  var track = document.getElementById("reel-track");
  var bar = document.getElementById("reel-bar");
  var cards = track ? [].slice.call(track.querySelectorAll(".card")) : [];
  var wide = window.matchMedia("(min-width: 761px)");
  var reelOn = false, travel = 0;

  function reelSetup() {
    reelOn = wide.matches && !reduceMotion.matches && track && cards.length > 1;
    document.documentElement.classList.toggle("reel-on", reelOn);
    if (!reelOn) {
      if (reel) reel.style.height = "";
      if (track) track.style.transform = "";
      cards.forEach(function (c) { c.classList.remove("is-active"); });
      return;
    }
    track.style.transform = "";
    travel = track.scrollWidth - track.clientWidth;
    reel.style.height = window.innerHeight + travel + "px";
    reelFrame();
  }

  function reelFrame() {
    if (!reelOn || travel <= 0) return;
    var p = (window.scrollY - reel.offsetTop) / travel;
    p = Math.max(0, Math.min(1, p));
    track.style.transform = "translate3d(" + (-p * travel).toFixed(1) + "px,0,0)";
    if (bar) bar.style.width = (p * 100).toFixed(2) + "%";
    var active = Math.round(p * (cards.length - 1));
    cards.forEach(function (c, i) { c.classList.toggle("is-active", i === active); });
  }

  /* fallback: when the row is swiped natively, the bar still tracks it */
  if (track) track.addEventListener("scroll", function () {
    if (reelOn || !bar) return;
    var max = track.scrollWidth - track.clientWidth;
    if (max > 0) bar.style.width = (track.scrollLeft / max * 100).toFixed(2) + "%";
  }, { passive: true });

  /* scroll-linked layers: hero phone drift, "you say, / it does." slide */
  var heroPhone = document.querySelector(".hero-phone .phone");
  var show = document.querySelector(".show");
  var headIn = document.querySelector(".show-head.in");
  var headOut = document.querySelector(".show-head.out");

  function scrollFrame() {
    if (reduceMotion.matches) return;
    reelFrame();
    if (heroPhone && window.scrollY < window.innerHeight * 1.5) {
      heroPhone.style.transform = "translateY(" + (window.scrollY * -0.06).toFixed(1) + "px)";
    }
    if (show && headIn && headOut) {
      var r = show.getBoundingClientRect();
      if (r.top < window.innerHeight && r.bottom > 0) {
        var p = Math.max(0, Math.min(1, (window.innerHeight - r.top) / (window.innerHeight * 0.9)));
        headIn.style.transform = "translateX(" + ((p - 1) * 70).toFixed(1) + "px)";
        headOut.style.transform = "translateX(" + ((1 - p) * 70).toFixed(1) + "px)";
        headIn.style.opacity = headOut.style.opacity = Math.min(1, p * 1.6).toFixed(2);
      }
    }
  }

  var ticking = false;
  function onScroll() {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(function () { ticking = false; scrollFrame(); });
  }

  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", reelSetup);
  wide.addEventListener("change", reelSetup);
  reduceMotion.addEventListener("change", reelSetup);
  if (document.readyState === "complete") { reelSetup(); scrollFrame(); }
  else window.addEventListener("load", function () { reelSetup(); scrollFrame(); });
})();
