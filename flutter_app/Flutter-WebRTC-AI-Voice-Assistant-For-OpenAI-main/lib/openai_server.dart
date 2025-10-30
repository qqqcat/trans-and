import 'dart:convert';
import 'package:http/http.dart' as http;

class OpenAIService {
  // Azure OpenAI 配置
  static const String azureEndpoint = 'https://cater-mh074r36-eastus2.openai.azure.com/';
  static const String apiKey = '***AZURE_OPENAI_API_KEY_REMOVED***';
  static const String realtimeDeployment = 'gpt-realtime-mini';

  static Future<String> getEphemeralToken() async {
    try {
      final sessionsUrl = Uri.parse(
        '$azureEndpoint/openai/realtimeapi/sessions?api-version=2025-04-01-preview',
      );

      final response = await http.post(
        sessionsUrl,
        headers: {
          'api-key': apiKey,
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'model': realtimeDeployment,
          'voice': 'verse',
        }),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final clientSecret = data['client_secret']?['value'] ?? data['token'];
        if (clientSecret == null) {
          throw Exception('No client_secret or token in response');
        }
        return clientSecret;
      } else {
        throw Exception(
          'Failed to get ephemeral token: ${response.statusCode} - ${response.body}',
        );
      }
    } catch (e) {
      throw Exception('Error getting ephemeral token: $e');
    }
  }
}
