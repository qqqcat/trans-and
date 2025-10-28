import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'settings_view_model.dart';

class SettingsPage extends ConsumerWidget {
  const SettingsPage({super.key});

  static const String routeName = 'settings';
  static const String routePath = 'settings';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(settingsViewModelProvider);
    final viewModel = ref.read(settingsViewModelProvider.notifier);

    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextFormField(
            initialValue: state.apiEndpoint ?? '',
            decoration: InputDecoration(
              labelText: 'Azure OpenAI endpoint',
              helperText: '例如 https://<resource>.openai.azure.com/',
              errorText: state.endpointError,
            ),
            onChanged: viewModel.onEndpointChanged,
          ),
          const SizedBox(height: 12),
          TextFormField(
            initialValue: state.realtimeDeployment,
            decoration: InputDecoration(
              labelText: 'Realtime deployment name',
              helperText: 'Azure Portal -> Model deployments 中的实时部署名称',
              errorText: state.realtimeDeploymentError,
            ),
            onChanged: viewModel.onRealtimeDeploymentChanged,
          ),
          const SizedBox(height: 12),
          TextFormField(
            initialValue: state.responsesDeployment,
            decoration: InputDecoration(
              labelText: 'Responses deployment name',
              helperText: '供 /openai/v1/responses 使用的部署名称',
              errorText: state.responsesDeploymentError,
            ),
            onChanged: viewModel.onResponsesDeploymentChanged,
          ),
          const SizedBox(height: 12),
          TextFormField(
            initialValue: state.webRtcUrl,
            decoration: InputDecoration(
              labelText: 'Realtime WebRTC URL',
              helperText:
                  '例如 https://eastus2.realtimeapi-preview.ai.azure.com/v1/realtimertc',
              errorText: state.webRtcUrlError,
            ),
            onChanged: viewModel.onWebRtcUrlChanged,
          ),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: () async {
              await viewModel.save();
              if (context.mounted) {
                ScaffoldMessenger.of(
                  context,
                ).showSnackBar(const SnackBar(content: Text('Settings saved')));
              }
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }
}
