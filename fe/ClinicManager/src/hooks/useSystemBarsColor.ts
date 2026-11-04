import { useEffect } from "react";

const ACTIVE_CLASS = "system-bars-modal";
const OVERLAY_SELECTOR = ".modal-overlay:not(:has(.modal.cc-light))";
const OVERLAY_COLOR_VARIABLE = "--items-background";
const PAPER_SELECTOR = ".dw-editor";
const PAPER_COLOR_VARIABLE = "--card-paper";
const LIGHT_PORTALS_CLASS = "cc-light-portals";

export function useSystemBarsColor(enabled: boolean, colorVariable = "--items-background") {
  useEffect(() => {
    if (!enabled) return;
    const root = document.documentElement;
    const styles = getComputedStyle(root);
    const baseColor = styles.getPropertyValue(colorVariable).trim();
    const overlayColor = styles.getPropertyValue(OVERLAY_COLOR_VARIABLE).trim();
    const paperColor = styles.getPropertyValue(PAPER_COLOR_VARIABLE).trim();

    let meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    const createdMeta = !meta;
    const previousColor = meta?.content ?? null;
    if (!meta) {
      meta = document.createElement("meta");
      meta.name = "theme-color";
      document.head.appendChild(meta);
    }
    const themeMeta = meta;

    const applyColor = () => {
      const lightPortals = document.body.classList.contains(LIGHT_PORTALS_CLASS);
      const color = document.querySelector(OVERLAY_SELECTOR)
        ? lightPortals
          ? paperColor
          : overlayColor
        : document.querySelector(PAPER_SELECTOR)
          ? paperColor
          : baseColor;
      if (!color) return;
      if (themeMeta.content !== color) themeMeta.content = color;
      root.style.setProperty("--system-bars-color", color);
    };

    applyColor();
    root.classList.add(ACTIVE_CLASS);

    const portalRoot = document.getElementById("portal-root");
    const observer = new MutationObserver(applyColor);
    if (portalRoot) observer.observe(portalRoot, { childList: true });
    observer.observe(document.body, { attributes: true, attributeFilter: ["class"] });

    return () => {
      observer.disconnect();
      root.classList.remove(ACTIVE_CLASS);
      root.style.removeProperty("--system-bars-color");
      if (createdMeta) {
        themeMeta.remove();
      } else if (previousColor !== null) {
        themeMeta.content = previousColor;
      }
    };
  }, [enabled, colorVariable]);
}

export default useSystemBarsColor;
