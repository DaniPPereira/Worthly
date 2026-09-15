import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:worthly_mobile/api/models.dart';
import 'package:worthly_mobile/api/worthly_client.dart';
import 'package:worthly_mobile/features/session/session.dart';

class ShellData {
  const ShellData({
    required this.connections,
    required this.notifications,
    required this.categories,
    required this.accounts,
  });

  final List<Connection> connections;
  final List<AppNotification> notifications;
  final List<Category> categories;
  final List<Account> accounts;
}

final shellDataProvider = FutureProvider<ShellData>((ref) async {
  final client = ref.watch(worthlyClientProvider);
  final connections = await client.get('/connections', (json) => listOf(json, Connection.fromJson));
  final notifications = await client.get('/notifications', (json) => listOf(json, AppNotification.fromJson));
  final categories = await client.get('/categories', (json) => listOf(json, Category.fromJson));
  final accounts = await client.get('/accounts', (json) => listOf(json, Account.fromJson));
  return ShellData(
    connections: connections,
    notifications: notifications,
    categories: categories,
    accounts: accounts,
  );
});

final tabIndexProvider = StateProvider<int>((ref) => 0);
final connectionsOpenProvider = StateProvider<bool>((ref) => false);
final connectionResultProvider = StateProvider<String?>((ref) => null);

class TransactionFocus {
  const TransactionFocus({this.categoryId, this.from, this.to, this.label});

  final String? categoryId;
  final String? from;
  final String? to;
  final String? label;
}

final transactionFocusProvider = StateProvider<TransactionFocus?>((ref) => null);

String connectionStatusLabel(String status) {
  switch (status) {
    case 'ACTIVE':
      return 'Active';
    case 'REAUTH_REQUIRED':
      return 'Reauth required';
    case 'CONFIGURATION_REQUIRED':
      return 'Configuration required';
    case 'ERROR':
      return 'Error';
    case 'DISABLED':
      return 'Disabled';
    default:
      return status;
  }
}
