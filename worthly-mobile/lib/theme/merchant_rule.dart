import 'package:worthly_mobile/api/models.dart';

class MerchantRuleHint {
  const MerchantRuleHint({
    required this.matchValue,
    required this.operator,
    required this.display,
    this.field = 'MERCHANT',
  });

  final String matchValue;
  final String operator;
  final String display;
  final String field;
}

const _prefixes = [
  'compra estrang',
  'compra mbw',
  'compra',
  'debito direto',
  'levantamento de numerario',
  'top-up by',
  'top up by',
];

const _drop = {
  'lda',
  'sa',
  'unipessoal',
  'comercial',
  'servicos',
  'serviços',
  'portugal',
  'lisboa',
  'porto',
  'braga',
  'coimbra',
  'faro',
  'setubal',
  'aveiro',
  'pt',
  'the',
  'de',
  'do',
  'da',
  'dos',
  'das',
  'e',
  'com',
  'www',
  'bill',
  'inst',
  'mb',
  'atm',
  'sucursal',
  'loja',
  'compra',
  'estrang',
  'mbw',
};

const _generic = {
  'paypal',
  'revolut',
  'klarna',
  'eupago',
  'mbway',
  'mb way',
  'trading 212',
  'trading212',
  'apple pay',
  'google pay',
  'compra',
};

MerchantRuleHint? stemMerchant(String? raw) {
  if (raw == null || raw.trim().isEmpty) {
    return null;
  }
  var text = _normalizeMerchant(raw);
  if (text.isEmpty) {
    return null;
  }
  var changed = true;
  while (changed) {
    changed = false;
    for (final prefix in _prefixes) {
      if (text == prefix || text.startsWith('$prefix ')) {
        text = text.substring(prefix.length).trim();
        changed = true;
      }
    }
  }
  final tokens = text
      .split(' ')
      .map((token) => token.replaceAll(RegExp(r'^\.+|\.+$'), ''))
      .where((token) => token.length >= 2 && !RegExp(r'^\d+$').hasMatch(token) && !_drop.contains(token))
      .toList();
  if (tokens.isEmpty) {
    return null;
  }
  final matchValue = tokens.length >= 2 && tokens.first.length <= 6 && !tokens.first.contains('.')
      ? '${tokens.first} ${tokens[1]}'
      : tokens.first;
  if (matchValue.length < 3 || _generic.contains(matchValue)) {
    return null;
  }
  final source = _normalizeMerchant(raw);
  return MerchantRuleHint(
    matchValue: matchValue,
    operator: source == matchValue ? 'EQUALS' : 'CONTAINS',
    display: _titleCase(matchValue),
  );
}

List<String> splitMatchPhrases(String raw) {
  final seen = <String>{};
  final phrases = <String>[];
  for (final part in raw.split(RegExp(r'[\n,;]+'))) {
    final value = part.trim().replaceAll(RegExp(r'\s+'), ' ');
    if (value.length < 3 || value.length > 200) {
      continue;
    }
    final key = value.toLowerCase();
    if (seen.contains(key)) {
      continue;
    }
    seen.add(key);
    phrases.add(value);
  }
  return phrases;
}

MerchantRuleHint? merchantRuleSuggestion({
  String? merchant,
  String? description,
  String? categoryCode,
  String? previousCategoryId,
  required String nextCategoryId,
  required List<CategorizationRule> rules,
}) {
  if (categoryCode == 'uncategorized' || previousCategoryId == nextCategoryId) {
    return null;
  }
  final merchantText = merchant?.trim() ?? '';
  final descriptionText = description?.trim() ?? '';
  final merchantHint = stemMerchant(merchantText);
  if (merchantHint != null && !fieldAlreadyCovered(merchantText, 'MERCHANT', rules)) {
    return MerchantRuleHint(
      matchValue: merchantHint.matchValue,
      operator: merchantHint.operator,
      display: merchantHint.display,
      field: 'MERCHANT',
    );
  }
  final descriptionHint = stemMerchant(descriptionText);
  if (descriptionHint != null && !fieldAlreadyCovered(descriptionText, 'DESCRIPTION', rules)) {
    return MerchantRuleHint(
      matchValue: descriptionHint.matchValue,
      operator: descriptionHint.operator,
      display: descriptionHint.display,
      field: 'DESCRIPTION',
    );
  }
  return null;
}

bool fieldAlreadyCovered(String source, String field, List<CategorizationRule> rules) {
  final haystack = _normalizeMerchant(source);
  final expected = field.toUpperCase();
  return rules.any((rule) {
    if (!rule.enabled || rule.field.toUpperCase() != expected) {
      return false;
    }
    final needle = _normalizeMerchant(rule.matchValue);
    if (needle.isEmpty) {
      return false;
    }
    switch (rule.operator.toUpperCase()) {
      case 'EQUALS':
        return haystack == needle;
      case 'STARTS_WITH':
        return haystack.startsWith(needle);
      default:
        return haystack.contains(needle);
    }
  });
}

bool merchantAlreadyCovered(String source, List<CategorizationRule> rules) {
  return fieldAlreadyCovered(source, 'MERCHANT', rules);
}

String formatMerchantRule(CategorizationRule rule, String categoryLabel) {
  final field = rule.field.toUpperCase() == 'DESCRIPTION' ? 'Description' : 'Merchant';
  final operator = switch (rule.operator.toUpperCase()) {
    'EQUALS' => 'equals',
    'STARTS_WITH' => 'starts with',
    _ => 'contains',
  };
  return '$field $operator “${rule.matchValue}” → $categoryLabel';
}

String _normalizeMerchant(String raw) {
  return raw
      .toLowerCase()
      .replaceAll('www.', ' ')
      .replaceAll(RegExp(r'[^\p{L}\p{N}.]+', unicode: true), ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

String _titleCase(String value) {
  return value.split(' ').map((part) {
    if (part.contains('.') || part.isEmpty) {
      return part;
    }
    return '${part[0].toUpperCase()}${part.substring(1)}';
  }).join(' ');
}
