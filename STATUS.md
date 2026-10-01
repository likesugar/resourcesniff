# 资源嗅探（com.wink.xgjhome）状态存档 2026-10-01

- 最新 vc99 / v97.0；下版 versionCode 从 101 起
- 双核 MediaPlayer + IjkPlayer（so: douyin_redesign 构建产物；java: DK dkplayer-players/ijk）
- 首页卡片：资源嗅探(🪩) / 下载(➼→DownloadManagerActivity)
- DownloadManagerActivity：XML 布局，路由=进行中→下载栏，完成→全部/视频/录制；RecEngine 后台线程+暂停(Range续传)+ffmpeg-kit genpts 重建
- SniffActivity：DK 控制层/沉浸式/手机UA/bofq+DK 抖音规则/BiliResolver(cookie 7天)
- 坑：本地(/android_build/)与仓库 build.sh 两份必须同步；ffk_out/sex 与 sex/common 都需 smart-exception jar；u0026 先解码再正则
