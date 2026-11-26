(function () {
    'use strict';
    var hosts = ['e54144713.at.baijiayun.com', 'e54144713.at.baijiacloud.com'];
    if (location.protocol !== 'https:' || hosts.indexOf(location.hostname) < 0 ||
        (location.port && location.port !== '443')) return;
    if (window.__coursePlaybackBridge) {
        if (document.hidden) window.__coursePlaybackBridge.pause();
        return;
    }

    var restored = new WeakMap();
    var suspended = false;
    var touchStart = null;

    // Verified against the provider's bplayer/1.12.0/bplayer.js and a real course.
    // Select only its registered instance attached to this page's known player root.
    function player() {
        var ctor = window.BPlayer;
        var app = document.getElementById('app');
        if (typeof ctor !== 'function' || !ctor.instances || !app || !app.isConnected) return null;
        var entries = Object.values(ctor.instances);
        for (var i = 0; i < entries.length; i++) {
            var p = entries[i];
            if (p instanceof ctor && p.container === app && p.template &&
                p.template.container && app.contains(p.template.container) &&
                typeof p.pause === 'function' && typeof p.seek === 'function') return p;
        }
        return null;
    }

    function pausePlayers() {
        var p = player();
        if (p) {
            try { p.pause(); } catch (ignored) { /* Provider lifecycle may be ending. */ }
        }
        document.querySelectorAll('video,audio').forEach(function (v) {
            try { v.pause(); } catch (ignored) { /* Detached media. */ }
        });
    }

    function pause() {
        suspended = true;
        pausePlayers();
    }

    document.addEventListener('visibilitychange', function () {
        if (document.hidden) pause();
        else if (suspended) {
            // The provider's WASM engine can resume in its own visibility handler.
            // Our later listener restores the user's paused state on return.
            pausePlayers();
            suspended = false;
        }
    });

    document.addEventListener('touchstart', function (event) {
        var t = event.touches && event.touches[0];
        touchStart = t ? { x: t.clientX, y: t.clientY, at: Date.now() } : null;
    }, { capture: true, passive: true });
    document.addEventListener('touchend', function (event) {
        var start = touchStart;
        touchStart = null;
        var t = event.changedTouches && event.changedTouches[0];
        if (!start || !t || Date.now() - start.at > 700 ||
            Math.abs(t.clientX - start.x) > 12 || Math.abs(t.clientY - start.y) > 12) return;
        var p = player();
        if (p && p.container.contains(event.target) && p.controller &&
            p.controller.player === p && typeof p.controller.show === 'function') {
            // Keep the provider's normal controls and five-second auto-hide behavior.
            p.controller.show();
        }
    }, { capture: true, passive: true });

    function finitePosition(value) {
        return typeof value === 'number' && isFinite(value) && value >= 0;
    }

    function position(initialSeconds) {
        if (document.hidden) return null;
        var target = finitePosition(initialSeconds) ? initialSeconds : 0;
        var p = player();
        if (p) {
            if (p.isCanplay !== true || !finitePosition(p.currentTime)) return null;
            var state = restored.get(p);
            if (!state) {
                state = { done: false, pending: false };
                restored.set(p, state);
            }
            if (!state.done) {
                if (state.pending) return null;
                if (target > 0) {
                    if (!finitePosition(p.duration) || p.duration <= 0) return null;
                    state.pending = true;
                    try {
                        // Public seek applies the provider's own viewing restrictions.
                        var result = p.seek(Math.min(target, Math.max(0, p.duration - 0.5)), true, false);
                        Promise.resolve(result).then(function (seeked) {
                            state.pending = false;
                            state.done = finitePosition(seeked);
                            if (document.hidden || suspended) pausePlayers();
                        }, function () { state.pending = false; });
                    } catch (ignored) { state.pending = false; }
                    return null;
                }
                state.done = true;
            }
            return p.isSeeking ? null : p.currentTime;
        }

        var video = document.querySelector('video');
        if (!video || video.readyState < 1) return null;
        if (!restored.has(video)) {
            if (target > 0) {
                if (!finitePosition(video.duration) || video.duration <= 0) return null;
                try { video.currentTime = Math.min(target, Math.max(0, video.duration - 0.5)); }
                catch (ignored) { return null; }
            }
            restored.set(video, { done: true });
        }
        return video.seeking || !finitePosition(video.currentTime) ? null : video.currentTime;
    }

    window.__coursePlaybackBridge = { position: position, pause: pause };
    if (document.hidden) pause();
})();
