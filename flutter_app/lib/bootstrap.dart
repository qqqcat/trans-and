import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app.dart';
import 'core/config/app_config.dart';
import 'core/logging/logger.dart';
import 'core/services/service_locator.dart';

Future<void> bootstrap() async {
  await configureLogger();
  await AppConfig.load();
  await configureServices();

  runApp(
    const ProviderScope(
      child: TransAndApp(),
    ),
  );
}
