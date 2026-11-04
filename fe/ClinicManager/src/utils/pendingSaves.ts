type PendingSave = () => Promise<void>;

const pendingSaves = new Set<PendingSave>();

export function registerPendingSave(save: PendingSave): () => void {
  pendingSaves.add(save);
  return () => {
    pendingSaves.delete(save);
  };
}

export function hasPendingSaves(): boolean {
  return pendingSaves.size > 0;
}

export async function flushPendingSaves(timeoutMs = 4000): Promise<void> {
  if (pendingSaves.size === 0) return;
  const saves = Promise.allSettled([...pendingSaves].map((save) => save()));
  const timeout = new Promise<void>((resolve) => setTimeout(resolve, timeoutMs));
  await Promise.race([saves, timeout]);
}
