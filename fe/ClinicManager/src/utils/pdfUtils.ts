import { MOBILE_BREAKPOINT } from "../hooks/useIsMobile";

const downloadBlobUrl = (url: string, filename: string): void => {
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
};

const renderInFrame = (target: Window, url: string, filename: string): void => {
  target.document.title = filename;
  const style = target.document.createElement("style");
  style.textContent =
    "body { margin: 0; } iframe { width: 100%; height: 100vh; border: none; }";
  target.document.head.appendChild(style);
  const iframe = target.document.createElement("iframe");
  iframe.src = url;
  target.document.body.replaceChildren(iframe);
};

export const openPdf = async (
  loadBlob: () => Promise<Blob>,
  filename: string
): Promise<boolean> => {
  const target = window.open("", "_blank");
  if (target?.document.body) {
    target.document.title = "Generowanie PDF...";
    target.document.body.textContent = "Generowanie PDF...";
  }

  try {
    const blob = await loadBlob();
    const url = window.URL.createObjectURL(blob);

    if (!target || target.closed) {
      downloadBlobUrl(url, filename);
      return true;
    }

    const isMobile = window.matchMedia(
      `(max-width: ${MOBILE_BREAKPOINT - 1}px)`
    ).matches;

    if (isMobile) {
      target.location.href = url;
    } else {
      renderInFrame(target, url, filename);
    }
    return true;
  } catch (error) {
    console.error("Error generating PDF:", error);
    target?.close();
    return false;
  }
};
