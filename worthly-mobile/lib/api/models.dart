class Owner {
  const Owner({
    required this.id,
    required this.email,
    required this.reportingTimezone,
    required this.reportingCurrency,
  });

  final String id;
  final String email;
  final String reportingTimezone;
  final String reportingCurrency;

  factory Owner.fromJson(Map<String, dynamic> json) {
    return Owner(
      id: json['id'] as String? ?? '',
      email: json['email'] as String,
      reportingTimezone: json['reportingTimezone'] as String,
      reportingCurrency: json['reportingCurrency'] as String,
    );
  }
}

class Money {
  const Money({required this.amount, required this.currency});

  final String amount;
  final String currency;

  factory Money.fromJson(Map<String, dynamic> json) {
    return Money(amount: json['amount'] as String, currency: json['currency'] as String);
  }
}

class Connection {
  const Connection({
    required this.id,
    required this.provider,
    required this.status,
    this.institutionName,
    this.institutionCountry,
    this.lastSuccessfulSyncAt,
    this.consentExpiresAt,
  });

  final String id;
  final String provider;
  final String status;
  final String? institutionName;
  final String? institutionCountry;
  final String? lastSuccessfulSyncAt;
  final String? consentExpiresAt;

  String get label => provider == 'TRADING_212' ? 'Trading 212' : (institutionName ?? provider);

  factory Connection.fromJson(Map<String, dynamic> json) {
    return Connection(
      id: json['id'] as String,
      provider: json['provider'] as String,
      status: json['status'] as String,
      institutionName: json['institutionName'] as String?,
      institutionCountry: json['institutionCountry'] as String?,
      lastSuccessfulSyncAt: json['lastSuccessfulSyncAt'] as String?,
      consentExpiresAt: json['consentExpiresAt'] as String?,
    );
  }
}

class BankChoice {
  const BankChoice({required this.name, required this.country});

  final String name;
  final String country;

  factory BankChoice.fromJson(Map<String, dynamic> json) {
    return BankChoice(name: json['name'] as String, country: json['country'] as String);
  }
}

class Account {
  const Account({
    required this.id,
    required this.provider,
    required this.displayName,
    required this.type,
    required this.currency,
    this.maskedIdentifier,
    required this.includedInLiquidCash,
  });

  final String id;
  final String provider;
  final String displayName;
  final String type;
  final String currency;
  final String? maskedIdentifier;
  final bool includedInLiquidCash;

  factory Account.fromJson(Map<String, dynamic> json) {
    return Account(
      id: json['id'] as String,
      provider: json['provider'] as String,
      displayName: json['displayName'] as String,
      type: json['type'] as String,
      currency: json['currency'] as String,
      maskedIdentifier: json['maskedIdentifier'] as String?,
      includedInLiquidCash: json['includedInLiquidCash'] as bool? ?? true,
    );
  }
}

class Balance {
  const Balance({required this.money, required this.observedAt, required this.usedForLiquidCash});

  final Money money;
  final String observedAt;
  final bool usedForLiquidCash;

  factory Balance.fromJson(Map<String, dynamic> json) {
    return Balance(
      money: Money.fromJson(json['money'] as Map<String, dynamic>),
      observedAt: json['observedAt'] as String,
      usedForLiquidCash: json['usedForLiquidCash'] as bool? ?? false,
    );
  }
}

class Tx {
  const Tx({
    required this.id,
    required this.accountId,
    required this.direction,
    required this.lifecycleStatus,
    required this.economicType,
    required this.money,
    this.merchant,
    this.description,
    this.location,
    required this.reportingAt,
    this.categoryId,
    this.notes,
    this.transferMatchId,
  });

  final String id;
  final String accountId;
  final String direction;
  final String lifecycleStatus;
  final String economicType;
  final Money money;
  final String? merchant;
  final String? description;
  final String? location;
  final String reportingAt;
  final String? categoryId;
  final String? notes;
  final String? transferMatchId;

  bool get credit => direction == 'CREDIT';
  bool get pending => lifecycleStatus == 'PENDING';
  bool get transfer => economicType == 'INTERNAL_TRANSFER' || transferMatchId != null;
  String get title => merchant ?? description ?? 'Transaction';

  factory Tx.fromJson(Map<String, dynamic> json) {
    return Tx(
      id: json['id'] as String,
      accountId: json['accountId'] as String,
      direction: json['direction'] as String,
      lifecycleStatus: json['lifecycleStatus'] as String,
      economicType: json['economicType'] as String,
      money: Money.fromJson(json['money'] as Map<String, dynamic>),
      merchant: json['merchant'] as String?,
      description: json['description'] as String?,
      location: json['location'] as String?,
      reportingAt: json['reportingAt'] as String,
      categoryId: json['categoryId'] as String?,
      notes: json['notes'] as String?,
      transferMatchId: json['transferMatchId'] as String?,
    );
  }

  Tx copyWith({
    String? categoryId,
    String? notes,
    String? transferMatchId,
    String? economicType,
    bool clearTransfer = false,
  }) {
    return Tx(
      id: id,
      accountId: accountId,
      direction: direction,
      lifecycleStatus: lifecycleStatus,
      economicType: economicType ?? this.economicType,
      money: money,
      merchant: merchant,
      description: description,
      location: location,
      reportingAt: reportingAt,
      categoryId: categoryId ?? this.categoryId,
      notes: notes ?? this.notes,
      transferMatchId: clearTransfer ? null : (transferMatchId ?? this.transferMatchId),
    );
  }
}

class TxPage {
  const TxPage({required this.items, required this.total});

  final List<Tx> items;
  final int total;

  factory TxPage.fromJson(Map<String, dynamic> json) {
    return TxPage(
      items: (json['items'] as List<dynamic>).map((item) => Tx.fromJson(item as Map<String, dynamic>)).toList(),
      total: (json['total'] as num).toInt(),
    );
  }
}

class Category {
  const Category({required this.id, this.code, required this.label});

  final String id;
  final String? code;
  final String label;

  factory Category.fromJson(Map<String, dynamic> json) {
    return Category(id: json['id'] as String, code: json['code'] as String?, label: json['label'] as String);
  }
}

class WealthRow {
  const WealthRow({required this.currency, required this.liquidCash, required this.investmentValue, required this.netWorth});

  final String currency;
  final String liquidCash;
  final String investmentValue;
  final String netWorth;

  factory WealthRow.fromJson(Map<String, dynamic> json) {
    return WealthRow(
      currency: json['currency'] as String,
      liquidCash: json['liquidCash'] as String,
      investmentValue: json['investmentValue'] as String,
      netWorth: json['netWorth'] as String,
    );
  }
}

class WealthSummary {
  const WealthSummary({required this.asOf, required this.timezone, required this.totalsByCurrency});

  final String asOf;
  final String timezone;
  final List<WealthRow> totalsByCurrency;

  factory WealthSummary.fromJson(Map<String, dynamic> json) {
    return WealthSummary(
      asOf: json['asOf'] as String,
      timezone: json['timezone'] as String,
      totalsByCurrency: (json['totalsByCurrency'] as List<dynamic>)
          .map((item) => WealthRow.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class MonthlyRow {
  const MonthlyRow({
    required this.currency,
    required this.income,
    required this.expenses,
    required this.invested,
    required this.savings,
    this.savingsRate,
    this.savingsRateReason,
  });

  final String currency;
  final String income;
  final String expenses;
  final String invested;
  final String savings;
  final String? savingsRate;
  final String? savingsRateReason;

  factory MonthlyRow.fromJson(Map<String, dynamic> json) {
    return MonthlyRow(
      currency: json['currency'] as String,
      income: json['income'] as String,
      expenses: json['expenses'] as String,
      invested: json['invested'] as String,
      savings: json['savings'] as String,
      savingsRate: json['savingsRate'] as String?,
      savingsRateReason: json['savingsRateReason'] as String?,
    );
  }
}

class MonthlyAnalytics {
  const MonthlyAnalytics({required this.month, required this.totalsByCurrency});

  final String month;
  final List<MonthlyRow> totalsByCurrency;

  factory MonthlyAnalytics.fromJson(Map<String, dynamic> json) {
    return MonthlyAnalytics(
      month: json['month'] as String,
      totalsByCurrency: (json['totalsByCurrency'] as List<dynamic>)
          .map((item) => MonthlyRow.fromJson(item as Map<String, dynamic>))
          .toList(),
    );
  }
}

class InvestmentSummary {
  const InvestmentSummary({required this.totalsByCurrency, this.observedAt});

  final List<({String currency, String cash, String portfolioValue})> totalsByCurrency;
  final String? observedAt;

  factory InvestmentSummary.fromJson(Map<String, dynamic> json) {
    return InvestmentSummary(
      observedAt: json['observedAt'] as String?,
      totalsByCurrency: (json['totalsByCurrency'] as List<dynamic>).map((item) {
        final map = item as Map<String, dynamic>;
        return (
          currency: map['currency'] as String,
          cash: map['cash'] as String,
          portfolioValue: map['portfolioValue'] as String,
        );
      }).toList(),
    );
  }
}

class Position {
  const Position({required this.instrumentKey, this.ticker, this.quantity, this.marketValue});

  final String instrumentKey;
  final String? ticker;
  final String? quantity;
  final Money? marketValue;

  factory Position.fromJson(Map<String, dynamic> json) {
    return Position(
      instrumentKey: json['instrumentKey'] as String,
      ticker: json['ticker'] as String?,
      quantity: json['quantity'] as String?,
      marketValue: json['marketValue'] == null ? null : Money.fromJson(json['marketValue'] as Map<String, dynamic>),
    );
  }
}

class AppNotification {
  const AppNotification({required this.id, required this.type, required this.createdAt, this.readAt});

  final String id;
  final String type;
  final String createdAt;
  final String? readAt;

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    return AppNotification(
      id: json['id'] as String,
      type: json['type'] as String,
      createdAt: json['createdAt'] as String,
      readAt: json['readAt'] as String?,
    );
  }
}

class Device {
  const Device({required this.id, this.name, this.platform, this.lastSeenAt, required this.revoked});

  final String id;
  final String? name;
  final String? platform;
  final String? lastSeenAt;
  final bool revoked;

  factory Device.fromJson(Map<String, dynamic> json) {
    return Device(
      id: json['id'] as String,
      name: json['name'] as String?,
      platform: json['platform'] as String?,
      lastSeenAt: json['lastSeenAt'] as String?,
      revoked: json['revoked'] as bool? ?? false,
    );
  }
}

class SyncRun {
  const SyncRun({
    required this.id,
    required this.status,
    required this.startedAt,
    this.finishedAt,
    this.importedCount,
    this.updatedCount,
    this.errorCode,
  });

  final String id;
  final String status;
  final String startedAt;
  final String? finishedAt;
  final int? importedCount;
  final int? updatedCount;
  final String? errorCode;

  factory SyncRun.fromJson(Map<String, dynamic> json) {
    return SyncRun(
      id: json['id'] as String,
      status: json['status'] as String,
      startedAt: json['startedAt'] as String,
      finishedAt: json['finishedAt'] as String?,
      importedCount: (json['importedCount'] as num?)?.toInt(),
      updatedCount: (json['updatedCount'] as num?)?.toInt(),
      errorCode: json['errorCode'] as String?,
    );
  }
}

class SyncRunPage {
  const SyncRunPage({required this.items});

  final List<SyncRun> items;

  factory SyncRunPage.fromJson(Map<String, dynamic> json) {
    return SyncRunPage(
      items: (json['items'] as List<dynamic>).map((item) => SyncRun.fromJson(item as Map<String, dynamic>)).toList(),
    );
  }
}

class TransferMatch {
  const TransferMatch({
    required this.id,
    required this.leftTransactionId,
    required this.rightTransactionId,
    required this.confidence,
    required this.status,
  });

  final String id;
  final String leftTransactionId;
  final String rightTransactionId;
  final int confidence;
  final String status;

  factory TransferMatch.fromJson(Map<String, dynamic> json) {
    return TransferMatch(
      id: json['id'] as String,
      leftTransactionId: json['leftTransactionId'] as String,
      rightTransactionId: json['rightTransactionId'] as String,
      confidence: json['confidence'] as int,
      status: json['status'] as String,
    );
  }
}
