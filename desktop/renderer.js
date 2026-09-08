const HOME_URL = new URL('start_page.html', window.location.href).href;

// ---------- Хранилища (localStorage) ----------
const Store = {
  get(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch (e) {
      return fallback;
    }
  },
  set(key, value) {
    localStorage.setItem(key, JSON.stringify(value));
  }
};

const HistoryStore = {
  add(url, title) {
    if (!url || url.endsWith(HOME_URL)) return;
    const list = Store.get('history', []);
    if (list[0] && list[0].url === url) return;
    list.unshift({ url, title: title || url, timestamp: Date.now() });
    Store.set('history', list.slice(0, 300));
  },
  getAll() { return Store.get('history', []); },
  clear() { Store.set('history', []); }
};

const BookmarkStore = {
  isBookmarked(url) { return Store.get('bookmarks', []).some((b) => b.url === url); },
  toggle(url, title) {
    const list = Store.get('bookmarks', []);
    const idx = list.findIndex((b) => b.url === url);
    if (idx >= 0) { list.splice(idx, 1); Store.set('bookmarks', list); return false; }
    list.unshift({ url, title: title || url, timestamp: Date.now() });
    Store.set('bookmarks', list);
    return true;
  },
  remove(url) {
    Store.set('bookmarks', Store.get('bookmarks', []).filter((b) => b.url !== url));
  },
  getAll() { return Store.get('bookmarks', []); },
  clear() { Store.set('bookmarks', []); }
};

const Settings = {
  isAdblockEnabled() { return Store.get('adblock_enabled', true); },
  setAdblockEnabled(v) { Store.set('adblock_enabled', v); window.omegaAPI.setAdblock(v); },
  getSearchEngine() { return Store.get('search_engine', 'google'); },
  setSearchEngine(v) { Store.set('search_engine', v); },
  searchUrl(query) {
    const q = encodeURIComponent(query);
    const engine = this.getSearchEngine();
    if (engine === 'yandex') return `https://yandex.ru/search/?text=${q}`;
    if (engine === 'duckduckgo') return `https://duckduckgo.com/?q=${q}`;
    return `https://www.google.com/search?q=${q}`;
  }
};

// ---------- Вкладки ----------
let tabs = [];
let currentTabId = null;
let tabCounter = 0;

const webviewContainer = document.getElementById('webviewContainer');
const tabStrip = document.getElementById('tabStrip');
const addressBar = document.getElementById('addressBar');
const btnBack = document.getElementById('btnBack');
const btnForward = document.getElementById('btnForward');
const btnReload = document.getElementById('btnReload');
const progressBar = document.getElementById('progressBar');
const sslIndicator = document.getElementById('sslIndicator');

const ICON_LOCK_SECURE = '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="#0B8043" d="M12,17c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM8.9,6c0,-1.71 1.39,-3.1 3.1,-3.1s3.1,1.39 3.1,3.1v2H8.9V6zM18,20L6,20L6,10h12v10z"/></svg>';
const ICON_LOCK_INSECURE = '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="#D93025" d="M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z"/></svg>';

function isPrivateAllowedUrl(url) {
  return url && url !== HOME_URL;
}

function createTab(url, isPrivate) {
  const id = 'tab-' + (++tabCounter);
  const partition = isPrivate ? `private-${id}` : 'persist:omegarouser';
  if (isPrivate) window.omegaAPI.setupPrivateSession(partition);

  const webview = document.createElement('webview');
  webview.setAttribute('partition', partition);
  webview.setAttribute('src', url || HOME_URL);
  webview.style.display = 'none';
  webviewContainer.appendChild(webview);

  const tab = { id, webview, isPrivate, title: 'Новая вкладка', url: url || HOME_URL };
  tabs.push(tab);
  attachWebviewEvents(tab);
  switchTab(id);
  renderTabStrip();
  return tab;
}

function attachWebviewEvents(tab) {
  const wv = tab.webview;

  wv.addEventListener('did-start-loading', () => {
    if (tab.id === currentTabId) { progressBar.style.display = 'block'; progressBar.value = 20; }
  });
  wv.addEventListener('did-stop-loading', () => {
    if (tab.id === currentTabId) progressBar.style.display = 'none';
  });
  wv.addEventListener('did-navigate', (e) => {
    tab.url = e.url;
    if (tab.id === currentTabId) {
      addressBar.value = displayUrl(tab.url);
      updateSslIndicator(tab.url);
      updateNavButtons();
    }
    renderTabStrip();
  });
  wv.addEventListener('did-navigate-in-page', (e) => {
    tab.url = e.url;
    if (tab.id === currentTabId) addressBar.value = displayUrl(tab.url);
  });
  wv.addEventListener('page-title-updated', (e) => {
    tab.title = e.title;
    renderTabStrip();
  });
  wv.addEventListener('dom-ready', () => {
    injectBrandingScript(wv);
    if (isPrivateAllowedUrl(tab.url) && !tab.isPrivate) {
      HistoryStore.add(tab.url, tab.title);
    }
  });
  wv.addEventListener('found-in-page', (e) => {
    const r = e.result;
    document.getElementById('findMatchCount').textContent =
      r.matches > 0 ? `${r.activeMatchOrdinal}/${r.matches}` : '0/0';
  });
}

function switchTab(id) {
  tabs.forEach((t) => { t.webview.style.display = t.id === id ? 'block' : 'none'; });
  currentTabId = id;
  const tab = tabs.find((t) => t.id === id);
  if (tab) {
    addressBar.value = displayUrl(tab.url);
    updateSslIndicator(tab.url);
    updateNavButtons();
  }
  renderTabStrip();
}

function closeTab(id) {
  const idx = tabs.findIndex((t) => t.id === id);
  if (idx < 0) return;
  const closed = tabs[idx];
  webviewContainer.removeChild(closed.webview);
  tabs.splice(idx, 1);

  if (tabs.length === 0) {
    createTab(HOME_URL, false);
    return;
  }
  if (currentTabId === id) {
    const newIdx = Math.max(0, idx - 1);
    switchTab(tabs[newIdx].id);
  }
  renderTabStrip();
}

function renderTabStrip() {
  tabStrip.innerHTML = '';
  tabs.forEach((t) => {
    const chip = document.createElement('div');
    chip.className = 'tab-chip' + (t.id === currentTabId ? ' active' : '') + (t.isPrivate ? ' private' : '');
    chip.innerHTML = `<span class="tab-title">${escapeHtml(t.title || 'Новая вкладка')}</span><span class="tab-close">✕</span>`;
    chip.querySelector('.tab-title').addEventListener('click', () => switchTab(t.id));
    chip.querySelector('.tab-close').addEventListener('click', (e) => { e.stopPropagation(); closeTab(t.id); });
    tabStrip.appendChild(chip);
  });
}

function currentWebview() {
  const tab = tabs.find((t) => t.id === currentTabId);
  return tab ? tab.webview : null;
}
function currentTab() {
  return tabs.find((t) => t.id === currentTabId);
}

function displayUrl(url) {
  return url === HOME_URL ? '' : url;
}

function updateSslIndicator(url) {
  if (!url || url === HOME_URL) { sslIndicator.style.display = 'none'; return; }
  if (url.startsWith('https://')) {
    sslIndicator.style.display = 'inline-block';
    sslIndicator.innerHTML = ICON_LOCK_SECURE;
  } else if (url.startsWith('http://')) {
    sslIndicator.style.display = 'inline-block';
    sslIndicator.innerHTML = ICON_LOCK_INSECURE;
  } else {
    sslIndicator.style.display = 'none';
  }
}

function updateNavButtons() {
  const wv = currentWebview();
  btnBack.disabled = !(wv && wv.canGoBack());
  btnForward.disabled = !(wv && wv.canGoForward());
}

function escapeHtml(s) {
  return (s || '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// ---------- Навигация ----------
const URL_REGEX = /^(https?:\/\/)?([\w-]+\.)+[\w-]{2,}(\/\S*)?$/i;

function navigateFromInput(input) {
  const query = (input || '').trim();
  if (!query) return;
  let url;
  if (URL_REGEX.test(query)) {
    url = query.startsWith('http://') || query.startsWith('https://') ? query : `https://${query}`;
  } else {
    url = Settings.searchUrl(query);
  }
  const wv = currentWebview();
  if (wv) wv.loadURL(url);
}

// ---------- Внедрение замены брендинга ----------
function injectBrandingScript(webview) {
  const js = `
    (function() {
      function shouldSkip(node) {
        var p = node.parentNode;
        if (!p) return false;
        var tag = p.nodeName;
        return tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT' || tag === 'TEXTAREA' || tag === 'INPUT';
      }
      function replaceIn(node) {
        if (node.nodeType === 3) {
          if (shouldSkip(node)) return;
          var t = node.nodeValue;
          if (/Gemini|Google/.test(t)) {
            node.nodeValue = t.replace(/Gemini/g, 'Omegarouser').replace(/Google/g, 'Omegarouser');
          }
        } else if (node.nodeType === 1) {
          for (var i = 0; i < node.childNodes.length; i++) replaceIn(node.childNodes[i]);
        }
      }
      function replaceLogoImages(root) {
        if (!root || !root.querySelectorAll) return;
        var nodes = root.querySelectorAll('img[alt*="Google"], img[title*="Google"], svg[aria-label*="Google"], [aria-label="Google"]');
        nodes.forEach(function(el) {
          if (el.dataset && el.dataset.omegarouserLogoDone) return;
          if (el.dataset) el.dataset.omegarouserLogoDone = '1';
          var h = el.offsetHeight || 32;
          var span = document.createElement('span');
          span.textContent = 'Omegarouser';
          span.style.fontWeight = '700';
          span.style.fontSize = Math.max(16, Math.min(h, 40)) + 'px';
          span.style.color = '#137333';
          el.style.display = 'none';
          if (el.parentNode) el.parentNode.insertBefore(span, el);
        });
      }
      if (document.body) { replaceIn(document.body); replaceLogoImages(document); }
      if (!window.__omegarouserObserver) {
        window.__omegarouserObserver = new MutationObserver(function(muts) {
          muts.forEach(function(m) { m.addedNodes.forEach(function(n) { replaceIn(n); if (n.nodeType === 1) replaceLogoImages(n); }); });
          replaceLogoImages(document);
        });
        if (document.body) window.__omegarouserObserver.observe(document.body, { childList: true, subtree: true, characterData: true });
      }
    })();
  `;
  webview.executeJavaScript(js).catch(() => {});
}

// ---------- Панель поиска на странице ----------
const findBar = document.getElementById('findBar');
const findQuery = document.getElementById('findQuery');

function showFindBar() {
  findBar.style.display = 'flex';
  findQuery.focus();
}
function hideFindBar() {
  findBar.style.display = 'none';
  const wv = currentWebview();
  if (wv) wv.stopFindInPage('clearSelection');
  findQuery.value = '';
  document.getElementById('findMatchCount').textContent = '';
}
findQuery.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    const wv = currentWebview();
    if (wv) wv.findInPage(findQuery.value);
  }
});
document.getElementById('findNext').addEventListener('click', () => {
  const wv = currentWebview();
  if (wv) wv.findInPage(findQuery.value, { forward: true, findNext: true });
});
document.getElementById('findPrev').addEventListener('click', () => {
  const wv = currentWebview();
  if (wv) wv.findInPage(findQuery.value, { forward: false, findNext: true });
});
document.getElementById('findClose').addEventListener('click', hideFindBar);

// ---------- Меню ----------
const menuDropdown = document.getElementById('menuDropdown');
document.getElementById('btnMenu').addEventListener('click', (e) => {
  e.stopPropagation();
  const isBookmarked = currentTab() && BookmarkStore.isBookmarked(currentTab().url);
  document.querySelector('[data-action="bookmark-toggle"]').textContent =
    isBookmarked ? 'Убрать из закладок' : 'Добавить в закладки';
  menuDropdown.style.display = menuDropdown.style.display === 'block' ? 'none' : 'block';
});
document.addEventListener('click', () => { menuDropdown.style.display = 'none'; });

menuDropdown.addEventListener('click', (e) => {
  const action = e.target.dataset.action;
  if (!action) return;
  const tab = currentTab();
  switch (action) {
    case 'home': if (tab) tab.webview.loadURL(HOME_URL); break;
    case 'bookmark-toggle':
      if (tab && isPrivateAllowedUrl(tab.url)) {
        const added = BookmarkStore.toggle(tab.url, tab.title);
        alert(added ? 'Добавлено в закладки' : 'Убрано из закладок');
      }
      break;
    case 'bookmarks': openPanel('panelBookmarks'); renderBookmarksList(); break;
    case 'history': openPanel('panelHistory'); renderHistoryList(); break;
    case 'downloads': openPanel('panelDownloads'); renderDownloadsList(); break;
    case 'find': showFindBar(); break;
    case 'private-tab': createTab(HOME_URL, true); break;
    case 'settings': openPanel('panelSettings'); loadSettingsUI(); break;
  }
});

// ---------- Панели (оверлеи) ----------
const panelOverlay = document.getElementById('panelOverlay');
function openPanel(panelId) {
  panelOverlay.style.display = 'flex';
  document.querySelectorAll('#panelOverlay .panel').forEach((p) => { p.style.display = 'none'; });
  document.getElementById(panelId).style.display = 'flex';
}
function closeAllPanels() { panelOverlay.style.display = 'none'; }
panelOverlay.addEventListener('click', (e) => { if (e.target === panelOverlay) closeAllPanels(); });
document.getElementById('closeHistoryPanel').addEventListener('click', closeAllPanels);
document.getElementById('closeBookmarksPanel').addEventListener('click', closeAllPanels);
document.getElementById('closeDownloadsPanel').addEventListener('click', closeAllPanels);
document.getElementById('closeSettingsPanel').addEventListener('click', closeAllPanels);

document.querySelectorAll('[data-clear]').forEach((btn) => {
  btn.addEventListener('click', () => {
    const kind = btn.dataset.clear;
    if (kind === 'history') { HistoryStore.clear(); renderHistoryList(); }
    if (kind === 'bookmarks') { BookmarkStore.clear(); renderBookmarksList(); }
  });
});

function renderHistoryList() {
  const list = document.getElementById('historyList');
  const items = HistoryStore.getAll();
  if (items.length === 0) { list.innerHTML = '<div class="list-empty">Пока пусто</div>'; return; }
  list.innerHTML = items.map((it) => `
    <div class="list-item" data-url="${escapeHtml(it.url)}">
      <div class="li-main"><div class="li-title">${escapeHtml(it.title)}</div><div class="li-sub">${escapeHtml(it.url)}</div></div>
    </div>`).join('');
  list.querySelectorAll('.list-item').forEach((el) => {
    el.addEventListener('click', () => { navigateFromInput(el.dataset.url); closeAllPanels(); });
  });
}

function renderBookmarksList() {
  const list = document.getElementById('bookmarksList');
  const items = BookmarkStore.getAll();
  if (items.length === 0) { list.innerHTML = '<div class="list-empty">Пока пусто — добавь через меню ⋮</div>'; return; }
  list.innerHTML = items.map((it) => `
    <div class="list-item" data-url="${escapeHtml(it.url)}">
      <div class="li-main"><div class="li-title">${escapeHtml(it.title)}</div><div class="li-sub">${escapeHtml(it.url)}</div></div>
      <div class="li-remove">✕</div>
    </div>`).join('');
  list.querySelectorAll('.list-item').forEach((el) => {
    el.querySelector('.li-main').addEventListener('click', () => { navigateFromInput(el.dataset.url); closeAllPanels(); });
    el.querySelector('.li-remove').addEventListener('click', (e) => {
      e.stopPropagation();
      BookmarkStore.remove(el.dataset.url);
      renderBookmarksList();
    });
  });
}

const downloadsMap = {};
window.omegaAPI.onDownloadUpdate((data) => {
  downloadsMap[data.id] = data;
  renderDownloadsList();
});
function renderDownloadsList() {
  const list = document.getElementById('downloadsList');
  const items = Object.values(downloadsMap).sort((a, b) => 0);
  if (items.length === 0) { list.innerHTML = '<div class="list-empty">Пока пусто</div>'; return; }
  list.innerHTML = items.map((it) => {
    const percent = it.totalBytes > 0 ? Math.round((it.receivedBytes / it.totalBytes) * 100) : 0;
    const status = it.state === 'progressing' ? `${percent}%` : it.state === 'completed' ? 'Готово' : it.state;
    return `<div class="list-item"><div class="li-main"><div class="li-title">${escapeHtml(it.fileName)}</div><div class="li-sub">${status}</div></div></div>`;
  }).join('');
}

function loadSettingsUI() {
  document.getElementById('settingsAdblock').checked = Settings.isAdblockEnabled();
  document.querySelectorAll('input[name="searchEngine"]').forEach((r) => {
    r.checked = r.value === Settings.getSearchEngine();
  });
}
document.getElementById('settingsAdblock').addEventListener('change', (e) => {
  Settings.setAdblockEnabled(e.target.checked);
});
document.querySelectorAll('input[name="searchEngine"]').forEach((r) => {
  r.addEventListener('change', (e) => { if (e.target.checked) Settings.setSearchEngine(e.target.value); });
});

// ---------- Вкладки: панель списка ----------
const tabsOverlay = document.getElementById('tabsOverlay');
document.getElementById('btnNewTab').addEventListener('click', () => createTab(HOME_URL, false));
document.getElementById('closeTabsOverlay').addEventListener('click', () => { tabsOverlay.style.display = 'none'; });

// ---------- Автодополнение ----------
const suggestionsBox = document.getElementById('suggestions');
addressBar.addEventListener('input', () => {
  const q = addressBar.value.trim().toLowerCase();
  if (!q) { suggestionsBox.style.display = 'none'; return; }
  const combined = [...BookmarkStore.getAll(), ...HistoryStore.getAll()];
  const seen = new Set();
  const matches = combined.filter((it) => {
    if (seen.has(it.url)) return false;
    const hit = it.title.toLowerCase().includes(q) || it.url.toLowerCase().includes(q);
    if (hit) seen.add(it.url);
    return hit;
  }).slice(0, 6);
  if (matches.length === 0) { suggestionsBox.style.display = 'none'; return; }
  suggestionsBox.innerHTML = matches.map((m) => `
    <div class="suggestion-item" data-url="${escapeHtml(m.url)}">
      <div class="s-title">${escapeHtml(m.title)}</div>
      <div class="s-url">${escapeHtml(m.url)}</div>
    </div>`).join('');
  suggestionsBox.style.display = 'block';
  suggestionsBox.querySelectorAll('.suggestion-item').forEach((el) => {
    el.addEventListener('click', () => {
      addressBar.value = el.dataset.url;
      navigateFromInput(el.dataset.url);
      suggestionsBox.style.display = 'none';
    });
  });
});
addressBar.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { navigateFromInput(addressBar.value); suggestionsBox.style.display = 'none'; }
});
document.addEventListener('click', (e) => {
  if (e.target !== addressBar) suggestionsBox.style.display = 'none';
});

// ---------- Кнопки навигации ----------
btnBack.addEventListener('click', () => { const wv = currentWebview(); if (wv && wv.canGoBack()) wv.goBack(); });
btnForward.addEventListener('click', () => { const wv = currentWebview(); if (wv && wv.canGoForward()) wv.goForward(); });
btnReload.addEventListener('click', () => { const wv = currentWebview(); if (wv) wv.reload(); });

// ---------- Запуск ----------
window.omegaAPI.setAdblock(Settings.isAdblockEnabled());
createTab(HOME_URL, false);
