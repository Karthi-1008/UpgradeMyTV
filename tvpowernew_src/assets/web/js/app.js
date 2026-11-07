// TV Remote Application Logic
(function() {
  function resolveApiKey() {
    try {
      const hash = window.location.hash || '';
      if (hash.startsWith('#k=')) {
        const token = hash.substring(3).trim();
        if (token) {
          localStorage.setItem('tv_pairing_key', token);
          history.replaceState(null, '', window.location.pathname + window.location.search);
          return token;
        }
      }
      const params = new URLSearchParams(window.location.search);
      const urlKey = params.get('key');
      if (urlKey) {
        localStorage.setItem('tv_pairing_key', urlKey);
        return urlKey;
      }
    } catch (ignored) {}
    return localStorage.getItem('tv_pairing_key') || "TV_POWER_SECURE_KEY";
  }

  let API_KEY = resolveApiKey();
  const ENDPOINT = "/cgi-bin/api";

  // Application State
  const state = {
    connected: false,
    latency: null,
    tvName: localStorage.getItem('tv_remote_name') || "Living Room TV",
    theme: localStorage.getItem('tv_remote_theme') || "dark",
    leftHand: localStorage.getItem('tv_remote_left_hand') === 'true',
    navMode: localStorage.getItem('tv_remote_nav_mode') || "buttons",
    liveTyping: localStorage.getItem('tv_remote_live_typing') !== 'false',
    recentSearches: JSON.parse(localStorage.getItem('tv_remote_recents') || '[]'),
    activeTab: 'remote',
    wakeLock: null,
    lastAvailKb: null,
    // Files State
    currentPath: '/storage/emulated/0/Download',
    clipboard: null, // { action: 'copy', path: '', name: '', is_dir: false }
    drives: []
  };

  // DOM Elements cache
  const el = {};

  function initElements() {
    el.tvName = document.getElementById('tvNameDisplay');
    el.connectionDot = document.getElementById('connectionDot');
    el.connectionText = document.getElementById('connectionText');
    el.toast = document.getElementById('toast');
    el.tabPanels = document.querySelectorAll('.tab-page');
    el.tabButtons = document.querySelectorAll('.tab-item');
    el.moreSheet = document.getElementById('moreControlsSheet');
    el.settingsSheet = document.getElementById('settingsSheet');
    el.sheetBackdrop = document.getElementById('sheetBackdrop');
    el.btnPower = document.getElementById('btnPower');
    el.powerProgress = document.getElementById('powerProgress');
    el.dpadSvg = document.getElementById('dpadSvg');
    el.swipePad = document.getElementById('swipePad');
    el.btnModeButtons = document.getElementById('btnModeButtons');
    el.btnModeSwipe = document.getElementById('btnModeSwipe');
    el.btnMute = document.getElementById('btnMute');
    
    // Type elements
    el.typeInput = document.getElementById('typeInput');
    el.liveTypingSwitch = document.getElementById('liveTypingSwitch');
    el.btnSendText = document.getElementById('btnSendText');
    el.recentChips = document.getElementById('recentChips');
    
    // Screen elements
    el.screenImg = document.getElementById('screenImg');
    el.screenEmpty = document.getElementById('screenEmpty');
    el.screenShimmer = document.getElementById('screenShimmer');
    el.btnCaptureHero = document.getElementById('btnCaptureHero');
    el.btnSaveScreen = document.getElementById('btnSaveScreen');
    el.btnViewScreen = document.getElementById('btnViewScreen');
    
    // Files elements (2 Main Storage Options: TV & USB)
    el.btnStorageTv = document.getElementById('btnStorageTv');
    el.btnStorageUsb = document.getElementById('btnStorageUsb');
    el.tvStorageSub = document.getElementById('tvStorageSub');
    el.usbStorageSub = document.getElementById('usbStorageSub');
    el.pasteBanner = document.getElementById('pasteBanner');
    el.pasteItemName = document.getElementById('pasteItemName');
    el.pasteItemSize = document.getElementById('pasteItemSize');
    el.btnPasteAction = document.getElementById('btnPasteAction');
    el.btnCancelPaste = document.getElementById('btnCancelPaste');
    el.readOnlyNotice = document.getElementById('readOnlyNotice');
    el.btnFolderUp = document.getElementById('btnFolderUp');
    el.currentPathText = document.getElementById('currentPathText');
    el.btnUploadCurrent = document.getElementById('btnUploadCurrent');
    el.btnRefreshFiles = document.getElementById('btnRefreshFiles');
    el.uploadProgressWrap = document.getElementById('uploadProgressWrap');
    el.uploadStatusText = document.getElementById('uploadStatusText');
    el.uploadPercentText = document.getElementById('uploadPercentText');
    el.uploadProgressBar = document.getElementById('uploadProgressBar');
    el.fileUploadInput = document.getElementById('fileUploadInput');
    el.fileListWrap = document.getElementById('fileListWrap');
    el.mediaModal = document.getElementById('mediaModal');
    el.mediaModalTitle = document.getElementById('mediaModalTitle');
    el.btnCloseMediaModal = document.getElementById('btnCloseMediaModal');
    el.mediaVideoPlayer = document.getElementById('mediaVideoPlayer');
    el.mediaAudioPlayer = document.getElementById('mediaAudioPlayer');
    el.mediaImageViewer = document.getElementById('mediaImageViewer');
    el.videoAdjustBar = document.getElementById('videoAdjustBar');
    el.btnVideoSpeed = document.getElementById('btnVideoSpeed');
    el.btnVideoFit = document.getElementById('btnVideoFit');
    el.btnVideoFullscreen = document.getElementById('btnVideoFullscreen');

    // Tools elements
    el.linkUrlInput = document.getElementById('linkUrlInput');
    el.btnOpenSmartTube = document.getElementById('btnOpenSmartTube');
    el.btnOpenYouTube = document.getElementById('btnOpenYouTube');
    el.btnOpenBrowser = document.getElementById('btnOpenBrowser');
    el.btnBoostRam = document.getElementById('btnBoostRam');
    el.ramSub = document.getElementById('ramSub');
    el.btnRefreshStatus = document.getElementById('btnRefreshStatus');
    el.dashModel = document.getElementById('dashModel');
    el.dashAndroid = document.getElementById('dashAndroid');
    el.dashRamText = document.getElementById('dashRamText');
    el.dashRamBar = document.getElementById('dashRamBar');
    el.dashStorageText = document.getElementById('dashStorageText');
    el.dashStorageBar = document.getElementById('dashStorageBar');
    el.dashDisplay = document.getElementById('dashDisplay');
    el.dashCpu = document.getElementById('dashCpu');
    el.dashUsb = document.getElementById('dashUsb');
    el.dashUptime = document.getElementById('dashUptime');
    el.dashFocus = document.getElementById('dashFocus');

    // V2: Groq AI elements
    el.groqApiKeyInput = document.getElementById('groqApiKeyInput');
    el.btnSaveGroqKey = document.getElementById('btnSaveGroqKey');
    el.aiKeyStatusPill = document.getElementById('aiKeyStatusPill');
    el.aiCategoryChips = document.getElementById('aiCategoryChips');
    el.aiLanguageSelect = document.getElementById('aiLanguageSelect');
    el.aiGenreSelect = document.getElementById('aiGenreSelect');
    el.aiMoodInput = document.getElementById('aiMoodInput');
    el.btnGetAiRecs = document.getElementById('btnGetAiRecs');
    el.aiRecsContainer = document.getElementById('aiRecsContainer');
    el.aiSummarizerSwitch = document.getElementById('aiSummarizerSwitch');

    // V2: Audio EQ & Display elements
    el.btnAudioDialogue = document.getElementById('btnAudioDialogue');
    el.btnAudioNight = document.getElementById('btnAudioNight');
    el.btnAudioMusic = document.getElementById('btnAudioMusic');
    el.btnAudioFlat = document.getElementById('btnAudioFlat');
    el.btnPicCinema = document.getElementById('btnPicCinema');
    el.btnPicSports = document.getElementById('btnPicSports');
    el.btnPicGame = document.getElementById('btnPicGame');
    el.btnPicStd = document.getElementById('btnPicStd');

    // V2: Notification Relay elements
    el.customAlertInput = document.getElementById('customAlertInput');
    el.btnSendCustomAlert = document.getElementById('btnSendCustomAlert');
    el.btnAlertCall = document.getElementById('btnAlertCall');
    el.btnAlertBattery = document.getElementById('btnAlertBattery');
    el.btnAlertMsg = document.getElementById('btnAlertMsg');
    el.autoBatteryAlertSwitch = document.getElementById('autoBatteryAlertSwitch');


    // V2: Automations elements
    el.sleepTimerBtnsGroup = document.getElementById('sleepTimerBtnsGroup');

    // Settings elements
    el.themeSelect = document.getElementById('themeSelect');
    el.hapticsSwitch = document.getElementById('hapticsSwitch');
    el.leftHandSwitch = document.getElementById('leftHandSwitch');
    el.tvNameInput = document.getElementById('tvNameInput');
  }

  // Toast Notification
  let toastTimer = null;
  function showToast(msg) {
    if (!el.toast) return;
    el.toast.textContent = msg;
    el.toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      el.toast.classList.remove('show');
    }, 2200);
  }

  // Visual Ack / Error Feedback
  function triggerBlip(targetEl) {
    if (!targetEl) return;
    targetEl.classList.add('blip');
    setTimeout(() => targetEl.classList.remove('blip'), 180);
  }

  function triggerShake(targetEl) {
    if (!targetEl) return;
    targetEl.classList.add('shake');
    setTimeout(() => targetEl.classList.remove('shake'), 260);
  }

// WebSocket Ultra-Low-Latency Remote Channel (A2)
  let ws = null;
  let wsReady = false;
  let wsReqId = 1;
  const wsPendingAcks = new Map();

  function initWebSocket() {
    try {
      const loc = window.location;
      const wsProto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = ${wsProto}///ws?key=;
      ws = new WebSocket(wsUrl);

      ws.onopen = () => {
        wsReady = true;
        console.log('[WS] Connected to TV Powerhouse WebSocket (< 40ms channel)');
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.ack && wsPendingAcks.has(data.ack)) {
            const cb = wsPendingAcks.get(data.ack);
            wsPendingAcks.delete(data.ack);
            cb(data);
          }
        } catch (ignored) {}
      };

      ws.onclose = () => {
        wsReady = false;
        setTimeout(initWebSocket, 2500);
      };

      ws.onerror = () => {
        wsReady = false;
      };
    } catch (e) {
      wsReady = false;
    }
  }

  // Unified Send Action
  async function send(action, params = {}, triggerElement = null) {
    // Ultra-fast path: route keys over WebSocket when connected (< 40ms)
    if (action === 'key' && wsReady && ws && ws.readyState === WebSocket.OPEN) {
      if (triggerElement) triggerBlip(triggerElement);
      const code = parseInt(params.code, 10);
      const id = ++wsReqId;
      try {
        ws.send(JSON.stringify({ t: 'key', c: code, id: id }));
        return { status: 'ok', via: 'ws', code: code };
      } catch (wsErr) {
        // Fall back to HTTP fetch below if send fails
      }
    }

    const url = new URL(ENDPOINT, window.location.origin);
    url.searchParams.set('action', action);
    url.searchParams.set('key', API_KEY);
    for (const [k, v] of Object.entries(params)) {
      url.searchParams.set(k, v);
    }

    try {
      const res = await fetch(url.toString(), {
        cache: 'no-store',
        headers: {
          'X-TVHub-Key': API_KEY
        }
      });
      if (res.ok) {
        if (triggerElement) triggerBlip(triggerElement);
        return await res.json();
      } else {
        const errJson = await res.json().catch(() => null);
        const errMsg = errJson?.message || ('Server error ' + res.status);
        throw new Error(errMsg);
      }
    } catch (err) {
      if (triggerElement) triggerShake(triggerElement);
      if (err.name === 'TypeError' || err.message.includes('fetch') || err.message.includes('NetworkError') || err.message.includes('Failed to fetch')) {
        updateConnectionStatus(false, null, "Couldn't reach TV — retrying");
      }
      throw err;
    }
  }

  // Hold-Repeat Helper (Section 12.4)
  function holdRepeat(element, actionFn, { delay = 350, every = 90 } = {}) {
    if (!element) return;
    let t1 = null, t2 = null;

    const stop = () => {
      clearTimeout(t1);
      clearInterval(t2);
      t1 = null;
      t2 = null;
      element.classList.remove('is-down');
    };

    element.addEventListener('pointerdown', e => {
      e.preventDefault();
      element.classList.add('is-down');
      actionFn(element);
      window.Haptics?.tap();

      t1 = setTimeout(() => {
        t2 = setInterval(() => {
          actionFn(element);
          window.Haptics?.tap();
        }, every);
      }, delay);
    });

    ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => {
      element.addEventListener(ev, stop);
    });
  }

  // Power Button Hold Logic (600ms)
  function setupPowerHold() {
    if (!el.btnPower || !el.powerProgress) return;
    let timer = null;
    let pressStart = 0;

    const resetRing = () => {
      if (timer) {
        clearTimeout(timer);
        timer = null;
        if (Date.now() - pressStart < 600) {
          showToast('Hold 0.6s to turn off TV');
        }
      }
      el.powerProgress.style.transition = 'none';
      el.powerProgress.style.strokeDashoffset = '100';
      el.btnPower.classList.remove('is-down');
    };

    el.btnPower.addEventListener('pointerdown', e => {
      e.preventDefault();
      pressStart = Date.now();
      el.btnPower.classList.add('is-down');
      el.powerProgress.style.transition = 'stroke-dashoffset 600ms linear';
      el.powerProgress.style.strokeDashoffset = '0';
      window.Haptics?.tap();

      timer = setTimeout(async () => {
        timer = null;
        window.Haptics?.longPress();
        showToast('TV Powering off…');
        try {
          await send('key', { code: 26 }, el.btnPower);
        } catch (e) {}
        resetRing();
      }, 600);
    });

    ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => {
      el.btnPower.addEventListener(ev, resetRing);
    });
  }

  // D-Pad Setup
  function setupDpad() {
    const keyMap = {
      UP: 19,
      DOWN: 20,
      LEFT: 21,
      RIGHT: 22,
      OK: 23
    };

    // Segments hold-repeat
    ['UP', 'DOWN', 'LEFT', 'RIGHT'].forEach(dir => {
      const seg = el.dpadSvg?.querySelector(`[data-key="${dir}"]`);
      if (seg) {
        holdRepeat(seg, () => {
          send('key', { code: keyMap[dir] }, seg);
        }, { delay: 350, every: 90 });
      }
    });

    // OK button
    const okBtn = el.dpadSvg?.querySelector('[data-key="OK"]');
    if (okBtn) {
      okBtn.addEventListener('pointerdown', e => {
        e.preventDefault();
        okBtn.classList.add('is-down');
        window.Haptics?.ok();
        send('key', { code: keyMap.OK }, okBtn);
      });
      ['pointerup', 'pointerleave', 'pointercancel'].forEach(ev => {
        okBtn.addEventListener(ev, () => okBtn.classList.remove('is-down'));
      });
    }

    // Swipe Trackpad
    if (el.swipePad) {
      let startX = 0, startY = 0, curX = 0, curY = 0, moved = false;
      const SWIPE_THRESHOLD = 44;

      el.swipePad.addEventListener('pointerdown', e => {
        e.preventDefault();
        startX = e.clientX;
        startY = e.clientY;
        curX = startX;
        curY = startY;
        moved = false;
        el.swipePad.setPointerCapture(e.pointerId);
      });

      el.swipePad.addEventListener('pointermove', e => {
        if (!e.buttons) return;
        curX = e.clientX;
        curY = e.clientY;
        const dx = curX - startX;
        const dy = curY - startY;

        if (Math.abs(dx) > SWIPE_THRESHOLD) {
          moved = true;
          const code = dx > 0 ? keyMap.RIGHT : keyMap.LEFT;
          send('key', { code });
          window.Haptics?.tap();
          startX = curX;
          startY = curY;
        } else if (Math.abs(dy) > SWIPE_THRESHOLD) {
          moved = true;
          const code = dy > 0 ? keyMap.DOWN : keyMap.UP;
          send('key', { code });
          window.Haptics?.tap();
          startX = curX;
          startY = curY;
        }
      });

      el.swipePad.addEventListener('pointerup', e => {
        if (!moved) {
          send('key', { code: keyMap.OK });
          window.Haptics?.ok();
          showToast('OK');
        }
      });
    }

    // Navigation mode switch
    el.btnModeButtons?.addEventListener('click', () => setNavMode('buttons'));
    el.btnModeSwipe?.addEventListener('click', () => setNavMode('swipe'));
  }

  function setNavMode(mode) {
    state.navMode = mode;
    localStorage.setItem('tv_remote_nav_mode', mode);
    if (mode === 'swipe') {
      el.swipePad?.classList.add('active');
      el.btnModeSwipe?.classList.add('active');
      el.btnModeButtons?.classList.remove('active');
    } else {
      el.swipePad?.classList.remove('active');
      el.btnModeButtons?.classList.add('active');
      el.btnModeSwipe?.classList.remove('active');
    }
  }

  // Setup Pill Buttons & Lower Columns
  function setupButtons() {
    const btnBack = document.getElementById('btnBack');
    const btnHome = document.getElementById('btnHome');
    const btnMore = document.getElementById('btnMore');

    btnBack?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 4 }, btnBack);
    });

    btnHome?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 3 }, btnHome);
    });

    btnMore?.addEventListener('click', () => {
      window.Haptics?.tap();
      openSheet(el.moreSheet);
    });

    // Volume Rocker (Hold-Repeat supported)
    const btnVolUp = document.getElementById('btnVolUp');
    const btnVolDown = document.getElementById('btnVolDown');

    holdRepeat(btnVolUp, el => {
      send('key', { code: 24 }, el);
    }, { delay: 350, every: 120 });

    holdRepeat(btnVolDown, el => {
      send('key', { code: 25 }, el);
    }, { delay: 350, every: 120 });

    // Mute
    el.btnMute?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 164 }, el.btnMute);
    });

    // Transport (Play/Pause, Rewind, Fast Forward - F-08 button ID fix)
    const btnPlayPause = document.getElementById('btnPlayPause');
    const btnRewind = document.getElementById('btnSkipBack') || document.getElementById('btnRewind');
    const btnFastForward = document.getElementById('btnSkipFwd') || document.getElementById('btnFastForward');

    btnPlayPause?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 85 }, btnPlayPause);
    });

    btnRewind?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 89 }, btnRewind);
    });

    btnFastForward?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 90 }, btnFastForward);
    });
  }

  // Bottom Sheets Management
  function openSheet(sheetEl) {
    if (!sheetEl) return;
    el.sheetBackdrop?.classList.add('open');
    sheetEl.classList.add('open');
  }

  function closeSheets() {
    el.sheetBackdrop?.classList.remove('open');
    document.querySelectorAll('.bottom-sheet').forEach(s => s.classList.remove('open'));
  }

  function setupSheets() {
    el.sheetBackdrop?.addEventListener('click', closeSheets);
    document.querySelectorAll('.btn-sheet-close').forEach(btn => btn.addEventListener('click', closeSheets));

    // More Controls items
    document.getElementById('sheetBtnInput')?.addEventListener('click', () => {
      send('key', { code: 178 });
      window.Haptics?.tap();
      closeSheets();
      showToast('Input Source');
    });
    document.getElementById('sheetBtnSettings')?.addEventListener('click', () => {
      send('key', { code: 176 });
      window.Haptics?.tap();
      closeSheets();
      showToast('TV Settings');
    });
    document.getElementById('sheetBtnScreenOff')?.addEventListener('click', () => {
      send('key', { code: 223 });
      window.Haptics?.tap();
      closeSheets();
      showToast('Screen Off');
    });

    // TV Name click opens Settings Sheet
    document.getElementById('tvInfoTrigger')?.addEventListener('click', () => openSheet(el.settingsSheet));
    document.getElementById('btnHeaderSettings')?.addEventListener('click', () => openSheet(el.settingsSheet));
  }

  // Tab Navigation & Wake Lock
  function setupTabs() {
    el.tabButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const targetTab = btn.getAttribute('data-tab');
        switchTab(targetTab);
      });
    });
  }

  async function switchTab(tabId) {
    state.activeTab = tabId;
    window.Haptics?.tap();

    el.tabButtons.forEach(btn => {
      if (btn.getAttribute('data-tab') === tabId) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    el.tabPanels.forEach(p => {
      if (p.id === 'tab-' + tabId) {
        p.classList.add('active');
      } else {
        p.classList.remove('active');
      }
    });

    // Wake Lock handling
    if (tabId === 'remote') {
      acquireWakeLock();
    } else {
      releaseWakeLock();
    }

    // Refresh status if entering tools tab
    if (tabId === 'tools') {
      refreshTvStatus();
    }

    // Load file list if entering files tab
    if (tabId === 'files') {
      loadDirectory(state.currentPath);
    }
  }

  async function acquireWakeLock() {
    if ('wakeLock' in navigator && !state.wakeLock) {
      try {
        state.wakeLock = await navigator.wakeLock.request('screen');
        state.wakeLock.addEventListener('release', () => {
          state.wakeLock = null;
        });
      } catch (e) {}
    }
  }

  function releaseWakeLock() {
    if (state.wakeLock) {
      try {
        state.wakeLock.release();
      } catch (e) {}
      state.wakeLock = null;
    }
  }

  // Type Tab Setup
  let typeDebounceTimer = null;
  function setupTypeTab() {
    if (!el.typeInput) return;

    // Switch for Live typing
    if (el.liveTypingSwitch) {
      el.liveTypingSwitch.checked = state.liveTyping;
      updateLiveTypingUI();

      el.liveTypingSwitch.addEventListener('change', () => {
        state.liveTyping = el.liveTypingSwitch.checked;
        localStorage.setItem('tv_remote_live_typing', state.liveTyping ? 'true' : 'false');
        updateLiveTypingUI();
      });
    }

    // Live typing input handler
    el.typeInput.addEventListener('input', e => {
      if (!state.liveTyping) return;
      const text = el.typeInput.value;
      clearTimeout(typeDebounceTimer);
      typeDebounceTimer = setTimeout(async () => {
        if (text) {
          try {
            await send('text', { text: text });
          } catch (e) {}
        }
      }, 250);
    });

    // Send Button (Manual send)
    el.btnSendText?.addEventListener('click', async () => {
      const val = el.typeInput.value.trim();
      if (!val) return;
      window.Haptics?.tap();
      await sendTextToTv(val);
      saveRecentSearch(val);
      el.typeInput.value = '';
    });

    // Quick Actions (F-08 button ID fix)
    const btnBS = document.getElementById('btnAccBackspace') || document.getElementById('btnTypeBackspace');
    const btnEnt = document.getElementById('btnAccEnter') || document.getElementById('btnTypeEnter');
    const btnClr = document.getElementById('btnAccClear') || document.getElementById('btnTypeClear');
    const btnPst = document.getElementById('btnAccPaste') || document.getElementById('btnTypePaste');

    btnBS?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 67 });
    });

    btnEnt?.addEventListener('click', () => {
      window.Haptics?.ok();
      send('key', { code: 66 });
    });

    btnClr?.addEventListener('click', () => {
      window.Haptics?.tap();
      el.typeInput.value = '';
      el.typeInput.focus();
    });

    btnPst?.addEventListener('click', async () => {
      window.Haptics?.tap();
      try {
        const text = await navigator.clipboard.readText();
        if (text) {
          el.typeInput.value = text;
          if (state.liveTyping) {
            await sendTextToTv(text);
          }
          showToast('Pasted clipboard');
        }
      } catch (e) {
        showToast('Clipboard access denied');
      }
    });

    renderRecentChips();
  }

  function updateLiveTypingUI() {
    if (!el.btnSendText) return;
    if (state.liveTyping) {
      el.btnSendText.style.display = 'none';
    } else {
      el.btnSendText.style.display = 'inline-flex';
    }
  }

  async function sendTextToTv(text) {
    try {
      await send('text', { text: text }, el.btnSendText);
      showToast(`Sent: "${text}"`);
    } catch (e) {
      showToast('Failed to send text');
    }
  }

  function saveRecentSearch(text) {
    let recents = state.recentSearches.filter(t => t.toLowerCase() !== text.toLowerCase());
    recents.unshift(text);
    if (recents.length > 5) recents.pop();
    state.recentSearches = recents;
    localStorage.setItem('tv_remote_recents', JSON.stringify(recents));
    renderRecentChips();
  }

  function renderRecentChips() {
    if (!el.recentChips) return;
    el.recentChips.innerHTML = '';
    state.recentSearches.forEach(phrase => {
      const chip = document.createElement('button');
      chip.className = 'btn recent-chip';
      chip.textContent = phrase;
      chip.addEventListener('click', async () => {
        window.Haptics?.tap();
        el.typeInput.value = phrase;
        showToast(`Sent: "${phrase}"`);
        await send('text', { text: phrase });
      });
      el.recentChips.appendChild(chip);
    });
  }

  // =========================================
  // Screen Tab Setup (Perfect Centering, Download & Full Size)
  // =========================================
  function setupScreenTab() {
    el.btnCaptureHero?.addEventListener('click', takeScreenshot);

    el.btnSaveScreen?.addEventListener('click', () => {
      if (!el.screenImg || !el.screenImg.src) return;
      const a = document.createElement('a');
      a.href = el.screenImg.src;
      a.download = `tcl_tv_${Date.now()}.png`;
      a.click();
      showToast('Screenshot downloaded');
    });

    el.btnViewScreen?.addEventListener('click', () => {
      if (el.screenImg?.src) window.open(el.screenImg.src, '_blank');
    });
  }

  async function takeScreenshot() {
    window.Haptics?.tap();
    el.screenShimmer?.classList.add('active');
    try {
      const data = await send('screenshot');
      if (data && data.url) {
        let fullUrl = data.url.startsWith('/') ? data.url : '/' + data.url;
        if (!fullUrl.includes('key=')) {
          fullUrl += (fullUrl.includes('?') ? '&' : '?') + 'key=' + encodeURIComponent(API_KEY);
        }

        // Fetch via blob for reliable authentication and immediate display
        try {
          const resp = await fetch(fullUrl, {
            headers: { 'X-TVHub-Key': API_KEY },
            cache: 'no-store'
          });
          if (resp.ok) {
            const blob = await resp.blob();
            const objUrl = URL.createObjectURL(blob);
            el.screenImg.onload = () => {
              el.screenShimmer?.classList.remove('active');
              if (el.screenEmpty) el.screenEmpty.style.display = 'none';
              el.screenImg.classList.add('loaded');
              if (el.btnSaveScreen) el.btnSaveScreen.style.display = 'inline-flex';
              if (el.btnViewScreen) el.btnViewScreen.style.display = 'inline-flex';
              showToast('Screen captured');
            };
            el.screenImg.onerror = () => {
              el.screenShimmer?.classList.remove('active');
              showToast('Failed to display screen capture');
            };
            el.screenImg.src = objUrl;
            return;
          }
        } catch (fetchErr) {
          console.warn('Screenshot blob fetch fallback:', fetchErr);
        }

        // Fallback to direct image src
        el.screenImg.onload = () => {
          el.screenShimmer?.classList.remove('active');
          if (el.screenEmpty) el.screenEmpty.style.display = 'none';
          el.screenImg.classList.add('loaded');
          if (el.btnSaveScreen) el.btnSaveScreen.style.display = 'inline-flex';
          if (el.btnViewScreen) el.btnViewScreen.style.display = 'inline-flex';
          showToast('Screen captured');
        };
        el.screenImg.onerror = () => {
          el.screenShimmer?.classList.remove('active');
          showToast('Failed to load screen capture');
        };
        el.screenImg.src = fullUrl;
      } else {
        el.screenShimmer?.classList.remove('active');
        showToast(data?.message || 'Screenshot failed');
      }
    } catch (err) {
      el.screenShimmer?.classList.remove('active');
      showToast('Failed to capture screen');
    }
  }

  // =========================================
  // Files Tab Setup (Phone <-> TV <-> USB Manager)
  // =========================================
  function formatFileSize(bytes) {
    const b = parseInt(bytes, 10);
    if (isNaN(b) || b <= 0) return '0 B';
    if (b < 1024) return b + ' B';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
    if (b < 1024 * 1024 * 1024) return (b / (1024 * 1024)).toFixed(1) + ' MB';
    return (b / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
  }

  // =========================================
  // Files Tab Setup (Universal 2-Storage: TV Storage & USB Storage)
  // =========================================
  state.tvRoot = '/storage/emulated/0/Download';
  state.usbRoot = '';
  state.clipboard = null;
  state.currentDrives = [];
  let videoSpeedIdx = 1;
  const videoSpeeds = [0.75, 1.0, 1.25, 1.5, 2.0];
  let isVideoFitCover = false;

  function setupFilesTab() {
    // 1. TV Storage Toggle Button
    el.btnStorageTv?.addEventListener('click', () => {
      window.Haptics?.tap();
      setStorageActive('tv');
      loadDirectory(state.tvRoot);
    });

    // 2. USB Storage Toggle Button (Handles multiple USB drives)
    let usbDriveIndex = 0;
    el.btnStorageUsb?.addEventListener('click', () => {
      window.Haptics?.tap();
      if (!state.currentDrives || state.currentDrives.length === 0) {
        showToast('No USB drive detected on TV. Insert a flash drive.');
        return;
      }
      setStorageActive('usb');
      if (state.currentDrives.length > 1 && isCurrentPathUsb()) {
        usbDriveIndex = (usbDriveIndex + 1) % state.currentDrives.length;
      } else {
        usbDriveIndex = 0;
      }
      const targetDrive = state.currentDrives[usbDriveIndex] || state.currentDrives[0];
      state.usbRoot = targetDrive.path;
      loadDirectory(targetDrive.path);
    });

    // 3. Parent Folder (Up)
    el.btnFolderUp?.addEventListener('click', () => {
      window.Haptics?.tap();
      const cur = (state.currentPath || '').replace(/\/+$/, '');
      if (cur === '/storage' || cur === '' || cur === '/storage/emulated/0') return;
      if (state.usbRoot && cur === state.usbRoot) return; // Do not go above USB root
      const parent = cur.substring(0, cur.lastIndexOf('/')) || '/storage';
      loadDirectory(parent);
    });

    // 4. Refresh Current Directory
    el.btnRefreshFiles?.addEventListener('click', () => {
      window.Haptics?.tap();
      loadDirectory(state.currentPath || state.tvRoot);
    });

    // 4b. Storage Speed Benchmark (F-30)
    const btnBench = document.getElementById('btnBenchmarkStorage');
    btnBench?.addEventListener('click', async () => {
      window.Haptics?.tap();
      const targetDir = state.currentPath || state.tvRoot;
      showToast('⚡ Running storage speed benchmark… (please wait 3s)');
      btnBench.disabled = true;
      try {
        const res = await send('usb_benchmark', { path: targetDir });
        if (res && res.status === 'ok') {
          const wStr = res.write_speed_mbps != null ? `${res.write_speed_mbps} MB/s` : (res.write_status === 'read_only' ? 'Read-Only (No Write)' : 'N/A');
          const rStr = res.read_speed_mbps != null ? `${res.read_speed_mbps} MB/s` : 'N/A';
          alert(`⚡ Storage Benchmark Results\n\n📁 Path: ${res.path}\nFree Space: ${res.free_str}\n📥 Read Speed: ${rStr}\n📤 Write Speed: ${wStr}\nTotal: ${res.total_str}`);
        } else {
          showToast(res.message || 'Benchmark failed');
        }
      } catch (err) {
        showToast(err.message || 'Benchmark error');
      } finally {
        btnBench.disabled = false;
      }
    });

    // 5. Upload File Button (Uploads directly to whichever directory is currently open)
    el.btnUploadCurrent?.addEventListener('click', () => {
      window.Haptics?.tap();
      const isUsb = isCurrentPathUsb();
      if (isUsb && isUsbReadOnly()) {
        const drv = getCurrentDrive();
        const fs = drv?.fs_type || 'Unknown';
        if (fs.toUpperCase().includes('NTFS')) {
          alert('USB Drive is Read-Only (NTFS)\n\nAndroid TV Linux driver mounts NTFS drives as read-only.\nTo upload files to USB, the drive must be formatted as FAT32 or exFAT.\n\nTip: Switch to TV Storage to upload files!');
        } else {
          alert(`USB Drive Write Restricted (${fs})\n\nAndroid TV restricted write access to this drive.\nEnsure "All files access" is granted in TV Settings → Apps → TV Power Hub.`);
        }
        return;
      }
      el.fileUploadInput?.click();
    });

    el.fileUploadInput?.addEventListener('change', () => {
      const files = Array.from(el.fileUploadInput.files || []);
      if (files.length > 0) {
        const dest = state.currentPath || state.tvRoot;
        const targetLabel = isCurrentPathUsb() ? 'USB Storage' : 'TV Storage';
        uploadFilesSequentially(files, dest, targetLabel);
      }
      el.fileUploadInput.value = '';
    });

    // 6. Sticky Paste Action
    el.btnPasteAction?.addEventListener('click', async () => {
      if (!state.clipboard) return;
      window.Haptics?.ok();

      const isUsb = isCurrentPathUsb();
      if (isUsb && isUsbReadOnly()) {
        const drv = getCurrentDrive();
        const fs = drv?.fs_type || 'Unknown';
        if (fs.toUpperCase().includes('NTFS')) {
          alert('Cannot Paste to this USB Drive (Read-Only NTFS)\n\nAndroid TV cannot write or paste files onto NTFS drives.\nPlease switch to TV Storage or insert a FAT32/exFAT drive.');
        } else {
          alert(`Cannot Paste to this USB Drive (${fs})\n\nAndroid TV restricted write access to this drive.\nPlease verify "All files access" permission in TV Settings.`);
        }
        return;
      }

      const dest = state.currentPath.replace(/\/+$/, '') + '/' + state.clipboard.name;
      el.btnPasteAction.disabled = true;
      el.btnPasteAction.textContent = 'Pasting...';

      try {
        const res = await send('copy_file', { src: state.clipboard.path, dest: dest });
        if (res && res.status === 'ok') {
          showToast(`Pasted "${state.clipboard.name}"!`);
          state.clipboard = null;
          hidePasteBanner();
          loadDirectory(state.currentPath);
        } else {
          showToast(res.message || 'Paste failed. Check permissions.');
        }
      } catch (err) {
        showToast(err.message || 'Paste error: Could not copy file');
      } finally {
        if (el.btnPasteAction) {
          el.btnPasteAction.disabled = false;
          el.btnPasteAction.innerHTML = '<svg class="icon icon-16"><use href="#i-paste"/></svg><span>Paste Here</span>';
        }
      }
    });

    el.btnCancelPaste?.addEventListener('click', () => {
      state.clipboard = null;
      hidePasteBanner();
      showToast('Copy canceled');
    });

    // Media Modal Controls
    el.btnCloseMediaModal?.addEventListener('click', () => closeMediaModal());
    el.mediaModal?.addEventListener('click', e => {
      if (e.target === el.mediaModal) closeMediaModal();
    });

    // Video Speed Adjust
    el.btnVideoSpeed?.addEventListener('click', () => {
      videoSpeedIdx = (videoSpeedIdx + 1) % videoSpeeds.length;
      const speed = videoSpeeds[videoSpeedIdx];
      if (el.mediaVideoPlayer) el.mediaVideoPlayer.playbackRate = speed;
      if (el.btnVideoSpeed) el.btnVideoSpeed.textContent = `Speed: ${speed}x`;
    });

    // Video Fit / Aspect Adjust
    el.btnVideoFit?.addEventListener('click', () => {
      isVideoFitCover = !isVideoFitCover;
      if (el.mediaVideoPlayer) {
        el.mediaVideoPlayer.style.objectFit = isVideoFitCover ? 'cover' : 'contain';
      }
      if (el.btnVideoFit) {
        el.btnVideoFit.textContent = isVideoFitCover ? 'Fit: Fill' : 'Fit: Contain';
      }
    });

    // Video Fullscreen
    el.btnVideoFullscreen?.addEventListener('click', () => {
      if (el.mediaVideoPlayer) {
        if (el.mediaVideoPlayer.requestFullscreen) {
          el.mediaVideoPlayer.requestFullscreen();
        } else if (el.mediaVideoPlayer.webkitRequestFullscreen) {
          el.mediaVideoPlayer.webkitRequestFullscreen();
        }
      }
    });
  }

  function setStorageActive(mode) {
    if (mode === 'tv') {
      el.btnStorageTv?.classList.add('active');
      el.btnStorageUsb?.classList.remove('active');
    } else {
      el.btnStorageTv?.classList.remove('active');
      el.btnStorageUsb?.classList.add('active');
    }
  }

  function isCurrentPathUsb() {
    return !((state.currentPath || '').startsWith('/storage/emulated'));
  }

  function getCurrentDrive() {
    if (!state.currentDrives || state.currentDrives.length === 0) return null;
    const cur = state.currentPath || '';
    return state.currentDrives.find(d => cur.startsWith(d.path)) || state.currentDrives[0];
  }

  function isUsbReadOnly() {
    const drv = getCurrentDrive();
    if (!drv) return false;
    if (drv.is_writable !== undefined && drv.is_writable !== null) {
      return !drv.is_writable;
    }
    if (drv.fs_type && drv.fs_type.toUpperCase().includes('NTFS')) return true;
    return false;
  }

  function showPasteBanner(item) {
    if (!el.pasteBanner) return;
    el.pasteBanner.style.display = 'flex';
    if (el.pasteItemName) el.pasteItemName.textContent = item.name;
    if (el.pasteItemSize) el.pasteItemSize.textContent = `(${formatFileSize(item.size)})`;
  }

  function hidePasteBanner() {
    if (el.pasteBanner) el.pasteBanner.style.display = 'none';
  }

  async function loadDirectory(path) {
    if (!el.fileListWrap) return;
    el.fileListWrap.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-3); font-size: 13px;">Loading files…</div>';

    try {
      const data = await send('list_files', { path: path });
      if (!data || data.status !== 'ok') {
        el.fileListWrap.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--err); font-size: 13px;">' + (data?.message || 'Failed to read folder') + '</div>';
        return;
      }

      state.currentPath = data.path;
      if (el.currentPathText) {
        el.currentPathText.textContent = data.path;
        el.currentPathText.title = data.path;
      }

      // Handle drives detection
      const drives = data.drives || [];
      state.currentDrives = drives;
      if (drives.length > 0) {
        const curDrive = getCurrentDrive() || drives[0];
        state.usbRoot = curDrive.path;
        if (el.usbStorageSub) {
          const fsLabel = curDrive.fs_type ? ` · ${curDrive.fs_type}` : '';
          const driveCount = drives.length > 1 ? ` (${drives.length} drives)` : '';
          el.usbStorageSub.textContent = `${curDrive.free_str || curDrive.name} free${fsLabel}${driveCount}`;
        }
      } else {
        state.usbRoot = '';
        if (el.usbStorageSub) el.usbStorageSub.textContent = 'No USB detected';
      }

      // Update Active storage toggle button
      if (isCurrentPathUsb()) {
        setStorageActive('usb');
      } else {
        setStorageActive('tv');
      }

      // Show/Hide Read-Only Notice with dynamic message based on actual filesystem
      if (el.readOnlyNotice) {
        const isUsb = isCurrentPathUsb();
        const isRo = isUsb && isUsbReadOnly();
        el.readOnlyNotice.style.display = isRo ? 'flex' : 'none';
        if (isRo) {
          const drv = getCurrentDrive();
          const fs = drv?.fs_type || 'Unknown';
          const isNtfs = fs.toUpperCase().includes('NTFS');
          if (isNtfs) {
            el.readOnlyNotice.innerHTML = `
              <svg class="icon icon-16" style="color: var(--warn); flex-shrink: 0;"><use href="#i-lock"/></svg>
              <div style="flex: 1; font-size: 11px; line-height: 1.35; color: var(--text-2);">
                <b style="color: var(--warn);">USB Drive is Read-Only (${fs})</b><br/>
                Android TV mounts NTFS drives as read-only. Files can be streamed and copied to TV Storage. To paste or upload directly to USB, format it as FAT32 or exFAT.
              </div>
            `;
          } else {
            el.readOnlyNotice.innerHTML = `
              <svg class="icon icon-16" style="color: var(--warn); flex-shrink: 0;"><use href="#i-lock"/></svg>
              <div style="flex: 1; font-size: 11px; line-height: 1.35; color: var(--text-2);">
                <b style="color: var(--warn);">USB Drive Write Restricted (${fs})</b><br/>
                Android TV restricted write access to this drive. Make sure "All files access" is granted in TV Settings → Apps → TV Power Hub → Permissions.
              </div>
            `;
          }
        }
      }

      // Restore paste banner if clipboard has item
      if (state.clipboard) {
        showPasteBanner(state.clipboard);
      } else {
        hidePasteBanner();
      }

      // Render file & folder rows
      renderFileRows(data.items || []);

    } catch (e) {
      el.fileListWrap.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--err); font-size: 13px;">Connection error reading files</div>';
    }
  }

  function renderFileRows(items) {
    if (!el.fileListWrap) return;
    el.fileListWrap.innerHTML = '';

    if (items.length === 0) {
      el.fileListWrap.innerHTML = '<div style="padding: 32px 16px; text-align: center; color: var(--text-3); font-size: 13px;">Folder is empty</div>';
      return;
    }

    // Sort folders first, then files alphabetically
    const sorted = [...items].sort((a, b) => {
      if (a.is_dir && !b.is_dir) return -1;
      if (!a.is_dir && b.is_dir) return 1;
      return a.name.localeCompare(b.name);
    });

    sorted.forEach(item => {
      const row = document.createElement('div');
      row.className = 'file-row';

      const info = document.createElement('div');
      info.className = 'file-info';

      const iconSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      iconSvg.setAttribute('class', `file-icon ${item.is_dir ? 'is-dir' : ''}`);
      const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
      use.setAttribute('href', item.is_dir ? '#i-folder' : '#i-file');
      iconSvg.appendChild(use);

      const meta = document.createElement('div');
      meta.className = 'file-meta';

      const title = document.createElement('span');
      title.className = 'file-title';
      title.textContent = item.name;

      const size = document.createElement('span');
      size.className = 'file-size';
      size.textContent = item.is_dir ? 'Folder' : (item.size_str || formatFileSize(item.size));

      meta.appendChild(title);
      meta.appendChild(size);
      info.appendChild(iconSvg);
      info.appendChild(meta);

      // Clicking folder enters directory
      if (item.is_dir) {
        info.addEventListener('click', () => {
          window.Haptics?.tap();
          const next = state.currentPath.replace(/\/+$/, '') + '/' + item.name;
          loadDirectory(next);
        });
      }

      // Action Buttons
      const actions = document.createElement('div');
      actions.className = 'file-actions';
      const filePath = state.currentPath.replace(/\/+$/, '') + '/' + item.name;

      // 1. APK Installer on TV
      if (!item.is_dir && item.is_apk) {
        const btnInstall = document.createElement('button');
        btnInstall.className = 'btn btn-file-icon';
        btnInstall.style.color = 'var(--ok)';
        btnInstall.title = 'Install APK on TV display';
        btnInstall.innerHTML = '<svg class="icon icon-16"><use href="#i-check"/></svg>';
        btnInstall.addEventListener('click', async e => {
          e.stopPropagation();
          window.Haptics?.ok();
          showToast(`Opening installer for "${item.name}" on TV...`);
          try {
            const res = await send('install_apk', { path: filePath });
            if (res.status === 'ok') {
              showToast('Package installer launched on TV display!');
            } else {
              showToast(res.message || 'Install failed');
            }
          } catch (err) {
            showToast('Install error');
          }
        });
        actions.appendChild(btnInstall);
      }

      // 2. Stream / Preview Media (Videos, Audio, Images)
      if (!item.is_dir && (item.is_video || item.is_audio || item.is_image)) {
        const btnStream = document.createElement('button');
        btnStream.className = 'btn btn-file-icon';
        btnStream.style.color = 'var(--accent)';
        btnStream.title = 'Stream / Preview on Phone';
        btnStream.innerHTML = '<svg class="icon icon-16"><use href="#i-play"/></svg>';
        btnStream.addEventListener('click', e => {
          e.stopPropagation();
          window.Haptics?.tap();
          openMediaModal(item.name, filePath, item.is_video, item.is_audio, item.is_image);
        });
        actions.appendChild(btnStream);
      }

      // 3. Download to Phone (files only)
      if (!item.is_dir) {
        const btnDownload = document.createElement('button');
        btnDownload.className = 'btn btn-file-icon';
        btnDownload.title = 'Download to phone';
        btnDownload.innerHTML = '<svg class="icon icon-16"><use href="#i-download"/></svg>';
        btnDownload.addEventListener('click', e => {
          e.stopPropagation();
          window.Haptics?.tap();
          const downloadUrl = `${ENDPOINT}?action=download_file&path=${encodeURIComponent(filePath)}&key=${API_KEY}`;
          const a = document.createElement('a');
          a.href = downloadUrl;
          a.download = item.name;
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          showToast(`Downloading "${item.name}"`);
        });
        actions.appendChild(btnDownload);
      }

      // 4. Universal Copy Button
      if (!item.is_dir) {
        const btnCopy = document.createElement('button');
        btnCopy.className = 'btn btn-file-icon';
        btnCopy.title = 'Copy file';
        btnCopy.innerHTML = '<svg class="icon icon-16"><use href="#i-copy"/></svg>';
        btnCopy.addEventListener('click', e => {
          e.stopPropagation();
          window.Haptics?.tap();
          state.clipboard = {
            name: item.name,
            path: filePath,
            size: item.size
          };
          showPasteBanner(state.clipboard);
          showToast(`Copied "${item.name}". Navigate to any folder and tap "Paste Here".`);
        });
        actions.appendChild(btnCopy);
      }

      // 5. Universal Delete Button
      const btnDel = document.createElement('button');
      btnDel.className = 'btn btn-file-icon';
      btnDel.title = 'Delete';
      btnDel.innerHTML = '<svg class="icon icon-16" style="color: var(--err);"><use href="#i-trash"/></svg>';
      btnDel.addEventListener('click', async e => {
        e.stopPropagation();
        window.Haptics?.tap();

        if (isCurrentPathUsb() && isUsbReadOnly()) {
          alert('Cannot delete: USB drive is mounted as Read-Only (NTFS).');
          return;
        }

        if (!confirm(`Permanently delete "${item.name}"?`)) return;

        try {
          const res = await send('delete_file', { path: filePath });
          if (res && res.status === 'ok') {
            showToast(`Deleted "${item.name}"`);
            loadDirectory(state.currentPath);
          } else {
            showToast(res.message || 'Delete failed');
          }
        } catch (err) {
          showToast('Delete error');
        }
      });
      actions.appendChild(btnDel);

      row.appendChild(info);
      row.appendChild(actions);
      el.fileListWrap.appendChild(row);
    });
  }

  function openMediaModal(title, filePath, isVideo, isAudio, isImage) {
    if (!el.mediaModal) return;
    if (el.mediaModalTitle) el.mediaModalTitle.textContent = title;
    const streamUrl = `${ENDPOINT}?action=stream_media&path=${encodeURIComponent(filePath)}&key=${API_KEY}`;

    if (el.mediaVideoPlayer) {
      el.mediaVideoPlayer.pause();
      el.mediaVideoPlayer.src = '';
      el.mediaVideoPlayer.style.display = 'none';
      el.mediaVideoPlayer.playbackRate = 1.0;
      el.mediaVideoPlayer.style.objectFit = 'contain';
    }
    if (el.mediaAudioPlayer) {
      el.mediaAudioPlayer.pause();
      el.mediaAudioPlayer.src = '';
      el.mediaAudioPlayer.style.display = 'none';
    }
    if (el.mediaImageViewer) {
      el.mediaImageViewer.src = '';
      el.mediaImageViewer.style.display = 'none';
    }
    if (el.videoAdjustBar) {
      el.videoAdjustBar.style.display = isVideo ? 'flex' : 'none';
    }
    if (el.btnVideoSpeed) el.btnVideoSpeed.textContent = 'Speed: 1.0x';
    if (el.btnVideoFit) el.btnVideoFit.textContent = 'Fit: Contain';
    videoSpeedIdx = 1;
    isVideoFitCover = false;

    if (isVideo && el.mediaVideoPlayer) {
      el.mediaVideoPlayer.src = streamUrl;
      el.mediaVideoPlayer.style.display = 'block';
      el.mediaVideoPlayer.play().catch(() => {});
    } else if (isAudio && el.mediaAudioPlayer) {
      el.mediaAudioPlayer.src = streamUrl;
      el.mediaAudioPlayer.style.display = 'block';
      el.mediaAudioPlayer.play().catch(() => {});
    } else if (isImage && el.mediaImageViewer) {
      el.mediaImageViewer.src = streamUrl;
      el.mediaImageViewer.style.display = 'block';
    }

    el.mediaModal.style.display = 'flex';
  }

  function closeMediaModal() {
    if (!el.mediaModal) return;
    if (el.mediaVideoPlayer) {
      el.mediaVideoPlayer.pause();
      el.mediaVideoPlayer.src = '';
    }
    if (el.mediaAudioPlayer) {
      el.mediaAudioPlayer.pause();
      el.mediaAudioPlayer.src = '';
    }
    if (el.mediaImageViewer) {
      el.mediaImageViewer.src = '';
    }
    if (el.videoAdjustBar) {
      el.videoAdjustBar.style.display = 'none';
    }
    el.mediaModal.style.display = 'none';
  }

  function uploadFilesSequentially(files, destDir, targetLabel) {
    if (!files || files.length === 0) return;
    const dest = destDir || state.currentPath || state.tvRoot;
    const label = targetLabel || 'Storage';

    const isUsb = isCurrentPathUsb();
    const drv = getCurrentDrive();
    const fs = (drv?.fs_type || '').toUpperCase();

    // 1. FAT32 4GB single-file limit architectural check
    for (const f of files) {
      if (isUsb && f.size >= 4294967295) {
        if (!fs.includes('EXFAT') && !fs.includes('EXT')) {
          alert(`Cannot Upload to FAT32 USB (File > 4 GB)\n\n"${f.name}" is ${formatFileSize(f.size)}.\n\nThis USB drive is formatted as FAT32, which has a strict physical limit of 4 GB (4,096 MB) per individual file.\n\nHow to solve this:\n1. Switch to "TV Storage" above (the TV internal storage supports large 4K movies/files), or\n2. Reformat this USB drive as exFAT on your PC.`);
          return;
        }
      }
    }
    if (el.uploadProgressWrap) el.uploadProgressWrap.classList.add('show');
    if (el.uploadProgressBar) el.uploadProgressBar.style.width = '0%';
    if (el.uploadPercentText) el.uploadPercentText.textContent = '0%';
    if (el.uploadStatusText) el.uploadStatusText.textContent = `Preparing upload to ${label}…`;

    let idx = 0;
    const successList = [];
    const failList = [];

    function next() {
      if (idx >= files.length) {
        if (el.uploadProgressWrap) el.uploadProgressWrap.classList.remove('show');
        if (failList.length === 0 && successList.length > 0) {
          showToast(`Uploaded ${successList.length} file(s) to ${label}!`);
          state.currentPath = dest;
          loadDirectory(dest);
        } else if (successList.length > 0 && failList.length > 0) {
          showToast(`${successList.length} uploaded, ${failList.length} failed.`);
          state.currentPath = dest;
          loadDirectory(dest);
        } else {
          const firstErr = failList[0]?.error || 'Failed to save file';
          showToast(`Upload failed: ${firstErr}`);
          alert(`Upload Failed\n\nCould not write file(s) to:\n${dest}\n\nReason: ${firstErr}`);
        }
        return;
      }

      const file = files[idx];
      idx++;

      const uploadUrl = `${ENDPOINT}?action=upload_file&dest=${encodeURIComponent(dest)}&name=${encodeURIComponent(file.name)}&key=${API_KEY}`;

      const xhr = new XMLHttpRequest();
      xhr.open('POST', uploadUrl, true);
      xhr.setRequestHeader('Content-Type', 'application/octet-stream');
      xhr.setRequestHeader('X-TVHub-Key', API_KEY);
      xhr.timeout = 300000; // 5 minute timeout for large uploads

      xhr.upload.onprogress = ev => {
        if (ev.lengthComputable) {
          const pct = Math.round((ev.loaded / ev.total) * 100);
          if (el.uploadProgressBar) el.uploadProgressBar.style.width = pct + '%';
          if (el.uploadPercentText) el.uploadPercentText.textContent = pct + '%';
          if (el.uploadStatusText) el.uploadStatusText.textContent = `[${idx}/${files.length}] Uploading "${file.name}" (${pct}%)`;
        }
      };

      xhr.onload = () => {
        try {
          const res = JSON.parse(xhr.responseText);
          if (xhr.status === 200 && res.status === 'ok') {
            successList.push({ name: file.name, path: res.path, size: res.size });
          } else {
            failList.push({ name: file.name, error: res.message || ('Server error ' + xhr.status) });
          }
        } catch (e) {
          if (xhr.status === 200) {
            successList.push({ name: file.name });
          } else {
            failList.push({ name: file.name, error: 'Server error ' + xhr.status });
          }
        }
        next();
      };

      xhr.ontimeout = () => {
        failList.push({ name: file.name, error: 'Upload timed out. Wi-Fi connection is too slow.' });
        next();
      };

      xhr.onerror = () => {
        const destFs = (getCurrentDrive()?.fs_type || '').toUpperCase();
        let err;
        if (isCurrentPathUsb() && destFs.includes('NTFS')) {
          err = 'Drive is read-only NTFS. Android TV cannot write to NTFS drives.';
        } else {
          err = 'Upload interrupted or rejected by TV. Check Wi-Fi connection and ensure file size is under 4GB for FAT32 drives.';
        }
        failList.push({ name: file.name, error: err });
        next();
      };

      xhr.send(file);
    }

    next();
  }

  // =========================================
  // Tools Tab Setup (Link Launcher, RAM Boost & Dashboard)
  // =========================================
  function setupToolsTab() {
    // 1. Link / Video Launcher
    async function launchLink(target) {
      if (!el.linkUrlInput) return;
      let rawVal = el.linkUrlInput.value.trim();
      if (!rawVal) {
        showToast("Please paste a link or video URL");
        el.linkUrlInput.focus();
        return;
      }

      let urlVal = rawVal;
      const isUrl = /^https?:\/\//i.test(rawVal) || (rawVal.includes(".") && !rawVal.includes(" "));
      if (!isUrl) {
        if (target === "browser") {
          urlVal = "https://www.google.com/search?q=" + encodeURIComponent(rawVal);
        } else {
          urlVal = "https://www.youtube.com/results?search_query=" + encodeURIComponent(rawVal);
        }
      } else if (!/^https?:\/\//i.test(urlVal)) {
        urlVal = "https://" + urlVal;
      }

      const appName = target === "smarttube" ? "SmartTube" : (target === "browser" ? "Browser" : "YouTube");
      window.Haptics?.ok();
      showToast(`Opening on ${appName}...`);
      try {
        await send("open_url", { url: urlVal, target: target });
        showToast(`Opened on ${appName}!`);
      } catch (e) {
        showToast(`Failed to open on ${appName}`);
      }
    }

    el.btnOpenSmartTube?.addEventListener('click', () => launchLink('smarttube'));
    el.btnOpenYouTube?.addEventListener('click', () => launchLink('youtube'));
    el.btnOpenBrowser?.addEventListener('click', () => launchLink('browser'));

    // 2. RAM Booster
    if (el.btnBoostRam) {
      el.btnBoostRam.addEventListener('click', async () => {
        window.Haptics?.tap();
        el.btnBoostRam.textContent = "Cleaning…";
        el.btnBoostRam.disabled = true;

        try {
          const data = await send('boost', {}, el.btnBoostRam);
          const availKb = parseInt(data.mem_avail_kb, 10);
          const availMb = Math.round(availKb / 1024);

          let freedMsg = "Done";
          if (state.lastAvailKb && availKb > state.lastAvailKb) {
            const freedMb = Math.round((availKb - state.lastAvailKb) / 1024);
            freedMsg = `Freed ${freedMb} MB · ${availMb} MB available`;
          } else {
            freedMsg = `Done · ${availMb} MB available`;
          }
          state.lastAvailKb = availKb;

          if (el.ramSub) el.ramSub.textContent = freedMsg;
          el.btnBoostRam.textContent = "Cleaned ✓";
          window.Haptics?.ok();
          showToast(`Memory Boosted! (${availMb} MB available)`);

          setTimeout(() => {
            el.btnBoostRam.textContent = "Clean now";
            el.btnBoostRam.disabled = false;
          }, 2400);

          refreshTvStatus();
        } catch (e) {
          el.btnBoostRam.textContent = "Clean now";
          el.btnBoostRam.disabled = false;
          showToast('Memory clean failed');
        }
      });
    }

    // 3. Status Dashboard Refresh
    el.btnRefreshStatus?.addEventListener('click', () => {
      window.Haptics?.tap();
      refreshTvStatus();
    });

    // 4. Version 2.0 Subsystem Setups
    setupGroqAi();
    setupAudioEq();
    setupNotificationRelay();
    setupAutomations();
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // =========================================
  // Groq Cloud AI Copilot (Recommender & Summarizer)
  // =========================================
  function setupGroqAi() {
    let savedKey = localStorage.getItem('tv_groq_api_key') || '';
    if (el.groqApiKeyInput) el.groqApiKeyInput.value = savedKey;

    const updateKeyPill = () => {
      if (!el.aiKeyStatusPill) return;
      if (savedKey && savedKey.startsWith('gsk_')) {
        el.aiKeyStatusPill.textContent = 'Active';
        el.aiKeyStatusPill.className = 'ai-status-pill active';
      } else {
        el.aiKeyStatusPill.textContent = 'No Key';
        el.aiKeyStatusPill.className = 'ai-status-pill';
      }
    };
    updateKeyPill();

    el.btnSaveGroqKey?.addEventListener('click', () => {
      window.Haptics?.tap();
      const val = el.groqApiKeyInput ? el.groqApiKeyInput.value.trim() : '';
      if (!val) {
        localStorage.removeItem('tv_groq_api_key');
        savedKey = '';
        showToast('Groq API Key removed');
      } else {
        localStorage.setItem('tv_groq_api_key', val);
        savedKey = val;
        showToast('Groq API Key saved!');
      }
      updateKeyPill();
    });

    // Category chips
    let selectedCategory = 'Movies';
    if (el.aiCategoryChips) {
      el.aiCategoryChips.querySelectorAll('.ai-chip').forEach(btn => {
        btn.addEventListener('click', () => {
          window.Haptics?.tap();
          el.aiCategoryChips.querySelectorAll('.ai-chip').forEach(b => b.classList.remove('active'));
          btn.classList.add('active');
          selectedCategory = btn.dataset.cat || 'Movies';
        });
      });
    }

    // Recommendations fetch
    el.btnGetAiRecs?.addEventListener('click', async () => {
      window.Haptics?.tap();
      if (!savedKey) {
        showToast('Please paste your Groq API Key above first');
        el.groqApiKeyInput?.focus();
        return;
      }

      const lang = el.aiLanguageSelect?.value || 'English';
      const genre = el.aiGenreSelect?.value || 'Sci-Fi';
      const mood = el.aiMoodInput?.value.trim() || 'thrilling and memorable';

      el.btnGetAiRecs.disabled = true;
      el.btnGetAiRecs.innerHTML = '<span class="status-dot reconnecting"></span> Thinking…';
      if (el.aiRecsContainer) {
        el.aiRecsContainer.style.display = 'flex';
        el.aiRecsContainer.innerHTML = '<div style="font-size:12px; color:var(--text-3); text-align:center; padding:12px;">Querying Llama 3.3 on Groq Cloud…</div>';
      }

      try {
        const sysPrompt = 'You are an expert TV movie & media curator. Return exactly 3 recommendations matching the user\'s category, language, genre, and mood. Format strictly as a valid JSON array of objects with keys: "title", "year", "rating" (IMDb e.g. "8.4"), "hook" (1 punchy spoiler-free sentence), "match_reason" (why it fits the mood). Output NO markdown ticks, NO commentary, ONLY the raw JSON array.';
        const userPrompt = `Category: ${selectedCategory}\nLanguage: ${lang}\nGenre: ${genre}\nMood / Vibe: ${mood}`;

        const resp = await fetch('https://api.groq.com/openai/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${savedKey}`
          },
          body: JSON.stringify({
            model: 'llama-3.3-70b-versatile',
            messages: [
              { role: 'system', content: sysPrompt },
              { role: 'user', content: userPrompt }
            ],
            temperature: 0.7,
            max_tokens: 600
          })
        });

        if (!resp.ok) {
          throw new Error('Groq API Error: ' + resp.status);
        }

        const data = await resp.json();
        const contentStr = data.choices[0]?.message?.content || '[]';
        const cleanJson = contentStr.replace(/```json/g, '').replace(/```/g, '').trim();
        const recs = JSON.parse(cleanJson);

        if (!Array.isArray(recs) || recs.length === 0) {
          throw new Error('No items returned');
        }

        if (el.aiRecsContainer) {
          el.aiRecsContainer.innerHTML = '';
          recs.forEach(item => {
            const card = document.createElement('div');
            card.className = 'ai-rec-card';
            card.innerHTML = `
              <div class="ai-rec-header">
                <span class="ai-rec-title">${escapeHtml(item.title)} <span style="font-size:11px; font-weight:600; color:var(--text-3);">(${escapeHtml(item.year)})</span></span>
                <span class="ai-rec-badge">★ ${escapeHtml(item.rating || '8.0')}</span>
              </div>
              <div class="ai-rec-hook">${escapeHtml(item.hook || '')}</div>
              <div style="font-size:11px; color:var(--accent);">${escapeHtml(item.match_reason || '')}</div>
              <button class="btn ai-rec-action" data-title="${escapeHtml(item.title)}">Watch on TV</button>
            `;

            card.querySelector('.ai-rec-action').addEventListener('click', (e) => {
              e.stopPropagation();
              window.Haptics?.tap();
              const targetQuery = `https://www.youtube.com/results?search_query=${encodeURIComponent(item.title + ' trailer')}`;
              send('open_url', { url: targetQuery, target: 'smarttube' });
              showToast(`Launched on TV: ${item.title}`);
            });

            el.aiRecsContainer.appendChild(card);
          });
        }

      } catch (err) {
        if (el.aiRecsContainer) {
          el.aiRecsContainer.innerHTML = `<div style="font-size:12px; color:var(--danger); padding:8px;">AI Recommender error: ${escapeHtml(err.message)}</div>`;
        }
      } finally {
        if (el.btnGetAiRecs) {
          el.btnGetAiRecs.disabled = false;
          el.btnGetAiRecs.innerHTML = '<svg class="icon icon-16"><use href="#i-sparkle"/></svg> <span>Ask AI</span>';
        }
      }
    });
  }

  // =========================================
  // Display & Audio EQ Calibrator
  // =========================================
  function setupAudioEq() {
    const audioBtns = [
      { el: el.btnAudioDialogue, mode: 'dialogue_boost', label: 'Dialogue Boost (+4.5dB)' },
      { el: el.btnAudioNight, mode: 'night_mode', label: 'Night Compression' },
      { el: el.btnAudioMusic, mode: 'music', label: 'Music Mode' },
      { el: el.btnAudioFlat, mode: 'flat', label: 'Audio Reset' }
    ];

    audioBtns.forEach(item => {
      item.el?.addEventListener('click', async () => {
        window.Haptics?.tap();
        audioBtns.forEach(b => b.el?.classList.remove('active'));
        item.el?.classList.add('active');
        try {
          await send('audio_eq', { mode: item.mode });
          showToast(`Audio: ${item.label}`);
        } catch (e) {
          showToast('Failed to apply Audio EQ');
        }
      });
    });

    const picBtns = [
      { el: el.btnPicCinema, mode: 'cinema', label: 'Cinema Warm (D65)' },
      { el: el.btnPicSports, mode: 'vivid', label: 'Vivid Sports' },
      { el: el.btnPicGame, mode: 'game', label: 'Game Mode (Low Latency)' },
      { el: el.btnPicStd, mode: 'standard', label: 'Standard Picture' }
    ];

    picBtns.forEach(item => {
      item.el?.addEventListener('click', async () => {
        window.Haptics?.tap();
        picBtns.forEach(b => b.el?.classList.remove('active'));
        item.el?.classList.add('active');
        try {
          await send('picture_mode', { mode: item.mode });
          showToast(`Picture: ${item.label}`);
        } catch (e) {
          showToast('Failed to apply Picture Mode');
        }
      });
    });
  }

  // =========================================
  // Mobile-to-TV Notification Relay
  // =========================================
  function setupNotificationRelay() {
    async function maybeSummarize(text) {
      const apiKey = localStorage.getItem('tv_groq_api_key');
      const summarizeEnabled = el.aiSummarizerSwitch?.checked;
      if (!summarizeEnabled || !apiKey || text.length <= 45) {
        return text;
      }
      try {
        const resp = await fetch('https://api.groq.com/openai/v1/chat/completions', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${apiKey}`
          },
          body: JSON.stringify({
            model: 'llama-3.1-8b-instant',
            messages: [
              {
                role: 'system',
                content: 'Compress this notification to a single punchy line under 50 characters for a TV screen banner. Return ONLY the line with no quotes or preface.'
              },
              { role: 'user', content: text }
            ],
            max_tokens: 30,
            temperature: 0.3
          })
        });
        if (resp.ok) {
          const data = await resp.json();
          const clean = data.choices[0]?.message?.content?.trim();
          if (clean) return clean;
        }
      } catch (ignored) {}
      return text;
    }

    el.btnSendCustomAlert?.addEventListener('click', async () => {
      window.Haptics?.tap();
      let msg = el.customAlertInput?.value.trim();
      if (!msg) {
        showToast('Type a message first');
        el.customAlertInput?.focus();
        return;
      }
      msg = await maybeSummarize(msg);
      try {
        await send('send_toast', { message: msg, type: 'custom' });
        showToast('Banner displayed on TV');
        if (el.customAlertInput) el.customAlertInput.value = '';
      } catch (e) {
        showToast('Failed to send alert');
      }
    });

    el.btnAlertCall?.addEventListener('click', async () => {
      window.Haptics?.tap();
      await send('send_toast', { title: 'Incoming Call', message: 'Mom (Mobile)', type: 'call' });
      showToast('Call Alert sent to TV');
    });

    el.btnAlertBattery?.addEventListener('click', async () => {
      window.Haptics?.tap();
      await send('send_toast', { title: 'Battery Warning', message: 'Phone Battery 12% - Connect Charger', type: 'battery' });
      showToast('Battery Alert sent to TV');
    });

    el.btnAlertMsg?.addEventListener('click', async () => {
      window.Haptics?.tap();
      await send('send_toast', { title: 'WhatsApp', message: 'Karthik: Hey! Are you free for a quick call?', type: 'sms' });
      showToast('Message Alert sent to TV');
    });

    if ('getBattery' in navigator) {
      navigator.getBattery().then(battery => {
        let lastNotifiedBattery = 0;
        const checkBattery = () => {
          if (!el.autoBatteryAlertSwitch?.checked) return;
          const levelPct = Math.round(battery.level * 100);
          const now = Date.now();
          if (levelPct <= 15 && !battery.charging && (now - lastNotifiedBattery > 900000)) {
            lastNotifiedBattery = now;
            send('send_toast', {
              title: 'Low Battery Alert',
              message: `Phone battery at ${levelPct}% - Please plug in charger`,
              type: 'battery'
            });
          }
        };
        battery.addEventListener('levelchange', checkBattery);
        battery.addEventListener('chargingchange', checkBattery);
      }).catch(() => {});
    }
  }


  // =========================================
  // TV Automations & Sleep Timer
  // =========================================
  function setupAutomations() {
    if (!el.sleepTimerBtnsGroup) return;
    el.sleepTimerBtnsGroup.querySelectorAll('.btn-timer').forEach(btn => {
      btn.addEventListener('click', async () => {
        window.Haptics?.tap();
        el.sleepTimerBtnsGroup.querySelectorAll('.btn-timer').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        const mins = parseInt(btn.dataset.mins || '0', 10);
        try {
          await send('sleep_timer', { minutes: mins });
          showToast(mins > 0 ? `Sleep timer set: ${mins} minutes` : 'Sleep timer turned off');
        } catch (e) {
          showToast('Failed to set sleep timer');
        }
      });
    });
  }

  async function refreshTvStatus() {
    try {
      const data = await send('status');
      if (!data) return;

      // RAM
      const totalMb = Math.round(parseInt(data.mem_total_kb, 10) / 1024);
      const availMb = Math.round(parseInt(data.mem_avail_kb, 10) / 1024);
      state.lastAvailKb = parseInt(data.mem_avail_kb, 10);
      const usedMb = totalMb - availMb;
      const ramPct = totalMb > 0 ? Math.round((usedMb / totalMb) * 100) : 0;

      if (el.dashModel) {
        el.dashModel.textContent = (data.brand ? data.brand + ' ' : '') + (data.model || 'TCL Smart TV');
      }
      if (el.dashAndroid) {
        el.dashAndroid.textContent = `Android ${data.android_ver || '11'} (API ${data.sdk || '30'})`;
      }
      if (el.dashRamText) {
        el.dashRamText.textContent = `${availMb} MB free / ${totalMb} MB (${ramPct}% used)`;
      }
      if (el.dashRamBar) {
        el.dashRamBar.style.width = ramPct + '%';
        el.dashRamBar.style.background = ramPct > 85 ? 'var(--err)' : (ramPct > 70 ? 'var(--warn)' : 'var(--ok)');
      }

      // Storage (/data)
      if (el.dashStorageText) {
        el.dashStorageText.textContent = `${data.storage_avail || '--'} free / ${data.storage_total || '--'} (${data.storage_pct || '0%'} used)`;
      }
      if (el.dashStorageBar) {
        el.dashStorageBar.style.width = data.storage_pct || '0%';
      }

      // Display, CPU, USB, Uptime, Focus
      if (el.dashDisplay) {
        el.dashDisplay.textContent = `${data.resolution || '1280x720'} (${data.density || '213dpi'})`;
      }
      if (el.dashCpu) {
        el.dashCpu.textContent = data.cpu || 'MediaTek Quad-Core Cortex-A55';
      }
      if (el.dashUsb) {
        let usbSummary = 'None detected';
        if (data.usb_drives && data.usb_drives !== 'none') {
          try {
            const drives = typeof data.usb_drives === 'string' ? JSON.parse(data.usb_drives) : data.usb_drives;
            if (Array.isArray(drives) && drives.length > 0) {
              const d = drives[0];
              const fs = d.fs_type ? ` · ${d.fs_type}` : '';
              usbSummary = `${d.free_str || d.name} free${fs}`;
            } else if (typeof data.usb_drives === 'string' && !data.usb_drives.startsWith('[')) {
              usbSummary = data.usb_drives.trim();
            }
          } catch (e) {
            usbSummary = '1 Connected';
          }
        }
        el.dashUsb.textContent = usbSummary;
      }
      if (el.dashUptime) {
        el.dashUptime.textContent = data.uptime ? data.uptime.split(',')[0].replace('up', '').trim() : '--';
      }
      if (el.dashFocus) {
        let cleanFocus = data.focus ? data.focus.split('/').pop().replace('}', '').trim() : 'Launcher';
        if (cleanFocus.includes('tvpowerhub')) cleanFocus = 'TV Power Hub';
        else if (cleanFocus.includes('smarttube')) cleanFocus = 'SmartTube';
        else if (cleanFocus.includes('browser')) cleanFocus = 'Browser';
        el.dashFocus.textContent = cleanFocus;
      }

    } catch (e) {}
  }

  // =========================================
  // Settings Setup
  // =========================================
  function setupSettings() {
    // Theme
    applyTheme(state.theme);
    if (el.themeSelect) {
      el.themeSelect.value = state.theme;
      el.themeSelect.addEventListener('change', () => {
        state.theme = el.themeSelect.value;
        localStorage.setItem('tv_remote_theme', state.theme);
        applyTheme(state.theme);
      });
    }

    // Haptics
    if (el.hapticsSwitch) {
      el.hapticsSwitch.checked = window.Haptics?.getEnabled();
      el.hapticsSwitch.addEventListener('change', () => {
        window.Haptics?.setEnabled(el.hapticsSwitch.checked);
      });
    }

    // Left hand
    applyLeftHand(state.leftHand);
    if (el.leftHandSwitch) {
      el.leftHandSwitch.checked = state.leftHand;
      el.leftHandSwitch.addEventListener('change', () => {
        state.leftHand = el.leftHandSwitch.checked;
        localStorage.setItem('tv_remote_left_hand', state.leftHand ? 'true' : 'false');
        applyLeftHand(state.leftHand);
      });
    }

    // TV Name
    if (el.tvNameInput) {
      el.tvNameInput.value = state.tvName;
      if (el.tvName) el.tvName.textContent = state.tvName;
      el.tvNameInput.addEventListener('input', () => {
        state.tvName = el.tvNameInput.value.trim() || "Living Room TV";
        if (el.tvName) el.tvName.textContent = state.tvName;
        localStorage.setItem('tv_remote_name', state.tvName);
      });
    }
  }

  function applyTheme(th) {
    if (th === 'auto') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', th);
    }
  }

  function applyLeftHand(isLeft) {
    if (isLeft) {
      document.documentElement.setAttribute('data-hand', 'left');
    } else {
      document.documentElement.removeAttribute('data-hand');
    }
  }

  // Connection Ping Loop
  function updateConnectionStatus(isConnected, latency = null, customMsg = null) {
    state.connected = isConnected;
    if (!el.connectionDot || !el.connectionText) return;
    if (isConnected) {
      el.connectionDot.className = 'status-dot';
      el.connectionText.textContent = `Connected · ${latency || 12} ms`;
    } else {
      el.connectionDot.className = 'status-dot reconnecting';
      el.connectionText.textContent = customMsg || 'Reconnecting…';
    }
  }

  async function pingTv() {
    const tStart = performance.now();
    try {
      const res = await fetch(`${ENDPOINT}?action=status&key=${API_KEY}`, { cache: 'no-store' });
      const latency = Math.round(performance.now() - tStart);
      if (res.ok) {
        updateConnectionStatus(true, latency);
      } else {
        updateConnectionStatus(false);
      }
    } catch (e) {
      updateConnectionStatus(false);
    }
  }

  // Desktop Keyboard Shortcuts
  function setupKeyboardShortcuts() {
    window.addEventListener('keydown', e => {
      if (['TEXTAREA', 'INPUT'].includes(document.activeElement?.tagName)) return;

      const key = e.key;
      switch (key) {
        case 'ArrowUp':
          e.preventDefault();
          send('key', { code: 19 });
          triggerBlip(el.dpadSvg?.querySelector('[data-key="UP"]'));
          break;
        case 'ArrowDown':
          e.preventDefault();
          send('key', { code: 20 });
          triggerBlip(el.dpadSvg?.querySelector('[data-key="DOWN"]'));
          break;
        case 'ArrowLeft':
          e.preventDefault();
          send('key', { code: 21 });
          triggerBlip(el.dpadSvg?.querySelector('[data-key="LEFT"]'));
          break;
        case 'ArrowRight':
          e.preventDefault();
          send('key', { code: 22 });
          triggerBlip(el.dpadSvg?.querySelector('[data-key="RIGHT"]'));
          break;
        case 'Enter':
          e.preventDefault();
          send('key', { code: 23 });
          triggerBlip(el.dpadSvg?.querySelector('[data-key="OK"]'));
          break;
        case 'Backspace':
        case 'Escape':
          e.preventDefault();
          send('key', { code: 4 });
          triggerBlip(document.getElementById('btnBack'));
          break;
        case 'h':
        case 'H':
          e.preventDefault();
          send('key', { code: 3 });
          triggerBlip(document.getElementById('btnHome'));
          break;
        case '+':
        case '=':
          e.preventDefault();
          send('key', { code: 24 });
          triggerBlip(document.getElementById('btnVolUp'));
          break;
        case '-':
        case '_':
          e.preventDefault();
          send('key', { code: 25 });
          triggerBlip(document.getElementById('btnVolDown'));
          break;
        case 'm':
        case 'M':
          e.preventDefault();
          send('key', { code: 164 });
          triggerBlip(el.btnMute);
          break;
        case ' ':
          e.preventDefault();
          send('key', { code: 85 });
          triggerBlip(document.getElementById('btnPlayPause'));
          break;
      }
    });
  }

  // Initialization
  document.addEventListener('DOMContentLoaded', () => {
    initElements();
    setupPowerHold();
    setupDpad();
    setupButtons();
    setupSheets();
    setupTabs();
    setupTypeTab();
    setupScreenTab();
    setupFilesTab();
    setupToolsTab();
    setupSettings();
    setupKeyboardShortcuts();

    initWebSocket();
    setNavMode(state.navMode);
    pingTv();
    setInterval(pingTv, 4000);

    // Initial wake lock on remote tab
    acquireWakeLock();
  });
})();
