import 'dart:io';
import 'dart:math';
import 'package:dio/dio.dart';

/// 最小化 WebRTC SDP 交换测试脚本
/// 按 Azure OpenAI 官方示例：先获取 ephemeral key，再发送 offer SDP 获取 answer SDP
/// 用法：dart run test_webrtc.dart <API_KEY> <RESOURCE_ENDPOINT> <DEPLOYMENT_NAME> <REGION>
///
/// 示例：dart run test_webrtc.dart sk-... https://your-resource.openai.azure.com gpt-realtime-mini eastus2

void main(List<String> args) async {
  if (args.length != 4) {
    print('用法: dart run test_webrtc.dart <API_KEY> <RESOURCE_ENDPOINT> <DEPLOYMENT_NAME> <REGION>');
    print('示例: dart run test_webrtc.dart sk-... https://your-resource.openai.azure.com gpt-realtime-mini eastus2');
    exit(1);
  }

  final apiKey = args[0];
  final resourceEndpoint = args[1];
  final deployment = args[2];
  final region = args[3];

  final dio = Dio(BaseOptions(
    connectTimeout: const Duration(seconds: 30),
    sendTimeout: const Duration(seconds: 30),
    receiveTimeout: const Duration(seconds: 60),
  ));

  try {
    // 步骤1: 获取 ephemeral key
    print('步骤1: 获取 ephemeral key...');
    final sessionsUrl = Uri.parse(
      '$resourceEndpoint/openai/realtimeapi/sessions?api-version=2025-04-01-preview',
    );
    final sessionResp = await dio.postUri(
      sessionsUrl,
      data: {
        'model': deployment,
        'voice': 'verse',
      },
      options: Options(
        headers: {
          'api-key': apiKey,
          'Content-Type': 'application/json',
        },
        responseType: ResponseType.json,
      ),
    );

    final clientSecret = sessionResp.data['client_secret']?['value'] as String?;
    if (clientSecret == null) {
      throw Exception('获取 ephemeral key 失败: ${sessionResp.data}');
    }
    print('✓ 获取 ephemeral key 成功');

    // 步骤2: 立即发送 offer SDP 获取 answer SDP
    print('步骤2: 发送 offer SDP 获取 answer SDP...');
    final webrtcUrl = Uri.parse(
      'https://$region.realtimeapi-preview.ai.azure.com/v1/realtimertc?model=$deployment',
    );

    print('WebRTC URL: $webrtcUrl');

    // Create a proper WebRTC SDP offer based on Azure OpenAI documentation
    const offerSdp = '''v=0
o=- 1234567890 1234567890 IN IP4 127.0.0.1
s=-
t=0 0
a=group:BUNDLE 0
a=msid-semantic: WMS
m=audio 9 UDP/TLS/RTP/SAVPF 111
c=IN IP4 0.0.0.0
a=rtcp:9 IN IP4 0.0.0.0
a=ice-ufrag:abcd
a=ice-pwd:abcd
a=ice-options:trickle
a=fingerprint:sha-256 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00
a=setup:actpass
a=mid:0
a=sendrecv
a=msid:- audio
a=rtcp-mux
a=rtpmap:111 opus/48000/2
a=fmtp:111 minptime=10;useinbandfec=1
a=ssrc:1234567890 cname:abcd
a=ssrc:1234567890 msid: audio
a=ssrc:1234567890 mslabel:audio
a=ssrc:1234567890 label:audio
''';

    print('发送 offer SDP...');
    final sdpResp = await dio.postUri(
      webrtcUrl,
      data: offerSdp,
      options: Options(
        headers: {
          'Authorization': 'Bearer $clientSecret',
          'Content-Type': 'application/sdp',
          'Accept': 'application/sdp',
        },
        responseType: ResponseType.plain,
      ),
    );

    print('SDP 响应状态码: ${sdpResp.statusCode}');
    print('SDP 响应数据类型: ${sdpResp.data.runtimeType}');
    if (sdpResp.data != null) {
      print('SDP 响应数据长度: ${sdpResp.data.toString().length}');
      print('SDP 响应数据预览: ${sdpResp.data.toString().substring(0, min(200, sdpResp.data.toString().length))}');
    }

    if ((sdpResp.statusCode == 200 || sdpResp.statusCode == 201) && sdpResp.data is String) {
      print('✓ SDP 交换成功！');
      print('Answer SDP:');
      print(sdpResp.data);
    } else {
      print('✗ SDP 交换失败: ${sdpResp.statusCode} ${sdpResp.data}');
    }
  } on DioException catch (e) {
    print('✗ 请求失败: ${e.message}');
    if (e.response != null) {
      print('状态码: ${e.response?.statusCode}');
      print('响应: ${e.response?.data}');
    }
  } catch (e) {
    print('✗ 错误: $e');
  }
}