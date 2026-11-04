import { useCallback, useRef, useState } from "react";

export interface UploadRules {
  types: string[];
  maxBytes: number;
  typeMessage: string;
  sizeMessage: string;
}

const LOGO_RULES: UploadRules = {
  types: ["image/png"],
  maxBytes: 1024 * 1024,
  typeMessage: "Dozwolony format to PNG.",
  sizeMessage: "Plik jest za duży (maksymalnie 1 MB).",
};

export interface LogoApi {
  get: () => Promise<Blob>;
  upload: (file: File) => Promise<void>;
  remove: () => Promise<void>;
}

export interface LogoUpload {
  url: string | null;
  pendingFile: File | null;
  removed: boolean;
  changed: boolean;
  inputRef: React.RefObject<HTMLInputElement>;
  load: (hasLogo: boolean) => Promise<void>;
  select: (event: React.ChangeEvent<HTMLInputElement>) => void;
  clear: () => void;
  commit: () => Promise<void>;
  reset: () => void;
}

/**
 * Staged logo upload: picking a file only shows a preview, the request goes out
 * on commit() so it saves together with the rest of the settings form.
 */
export function useLogoUpload(
  api: LogoApi,
  onError: (message: string) => void,
  rules: UploadRules = LOGO_RULES
): LogoUpload {
  const [url, setUrl] = useState<string | null>(null);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [removed, setRemoved] = useState<boolean>(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const load = useCallback(
    async (hasLogo: boolean) => {
      setUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
      if (!hasLogo) return;
      try {
        const blob = await api.get();
        setUrl(URL.createObjectURL(blob));
      } catch {
        setUrl(null);
      }
    },
    [api]
  );

  // Mirrors the server-side rules so the user gets feedback without a round trip.
  // The server stays the source of truth - it also verifies the file really decodes.
  const validate = (file: File): string | null => {
    if (!rules.types.includes(file.type)) return rules.typeMessage;
    if (file.size > rules.maxBytes) return rules.sizeMessage;
    return null;
  };

  const select = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      event.target.value = "";
      if (!file) return;

      const error = validate(file);
      if (error) {
        onError(error);
        return;
      }

      setUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return URL.createObjectURL(file);
      });
      setPendingFile(file);
      setRemoved(false);
    },
    [onError, rules]
  );

  const clear = useCallback(() => {
    setUrl((prev) => {
      if (prev) URL.revokeObjectURL(prev);
      return null;
    });
    if (pendingFile) {
      setPendingFile(null);
      return;
    }
    setRemoved(true);
  }, [pendingFile]);

  const commit = useCallback(async () => {
    if (pendingFile) {
      await api.upload(pendingFile);
    } else if (removed) {
      await api.remove();
    }
  }, [api, pendingFile, removed]);

  const reset = useCallback(() => {
    setPendingFile(null);
    setRemoved(false);
  }, []);

  return {
    url,
    pendingFile,
    removed,
    changed: pendingFile !== null || removed,
    inputRef,
    load,
    select,
    clear,
    commit,
    reset,
  };
}

export default useLogoUpload;
