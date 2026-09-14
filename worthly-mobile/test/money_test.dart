import 'package:flutter_test/flutter_test.dart';
import 'package:worthly_mobile/theme/money.dart';

void main() {
  test('formats EUR with Portuguese grouping from a decimal string', () {
    expect(MoneyFmt.amount('12430.21', 'EUR'), '12.430,21 €');
  });

  test('privacy mode hides amounts without touching the source string', () {
    expect(MoneyFmt.amount('12430.21', 'EUR', privacy: true), '••••••');
    expect(MoneyFmt.signed('40.00', 'EUR', credit: true, privacy: true), '••••••');
  });

  test('signed amounts keep credit and debit distinct', () {
    expect(MoneyFmt.signed('40.00', 'EUR', credit: true), '+40,00 €');
    expect(MoneyFmt.signed('12.50', 'EUR', credit: false), '−12,50 €');
  });

  test('cents math stays in BigInt', () {
    expect(MoneyFmt.cents('12.50'), BigInt.from(1250));
    expect(MoneyFmt.fromCents(BigInt.from(1250)), '12.50');
    expect(MoneyFmt.weight('25.00', '100.00'), '25,0%');
  });
}
