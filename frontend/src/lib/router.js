/** Simple hash-based router for SPA. */

export function push(path) {
  window.location.hash = "#" + path;
}

export function replace(path) {
  window.location.replace("#" + path);
}
