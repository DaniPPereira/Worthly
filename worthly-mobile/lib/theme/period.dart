class Period {
  static String monthKey(String timeZone, [DateTime? at]) {
    final now = at ?? DateTime.now().toUtc();
    final local = _inZone(now, timeZone);
    return '${local.year.toString().padLeft(4, '0')}-${local.month.toString().padLeft(2, '0')}';
  }

  static List<String> previousMonths(String timeZone, int count, [DateTime? at]) {
    final current = monthKey(timeZone, at);
    var year = int.parse(current.substring(0, 4));
    var month = int.parse(current.substring(5, 7));
    final keys = <String>[];
    for (var i = 0; i < count; i++) {
      keys.insert(0, '${year.toString().padLeft(4, '0')}-${month.toString().padLeft(2, '0')}');
      month -= 1;
      if (month == 0) {
        month = 12;
        year -= 1;
      }
    }
    return keys;
  }

  static ({String from, String to}) monthRange(String monthKey) {
    final year = int.parse(monthKey.substring(0, 4));
    final month = int.parse(monthKey.substring(5, 7));
    final last = DateTime.utc(year, month + 1, 0).day;
    return (from: '$monthKey-01', to: '$monthKey-${last.toString().padLeft(2, '0')}');
  }

  static String monthLabel(String monthKey) {
    const names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    final month = int.parse(monthKey.substring(5, 7));
    return names[month - 1];
  }

  static String instant(String? iso, String timeZone) {
    if (iso == null || iso.isEmpty) {
      return 'Never';
    }
    final local = _inZone(DateTime.parse(iso).toUtc(), timeZone);
    return '${local.day} ${monthLabel(monthKey(timeZone, local.toUtc()))}, ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  static String day(String iso, String timeZone) {
    final local = _inZone(DateTime.parse(iso).toUtc(), timeZone);
    return '${local.day} ${monthLabel(monthKey(timeZone, local.toUtc()))}';
  }

  static DateTime _inZone(DateTime utc, String timeZone) {
    if (timeZone == 'UTC') {
      return DateTime.utc(utc.year, utc.month, utc.day, utc.hour, utc.minute, utc.second);
    }
    return utc.toLocal();
  }
}
