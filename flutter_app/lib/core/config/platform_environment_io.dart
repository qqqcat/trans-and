import 'dart:io';

String? getPlatformEnvironmentValue(String key) {
  final value = Platform.environment[key]?.trim();
  if (value == null || value.isEmpty) {
    return null;
  }
  return value;
}
