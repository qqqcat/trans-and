import 'platform_environment_stub.dart'
    if (dart.library.io) 'platform_environment_io.dart';

String? readPlatformEnvironment(String key) =>
    getPlatformEnvironmentValue(key);
