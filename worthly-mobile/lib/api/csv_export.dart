import 'dart:io';

import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:worthly_mobile/api/worthly_client.dart';

Future<void> shareTransactionsCsv(WorthlyClient client, {String? from, String? to}) async {
  final query = <String>[
    if (from != null && from.isNotEmpty) 'from=$from',
    if (to != null && to.isNotEmpty) 'to=$to',
  ];
  final path = '/exports/transactions.csv${query.isEmpty ? '' : '?${query.join('&')}'}';
  final bytes = await client.bytes(path);
  final dir = await getTemporaryDirectory();
  final file = File('${dir.path}/worthly-transactions.csv');
  await file.writeAsBytes(bytes, flush: true);
  await Share.shareXFiles(
    [XFile(file.path, mimeType: 'text/csv', name: 'transactions.csv')],
  );
}
