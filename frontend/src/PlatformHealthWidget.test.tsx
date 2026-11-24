import {act,cleanup,render,screen} from '@testing-library/react';
import {afterEach,expect,it,vi} from 'vitest';
import {platformApi} from './api';
import PlatformHealthWidget from './PlatformHealthWidget';

vi.mock('./api',()=>({platformApi:vi.fn()}));

afterEach(()=>{
 cleanup();
 vi.useRealTimers();
 vi.resetAllMocks();
});

it('refreshes health after each check, shows failures, and stops when unmounted',async()=>{
 vi.useFakeTimers();
 vi.mocked(platformApi)
  .mockResolvedValueOnce({status:'UP',checkedAt:'2026-11-24T10:00:00Z'})
  .mockRejectedValueOnce(new Error('Network failed'))
  .mockResolvedValueOnce({status:'DOWN',checkedAt:'2026-11-24T10:01:00Z'});
 const {unmount}=render(<PlatformHealthWidget/>);
 expect(screen.getByRole('status',{name:'Platform health CHECKING'})).toBeTruthy();
 await act(async()=>{});
 expect(screen.getByRole('status',{name:'Platform health UP'})).toBeTruthy();
 expect(screen.getByText('Last checked').querySelector('time')?.dateTime).toBe('2026-11-24T10:00:00Z');

 await act(async()=>{await vi.advanceTimersByTimeAsync(30_000);});
 expect(screen.getByRole('status',{name:'Platform health UNAVAILABLE'})).toBeTruthy();
 expect(screen.getByText('Last checked').querySelector('time')?.dateTime).toBe('2026-11-24T10:00:00Z');

 await act(async()=>{await vi.advanceTimersByTimeAsync(30_000);});
 expect(screen.getByRole('status',{name:'Platform health DOWN'})).toBeTruthy();
 expect(screen.getByText('Last checked').querySelector('time')?.dateTime).toBe('2026-11-24T10:01:00Z');

 unmount();
 await vi.advanceTimersByTimeAsync(60_000);
 expect(platformApi).toHaveBeenCalledTimes(3);
 expect(platformApi).toHaveBeenLastCalledWith('/health');
});
