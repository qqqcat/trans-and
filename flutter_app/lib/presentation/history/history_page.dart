import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/services/service_locator.dart';
import '../../domain/models/session_models.dart';

class HistoryPage extends ConsumerWidget {
  const HistoryPage({super.key});

  static const String routeName = 'history';
  static const String routePath = 'history';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final historyRepository = ref.watch(historyRepositoryProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('History')),
      body: FutureBuilder<void>(
        future: historyRepository.seedDemoData(),
        builder: (context, snapshot) {
          return StreamBuilder<List<HistoryEntry>>(
            stream: historyRepository.observeHistory(),
            builder: (context, snapshot) {
              final entries = snapshot.data ?? const [];
              if (entries.isEmpty) {
                return const Center(child: Text('No history yet'));
              }
              return ListView.builder(
                itemCount: entries.length,
                itemBuilder: (context, index) {
                  final entry = entries[index];
                  return ListTile(
                    title: Text(entry.targetText),
                    subtitle: Text(entry.sourceText),
                    trailing: Text(entry.timestampLabel),
                  );
                },
              );
            },
          );
        },
      ),
    );
  }
}
