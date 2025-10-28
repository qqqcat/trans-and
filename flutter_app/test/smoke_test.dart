import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:transand_flutter/app/app.dart';

void main() {
  testWidgets('app builds root router', (tester) async {
    await tester.pumpWidget(
      const ProviderScope(
        child: TransAndApp(),
      ),
    );
    expect(find.byType(TransAndApp), findsOneWidget);
  });
}
