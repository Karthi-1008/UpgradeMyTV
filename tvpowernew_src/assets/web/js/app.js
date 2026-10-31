// TV Remote Application Logic
(function() {
  const API_KEY = new URLSearchParams(window.location.search).get('key') || "TV_POWER_SECURE_KEY";
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
    
    // Files elements
    el.storageTabsWrap = document.getElementById('storageTabsWrap');
    el.btnStorageTv = document.getElementById('btnStorageTv');
    el.btnFolderUp = document.getElementById('btnFolderUp');
    el.currentPathText = document.getElementById('currentPathText');
    el.btnRefreshFiles = document.getElementById('btnRefreshFiles');
    el.uploadProgressWrap = document.getElementById('uploadProgressWrap');
    el.uploadStatusText = document.getElementById('uploadStatusText');
    el.uploadPercentText = document.getElementById('uploadPercentText');
    el.uploadProgressBar = document.getElementById('uploadProgressBar');
    el.btnTriggerUpload = document.getElementById('btnTriggerUpload');
    el.btnPasteFile = document.getElementById('btnPasteFile');
    el.pasteActionText = document.getElementById('pasteActionText');
    el.fileUploadInput = document.getElementById('fileUploadInput');
    el.fileListWrap = document.getElementById('fileListWrap');

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

  // Unified Send Action
  async function send(action, params = {}, triggerElement = null) {
    const url = new URL(ENDPOINT, window.location.origin);
    url.searchParams.set('action', action);
    url.searchParams.set('key', API_KEY);
    for (const [k, v] of Object.entries(params)) {
      url.searchParams.set(k, v);
    }

    try {
      const res = await fetch(url.toString(), { cache: 'no-store' });
      if (res.ok) {
        if (triggerElement) triggerBlip(triggerElement);
        return await res.json();
      } else {
        throw new Error('HTTP ' + res.status);
      }
    } catch (err) {
      if (triggerElement) triggerShake(triggerElement);
      updateConnectionStatus(false, null, "Couldn't reach TV — retrying");
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

    // Segments hold-repeat & tap (Single trigger on press, repeat on hold)
    ['UP', 'DOWN', 'LEFT', 'RIGHT'].forEach(dir => {
      const seg = el.dpadSvg?.querySelector(`[data-key="${dir}"]`);
      if (seg) {
        holdRepeat(seg, () => {
          send('key', { code: keyMap[dir] }, seg);
        }, { delay: 350, every: 110 });
        // Prevent synthetic click from double-firing
        seg.addEventListener('click', e => {
          e.preventDefault();
          e.stopPropagation();
        });
      }
    });

    // OK button (Single trigger on press, prevent click double-firing)
    const okBtn = el.dpadSvg?.querySelector('[data-key="OK"]');
    if (okBtn) {
      okBtn.addEventListener('pointerdown', e => {
        e.preventDefault();
        okBtn.classList.add('is-down');
        window.Haptics?.ok();
        send('key', { code: keyMap.OK }, okBtn);
      });
      okBtn.addEventListener('click', e => {
        e.preventDefault();
        e.stopPropagation();
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

    // Transport (Play/Pause, Rewind, Fast Forward)
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
      closeSheets();
      send('input_source', { source: 'native' });
      window.Haptics?.tap();
      showToast('Opened input source menu');
    });

    document.getElementById('sheetBtnSettings')?.addEventListener('click', () => {
      send('key', { code: 176 });
      window.Haptics?.tap();
      closeSheets();
      showToast('TV Settings');
    });

    document.getElementById('sheetBtnScreenOff')?.addEventListener('click', () => {
      send('key', { code: 26 });
      window.Haptics?.tap();
      closeSheets();
      showToast('Powering off TV');
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

    // Quick Actions
    const btnBackspace = document.getElementById('btnAccBackspace') || document.getElementById('btnTypeBackspace');
    btnBackspace?.addEventListener('click', () => {
      window.Haptics?.tap();
      send('key', { code: 67 });
    });

    const btnEnter = document.getElementById('btnAccEnter') || document.getElementById('btnTypeEnter');
    btnEnter?.addEventListener('click', () => {
      window.Haptics?.ok();
      send('key', { code: 66 });
    });

    const btnClear = document.getElementById('btnAccClear') || document.getElementById('btnTypeClear');
    btnClear?.addEventListener('click', () => {
      window.Haptics?.tap();
      if (el.typeInput) {
        el.typeInput.value = '';
        el.typeInput.focus();
      }
    });

    const btnPaste = document.getElementById('btnAccPaste') || document.getElementById('btnTypePaste');
    btnPaste?.addEventListener('click', async () => {
      window.Haptics?.tap();
      try {
        const text = await navigator.clipboard.readText();
        if (text && el.typeInput) {
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
        const fullUrl = data.url.startsWith('/') ? data.url : '/' + data.url;
        el.screenImg.src = fullUrl;
        el.screenImg.onload = () => {
          el.screenShimmer?.classList.remove('active');
          if (el.screenEmpty) el.screenEmpty.style.display = 'none';
          el.screenImg.classList.add('loaded');
          if (el.btnSaveScreen) el.btnSaveScreen.style.display = 'inline-flex';
          if (el.btnViewScreen) el.btnViewScreen.style.display = 'inline-flex';
          showToast('Screen captured');
        };
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

  function setupFilesTab() {
    // TV Storage Tab
    el.btnStorageTv?.addEventListener('click', () => {
      window.Haptics?.tap();
      setActiveStorageTab(el.btnStorageTv);
      loadDirectory('/storage/emulated/0/Download');
    });

    // Parent Folder (Up)
    el.btnFolderUp?.addEventListener('click', () => {
      window.Haptics?.tap();
      const cur = state.currentPath.replace(/\/+$/, '');
      if (cur === '/storage' || cur === '') return;
      const parent = cur.substring(0, cur.lastIndexOf('/')) || '/storage';
      loadDirectory(parent);
    });

    // Refresh Files
    el.btnRefreshFiles?.addEventListener('click', () => {
      window.Haptics?.tap();
      loadDirectory(state.currentPath);
    });

    // Trigger Upload Button -> file picker
    el.btnTriggerUpload?.addEventListener('click', () => {
      window.Haptics?.tap();
      el.fileUploadInput?.click();
    });

    // File selected for upload
    el.fileUploadInput?.addEventListener('change', () => {
      const files = Array.from(el.fileUploadInput.files || []);
      if (files.length > 0) {
        uploadFilesSequentially(files);
      }
      el.fileUploadInput.value = '';
    });

    // Paste file
    el.btnPasteFile?.addEventListener('click', async () => {
      if (!state.clipboard) return;
      window.Haptics?.ok();
      const src = state.clipboard.path;
      const dest = state.currentPath + '/' + state.clipboard.name;
      el.btnPasteFile.disabled = true;

      try {
        const res = await send('copy_file', { src, dest });
        if (res.status === 'ok') {
          showToast(`Pasted "${state.clipboard.name}"`);
          state.clipboard = null;
          el.btnPasteFile.style.display = 'none';
          loadDirectory(state.currentPath);
        } else {
          showToast('Paste failed');
        }
      } catch (e) {
        showToast('Paste failed');
      } finally {
        el.btnPasteFile.disabled = false;
      }
    });
  }

  function setActiveStorageTab(activeBtn) {
    if (!el.storageTabsWrap) return;
    el.storageTabsWrap.querySelectorAll('.btn-storage-tab').forEach(b => b.classList.remove('active'));
    activeBtn?.classList.add('active');
  }

  async function loadDirectory(path) {
    if (!el.fileListWrap) return;
    el.fileListWrap.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--text-3); font-size: 13px;">Loading files…</div>';

    try {
      const data = await send('list_files', { path: path });
      if (!data || data.status !== 'ok') {
        el.fileListWrap.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--err); font-size: 13px;">Failed to read folder</div>';
        return;
      }

      state.currentPath = data.path;
      if (el.currentPathText) el.currentPathText.textContent = data.path;

      // Update storage tabs (TV + any attached USB drives)
      updateStorageTabs(data.drives || []);

      // Render file & folder rows
      renderFileRows(data.items || []);

    } catch (e) {
      el.fileListWrap.innerHTML = '<div style="padding: 24px; text-align: center; color: var(--err); font-size: 13px;">Connection error reading files</div>';
    }
  }

  function updateStorageTabs(drives) {
    if (!el.storageTabsWrap) return;
    // Remove dynamic USB buttons while keeping TV Internal button
    const oldUsbBtns = el.storageTabsWrap.querySelectorAll('.btn-usb-drive');
    oldUsbBtns.forEach(b => b.remove());

    drives.forEach(drv => {
      const btn = document.createElement('button');
      btn.className = 'btn btn-storage-tab btn-usb-drive';
      btn.dataset.path = drv.path;
      btn.innerHTML = `<svg class="icon icon-16"><use href="#i-usb"/></svg><span>${drv.name}</span>`;
      btn.addEventListener('click', () => {
        window.Haptics?.tap();
        setActiveStorageTab(btn);
        loadDirectory(drv.path);
      });
      el.storageTabsWrap.appendChild(btn);
    });

    // Update active highlight based on current path
    if (state.currentPath.startsWith('/storage/emulated')) {
      setActiveStorageTab(el.btnStorageTv);
    } else {
      const match = el.storageTabsWrap.querySelector(`.btn-usb-drive[data-path="${state.currentPath}"]`);
      if (match) setActiveStorageTab(match);
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
      size.textContent = item.is_dir ? 'Folder' : formatFileSize(item.size);

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

      // 1. Download to Phone (files only)
      if (!item.is_dir) {
        const btnDownload = document.createElement('button');
        btnDownload.className = 'btn btn-file-icon';
        btnDownload.title = 'Download to phone';
        btnDownload.innerHTML = '<svg class="icon icon-16"><use href="#i-download"/></svg>';
        btnDownload.addEventListener('click', e => {
          e.stopPropagation();
          window.Haptics?.tap();
          const filePath = state.currentPath.replace(/\/+$/, '') + '/' + item.name;
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

      // 2. Copy button
      const btnCopy = document.createElement('button');
      btnCopy.className = 'btn btn-file-icon';
      btnCopy.title = 'Copy';
      btnCopy.innerHTML = '<svg class="icon icon-16"><use href="#i-copy"/></svg>';
      btnCopy.addEventListener('click', e => {
        e.stopPropagation();
        window.Haptics?.tap();
        state.clipboard = {
          action: 'copy',
          path: state.currentPath.replace(/\/+$/, '') + '/' + item.name,
          name: item.name,
          is_dir: item.is_dir
        };
        if (el.btnPasteFile && el.pasteActionText) {
          el.pasteActionText.textContent = `Paste "${item.name}"`;
          el.btnPasteFile.style.display = 'inline-flex';
        }
        showToast(`Copied "${item.name}"`);
      });
      actions.appendChild(btnCopy);

      // 3. Delete button
      const btnDel = document.createElement('button');
      btnDel.className = 'btn btn-file-icon';
      btnDel.title = 'Delete';
      btnDel.innerHTML = '<svg class="icon icon-16" style="color: var(--err);"><use href="#i-trash"/></svg>';
      btnDel.addEventListener('click', async e => {
        e.stopPropagation();
        window.Haptics?.tap();
        const itemPath = state.currentPath.replace(/\/+$/, '') + '/' + item.name;
        if (!confirm(`Permanently delete "${item.name}"?`)) return;

        try {
          const res = await send('delete_file', { path: itemPath });
          if (res.status === 'ok') {
            showToast(`Deleted "${item.name}"`);
            loadDirectory(state.currentPath);
          } else {
            showToast('Delete failed');
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

  function uploadFilesSequentially(files) {
    if (files.length === 0) return;
    if (el.uploadProgressWrap) el.uploadProgressWrap.classList.add('show');

    let idx = 0;
    function next() {
      if (idx >= files.length) {
        if (el.uploadProgressWrap) el.uploadProgressWrap.classList.remove('show');
        showToast(`Uploaded ${files.length} file(s) successfully!`);
        loadDirectory(state.currentPath);
        return;
      }

      const file = files[idx];
      idx++;

      const dest = state.currentPath;
      const uploadUrl = `${ENDPOINT}?action=upload_file&dest=${encodeURIComponent(dest)}&name=${encodeURIComponent(file.name)}&key=${API_KEY}`;

      const xhr = new XMLHttpRequest();
      xhr.open('POST', uploadUrl, true);
      xhr.setRequestHeader('Content-Type', 'application/octet-stream');

      xhr.upload.onprogress = ev => {
        if (ev.lengthComputable) {
          const pct = Math.round((ev.loaded / ev.total) * 100);
          if (el.uploadProgressBar) el.uploadProgressBar.style.width = pct + '%';
          if (el.uploadPercentText) el.uploadPercentText.textContent = pct + '%';
          if (el.uploadStatusText) el.uploadStatusText.textContent = `Uploading "${file.name}" (${pct}%)`;
        }
      };

      xhr.onload = () => {
        if (xhr.status === 200) {
          next();
        } else {
          showToast(`Upload failed for ${file.name}`);
          next();
        }
      };

      xhr.onerror = () => {
        showToast(`Network error uploading ${file.name}`);
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
      let urlVal = el.linkUrlInput.value.trim();
      if (!urlVal) {
        showToast('Please paste a link or video URL');
        el.linkUrlInput.focus();
        return;
      }
      if (!/^https?:\/\//i.test(urlVal)) {
        urlVal = 'https://' + urlVal;
      }

      window.Haptics?.ok();
      showToast(`Launching on TV…`);
      try {
        await send('open_url', { url: urlVal, target: target });
        showToast('Opened on TV!');
      } catch (e) {
        showToast('Failed to open URL on TV');
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
        const usbStr = data.usb_drives && data.usb_drives !== 'none' ? data.usb_drives.trim() : 'None detected';
        el.dashUsb.textContent = usbStr;
      }
      if (el.dashUptime) {
        el.dashUptime.textContent = data.uptime || '--';
      }
      if (el.dashFocus) {
        el.dashFocus.textContent = data.focus || 'Android TV Home';
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

    setNavMode(state.navMode);
    pingTv();
    setInterval(pingTv, 4000);

    // Initial wake lock on remote tab
    acquireWakeLock();
  });
})();
