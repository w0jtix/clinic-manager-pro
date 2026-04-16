import { renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useInactivityTimer } from '../../hooks/useInactivityTimer';
import AuthService from '../../services/AuthService';

vi.mock('../../services/AuthService', () => ({
  default: {
    logout: vi.fn(),
  },
}));

describe('useInactivityTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    vi.mocked(AuthService.logout).mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('logs out after 3 minutes of inactivity', () => {
    renderHook(() => useInactivityTimer());

    vi.advanceTimersByTime(3 * 60 * 1000 + 30_000);

    expect(AuthService.logout).toHaveBeenCalledTimes(1);
  });

  it('does not logout when user is active', () => {
    renderHook(() => useInactivityTimer());

    vi.advanceTimersByTime(2 * 60 * 1000 + 50_000); // 2min 50s — no logout yet

    window.dispatchEvent(new MouseEvent('mousemove'));

    vi.advanceTimersByTime(30_000);

    expect(AuthService.logout).not.toHaveBeenCalled();
  });

  it('registers event listeners for activity events', () => {
    const addEventListenerSpy = vi.spyOn(window, 'addEventListener');

    renderHook(() => useInactivityTimer());

    const events = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'];
    events.forEach((event) => {
      expect(addEventListenerSpy).toHaveBeenCalledWith(event, expect.any(Function));
    });
  });

  it('removes event listeners when component is unmounted', () => {
    const removeEventListenerSpy = vi.spyOn(window, 'removeEventListener');

    const { unmount } = renderHook(() => useInactivityTimer());
    unmount();

    const events = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'];
    events.forEach((event) => {
      expect(removeEventListenerSpy).toHaveBeenCalledWith(event, expect.any(Function));
    });
  });
});
