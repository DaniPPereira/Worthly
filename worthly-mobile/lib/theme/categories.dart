import 'package:worthly_mobile/api/models.dart';

List<Category> parentCategories(List<Category> categories) {
  return categories
      .where((item) => item.system && item.code != null && item.code != 'uncategorized' && !(item.code?.startsWith('transfer.') ?? false))
      .toList();
}

String defaultParentId(List<Category> categories) {
  final parents = parentCategories(categories);
  return parents.where((item) => item.code == 'expense.other').firstOrNull?.id ?? parents.firstOrNull?.id ?? '';
}

List<Category> customCategories(List<Category> categories) {
  return categories.where((item) => !item.system).toList();
}
