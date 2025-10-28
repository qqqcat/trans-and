enum SessionStatus {
  idle('Idle'),
  connecting('Connecting'),
  active('Active');

  const SessionStatus(this.label);
  final String label;
}

class LatencyMetrics {
  const LatencyMetrics({
    this.asrMs = 0,
    this.translationMs = 0,
    this.ttsMs = 0,
  });

  final int asrMs;
  final int translationMs;
  final int ttsMs;
}

class HistoryEntry {
  const HistoryEntry({
    required this.id,
    required this.sourceText,
    required this.targetText,
    required this.timestamp,
  });

  final String id;
  final String sourceText;
  final String targetText;
  final DateTime timestamp;

  String get timestampLabel =>
      '${timestamp.hour.toString().padLeft(2, '0')}:${timestamp.minute.toString().padLeft(2, '0')}';
}
