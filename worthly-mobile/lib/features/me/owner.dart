class Owner {
  const Owner({
    this.name,
    required this.email,
    required this.reportingTimezone,
    required this.reportingCurrency,
  });

  final String? name;
  final String email;
  final String reportingTimezone;
  final String reportingCurrency;

  factory Owner.fromJson(Map<String, dynamic> json) {
    return Owner(
      name: json['name'] as String?,
      email: json['email'] as String,
      reportingTimezone: json['reportingTimezone'] as String,
      reportingCurrency: json['reportingCurrency'] as String,
    );
  }
}
