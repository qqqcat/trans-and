import 'app_config_loader_stub.dart'
    if (dart.library.io) 'app_config_loader_io.dart';

Future<Map<String, String>?> loadLocalProperties() => loadLocalPropertiesImpl();
