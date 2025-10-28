import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../domain/models/session_models.dart';
import '../history/history_page.dart';
import '../settings/settings_page.dart';
import '../settings/settings_view_model.dart';
import '../shared/latency_metrics_view.dart';
import 'home_view_model.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  static const String routeName = 'home';
  static const String routePath = '';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(homeViewModelProvider);
    final settingsState = ref.watch(settingsViewModelProvider);
    final fallbackDeployment = settingsState.realtimeDeployment;
    final modelLabel = state.sessionStatus == SessionStatus.active
        ? (state.modelLabel.isEmpty ? fallbackDeployment : state.modelLabel)
        : fallbackDeployment;

    ref.listen<HomeState>(homeViewModelProvider, (_, next) {
      if (next.errorMessage != null) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(next.errorMessage!)));
      }
    });

    return Scaffold(
      appBar: AppBar(
        title: const Text('TransAnd Realtime'),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => context.pushNamed(SettingsPage.routeName),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Card(
              child: ListTile(
                leading: Icon(
                  state.sessionStatus == SessionStatus.active
                      ? Icons.record_voice_over
                      : Icons.mic_off_outlined,
                  color: state.sessionStatus == SessionStatus.active
                      ? Colors.green
                      : null,
                ),
                title: Text(state.sessionStatus.label),
                subtitle: Text(
                  'Deployment: ${modelLabel.isEmpty ? '—' : modelLabel}',
                ),
                trailing: FilledButton.icon(
                  onPressed: () async {
                    final viewModel = ref.read(homeViewModelProvider.notifier);
                    if (state.sessionStatus == SessionStatus.active) {
                      await viewModel.stopSession();
                    } else {
                      await viewModel.startSession();
                    }
                  },
                  icon: Icon(
                    state.sessionStatus == SessionStatus.active
                        ? Icons.stop
                        : Icons.play_arrow,
                  ),
                  label: Text(
                    state.sessionStatus == SessionStatus.active
                        ? 'End'
                        : 'Start',
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            LatencyMetricsView(metrics: state.metrics),
            const SizedBox(height: 12),
            Expanded(
              child: ListView.builder(
                itemCount: state.timeline.length,
                itemBuilder: (context, index) {
                  final item = state.timeline[index];
                  return Card(
                    child: ListTile(
                      title: Text(item.translation),
                      subtitle: Text(item.transcript),
                      trailing: Text(item.timestampLabel),
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 12),
            Center(
              child: TextButton(
                onPressed: () {
                  context.pushNamed(HistoryPage.routeName);
                },
                child: const Text('View history'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
