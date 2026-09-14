class MoneyFmt {
  static const hidden = '••••••';

  static ({bool negative, String integer, String fraction}) _parts(String amount) {
    final trimmed = amount.trim();
    final negative = trimmed.startsWith('-');
    final unsigned = negative ? trimmed.substring(1) : trimmed;
    final bits = unsigned.split('.');
    final integer = bits.first.replaceFirst(RegExp(r'^0+(?=\d)'), '');
    final fraction = '${(bits.length > 1 ? bits[1] : '')}00'.substring(0, 2);
    return (negative: negative, integer: integer.isEmpty ? '0' : integer, fraction: fraction);
  }

  static String _group(String integer) {
    return integer.replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => '.');
  }

  static String amount(String value, String currency, {bool privacy = false}) {
    if (privacy) {
      return hidden;
    }
    final p = _parts(value);
    final body = '${_group(p.integer)},${p.fraction}';
    final signed = p.negative ? '-$body' : body;
    return currency == 'EUR' ? '$signed €' : '$signed $currency';
  }

  static String signed(String value, String currency, {required bool credit, bool privacy = false}) {
    if (privacy) {
      return hidden;
    }
    final unsigned = value.trim().startsWith('-') ? value.trim().substring(1) : value.trim();
    final formatted = amount(unsigned, currency);
    return credit ? '+$formatted' : '−$formatted';
  }

  static String rate(String? value, {bool privacy = false}) {
    if (privacy) {
      return '•••';
    }
    if (value == null || value.isEmpty) {
      return '—';
    }
    final p = _parts(value);
    return '${p.negative ? '-' : ''}${_group(p.integer)},${p.fraction}%';
  }

  static BigInt cents(String amount) {
    final p = _parts(amount);
    final mag = BigInt.parse(p.integer) * BigInt.from(100) + BigInt.parse(p.fraction);
    return p.negative ? -mag : mag;
  }

  static String fromCents(BigInt value) {
    final negative = value < BigInt.zero;
    final mag = value.abs();
    final whole = mag ~/ BigInt.from(100);
    final frac = mag % BigInt.from(100);
    return '${negative ? '-' : ''}$whole.${frac.toString().padLeft(2, '0')}';
  }

  static String add(String left, String right) => fromCents(cents(left) + cents(right));

  static String weight(String part, String total) {
    final p = cents(part).abs();
    final t = cents(total).abs();
    if (t == BigInt.zero) {
      return '0,0%';
    }
    final bps = (p * BigInt.from(1000)) ~/ t;
    return '${bps ~/ BigInt.from(10)},${bps % BigInt.from(10)}%';
  }

  static double barFraction(String part, String total) {
    final p = cents(part).abs();
    final t = cents(total).abs();
    if (t == BigInt.zero) {
      return 0;
    }
    return p.toDouble() / t.toDouble();
  }
}
