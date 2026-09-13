class Owner {
  const Owner({
    required this.email,
    required this.reportingTimezone,
    required this.reportingCurrency,
  });

  final String email;
  final String reportingTimezone;
  final String reportingCurrency;

  factory Owner.fromJson(Map<String, dynamic> json) {
    return Owner(
      email: json['email'] as String,
      reportingTimezone: json['reportingTimezone'] as String,
      reportingCurrency: json['reportingCurrency'] as String,
    );
  }
}
