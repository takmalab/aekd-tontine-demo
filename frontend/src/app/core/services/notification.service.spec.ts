import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationSoundService } from './notification-sound.service';
import { NotificationService, base64UrlToBytes } from './notification.service';

describe('NotificationService – son des nouvelles notifications', () => {
  let service: NotificationService;
  let http: HttpTestingController;
  let plays: number;

  beforeEach(() => {
    plays = 0;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: NotificationSoundService, useValue: { play: () => plays++ } },
      ],
    });
    service = TestBed.inject(NotificationService);
    http = TestBed.inject(HttpTestingController);
  });

  function respond(count: number): void {
    service.refreshUnread().subscribe();
    http.expectOne((req) => req.url.endsWith('/notifications/unread-count')).flush({ count });
  }

  it('stays silent on first load, rings when the count increases, not when it decreases', () => {
    respond(3);
    expect(plays).toBe(0);

    respond(4);
    expect(plays).toBe(1);

    respond(2);
    respond(2);
    expect(plays).toBe(1);
    expect(service.unreadCount()).toBe(2);
  });

  it('stays silent after a reset (next account starts from scratch)', () => {
    respond(1);
    service.reset();
    respond(5);
    expect(plays).toBe(0);
  });
});

describe('base64UrlToBytes', () => {
  it('decodes base64url without padding (VAPID public key format)', () => {
    // "AEKD" -> QUVLRA (base64) ; avec "-" et "_" à la place de "+" et "/".
    expect(Array.from(base64UrlToBytes('QUVLRA'))).toEqual([65, 69, 75, 68]);
    expect(Array.from(base64UrlToBytes('-_8'))).toEqual([251, 255]);
  });

  it('decodes a 65-byte uncompressed P-256 point', () => {
    const key = 'B' + 'A'.repeat(86);
    const bytes = base64UrlToBytes(key);
    expect(bytes.length).toBe(65);
    expect(bytes[0]).toBe(4);
  });
});
