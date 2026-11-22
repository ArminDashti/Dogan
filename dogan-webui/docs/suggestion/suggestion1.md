# Suggestion: code-split the camera panel

**Component:** `CameraPanel.vue`

`hls.js` is already loaded on demand when the HLS fallback is used. Consider lazy-loading `CameraPanel` via dynamic `import()` on the device and stream routes to reduce initial bundle size further.

**Effort:** Low (1 route-level change per view).
