// Haptics controller for TV Remote
(function() {
  let enabled = localStorage.getItem('tv_remote_haptics') !== 'false';

  function isEnabled() {
    return enabled && typeof navigator !== 'undefined' && 'vibrate' in navigator;
  }

  window.Haptics = {
    tap() {
      if (isEnabled()) {
        try { navigator.vibrate(8); } catch (e) {}
      }
    },
    ok() {
      if (isEnabled()) {
        try { navigator.vibrate(12); } catch (e) {}
      }
    },
    longPress() {
      if (isEnabled()) {
        try { navigator.vibrate([25, 40, 25]); } catch (e) {}
      }
    },
    error() {
      if (isEnabled()) {
        try { navigator.vibrate([30, 40, 30]); } catch (e) {}
      }
    },
    setEnabled(val) {
      enabled = !!val;
      localStorage.setItem('tv_remote_haptics', enabled ? 'true' : 'false');
    },
    getEnabled() {
      return enabled;
    }
  };
})();
