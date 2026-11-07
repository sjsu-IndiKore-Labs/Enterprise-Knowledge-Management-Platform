import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProcessingTracker } from './ProcessingTracker';

const get = vi.fn();
vi.mock('@/lib/api/client', () => ({ api: { GET: (...args: unknown[]) => get(...args) } }));

const ok = (status: string, versions: unknown[] = []) => ({
  data: { id: 'd1', status, versions },
  response: new Response(null, { status: 200 }),
});

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe('ProcessingTracker', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    get.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('polls every 3 seconds until INDEXED, then stops and reports done', async () => {
    get
      .mockResolvedValueOnce(ok('QUEUED'))
      .mockResolvedValueOnce(ok('PROCESSING'))
      .mockResolvedValueOnce(ok('INDEXED'));
    const onDone = vi.fn();
    render(<ProcessingTracker documentId="d1" initialStatus="QUEUED" onDone={onDone} />);

    await advance(0);
    expect(get).toHaveBeenCalledTimes(1);
    await advance(3000);
    expect(get).toHaveBeenCalledTimes(2);
    await advance(3000);
    expect(get).toHaveBeenCalledTimes(3);
    expect(screen.getByText(/now searchable/i)).toBeInTheDocument();
    expect(onDone).toHaveBeenCalledTimes(1);

    await advance(15000);
    expect(get).toHaveBeenCalledTimes(3); // terminal status: no more polling
  });

  it('shows the failure reason from the latest failed version', async () => {
    get.mockResolvedValueOnce(
      ok('FAILED', [
        { version: 1, errorMessage: 'Could not extract text' },
        { version: 2, errorMessage: null },
      ]),
    );
    render(<ProcessingTracker documentId="d1" />);
    await advance(0);
    expect(screen.getByRole('alert')).toHaveTextContent('Could not extract text');
    await advance(10000);
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('stops polling when unmounted', async () => {
    get.mockResolvedValue(ok('PROCESSING'));
    const { unmount } = render(<ProcessingTracker documentId="d1" />);
    await advance(0);
    expect(get).toHaveBeenCalledTimes(1);
    unmount();
    await advance(12000);
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('keeps retrying after a transient error', async () => {
    get
      .mockResolvedValueOnce({ error: { message: 'boom' }, response: new Response(null, { status: 503 }) })
      .mockResolvedValueOnce(ok('INDEXED'));
    render(<ProcessingTracker documentId="d1" />);
    await advance(0);
    expect(screen.getByRole('status')).toHaveTextContent(/Retrying/);
    await advance(3000);
    expect(screen.getByText(/now searchable/i)).toBeInTheDocument();
  });
});
