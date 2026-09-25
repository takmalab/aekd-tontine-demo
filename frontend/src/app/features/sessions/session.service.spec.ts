import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Session } from './session.model';
import { SessionService } from './session.service';

describe('SessionService.timingOf', () => {
  let service: SessionService;
  const session = (date: string): Session => ({
    id: 's1',
    label: 'Séance',
    date,
    location: 'Douala',
    hostMember: null,
    beneficiaries: [],
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
    service = TestBed.inject(SessionService);
  });

  it("est « aujourd'hui » uniquement le jour de la séance", () => {
    const today = new Date(2026, 9, 3);
    expect(service.timingOf(session('2026-10-03'), today)).toBe('CURRENT');
    expect(service.timingOf(session('2026-10-04'), today)).toBe('UPCOMING');
    expect(service.timingOf(session('2026-10-02'), today)).toBe('PAST');
  });
});
