let suppressed = false;

export const suppressUnsavedChangesPrompt = () => {
  suppressed = true;
};

export const isUnsavedChangesPromptSuppressed = () => suppressed;
