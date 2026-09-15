import 'package:flutter_test/flutter_test.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/theme/merchant_rule.dart';

void main() {
  test('strips Portuguese bank prefixes and cities', () {
    final hint = stemMerchant('COMPRA  1531 DECATHLON BRAGA');
    expect(hint?.matchValue, 'decathlon');
    expect(hint?.operator, 'CONTAINS');
    expect(hint?.display, 'Decathlon');
  });

  test('keeps two short words like Pingo Doce', () {
    final hint = stemMerchant('COMPRA PINGO DOCE LISBOA');
    expect(hint?.matchValue, 'pingo doce');
    expect(hint?.display, 'Pingo Doce');
  });

  test('keeps domain-like tokens', () {
    expect(stemMerchant('COMPRA ESTRANG APPLE.COM/BILL')?.matchValue, 'apple.com');
  });

  test('uses equals only when the merchant is already the stem', () {
    expect(stemMerchant('Netflix')?.operator, 'EQUALS');
  });

  test('does not offer generic processors', () {
    expect(stemMerchant('Top-up by Revolut'), isNull);
    expect(stemMerchant('PAYPAL'), isNull);
  });

  test('skips Uncategorized and existing merchant rules', () {
    expect(
      merchantRuleSuggestion(
        merchant: 'COMPRA DECATHLON',
        categoryCode: 'uncategorized',
        nextCategoryId: 'cat-1',
        rules: const [],
      ),
      isNull,
    );
    expect(
      merchantRuleSuggestion(
        merchant: 'COMPRA DECATHLON BRAGA',
        categoryCode: 'expense.shopping',
        nextCategoryId: 'cat-1',
        rules: const [
          CategorizationRule(
            id: '1',
            priority: 0,
            field: 'MERCHANT',
            operator: 'CONTAINS',
            matchValue: 'decathlon',
            targetCategoryId: 'cat-1',
          ),
        ],
      ),
      isNull,
    );
  });

  test('formats a merchant contains rule', () {
    expect(
      formatMerchantRule(
        const CategorizationRule(
          id: '1',
          priority: 0,
          field: 'MERCHANT',
          operator: 'CONTAINS',
          matchValue: 'decathlon',
          targetCategoryId: 'cat-1',
        ),
        'Shopping',
      ),
      'Merchant contains “decathlon” → Shopping',
    );
  });

  test('splits description phrases', () {
    expect(splitMatchPhrases('veterinario\npetshop, Petshop'), ['veterinario', 'petshop']);
  });
}
