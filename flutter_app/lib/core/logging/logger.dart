import 'package:logger/logger.dart';

final Logger _logger = Logger(
  printer: PrettyPrinter(
    methodCount: 0,
    colors: true,
    lineLength: 120,
  ),
);

Future<void> configureLogger() async {
  // Placeholder for future log destinations (e.g. Sentry, Crashlytics).
}

void logInfo(String message, [Map<String, Object?> context = const {}]) {
  _logger.i({'message': message, 'context': context});
}

void logWarning(String message, [Map<String, Object?> context = const {}]) {
  _logger.w({'message': message, 'context': context});
}

void logError(String message, {Object? error, StackTrace? stackTrace}) {
  _logger.e(message, error: error, stackTrace: stackTrace);
}
