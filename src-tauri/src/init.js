(() => {
  "use strict";

  if (window.top !== window || window.location.origin !== "https://chatgpt.com" || window.__lumiToGpt) {
    return;
  }
  window.__lumiToGpt = true;

  const POLL_INTERVAL_MS = 500;
  const RESPONSE_TIMEOUT_MS = 180000;
  const RESPONSE_STABLE_MS = 6000;
  const PROJECT_KEY = "lumi-to-gpt.project.v1";
  const AUTO_NAV_KEY = "lumi-to-gpt.project-autonav.v1";
  const ACTIVE_PROJECT_KEY = "lumi-to-gpt.active-project.v1";
  const COMPOSER_SELECTOR = [
    '[data-testid="prompt-textarea"]',
    "#prompt-textarea",
    '[contenteditable="true"][data-lexical-editor="true"]'
  ].join(", ");
  const SEND_BUTTON_SELECTOR = [
    'button[data-testid="send-button"]',
    'button[aria-label*="Send"]',
    'button[aria-label*="보내기"]'
  ].join(", ");
  const STOP_BUTTON_SELECTOR = '[data-testid="stop-button"]';
  const COMPLETION_ACTION_SELECTOR = 'button[data-testid="copy-turn-action-button"]';
  const ASSISTANT_TURN_SELECTOR = [
    '[data-testid^="conversation-turn-"][data-turn="assistant"]',
    '[data-testid^="conversation-turn-"][data-message-author-role="assistant"]',
    '[data-testid^="conversation-turn-"]:has([data-message-author-role="assistant"])'
  ].join(", ");

  let bridgeBusy = false;

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  function toast(text, error = false) {
    document.getElementById("lumi-to-gpt-toast")?.remove();
    const element = document.createElement("div");
    element.id = "lumi-to-gpt-toast";
    element.textContent = text;
    Object.assign(element.style, {
      position: "fixed",
      right: "20px",
      bottom: "20px",
      zIndex: "2147483647",
      padding: "12px 16px",
      borderRadius: "10px",
      color: "white",
      background: error ? "#b42318" : "#10a37f",
      boxShadow: "0 8px 30px rgba(0,0,0,.22)",
      font: "600 14px system-ui, sans-serif"
    });
    document.documentElement.appendChild(element);
    setTimeout(() => element.remove(), 3500);
  }

  function projectKey(url = window.location.href) {
    try {
      const parsed = new URL(url);
      if (parsed.origin !== "https://chatgpt.com") return "";
      const segment = parsed.pathname.split("/").find((part) => part.startsWith("g-p-")) ?? "";
      return segment.match(/^(g-p-[a-f0-9]{32})(?:-|$)/i)?.[1] ?? segment;
    } catch (_) {
      return "";
    }
  }

  function configuredProjectUrl() {
    return localStorage.getItem(PROJECT_KEY) ?? "";
  }

  function isLumiProject() {
    const configured = projectKey(configuredProjectUrl());
    const current = projectKey();
    if (!configured) return false;
    if (current) {
      if (current === configured) {
        sessionStorage.setItem(ACTIVE_PROJECT_KEY, configured);
        return true;
      }
      sessionStorage.removeItem(ACTIVE_PROJECT_KEY);
      return false;
    }
    return window.location.pathname.startsWith("/c/")
      && sessionStorage.getItem(ACTIVE_PROJECT_KEY) === configured;
  }

  function currentProjectRoot() {
    const key = projectKey();
    return key ? `https://chatgpt.com/g/${key}/project` : "";
  }

  function updateSettingsStatus() {
    const status = document.getElementById("lumi-project-status");
    const openButton = document.getElementById("lumi-open-project");
    if (!status || !openButton) return;
    const saved = configuredProjectUrl();
    status.textContent = !saved
      ? "아직 연결된 프로젝트가 없습니다."
      : isLumiProject()
        ? "루미 챗 요청을 이 프로젝트의 ChatGPT로 전달합니다."
        : "현재 페이지는 LUMI 프로젝트가 아닙니다.";
    status.style.color = isLumiProject() ? "#56d6b0" : "#f2b84b";
    openButton.disabled = !saved;
  }

  function createProjectUi() {
    if (document.getElementById("lumi-project-button")) return;

    const style = document.createElement("style");
    style.textContent = `
      #lumi-project-button { position:fixed; right:18px; top:72px; z-index:2147483646; border:0; border-radius:999px; padding:9px 13px; color:#fff; background:#087f65; box-shadow:0 8px 24px #0003; font:600 13px system-ui; cursor:pointer }
      #lumi-project-panel { position:fixed; right:18px; top:116px; z-index:2147483646; width:min(390px,calc(100vw - 36px)); border:1px solid #ffffff24; border-radius:14px; padding:16px; color:#f7faf9; background:#15201ef5; box-shadow:0 18px 60px #0007; font:13px/1.5 system-ui }
      #lumi-project-panel[hidden] { display:none }
      #lumi-project-panel h2 { margin:0 0 8px; font-size:17px }
      #lumi-project-panel p { margin:7px 0; color:#c5d0cd }
      #lumi-project-panel .actions { display:flex; flex-wrap:wrap; gap:7px }
      #lumi-project-panel button { border:0; border-radius:8px; padding:8px 10px; color:#fff; background:#087f65; font:600 12px system-ui; cursor:pointer }
      #lumi-project-panel button.secondary { background:#34423f }
      #lumi-project-panel button:disabled { opacity:.45; cursor:default }
    `;
    document.documentElement.appendChild(style);

    const button = document.createElement("button");
    button.id = "lumi-project-button";
    button.type = "button";
    button.textContent = "LUMI 프로젝트";

    const panel = document.createElement("section");
    panel.id = "lumi-project-panel";
    panel.hidden = true;
    panel.innerHTML = `
      <h2>LUMI 전용 프로젝트</h2>
      <p id="lumi-project-status"></p>
      <p>ChatGPT에서 LUMI 프로젝트를 만든 뒤 그 프로젝트 안에서 연결하세요. AI와 목소리 설정은 Little LUMI의 루미 AI 설정을 사용합니다.</p>
      <div class="actions">
        <button id="lumi-use-project" type="button">현재 프로젝트 연결</button>
        <button id="lumi-open-project" class="secondary" type="button">연결한 프로젝트 열기</button>
      </div>
    `;

    document.documentElement.append(button, panel);
    button.addEventListener("click", () => {
      panel.hidden = !panel.hidden;
      updateSettingsStatus();
    });
    panel.querySelector("#lumi-use-project").addEventListener("click", () => {
      const url = currentProjectRoot();
      if (!url) {
        toast("먼저 ChatGPT의 LUMI 프로젝트를 열어 주세요.", true);
        return;
      }
      localStorage.setItem(PROJECT_KEY, url);
      sessionStorage.setItem(ACTIVE_PROJECT_KEY, projectKey(url));
      updateSettingsStatus();
      toast("현재 프로젝트를 LUMI 전용으로 연결했습니다.");
    });
    panel.querySelector("#lumi-open-project").addEventListener("click", () => {
      const url = configuredProjectUrl();
      if (url) window.location.assign(url);
    });
    updateSettingsStatus();
  }

  function autoOpenConfiguredProject() {
    const configured = configuredProjectUrl();
    if (!configured || sessionStorage.getItem(AUTO_NAV_KEY) || projectKey()) return;
    if (window.location.pathname !== "/") return;
    sessionStorage.setItem(AUTO_NAV_KEY, "1");
    window.location.replace(configured);
  }

  function composer() {
    return document.querySelector(COMPOSER_SELECTOR);
  }

  async function waitForComposer(timeoutMs = 30000) {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      const element = composer();
      if (element) return element;
      await sleep(250);
    }
    throw new Error("ChatGPT 입력창을 찾지 못했습니다. 로그인 상태를 확인하세요.");
  }

  function setComposerText(element, text) {
    element.focus();
    if (element instanceof HTMLTextAreaElement) {
      const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")?.set;
      setter?.call(element, text);
      element.dispatchEvent(new Event("input", { bubbles: true }));
      element.dispatchEvent(new Event("change", { bubbles: true }));
      return;
    }
    const selection = window.getSelection();
    const range = document.createRange();
    range.selectNodeContents(element);
    selection?.removeAllRanges();
    selection?.addRange(range);
    document.execCommand("insertText", false, text);
    element.dispatchEvent(
      new InputEvent("input", { bubbles: true, composed: true, inputType: "insertText", data: text })
    );
  }

  function dataUrlToFile(dataUrl, index) {
    const [metadata, encoded] = dataUrl.split(",", 2);
    const mime = metadata.match(/^data:([^;]+)/)?.[1] ?? "image/png";
    const bytes = Uint8Array.from(atob(encoded), (character) => character.charCodeAt(0));
    const extension = mime.split("/")[1] ?? "png";
    return new File([bytes], `lumi-screen-${index}.${extension}`, { type: mime });
  }

  async function attachImages(images) {
    if (!images?.length) return;
    const input = document.querySelector("input[type='file'][accept*='image'], input[type='file']");
    if (!input) throw new Error("ChatGPT 이미지 첨부 입력을 찾지 못했습니다.");
    const transfer = new DataTransfer();
    images.forEach((image, index) => transfer.items.add(dataUrlToFile(image, index)));
    input.files = transfer.files;
    input.dispatchEvent(new Event("change", { bubbles: true }));
    await sleep(1800);
  }

  function assistantMessages() {
    const turns = Array.from(document.querySelectorAll(ASSISTANT_TURN_SELECTOR));
    if (turns.length) return turns;
    const roleNodes = Array.from(document.querySelectorAll("[data-message-author-role='assistant']"));
    if (roleNodes.length) return roleNodes;

    // ChatGPT occasionally omits the role attributes while keeping the rendered Markdown.
    // User messages do not use this renderer, so its owning article is a stable fallback.
    const inferredTurns = Array.from(document.querySelectorAll("main .markdown, main .prose"))
      .filter((block, index, all) => !all.some(
        (other, otherIndex) => otherIndex !== index && other.contains(block)
      ))
      .map((block) => block.closest("[data-testid^='conversation-turn-'], article") ?? block)
      .filter((element) => !element.querySelector("[data-message-author-role='user']"));
    return Array.from(new Set(inferredTurns));
  }

  function assistantText(element) {
    const root = element.querySelector("[data-message-author-role='assistant']") ?? element;
    const blocks = Array.from(root.querySelectorAll(".markdown, .prose")).filter(
      (block, index, all) => !all.some((other, otherIndex) => otherIndex !== index && other.contains(block))
    );
    if (blocks.length) {
      return blocks.map((block) => block.innerText?.trim() ?? "").filter(Boolean).join("\n\n");
    }
    return root.innerText?.trim() ?? "";
  }

  function responseTextAfterPrompt(prompt) {
    const bodyText = document.body?.innerText ?? "";
    const promptLines = prompt.split("\n").map((line) => line.trim()).filter(Boolean);
    const lastPromptLine = promptLines[promptLines.length - 1] ?? "";
    if (!lastPromptLine) return "";
    const promptEnd = bodyText.lastIndexOf(lastPromptLine);
    if (promptEnd < 0) return "";
    let tail = bodyText.slice(promptEnd + lastPromptLine.length).trim();
    for (const marker of [
      "ChatGPT는 AI라 실수할 수 있습니다.",
      "ChatGPT에게 물어보세요",
      "ChatGPT와 채팅"
    ]) {
      const markerIndex = tail.indexOf(marker);
      if (markerIndex >= 0) tail = tail.slice(0, markerIndex).trim();
    }
    return tail.replace(/^(?:ChatGPT의 말|ChatGPT said):\s*/i, "").trim();
  }

  function stopButtonVisible() {
    return Boolean(document.querySelector(STOP_BUTTON_SELECTOR));
  }

  function completed(element) {
    return Boolean(element?.querySelector(COMPLETION_ACTION_SELECTOR));
  }

  async function sendPrompt(prompt, images) {
    const before = assistantMessages();
    const beforeCount = before.length;
    const previousLastText = beforeCount ? assistantText(before[beforeCount - 1]) : "";
    const input = await waitForComposer();
    await attachImages(images);
    setComposerText(input, prompt);
    await sleep(350);

    const sendButton = document.querySelector(SEND_BUTTON_SELECTOR);
    if (sendButton && !sendButton.disabled) {
      sendButton.click();
    } else {
      input.dispatchEvent(new KeyboardEvent("keydown", {
        key: "Enter", code: "Enter", keyCode: 13, which: 13, bubbles: true, cancelable: true
      }));
    }

    const deadline = Date.now() + RESPONSE_TIMEOUT_MS;
    let stableText = "";
    let stableSince = 0;
    while (Date.now() < deadline) {
      const messages = assistantMessages();
      const latest = messages[messages.length - 1];
      const text = (latest ? assistantText(latest) : "") || responseTextAfterPrompt(prompt);
      const isNew = messages.length > beforeCount || (text && text !== previousLastText);
      if (isNew && text) {
        if (completed(latest) && !stopButtonVisible()) return text;
        if (text !== stableText) {
          stableText = text;
          stableSince = Date.now();
        } else if (!stopButtonVisible() && Date.now() - stableSince > RESPONSE_STABLE_MS) {
          return text;
        }
      }
      await sleep(300);
    }
    throw new Error("ChatGPT 답변 대기 시간이 초과되었습니다.");
  }

  async function report(jobId, text, error) {
    await window.__TAURI__.core.invoke("bridge_result", { id: jobId, text, error });
  }

  async function pollBridge() {
    if (bridgeBusy || !isLumiProject()) return;
    try {
      const job = await window.__TAURI__.core.invoke("bridge_next");
      if (!job) return;
      bridgeBusy = true;
      toast("LUMI 요청을 ChatGPT에 전달합니다.");
      try {
        await window.__TAURI__.core.invoke("prewarm_gpt_sovits");
        const text = await sendPrompt(job.prompt, job.images ?? []);
        await report(job.id, text, null);
        toast("ChatGPT 답변을 꼬미에게 전달했습니다.");
      } catch (error) {
        await report(job.id, null, error instanceof Error ? error.message : String(error));
        toast("LUMI 연결 오류가 발생했습니다.", true);
      } finally {
        bridgeBusy = false;
      }
    } catch (error) {
      if (!(error instanceof TypeError)) console.debug("LUMI to GPT poll error", error);
    }
  }

  async function tick() {
    updateSettingsStatus();
    await pollBridge();
  }

  function start() {
    createProjectUi();
    autoOpenConfiguredProject();
    setInterval(tick, POLL_INTERVAL_MS);
    toast(isLumiProject() ? "LUMI 프로젝트에 연결되었습니다." : "LUMI 프로젝트에서 전용 프로젝트를 연결하세요.");
  }

  if (document.documentElement) start();
  else document.addEventListener("DOMContentLoaded", start, { once: true });
})();
