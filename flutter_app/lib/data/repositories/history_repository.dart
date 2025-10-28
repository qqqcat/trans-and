import 'dart:async';

import '../../domain/models/session_models.dart';

class HistoryRepository {
  final _controller = StreamController<List<HistoryEntry>>.broadcast();
  final List<HistoryEntry> _entries = [];

  Stream<List<HistoryEntry>> observeHistory() => _controller.stream;

  Future<void> addEntry(HistoryEntry entry) async {
    _entries.add(entry);
    _controller.add(List.unmodifiable(_entries.reversed));
  }

  Future<void> seedDemoData() async {
    // No-op: real data is captured during live sessions.
  }
}
