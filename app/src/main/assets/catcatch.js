// cat-catch 式通用媒体嗅探（性能资源表 + fetch/XHR 窃听），发现即上报
(function () {
    if (window.__xgjCatch) return;
    window.__xgjCatch = 1;
    var seen = {};
    var exts = /\.(ts|m4s|mp4|webm|flv|m3u8|mpd|aac|mp3|ogg|mov|mkv)(\?|$|#)/i;
    function report(u, why) {
        try {
            if (!u || typeof u !== 'string' || u.length < 12) return;
            if (u.indexOf('data:') === 0 || u.indexOf('blob:') === 0) return;
            if (seen[u]) return; seen[u] = 1;
            if (!exts.test(u)) return;
            if (/bilivideo|upos-|127\.0\.0\.1:8123/.test(u)) return; // 专用解析已覆盖
            if (window.xgj && window.xgj.onGenericMedia) window.xgj.onGenericMedia(u, why || '');
        } catch (e) { }
    }
    function scan() {
        try {
            var es = performance.getEntriesByType('resource') || [];
            for (var i = 0; i < es.length; i++) report(es[i].name, 'perf');
        } catch (e) { }
    }
    var of = window.fetch;
    if (of) {
        window.fetch = function () {
            try {
                var u = arguments[0];
                report(typeof u === 'string' ? u : (u && u.url), 'fetch');
            } catch (e) { }
            return of.apply(this, arguments);
        };
    }
    var oo = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (m, u) {
        try { report(typeof u === 'string' ? u : String(u), 'xhr'); } catch (e) { }
        return oo.apply(this, arguments);
    };
    setTimeout(scan, 1500);
    setInterval(scan, 2000);
})();
