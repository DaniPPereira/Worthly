import 'package:flutter_test/flutter_test.dart';
import 'package:worthly_mobile/theme/period.dart';

void main() {
  test('month key and previous months stay calendar-stable in UTC', () {
    final at = DateTime.utc(2026, 9, 14, 10);
    expect(Period.monthKey('UTC', at), '2026-09');
    expect(Period.previousMonths('UTC', 3, at), ['2026-07', '2026-08', '2026-09']);
  });

  test('month range uses the last calendar day', () {
    expect(Period.monthRange('2026-02').from, '2026-02-01');
    expect(Period.monthRange('2026-02').to, '2026-02-28');
    expect(Period.monthRange('2024-02').to, '2024-02-29');
  });

  test('shiftMonthKey walks calendar months', () {
    expect(Period.shiftMonthKey('2026-01', -1), '2025-12');
    expect(Period.shiftMonthKey('2026-12', 1), '2027-01');
    expect(Period.shiftMonthKey('2026-09', 0), '2026-09');
  });

  test('monthsThrough ends at the selected month', () {
    expect(Period.monthsThrough('2026-03', 4), ['2025-12', '2026-01', '2026-02', '2026-03']);
    expect(Period.monthTitle('2026-09'), 'September 2026');
  });

  test('ymd round-trips a calendar date', () {
    expect(Period.ymd(Period.parseYmd('2026-09-14')), '2026-09-14');
  });

  test('instant formatting in UTC is dated, not relative', () {
    expect(Period.instant('2026-09-12T18:20:00Z', 'UTC'), '12 Sep, 18:20');
    expect(Period.day('2026-09-12T18:20:00Z', 'UTC'), '12 Sep');
  });
}
