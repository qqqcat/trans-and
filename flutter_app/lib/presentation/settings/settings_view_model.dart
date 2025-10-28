import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/logging/logger.dart';
import '../../core/services/service_locator.dart';
import '../../data/repositories/settings_repository.dart';

final settingsViewModelProvider =
    StateNotifierProvider<SettingsViewModel, SettingsState>((ref) {
      final repository = ref.read(settingsRepositoryProvider);
      return SettingsViewModel(repository)..load();
    });

class SettingsViewModel extends StateNotifier<SettingsState> {
  SettingsViewModel(this._repository) : super(const SettingsState());

  final SettingsRepository _repository;

  Future<void> load() async {
    try {
      final endpoint = await _repository.getApiEndpoint();
      final realtimeDeployment = await _repository.getRealtimeDeployment();
      final responsesDeployment = await _repository.getResponsesDeployment();
      final webRtcUrl = await _repository.getWebRtcUrl();

      state = state.copyWith(
        apiEndpoint: endpoint,
        realtimeDeployment: realtimeDeployment,
        responsesDeployment: responsesDeployment,
        webRtcUrl: webRtcUrl,
        endpointError: null,
        realtimeDeploymentError: null,
        responsesDeploymentError: null,
        webRtcUrlError: null,
      );
    } catch (error, stack) {
      logError('Failed to load settings', error: error, stackTrace: stack);
    }
  }

  void onEndpointChanged(String value) {
    state = state.copyWith(apiEndpoint: value, endpointError: null);
  }

  void onRealtimeDeploymentChanged(String value) {
    state = state.copyWith(
      realtimeDeployment: value,
      realtimeDeploymentError: null,
    );
  }

  void onResponsesDeploymentChanged(String value) {
    state = state.copyWith(
      responsesDeployment: value,
      responsesDeploymentError: null,
    );
  }

  void onWebRtcUrlChanged(String value) {
    state = state.copyWith(webRtcUrl: value, webRtcUrlError: null);
  }

  Future<void> save() async {
    final endpoint = (state.apiEndpoint ?? '').trim();
    final realtimeDeployment = state.realtimeDeployment.trim();
    final responsesDeployment = state.responsesDeployment.trim();
    final webRtcUrl = state.webRtcUrl.trim();

    String? endpointError;
    String? realtimeError;
    String? responsesError;
    String? webRtcError;

    if (endpoint.isEmpty) {
      endpointError = 'Endpoint is required';
    }
    if (realtimeDeployment.isEmpty) {
      realtimeError = 'Realtime deployment is required';
    }
    if (responsesDeployment.isEmpty) {
      responsesError = 'Responses deployment is required';
    }
    if (webRtcUrl.isEmpty) {
      webRtcError = 'Realtime WebRTC URL is required';
    }

    if (endpointError != null ||
        realtimeError != null ||
        responsesError != null ||
        webRtcError != null) {
      state = state.copyWith(
        endpointError: endpointError,
        realtimeDeploymentError: realtimeError,
        responsesDeploymentError: responsesError,
        webRtcUrlError: webRtcError,
      );
      return;
    }

    await _repository.setApiEndpoint(endpoint);
    await _repository.setRealtimeDeployment(realtimeDeployment);
    await _repository.setResponsesDeployment(responsesDeployment);
    await _repository.setWebRtcUrl(webRtcUrl);

    state = state.copyWith(
      apiEndpoint: endpoint,
      realtimeDeployment: realtimeDeployment,
      responsesDeployment: responsesDeployment,
      webRtcUrl: webRtcUrl,
      endpointError: null,
      realtimeDeploymentError: null,
      responsesDeploymentError: null,
      webRtcUrlError: null,
    );
  }
}

class SettingsState {
  const SettingsState({
    this.apiEndpoint,
    this.realtimeDeployment = '',
    this.responsesDeployment = '',
    this.webRtcUrl = '',
    this.endpointError,
    this.realtimeDeploymentError,
    this.responsesDeploymentError,
    this.webRtcUrlError,
    this.availableRealtimeDeployments = const [
      'gpt-realtime',
      'gpt-realtime-mini',
      'gpt-4o-realtime-preview',
    ],
  });

  final String? apiEndpoint;
  final String realtimeDeployment;
  final String responsesDeployment;
  final String webRtcUrl;
  final String? endpointError;
  final String? realtimeDeploymentError;
  final String? responsesDeploymentError;
  final String? webRtcUrlError;
  final List<String> availableRealtimeDeployments;

  SettingsState copyWith({
    String? apiEndpoint,
    String? realtimeDeployment,
    String? responsesDeployment,
    String? webRtcUrl,
    String? endpointError,
    String? realtimeDeploymentError,
    String? responsesDeploymentError,
    String? webRtcUrlError,
    List<String>? availableRealtimeDeployments,
  }) {
    return SettingsState(
      apiEndpoint: apiEndpoint ?? this.apiEndpoint,
      realtimeDeployment: realtimeDeployment ?? this.realtimeDeployment,
      responsesDeployment: responsesDeployment ?? this.responsesDeployment,
      webRtcUrl: webRtcUrl ?? this.webRtcUrl,
      endpointError: endpointError,
      realtimeDeploymentError: realtimeDeploymentError,
      responsesDeploymentError: responsesDeploymentError,
      webRtcUrlError: webRtcUrlError,
      availableRealtimeDeployments:
          availableRealtimeDeployments ?? this.availableRealtimeDeployments,
    );
  }
}
