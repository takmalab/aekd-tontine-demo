import { DEFAULT_COUNTRY, formatPhone, withDialCode } from './countries';

describe('withDialCode', () => {
  it('adds the country dial code to a national number', () => {
    expect(withDialCode('6 00 00 00 03', DEFAULT_COUNTRY)).toBe('+237600000003');
  });

  it('keeps a number already typed with + or 00', () => {
    expect(withDialCode('+237600000003', DEFAULT_COUNTRY)).toBe('+237600000003');
    expect(withDialCode('00237600000003', DEFAULT_COUNTRY)).toBe('00237600000003');
  });

  it('recognizes a full number typed without +', () => {
    expect(withDialCode('237 600 000 003', DEFAULT_COUNTRY)).toBe('+237600000003');
  });
});

describe('formatPhone', () => {
  it('groups a Cameroonian number for display', () => {
    expect(formatPhone('+237600000003')).toBe('+237 6 00 00 00 03');
  });

  it('leaves unknown formats unchanged', () => {
    expect(formatPhone('+33612345678')).toBe('+33612345678');
    expect(formatPhone(null)).toBe('');
  });
});
