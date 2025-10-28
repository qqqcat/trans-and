import 'dart:io';

import 'package:path/path.dart' as p;

Future<Map<String, String>?> loadLocalPropertiesImpl() async {
  final candidates = <File>[
    File(p.join(Directory.current.path, 'local.properties')),
    File(p.join(Directory.current.path, '..', 'local.properties')),
    File(p.join(Directory.current.parent.path, 'local.properties')),
  ];
  for (final file in candidates) {
    if (await file.exists()) {
      return _parseProperties(file);
    }
  }
  return null;
}

Future<Map<String, String>> _parseProperties(File file) async {
  final lines = await file.readAsLines();
  final result = <String, String>{};
  for (final line in lines) {
    final trimmed = line.trim();
    if (trimmed.isEmpty || trimmed.startsWith('#')) continue;
    final index = trimmed.indexOf('=');
    if (index == -1) continue;
    final key = trimmed.substring(0, index).trim();
    final value = trimmed.substring(index + 1).trim();
    result[key] = value;
  }
  return result;
}
