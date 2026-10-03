// cat-catch 式嗅探（移植版）：fetch/XHR 响应 Content-Type 窃听 + 资源表扫描
// 不依赖文件后缀——按响应头 video/* mpegurl mp2t fmp4 octet-stream 判定
(function () {
    if (window.__xgjCatch2) return;
    window.__xgjCatch2 = 1;
    var seen = {};
    function isMediaCt(ct) {
        return ct && /video\/|application\/(vnd\.apple\.mpegurl|mp2t|octet-stream|dash\+xml)|mpegurl|fmp4/i.test(ct);
    }
    function report(u, tag) {
        try {
            if (!u || typeof u !== 'string' || u.length < 15) return;
            if (/^data:|^blob:|^about:/.test(u)) return;
            if (seen[u]) return; seen[u] = 1;
            if (window.xgj && window.xgj.onGenericMedia) window.xgj.onGenericMedia(u, tag || '');
        } catch (e) { }
    }
    // ---- fetch 窃听 ----
    var of = window.fetch;
    if (of) {
        window.fetch = function () {
            var args = arguments;
            var u = (typeof args[0] === 'string') ? args[0] : (args[0] && args[0].url);
            var p = of.apply(this, args);
            try {
                p.then(function (r) {
                    try {
                        var ct = r.headers.get('content-type') || '';
                        var url = r.url || u;
                        if (isMediaCt(ct)) report(url, ct);
                        else if (/\.(m3u8|mp4|flv|ts|mpd)(\?|$)/i.test(url)) report(url, ct);
                    } catch (e) { }
                }).catch(function () { });
            } catch (e) { }
            return p;
        };
    }
    // ---- XHR 窃听 ----
    var oo = XMLHttpRequest.prototype.open, osend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (m, u) { this.__u = u; return oo.apply(this, arguments); };
    XMLHttpRequest.prototype.send = function () {
        var x = this;
        try {
            x.addEventListener('readystatechange', function () {
                try {
                    if (x.readyState >= 2) {
                        var ct = x.getResponseHeader('content-type') || '';
                        var u = x.responseURL || x.__u;
                        if (isMediaCt(ct)) report(u, ct);
                        else if (u && /\.(m3u8|mp4|flv|ts|mpd)(\?|$)/i.test(u)) report(u, ct);
                    }
                } catch (e) { }
            });
        } catch (e) { }
        return osend.apply(this, arguments);
    };
    // ---- 资源表兜底扫描 ----
    setInterval(function () {
        try {
            var es = performance.getEntriesByType('resource') || [];
            for (var i = 0; i < es.length; i++) {
                var n = es[i].name;
                if (/\.(ts|m3u8|flv|mp4|m4s|mpd)(\?|$)/i.test(n)) report(n, 'perf');
            }
        } catch (e) { }
    }, 2000);
})();
