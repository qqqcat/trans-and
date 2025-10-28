import 'package:flutter/material.dart';

import '../../domain/models/session_models.dart';

class LatencyMetricsView extends StatelessWidget {
  const LatencyMetricsView({super.key, required this.metrics});

  final LatencyMetrics metrics;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            _MetricTile(label: 'ASR', value: metrics.asrMs),
            _MetricTile(label: 'Translate', value: metrics.translationMs),
            _MetricTile(label: 'TTS', value: metrics.ttsMs),
          ],
        ),
      ),
    );
  }
}

class _MetricTile extends StatelessWidget {
  const _MetricTile({required this.label, required this.value});

  final String label;
  final int value;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      children: [
        Text(label, style: textTheme.labelMedium),
        const SizedBox(height: 4),
        Text('$value ms', style: textTheme.titleMedium),
      ],
    );
  }
}
